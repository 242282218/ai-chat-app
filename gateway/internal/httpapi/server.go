package httpapi

import (
	"encoding/json"
	"errors"
	"net/http"
	"strings"
	"time"

	"example.com/ai-chat-app/gateway/internal/sandbox"
	"example.com/ai-chat-app/gateway/internal/search"
)

const serviceName = "ai-chat-gateway"

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
	return NewMuxWithSandboxRunner(version, sandbox.NewDockerRunner())
}

func NewMuxWithSandboxRunner(version string, sandboxRunner sandbox.Runner) http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("GET /health", healthHandler(version))
	mux.HandleFunc("GET /v1/tools/manifest", toolManifestHandler())
	mux.HandleFunc("POST /v1/search", searchHandler(search.MockAdapter{}))
	mux.HandleFunc("POST /v1/sandbox/run", sandboxRunHandler(sandboxRunner))
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
					Description:     "Search web or news sources through the configured gateway search adapter.",
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
					Description:     "Run short code snippets in the remote sandbox when enabled.",
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
		if err := json.NewDecoder(r.Body).Decode(&request); err != nil {
			writeGatewayError(w, http.StatusBadRequest, "invalid_request", "Request body must be valid JSON.", r.Header.Get("X-Request-Id"))
			return
		}
		request.Query = strings.TrimSpace(request.Query)
		if request.Query == "" {
			writeGatewayError(w, http.StatusBadRequest, "invalid_query", "Search query must not be blank.", r.Header.Get("X-Request-Id"))
			return
		}
		response, err := adapter.Search(r.Context(), request.Query)
		if err != nil {
			writeGatewayError(w, http.StatusServiceUnavailable, "search_unavailable", "Search adapter is unavailable.", r.Header.Get("X-Request-Id"))
			return
		}
		writeJSON(w, http.StatusOK, response)
	}
}

func sandboxRunHandler(runner sandbox.Runner) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		var request SandboxRunRequest
		if err := json.NewDecoder(r.Body).Decode(&request); err != nil {
			writeGatewayError(w, http.StatusBadRequest, "invalid_request", "Request body must be valid JSON.", r.Header.Get("X-Request-Id"))
			return
		}
		request.Language = strings.TrimSpace(strings.ToLower(request.Language))
		request.Code = strings.TrimSpace(request.Code)
		if request.Language != "python" {
			writeGatewayError(w, http.StatusBadRequest, "unsupported_language", "Only python sandbox runs are supported.", r.Header.Get("X-Request-Id"))
			return
		}
		if request.Code == "" {
			writeGatewayError(w, http.StatusBadRequest, "invalid_code", "Sandbox code must not be blank.", r.Header.Get("X-Request-Id"))
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
				writeGatewayError(w, http.StatusServiceUnavailable, "sandbox_unavailable", "Sandbox runner is unavailable.", r.Header.Get("X-Request-Id"))
				return
			}
			writeGatewayError(w, http.StatusServiceUnavailable, "sandbox_failed", "Sandbox runner failed.", r.Header.Get("X-Request-Id"))
			return
		}
		writeJSON(w, http.StatusOK, response)
	}
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
