package httpapi

import (
	"crypto/subtle"
	"encoding/json"
	"errors"
	"io"
	"net/http"
	"strings"
	"time"

	"example.com/ai-chat-app/gateway/internal/config"
	"example.com/ai-chat-app/gateway/internal/sandbox"
	"example.com/ai-chat-app/gateway/internal/search"
)

const serviceName = "ai-chat-gateway"
const bearerPrefix = "Bearer "
const minSandboxTimeoutSeconds = 1
const maxSandboxTimeoutSeconds = 10

type Options struct {
	APIToken            string
	MaxRequestBodyBytes int64
	MaxSandboxCodeBytes int
}

type HealthResponse struct {
	Status  string `json:"status"`
	Service string `json:"service"`
	Version string `json:"version"`
	Time    string `json:"time"`
}

type ToolManifestResponse struct {
	Version     int              `json:"version"`
	GeneratedAt string           `json:"generatedAt"`
	Tools       []ToolDefinition `json:"tools"`
}

type ToolDefinition struct {
	Name            string         `json:"name"`
	Description     string         `json:"description"`
	PermissionLevel string         `json:"permissionLevel"`
	InputSchema     map[string]any `json:"inputSchema"`
	OutputSchema    map[string]any `json:"outputSchema,omitempty"`
	TimeoutSeconds  int            `json:"timeoutSeconds,omitempty"`
}

type SearchRequest struct {
	Query string `json:"query"`
}

type SandboxRunRequest struct {
	Language       string `json:"language"`
	Code           string `json:"code"`
	TimeoutSeconds int    `json:"timeoutSeconds"`
}

type GatewayError struct {
	Code      string         `json:"code"`
	Message   string         `json:"message"`
	RequestID *string        `json:"requestId"`
	Details   map[string]any `json:"details"`
}

func NewMux(version string) http.Handler {
	return NewMuxWithDependencies(version, sandbox.NewDockerRunner(), search.MockAdapter{}, Options{})
}

func NewMuxWithSandboxRunner(version string, sandboxRunner sandbox.Runner) http.Handler {
	return NewMuxWithDependencies(version, sandboxRunner, search.MockAdapter{}, Options{})
}

func OptionsFromConfig(cfg config.Config) Options {
	return Options{
		APIToken:            cfg.APIToken,
		MaxRequestBodyBytes: cfg.MaxRequestBodyBytes,
		MaxSandboxCodeBytes: cfg.MaxSandboxCodeBytes,
	}
}

func NewMuxWithDependencies(version string, sandboxRunner sandbox.Runner, searchAdapter search.Adapter, options Options) http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("GET /health", healthHandler(version))
	mux.HandleFunc("GET /v1/tools/manifest", toolManifestHandler())
	mux.Handle("POST /v1/search", protectedHandler(searchHandler(searchAdapter), options))
	mux.Handle("POST /v1/sandbox/run", protectedHandler(sandboxRunHandler(sandboxRunner, options), options))
	return mux
}

func healthHandler(version string) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		writeJSON(w, http.StatusOK, HealthResponse{
			Status:  "ok",
			Service: serviceName,
			Version: version,
			Time:    time.Now().UTC().Format(time.RFC3339),
		})
	}
}

func toolManifestHandler() http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		writeJSON(w, http.StatusOK, ToolManifestResponse{
			Version:     1,
			GeneratedAt: time.Now().UTC().Format(time.RFC3339),
			Tools: []ToolDefinition{
				{
					Name:            "web_search",
					Description:     "通过已配置的 Gateway search adapter 搜索网页或新闻来源。",
					PermissionLevel: "Network",
					InputSchema: map[string]any{
						"type":     "object",
						"required": []string{"query"},
						"properties": map[string]any{
							"query": map[string]any{"type": "string", "minLength": 1},
						},
					},
					OutputSchema: map[string]any{
						"type": "object",
					},
					TimeoutSeconds: 20,
				},
				{
					Name:            "code_sandbox",
					Description:     "启用后在远端 Sandbox 运行短代码片段。",
					PermissionLevel: "Execute",
					InputSchema: map[string]any{
						"type":     "object",
						"required": []string{"language", "code"},
						"properties": map[string]any{
							"language":       map[string]any{"enum": []string{"python"}},
							"code":           map[string]any{"type": "string", "minLength": 1},
							"timeoutSeconds": map[string]any{"type": "integer", "minimum": 1, "maximum": 10},
						},
					},
					OutputSchema: map[string]any{
						"type": "object",
					},
					TimeoutSeconds: 10,
				},
			},
		})
	}
}

func searchHandler(adapter search.Adapter) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		var request SearchRequest
		if err := decodeJSON(w, r, &request); err != nil {
			if errors.Is(err, http.ErrBodyReadAfterClose) {
				return
			}
			writeGatewayError(w, http.StatusBadRequest, "invalid_request", "请求 body 必须是有效 JSON。", r.Header.Get("X-Request-Id"))
			return
		}
		request.Query = strings.TrimSpace(request.Query)
		if request.Query == "" {
			writeGatewayError(w, http.StatusBadRequest, "invalid_query", "Search query 不能为空。", r.Header.Get("X-Request-Id"))
			return
		}
		response, err := adapter.Search(r.Context(), request.Query)
		if err != nil {
			writeGatewayError(w, http.StatusServiceUnavailable, "search_unavailable", "Search adapter 不可用。", r.Header.Get("X-Request-Id"))
			return
		}
		writeJSON(w, http.StatusOK, response)
	}
}

