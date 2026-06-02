package config

import (
	"errors"
	"fmt"
	"net"
	"os"
	"strconv"
	"strings"
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

var (
	errNonPositiveInteger = errors.New("must be a positive integer")
	errPortOutOfRange     = errors.New("port must be between 1 and 65535")
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

type positiveIntConfig struct {
	MaxRequestBodyBytes int
	MaxSandboxCodeBytes int
	ReadHeaderTimeout   int
	ReadTimeout         int
	WriteTimeout        int
	IdleTimeout         int
}

func FromEnv() (Config, error) {
	addr, err := getEnvAddr("GATEWAY_ADDR", defaultAddr)
	if err != nil {
		return Config{}, err
	}
	positiveInts, err := getEnvPositiveInts()
	if err != nil {
		return Config{}, err
	}

	return Config{
		Addr:                addr,
		Version:             getEnv("GATEWAY_VERSION", defaultVersion),
		SearchProvider:      getEnv("SEARCH_PROVIDER", defaultSearchProvider),
		SearXNGBaseURL:      os.Getenv("SEARXNG_BASE_URL"),
		APIToken:            os.Getenv("GATEWAY_API_TOKEN"),
		MaxRequestBodyBytes: int64(positiveInts.MaxRequestBodyBytes),
		MaxSandboxCodeBytes: positiveInts.MaxSandboxCodeBytes,
		ReadHeaderTimeout:   positiveInts.ReadHeaderTimeout,
		ReadTimeout:         positiveInts.ReadTimeout,
		WriteTimeout:        positiveInts.WriteTimeout,
		IdleTimeout:         positiveInts.IdleTimeout,
	}, nil
}

func getEnvPositiveInts() (positiveIntConfig, error) {
	maxRequestBodyBytes, err := getEnvPositiveInt("GATEWAY_MAX_BODY_BYTES", defaultMaxRequestBodyBytes)
	if err != nil {
		return positiveIntConfig{}, err
	}
	maxSandboxCodeBytes, err := getEnvPositiveInt("GATEWAY_MAX_SANDBOX_CODE_BYTES", defaultMaxSandboxCodeBytes)
	if err != nil {
		return positiveIntConfig{}, err
	}
	readHeaderTimeout, err := getEnvPositiveInt("GATEWAY_READ_HEADER_TIMEOUT_SECONDS", defaultReadHeaderTimeoutSecs)
	if err != nil {
		return positiveIntConfig{}, err
	}
	readTimeout, err := getEnvPositiveInt("GATEWAY_READ_TIMEOUT_SECONDS", defaultReadTimeoutSecs)
	if err != nil {
		return positiveIntConfig{}, err
	}
	writeTimeout, err := getEnvPositiveInt("GATEWAY_WRITE_TIMEOUT_SECONDS", defaultWriteTimeoutSecs)
	if err != nil {
		return positiveIntConfig{}, err
	}
	idleTimeout, err := getEnvPositiveInt("GATEWAY_IDLE_TIMEOUT_SECONDS", defaultIdleTimeoutSecs)
	if err != nil {
		return positiveIntConfig{}, err
	}
	return positiveIntConfig{
		MaxRequestBodyBytes: maxRequestBodyBytes,
		MaxSandboxCodeBytes: maxSandboxCodeBytes,
		ReadHeaderTimeout:   readHeaderTimeout,
		ReadTimeout:         readTimeout,
		WriteTimeout:        writeTimeout,
		IdleTimeout:         idleTimeout,
	}, nil
}

func getEnvAddr(key string, fallback string) (string, error) {
	value := strings.TrimSpace(getEnv(key, fallback))
	if value == "" {
		return fallback, nil
	}
	_, port, err := net.SplitHostPort(value)
	if err != nil {
		return "", fmt.Errorf("parse %s: %w", key, err)
	}
	parsedPort, err := strconv.Atoi(port)
	if err != nil {
		return "", fmt.Errorf("parse %s port: %w", key, err)
	}
	if parsedPort < 1 || parsedPort > 65535 {
		return "", fmt.Errorf("parse %s port: %w", key, errPortOutOfRange)
	}
	return value, nil
}

func getEnv(key string, fallback string) string {
	value := os.Getenv(key)
	if value == "" {
		return fallback
	}
	return value
}

func getEnvPositiveInt(key string, fallback int) (int, error) {
	value := strings.TrimSpace(os.Getenv(key))
	if value == "" {
		return fallback, nil
	}
	parsed, err := strconv.Atoi(value)
	if err != nil {
		return 0, fmt.Errorf("parse %s: %w", key, err)
	}
	if parsed <= 0 {
		return 0, fmt.Errorf("parse %s: %w", key, errNonPositiveInteger)
	}
	return parsed, nil
}
