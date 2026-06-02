package httpapi

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"reflect"
	"strings"
	"testing"
	"time"

	"example.com/ai-chat-app/gateway/internal/sandbox"
	"example.com/ai-chat-app/gateway/internal/search"
)

func TestHealth(t *testing.T) {
	server := httptest.NewServer(NewMux("test"))
	defer server.Close()

	resp, err := http.Get(server.URL + "/health")
	if err != nil {
		t.Fatalf("get health: %v", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		t.Fatalf("status code = %d, want %d", resp.StatusCode, http.StatusOK)
	}

	var body HealthResponse
	if err := json.NewDecoder(resp.Body).Decode(&body); err != nil {
		t.Fatalf("decode response: %v", err)
	}

	if body.Status != "ok" {
		t.Fatalf("status = %q, want ok", body.Status)
	}
	if body.Service != serviceName {
		t.Fatalf("service = %q, want %q", body.Service, serviceName)
	}
	if body.Version != "test" {
		t.Fatalf("version = %q, want test", body.Version)
	}
}

func TestToolManifest(t *testing.T) {
	server := httptest.NewServer(NewMux("test"))
	defer server.Close()

	resp, err := http.Get(server.URL + "/v1/tools/manifest")
	if err != nil {
		t.Fatalf("get manifest: %v", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		t.Fatalf("status code = %d, want %d", resp.StatusCode, http.StatusOK)
	}

	var body ToolManifestResponse
	if err := json.NewDecoder(resp.Body).Decode(&body); err != nil {
		t.Fatalf("decode response: %v", err)
	}

	if body.Version != 1 {
		t.Fatalf("version = %d, want 1", body.Version)
	}
	if len(body.Tools) != 2 {
		t.Fatalf("tool count = %d, want 2", len(body.Tools))
	}
	if body.Tools[0].Name != "web_search" {
		t.Fatalf("first tool = %q, want web_search", body.Tools[0].Name)
	}
	if body.Tools[1].PermissionLevel != "Execute" {
		t.Fatalf("second permission = %q, want Execute", body.Tools[1].PermissionLevel)
	}
}

func TestToolManifestMatchesContractFixture(t *testing.T) {
	server := httptest.NewServer(NewMux("test"))
	defer server.Close()

	resp, err := http.Get(server.URL + "/v1/tools/manifest")
	if err != nil {
		t.Fatalf("get manifest: %v", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		t.Fatalf("status code = %d, want %d", resp.StatusCode, http.StatusOK)
	}

	var actual ToolManifestResponse
	if err := json.NewDecoder(resp.Body).Decode(&actual); err != nil {
		t.Fatalf("decode response: %v", err)
	}
	if _, err := time.Parse(time.RFC3339, actual.GeneratedAt); err != nil {
		t.Fatalf("generatedAt = %q, want RFC3339: %v", actual.GeneratedAt, err)
	}

	var expected ToolManifestResponse
	fixture := readContractFixture(t, "tool-manifest.json")
	if err := json.Unmarshal(fixture, &expected); err != nil {
		t.Fatalf("decode fixture: %v", err)
	}
	expected.GeneratedAt = actual.GeneratedAt

	if !reflect.DeepEqual(actual, expected) {
		t.Fatalf("manifest does not match contract fixture\nactual: %#v\nexpected: %#v", actual, expected)
	}
}

func TestSearch(t *testing.T) {
	server := httptest.NewServer(newAuthenticatedTestMux())
	defer server.Close()

	resp, err := postJSON(server.URL+"/v1/search", `{"query":"AI news"}`, testToken)
	if err != nil {
		t.Fatalf("post search: %v", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		t.Fatalf("status code = %d, want %d", resp.StatusCode, http.StatusOK)
	}

	var body struct {
		Query   string `json:"query"`
		Results []struct {
			Title  string `json:"title"`
			URL    string `json:"url"`
			Source string `json:"source"`
		} `json:"results"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&body); err != nil {
		t.Fatalf("decode response: %v", err)
	}

	if body.Query != "AI news" {
		t.Fatalf("query = %q, want AI news", body.Query)
	}
	if len(body.Results) != 1 {
		t.Fatalf("result count = %d, want 1", len(body.Results))
	}
	if body.Results[0].Source != "Mock Search" {
		t.Fatalf("source = %q, want Mock Search", body.Results[0].Source)
	}
}

func TestSearchRejectsBlankQuery(t *testing.T) {
	server := httptest.NewServer(newAuthenticatedTestMux())
	defer server.Close()

	resp, err := postJSON(server.URL+"/v1/search", `{"query":" "}`, testToken)
	if err != nil {
		t.Fatalf("post search: %v", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusBadRequest {
		t.Fatalf("status code = %d, want %d", resp.StatusCode, http.StatusBadRequest)
	}

	var body GatewayError
	if err := json.NewDecoder(resp.Body).Decode(&body); err != nil {
		t.Fatalf("decode response: %v", err)
	}
	if body.Code != "invalid_query" {
		t.Fatalf("code = %q, want invalid_query", body.Code)
	}
}

func TestGatewayErrorTrimsRequestID(t *testing.T) {
	server := httptest.NewServer(newAuthenticatedTestMux())
	defer server.Close()

	resp, err := postJSONWithRequestID(server.URL+"/v1/search", `{"query":" "}`, testToken, "  request-123  ")
	if err != nil {
		t.Fatalf("post search: %v", err)
	}
	defer resp.Body.Close()

	var body GatewayError
	if err := json.NewDecoder(resp.Body).Decode(&body); err != nil {
		t.Fatalf("decode response: %v", err)
	}
	if body.RequestID == nil || *body.RequestID != "request-123" {
		t.Fatalf("request id = %v, want request-123", body.RequestID)
	}
}

func TestGatewayErrorTruncatesLongRequestID(t *testing.T) {
	server := httptest.NewServer(newAuthenticatedTestMux())
	defer server.Close()
	requestID := strings.Repeat("x", maxGatewayRequestIDLength+16)

	resp, err := postJSONWithRequestID(server.URL+"/v1/search", `{"query":" "}`, testToken, requestID)
	if err != nil {
		t.Fatalf("post search: %v", err)
	}
	defer resp.Body.Close()

	var body GatewayError
	if err := json.NewDecoder(resp.Body).Decode(&body); err != nil {
		t.Fatalf("decode response: %v", err)
	}
	if body.RequestID == nil {
		t.Fatal("request id is nil")
	}
	if len(*body.RequestID) != maxGatewayRequestIDLength {
		t.Fatalf("request id length = %d, want %d", len(*body.RequestID), maxGatewayRequestIDLength)
	}
}

func TestSearchRejectsTrailingJSONDocument(t *testing.T) {
	server := httptest.NewServer(newAuthenticatedTestMux())
	defer server.Close()

	resp, err := postJSON(server.URL+"/v1/search", `{"query":"AI news"} {"query":"extra"}`, testToken)
	if err != nil {
		t.Fatalf("post search: %v", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusBadRequest {
		t.Fatalf("status code = %d, want %d", resp.StatusCode, http.StatusBadRequest)
	}

	var body GatewayError
	if err := json.NewDecoder(resp.Body).Decode(&body); err != nil {
		t.Fatalf("decode response: %v", err)
	}
	if body.Code != "invalid_request" {
		t.Fatalf("code = %q, want invalid_request", body.Code)
	}
}

func TestSearchRejectsTrailingGarbage(t *testing.T) {
	server := httptest.NewServer(newAuthenticatedTestMux())
	defer server.Close()

	resp, err := postJSON(server.URL+"/v1/search", `{"query":"AI news"} garbage`, testToken)
	if err != nil {
		t.Fatalf("post search: %v", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusBadRequest {
		t.Fatalf("status code = %d, want %d", resp.StatusCode, http.StatusBadRequest)
	}

	var body GatewayError
	if err := json.NewDecoder(resp.Body).Decode(&body); err != nil {
		t.Fatalf("decode response: %v", err)
	}
	if body.Code != "invalid_request" {
		t.Fatalf("code = %q, want invalid_request", body.Code)
	}
}

func TestSandboxRunReturnsStdout(t *testing.T) {
	runner := &fakeSandboxRunner{
		response: sandbox.Response{
			Language:   "python",
			Stdout:     "2\n",
			Stderr:     "",
			ExitCode:   0,
			DurationMs: 12,
			TimedOut:   false,
			Truncated:  false,
		},
	}
	server := httptest.NewServer(newAuthenticatedSandboxTestMux(runner, Options{APIToken: testToken}))
	defer server.Close()

	resp, err := postJSON(server.URL+"/v1/sandbox/run", `{"language":"python","code":"print(1 + 1)"}`, testToken)
	if err != nil {
		t.Fatalf("post sandbox: %v", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		t.Fatalf("status code = %d, want %d", resp.StatusCode, http.StatusOK)
	}

	var body sandbox.Response
	if err := json.NewDecoder(resp.Body).Decode(&body); err != nil {
		t.Fatalf("decode response: %v", err)
	}
	if body.Stdout != "2\n" {
		t.Fatalf("stdout = %q, want 2 newline", body.Stdout)
	}
	if runner.request.Code != "print(1 + 1)" {
		t.Fatalf("code = %q, want print snippet", runner.request.Code)
	}
}

func TestSandboxRunReturnsSyntaxErrorResult(t *testing.T) {
	runner := &fakeSandboxRunner{
		response: sandbox.Response{
			Language:   "python",
			Stdout:     "",
			Stderr:     "SyntaxError: invalid syntax\n",
			ExitCode:   1,
			DurationMs: 8,
			TimedOut:   false,
			Truncated:  false,
		},
	}
	server := httptest.NewServer(newAuthenticatedSandboxTestMux(runner, Options{APIToken: testToken}))
	defer server.Close()

	resp, err := postJSON(server.URL+"/v1/sandbox/run", `{"language":"python","code":"if"}`, testToken)
	if err != nil {
		t.Fatalf("post sandbox: %v", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		t.Fatalf("status code = %d, want %d", resp.StatusCode, http.StatusOK)
	}

	var body sandbox.Response
	if err := json.NewDecoder(resp.Body).Decode(&body); err != nil {
		t.Fatalf("decode response: %v", err)
	}
	if body.ExitCode == 0 {
		t.Fatalf("exit code = %d, want non-zero", body.ExitCode)
	}
	if body.Stderr == "" {
		t.Fatal("stderr is blank, want syntax error")
	}
}

func TestSandboxRunReturnsTimeoutResult(t *testing.T) {
	runner := &fakeSandboxRunner{
		response: sandbox.Response{
			Language:   "python",
			Stdout:     "",
			Stderr:     "",
			ExitCode:   -1,
			DurationMs: 3000,
			TimedOut:   true,
			Truncated:  false,
		},
	}
	server := httptest.NewServer(newAuthenticatedSandboxTestMux(runner, Options{APIToken: testToken}))
	defer server.Close()

	resp, err := postJSON(server.URL+"/v1/sandbox/run", `{"language":"python","code":"while True: pass","timeoutSeconds":1}`, testToken)
	if err != nil {
		t.Fatalf("post sandbox: %v", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		t.Fatalf("status code = %d, want %d", resp.StatusCode, http.StatusOK)
	}

	var body sandbox.Response
	if err := json.NewDecoder(resp.Body).Decode(&body); err != nil {
		t.Fatalf("decode response: %v", err)
	}
	if !body.TimedOut {
		t.Fatal("timedOut = false, want true")
	}
	if runner.request.Timeout != time.Second {
		t.Fatalf("timeout = %s, want 1s", runner.request.Timeout)
	}
}

func TestSandboxRunRejectsInvalidTimeout(t *testing.T) {
	tests := []struct {
		name string
		body string
	}{
		{
			name: "negative",
			body: `{"language":"python","code":"print(1)","timeoutSeconds":-1}`,
		},
		{
			name: "too_large",
			body: `{"language":"python","code":"print(1)","timeoutSeconds":11}`,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			runner := &fakeSandboxRunner{}
			server := httptest.NewServer(newAuthenticatedSandboxTestMux(runner, Options{APIToken: testToken}))
			defer server.Close()

			resp, err := postJSON(server.URL+"/v1/sandbox/run", tt.body, testToken)
			if err != nil {
				t.Fatalf("post sandbox: %v", err)
			}
			defer resp.Body.Close()

			if resp.StatusCode != http.StatusBadRequest {
				t.Fatalf("status code = %d, want %d", resp.StatusCode, http.StatusBadRequest)
			}

			var body GatewayError
			if err := json.NewDecoder(resp.Body).Decode(&body); err != nil {
				t.Fatalf("decode response: %v", err)
			}
			if body.Code != "invalid_timeout" {
				t.Fatalf("code = %q, want invalid_timeout", body.Code)
			}
			if runner.called {
				t.Fatal("runner was called for invalid timeout")
			}
		})
	}
}

func TestSandboxRunReturnsUnavailableWhenDockerUnavailable(t *testing.T) {
	runner := &fakeSandboxRunner{err: sandbox.ErrUnavailable}
	server := httptest.NewServer(newAuthenticatedSandboxTestMux(runner, Options{APIToken: testToken}))
	defer server.Close()

	resp, err := postJSON(server.URL+"/v1/sandbox/run", `{"language":"python","code":"print(1)"}`, testToken)
	if err != nil {
		t.Fatalf("post sandbox: %v", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusServiceUnavailable {
		t.Fatalf("status code = %d, want %d", resp.StatusCode, http.StatusServiceUnavailable)
	}

	var body GatewayError
	if err := json.NewDecoder(resp.Body).Decode(&body); err != nil {
		t.Fatalf("decode response: %v", err)
	}
	if body.Code != "sandbox_unavailable" {
		t.Fatalf("code = %q, want sandbox_unavailable", body.Code)
	}
}

func TestSandboxRunRejectsUnsupportedLanguage(t *testing.T) {
	runner := &fakeSandboxRunner{}
	server := httptest.NewServer(newAuthenticatedSandboxTestMux(runner, Options{APIToken: testToken}))
	defer server.Close()

	resp, err := postJSON(server.URL+"/v1/sandbox/run", `{"language":"javascript","code":"console.log(1)"}`, testToken)
	if err != nil {
		t.Fatalf("post sandbox: %v", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusBadRequest {
		t.Fatalf("status code = %d, want %d", resp.StatusCode, http.StatusBadRequest)
	}
	if runner.called {
		t.Fatal("runner was called for unsupported language")
	}
}

func TestSensitiveEndpointsRequireToken(t *testing.T) {
	server := httptest.NewServer(newAuthenticatedTestMux())
	defer server.Close()

	resp, err := postJSON(server.URL+"/v1/search", `{"query":"AI news"}`, "")
	if err != nil {
		t.Fatalf("post search: %v", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusUnauthorized {
		t.Fatalf("status code = %d, want %d", resp.StatusCode, http.StatusUnauthorized)
	}
}

func TestSensitiveEndpointsRejectWhenTokenNotConfigured(t *testing.T) {
	server := httptest.NewServer(NewMux("test"))
	defer server.Close()

	resp, err := http.Post(server.URL+"/v1/search", "application/json", bytes.NewBufferString(`{"query":"AI news"}`))
	if err != nil {
		t.Fatalf("post search: %v", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusServiceUnavailable {
		t.Fatalf("status code = %d, want %d", resp.StatusCode, http.StatusServiceUnavailable)
	}
}

func TestSensitiveEndpointsAcceptConfiguredTokenWithSurroundingWhitespace(t *testing.T) {
	server := httptest.NewServer(
		NewMuxWithDependencies(
			"test",
			&fakeSandboxRunner{},
			search.MockAdapter{},
			Options{APIToken: "  " + testToken + "  "},
		),
	)
	defer server.Close()

	resp, err := postJSON(server.URL+"/v1/search", `{"query":"AI news"}`, testToken)
	if err != nil {
		t.Fatalf("post search: %v", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		t.Fatalf("status code = %d, want %d", resp.StatusCode, http.StatusOK)
	}
}

func TestRequestBodyLimitReturnsStructuredError(t *testing.T) {
	server := httptest.NewServer(
		NewMuxWithDependencies(
			"test",
			&fakeSandboxRunner{},
			search.MockAdapter{},
			Options{APIToken: testToken, MaxRequestBodyBytes: 8},
		),
	)
	defer server.Close()

	resp, err := postJSON(server.URL+"/v1/search", `{"query":"AI news"}`, testToken)
	if err != nil {
		t.Fatalf("post search: %v", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusRequestEntityTooLarge {
		t.Fatalf("status code = %d, want %d", resp.StatusCode, http.StatusRequestEntityTooLarge)
	}
	var body GatewayError
	if err := json.NewDecoder(resp.Body).Decode(&body); err != nil {
		t.Fatalf("decode response: %v", err)
	}
	if body.Code != "request_too_large" {
		t.Fatalf("code = %q, want request_too_large", body.Code)
	}
}

func TestSandboxRunRejectsLargeCode(t *testing.T) {
	runner := &fakeSandboxRunner{}
	server := httptest.NewServer(
		newAuthenticatedSandboxTestMux(
			runner,
			Options{APIToken: testToken, MaxSandboxCodeBytes: 4},
		),
	)
	defer server.Close()

	resp, err := postJSON(server.URL+"/v1/sandbox/run", `{"language":"python","code":"print(1)"}`, testToken)
	if err != nil {
		t.Fatalf("post sandbox: %v", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusRequestEntityTooLarge {
		t.Fatalf("status code = %d, want %d", resp.StatusCode, http.StatusRequestEntityTooLarge)
	}
	if runner.called {
		t.Fatal("runner was called for oversized code")
	}
}

const testToken = "test-token"

func newAuthenticatedTestMux() http.Handler {
	return NewMuxWithDependencies("test", &fakeSandboxRunner{}, search.MockAdapter{}, Options{APIToken: testToken})
}

func newAuthenticatedSandboxTestMux(runner sandbox.Runner, options Options) http.Handler {
	return NewMuxWithDependencies("test", runner, search.MockAdapter{}, options)
}

func postJSON(url string, body string, token string) (*http.Response, error) {
	return postJSONWithRequestID(url, body, token, "")
}

func postJSONWithRequestID(url string, body string, token string, requestID string) (*http.Response, error) {
	request, err := http.NewRequest(http.MethodPost, url, bytes.NewBufferString(body))
	if err != nil {
		return nil, err
	}
	request.Header.Set("Content-Type", "application/json")
	if token != "" {
		request.Header.Set("Authorization", "Bearer "+token)
	}
	if requestID != "" {
		request.Header.Set("X-Request-Id", requestID)
	}
	return http.DefaultClient.Do(request)
}

func readContractFixture(t *testing.T, name string) []byte {
	t.Helper()
	wd, err := os.Getwd()
	if err != nil {
		t.Fatalf("get wd: %v", err)
	}
	for dir := wd; ; dir = filepath.Dir(dir) {
		path := filepath.Join(dir, "contracts", "gateway", "fixtures", name)
		content, err := os.ReadFile(path)
		if err == nil {
			return content
		}
		if !errors.Is(err, os.ErrNotExist) {
			t.Fatalf("read fixture %s: %v", path, err)
		}
		parent := filepath.Dir(dir)
		if parent == dir {
			t.Fatalf("fixture %s not found from %s", name, wd)
		}
	}
}

type fakeSandboxRunner struct {
	request  sandbox.Request
	response sandbox.Response
	err      error
	called   bool
}

func (runner *fakeSandboxRunner) Run(ctx context.Context, request sandbox.Request) (sandbox.Response, error) {
	if err := ctx.Err(); err != nil {
		return sandbox.Response{}, errors.Join(runner.err, err)
	}
	runner.called = true
	runner.request = request
	return runner.response, runner.err
}