func sandboxRunHandler(runner sandbox.Runner, options Options) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		var request SandboxRunRequest
		if err := decodeJSON(w, r, &request); err != nil {
			if errors.Is(err, http.ErrBodyReadAfterClose) {
				return
			}
			writeGatewayError(w, http.StatusBadRequest, "invalid_request", "请求 body 必须是有效 JSON。", r.Header.Get("X-Request-Id"))
			return
		}
		request.Language = strings.TrimSpace(strings.ToLower(request.Language))
		request.Code = strings.TrimSpace(request.Code)
		if request.Language != "python" {
			writeGatewayError(w, http.StatusBadRequest, "unsupported_language", "仅支持 python sandbox 运行。", r.Header.Get("X-Request-Id"))
			return
		}
		if request.Code == "" {
			writeGatewayError(w, http.StatusBadRequest, "invalid_code", "Sandbox code 不能为空。", r.Header.Get("X-Request-Id"))
			return
		}
		if options.MaxSandboxCodeBytes > 0 && len([]byte(request.Code)) > options.MaxSandboxCodeBytes {
			writeGatewayError(w, http.StatusRequestEntityTooLarge, "code_too_large", "Sandbox code 超出大小限制。", r.Header.Get("X-Request-Id"))
			return
		}
		if request.TimeoutSeconds != 0 && (request.TimeoutSeconds < minSandboxTimeoutSeconds || request.TimeoutSeconds > maxSandboxTimeoutSeconds) {
			writeGatewayError(w, http.StatusBadRequest, "invalid_timeout", "Sandbox timeoutSeconds 必须在 1 到 10 秒之间。", r.Header.Get("X-Request-Id"))
			return
		}
		timeout := time.Duration(request.TimeoutSeconds) * time.Second
		response, err := runner.Run(r.Context(), sandbox.Request{
			Language: request.Language,
			Code:     request.Code,
			Timeout:  timeout,
		})
		if err != nil {
			if errors.Is(err, sandbox.ErrUnavailable) {
				writeGatewayError(w, http.StatusServiceUnavailable, "sandbox_unavailable", "Sandbox runner 不可用。", r.Header.Get("X-Request-Id"))
				return
			}
			writeGatewayError(w, http.StatusServiceUnavailable, "sandbox_failed", "Sandbox runner 执行失败。", r.Header.Get("X-Request-Id"))
			return
		}
		writeJSON(w, http.StatusOK, response)
	}
}

func protectedHandler(next http.HandlerFunc, options Options) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		requestID := r.Header.Get("X-Request-Id")
		if strings.TrimSpace(options.APIToken) == "" {
			writeGatewayError(w, http.StatusServiceUnavailable, "gateway_token_required", "Gateway API token 未配置。", requestID)
			return
		}
		if !hasValidToken(r.Header.Get("Authorization"), options.APIToken) {
			writeGatewayError(w, http.StatusUnauthorized, "unauthorized", "Gateway API token 无效。", requestID)
			return
		}
		if options.MaxRequestBodyBytes > 0 {
			r.Body = http.MaxBytesReader(w, r.Body, options.MaxRequestBodyBytes)
		}
		next(w, r)
	})
}

func decodeJSON(w http.ResponseWriter, r *http.Request, target any) error {
	decoder := json.NewDecoder(r.Body)
	if err := decoder.Decode(target); err != nil {
		var maxBytesError *http.MaxBytesError
		if errors.As(err, &maxBytesError) {
			writeGatewayError(w, http.StatusRequestEntityTooLarge, "request_too_large", "请求 body 超出大小限制。", r.Header.Get("X-Request-Id"))
			return http.ErrBodyReadAfterClose
		}
		return err
	}
	if err := decoder.Decode(&struct{}{}); err != io.EOF {
		var maxBytesError *http.MaxBytesError
		if errors.As(err, &maxBytesError) {
			writeGatewayError(w, http.StatusRequestEntityTooLarge, "request_too_large", "请求 body 超出大小限制。", r.Header.Get("X-Request-Id"))
			return http.ErrBodyReadAfterClose
		}
		if err == nil {
			return errors.New("request body must contain only one JSON document")
		}
		return err
	}
	return nil
}

func hasValidToken(header string, token string) bool {
	if !strings.HasPrefix(header, bearerPrefix) {
		return false
	}
	actual := strings.TrimSpace(strings.TrimPrefix(header, bearerPrefix))
	expected := strings.TrimSpace(token)
	if actual == "" || expected == "" {
		return false
	}
	if len(actual) != len(expected) {
		return false
	}
	return subtle.ConstantTimeCompare([]byte(actual), []byte(expected)) == 1
}

func writeJSON(w http.ResponseWriter, status int, value any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(value)
}

func writeGatewayError(w http.ResponseWriter, status int, code string, message string, requestID string) {
	var requestIDPtr *string
	if requestID != "" {
		requestIDPtr = &requestID
	}
	writeJSON(w, status, GatewayError{
		Code:      code,
		Message:   message,
		RequestID: requestIDPtr,
		Details:   nil,
	})
}
