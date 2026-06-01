package config

import (
	"os"
	"strconv"
)

const (
	defaultAddr                  = "127.0.0.1:8080"
	defaultVersion               = "dev"
	defaultSearchProvider        = "mock"
	defaultMaxRequestBodyBytes   = 1 << 20
	defaultMaxSandboxCodeBytes   = 64 << 10
	defaultReadHeaderTimeoutSecs = 5
	defaultReadTimeoutSecs       = 15
	defaultWriteTimeoutSecs      = 30
	defaultIdleTimeoutSecs       = 60
)

type Config struct {
	Addr                string
	Version             string
	SearchProvider      string
	SearXNGBaseURL      string
	APIToken            string
	MaxRequestBodyBytes int64
	MaxSandboxCodeBytes int
	ReadHeaderTimeout   int
	ReadTimeout         int
	WriteTimeout        int
	IdleTimeout         int
}

func FromEnv() Config {
	return Config{
		Addr:                getEnv("GATEWAY_ADDR", defaultAddr),
		Version:             getEnv("GATEWAY_VERSION", defaultVersion),
		SearchProvider:      getEnv("SEARCH_PROVIDER", defaultSearchProvider),
		SearXNGBaseURL:      os.Getenv("SEARXNG_BASE_URL"),
		APIToken:            os.Getenv("GATEWAY_API_TOKEN"),
		MaxRequestBodyBytes: int64(getEnvInt("GATEWAY_MAX_BODY_BYTES", defaultMaxRequestBodyBytes)),
		MaxSandboxCodeBytes: getEnvInt("GATEWAY_MAX_SANDBOX_CODE_BYTES", defaultMaxSandboxCodeBytes),
		ReadHeaderTimeout:   getEnvInt("GATEWAY_READ_HEADER_TIMEOUT_SECONDS", defaultReadHeaderTimeoutSecs),
		ReadTimeout:         getEnvInt("GATEWAY_READ_TIMEOUT_SECONDS", defaultReadTimeoutSecs),
		WriteTimeout:        getEnvInt("GATEWAY_WRITE_TIMEOUT_SECONDS", defaultWriteTimeoutSecs),
		IdleTimeout:         getEnvInt("GATEWAY_IDLE_TIMEOUT_SECONDS", defaultIdleTimeoutSecs),
	}
}

func getEnv(key string, fallback string) string {
	value := os.Getenv(key)
	if value == "" {
		return fallback
	}
	return value
}

func getEnvInt(key string, fallback int) int {
	value := os.Getenv(key)
	if value == "" {
		return fallback
	}
	parsed, err := strconv.Atoi(value)
	if err != nil || parsed <= 0 {
		return fallback
	}
	return parsed
}
