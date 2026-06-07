package main

import (
	"context"
	"errors"
	"fmt"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"strings"
	"syscall"
	"time"

	"example.com/ai-chat-app/gateway/internal/config"
	"example.com/ai-chat-app/gateway/internal/httpapi"
	"example.com/ai-chat-app/gateway/internal/sandbox"
	"example.com/ai-chat-app/gateway/internal/search"
)

func main() {
	logger := slog.New(slog.NewJSONHandler(os.Stdout, nil))
	cfg, err := config.FromEnv()
	if err != nil {
		logger.Error("gateway config invalid", "error", err)
		os.Exit(1)
	}
	searchAdapter, err := searchAdapterFromConfig(cfg)
	if err != nil {
		logger.Error("gateway config invalid", "error", err)
		os.Exit(1)
	}

	server := &http.Server{
		Addr:              cfg.Addr,
		Handler:           httpapi.NewMuxWithDependencies(cfg.Version, sandbox.NewDockerRunner(), searchAdapter, httpapi.OptionsFromConfig(cfg)),
		ReadHeaderTimeout: time.Duration(cfg.ReadHeaderTimeout) * time.Second,
		ReadTimeout:       time.Duration(cfg.ReadTimeout) * time.Second,
		WriteTimeout:      time.Duration(cfg.WriteTimeout) * time.Second,
		IdleTimeout:       time.Duration(cfg.IdleTimeout) * time.Second,
	}

	errs := make(chan error, 1)
	go func() {
		logger.Info("gateway listening", "addr", cfg.Addr, "version", cfg.Version)
		errs <- server.ListenAndServe()
	}()

	stop := make(chan os.Signal, 1)
	signal.Notify(stop, os.Interrupt, syscall.SIGTERM)

	select {
	case err := <-errs:
		if !errors.Is(err, http.ErrServerClosed) {
			logger.Error("gateway stopped", "error", err)
			os.Exit(1)
		}
	case <-stop:
		ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
		defer cancel()

		if err := server.Shutdown(ctx); err != nil {
			logger.Error("gateway shutdown failed", "error", err)
			os.Exit(1)
		}
		logger.Info("gateway stopped")
	}
}

func searchAdapterFromConfig(cfg config.Config) (search.Adapter, error) {
	switch strings.ToLower(strings.TrimSpace(cfg.SearchProvider)) {
	case "", "disabled":
		return search.DisabledAdapter{}, nil
	case "mock":
		return search.MockAdapter{}, nil
	case "searxng":
		if strings.TrimSpace(cfg.SearXNGBaseURL) == "" {
			return nil, fmt.Errorf("SEARXNG_BASE_URL is required when SEARCH_PROVIDER=searxng")
		}
		return search.NewSearXNGAdapter(cfg.SearXNGBaseURL), nil
	default:
		return nil, fmt.Errorf("unsupported SEARCH_PROVIDER %q", cfg.SearchProvider)
	}
}
