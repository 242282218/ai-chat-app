package config

import (
	"strings"
	"testing"
)

func TestFromEnvUsesDefaults(t *testing.T) {
	clearConfigEnv(t)

	cfg, err := FromEnv()
	if err != nil {
		t.Fatalf("from env: %v", err)
	}

	if cfg.Addr != defaultAddr {
		t.Fatalf("addr = %q, want %q", cfg.Addr, defaultAddr)
	}
	if cfg.Version != defaultVersion {
		t.Fatalf("version = %q, want %q", cfg.Version, defaultVersion)
	}
	if cfg.SearchProvider != defaultSearchProvider {
		t.Fatalf("search provider = %q, want %q", cfg.SearchProvider, defaultSearchProvider)
	}
	if cfg.MaxRequestBodyBytes != defaultMaxRequestBodyBytes {
		t.Fatalf("max body bytes = %d, want %d", cfg.MaxRequestBodyBytes, defaultMaxRequestBodyBytes)
	}
	if cfg.MaxSandboxCodeBytes != defaultMaxSandboxCodeBytes {
		t.Fatalf("max sandbox code bytes = %d, want %d", cfg.MaxSandboxCodeBytes, defaultMaxSandboxCodeBytes)
	}
	if cfg.ReadHeaderTimeout != defaultReadHeaderTimeoutSecs {
		t.Fatalf("read header timeout = %d, want %d", cfg.ReadHeaderTimeout, defaultReadHeaderTimeoutSecs)
	}
	if cfg.ReadTimeout != defaultReadTimeoutSecs {
		t.Fatalf("read timeout = %d, want %d", cfg.ReadTimeout, defaultReadTimeoutSecs)
	}
	if cfg.WriteTimeout != defaultWriteTimeoutSecs {
		t.Fatalf("write timeout = %d, want %d", cfg.WriteTimeout, defaultWriteTimeoutSecs)
	}
	if cfg.IdleTimeout != defaultIdleTimeoutSecs {
		t.Fatalf("idle timeout = %d, want %d", cfg.IdleTimeout, defaultIdleTimeoutSecs)
	}
}

func TestFromEnvUsesOverrides(t *testing.T) {
	clearConfigEnv(t)
	t.Setenv("GATEWAY_ADDR", " 0.0.0.0:9090 ")
	t.Setenv("GATEWAY_VERSION", "v-test")
	t.Setenv("SEARCH_PROVIDER", "searxng")
	t.Setenv("SEARXNG_BASE_URL", "https://search.example.com")
	t.Setenv("GATEWAY_API_TOKEN", "token-1")
	t.Setenv("GATEWAY_MAX_BODY_BYTES", " 2048 ")
	t.Setenv("GATEWAY_MAX_SANDBOX_CODE_BYTES", "4096")
	t.Setenv("GATEWAY_READ_HEADER_TIMEOUT_SECONDS", "6")
	t.Setenv("GATEWAY_READ_TIMEOUT_SECONDS", "16")
	t.Setenv("GATEWAY_WRITE_TIMEOUT_SECONDS", "31")
	t.Setenv("GATEWAY_IDLE_TIMEOUT_SECONDS", "61")

	cfg, err := FromEnv()
	if err != nil {
		t.Fatalf("from env: %v", err)
	}

	if cfg.Addr != "0.0.0.0:9090" {
		t.Fatalf("addr = %q, want override", cfg.Addr)
	}
	if cfg.Version != "v-test" {
		t.Fatalf("version = %q, want override", cfg.Version)
	}
	if cfg.SearchProvider != "searxng" {
		t.Fatalf("search provider = %q, want override", cfg.SearchProvider)
	}
	if cfg.SearXNGBaseURL != "https://search.example.com" {
		t.Fatalf("searxng base url = %q, want override", cfg.SearXNGBaseURL)
	}
	if cfg.APIToken != "token-1" {
		t.Fatalf("api token = %q, want override", cfg.APIToken)
	}
	if cfg.MaxRequestBodyBytes != 2048 {
		t.Fatalf("max body bytes = %d, want 2048", cfg.MaxRequestBodyBytes)
	}
	if cfg.MaxSandboxCodeBytes != 4096 {
		t.Fatalf("max sandbox code bytes = %d, want 4096", cfg.MaxSandboxCodeBytes)
	}
	if cfg.ReadHeaderTimeout != 6 {
		t.Fatalf("read header timeout = %d, want 6", cfg.ReadHeaderTimeout)
	}
	if cfg.ReadTimeout != 16 {
		t.Fatalf("read timeout = %d, want 16", cfg.ReadTimeout)
	}
	if cfg.WriteTimeout != 31 {
		t.Fatalf("write timeout = %d, want 31", cfg.WriteTimeout)
	}
	if cfg.IdleTimeout != 61 {
		t.Fatalf("idle timeout = %d, want 61", cfg.IdleTimeout)
	}
}

func TestFromEnvRejectsInvalidPositiveIntegers(t *testing.T) {
	tests := []struct {
		name  string
		key   string
		value string
	}{
		{
			name:  "non_numeric_body_limit",
			key:   "GATEWAY_MAX_BODY_BYTES",
			value: "large",
		},
		{
			name:  "zero_sandbox_limit",
			key:   "GATEWAY_MAX_SANDBOX_CODE_BYTES",
			value: "0",
		},
		{
			name:  "negative_read_timeout",
			key:   "GATEWAY_READ_TIMEOUT_SECONDS",
			value: "-1",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			clearConfigEnv(t)
			t.Setenv(tt.key, tt.value)

			_, err := FromEnv()
			if err == nil {
				t.Fatal("err is nil, want invalid config")
			}
			if !strings.Contains(err.Error(), tt.key) {
				t.Fatalf("err = %q, want key %q", err.Error(), tt.key)
			}
		})
	}
}

func TestFromEnvRejectsInvalidAddr(t *testing.T) {
	tests := []struct {
		name  string
		value string
	}{
		{
			name:  "missing_port",
			value: "127.0.0.1",
		},
		{
			name:  "zero_port",
			value: "127.0.0.1:0",
		},
		{
			name:  "out_of_range_port",
			value: "127.0.0.1:70000",
		},
		{
			name:  "non_numeric_port",
			value: "127.0.0.1:http",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			clearConfigEnv(t)
			t.Setenv("GATEWAY_ADDR", tt.value)

			_, err := FromEnv()
			if err == nil {
				t.Fatal("err is nil, want invalid config")
			}
			if !strings.Contains(err.Error(), "GATEWAY_ADDR") {
				t.Fatalf("err = %q, want GATEWAY_ADDR", err.Error())
			}
		})
	}
}

func clearConfigEnv(t *testing.T) {
	t.Helper()
	for _, key := range []string{
		"GATEWAY_ADDR",
		"GATEWAY_VERSION",
		"SEARCH_PROVIDER",
		"SEARXNG_BASE_URL",
		"GATEWAY_API_TOKEN",
		"GATEWAY_MAX_BODY_BYTES",
		"GATEWAY_MAX_SANDBOX_CODE_BYTES",
		"GATEWAY_READ_HEADER_TIMEOUT_SECONDS",
		"GATEWAY_READ_TIMEOUT_SECONDS",
		"GATEWAY_WRITE_TIMEOUT_SECONDS",
		"GATEWAY_IDLE_TIMEOUT_SECONDS",
	} {
		t.Setenv(key, "")
	}
}
