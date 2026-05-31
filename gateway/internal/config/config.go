package config

import "os"

const (
	defaultAddr    = ":8080"
	defaultVersion = "dev"
)

type Config struct {
	Addr    string
	Version string
}

func FromEnv() Config {
	return Config{
		Addr:    getEnv("GATEWAY_ADDR", defaultAddr),
		Version: getEnv("GATEWAY_VERSION", defaultVersion),
	}
}

func getEnv(key string, fallback string) string {
	value := os.Getenv(key)
	if value == "" {
		return fallback
	}
	return value
}
