package main

import (
	"reflect"
	"testing"

	"example.com/ai-chat-app/gateway/internal/config"
	"example.com/ai-chat-app/gateway/internal/search"
)

func TestSearchAdapterFromConfigDefaultsToDisabled(t *testing.T) {
	adapter, err := searchAdapterFromConfig(config.Config{})
	if err != nil {
		t.Fatalf("search adapter from config: %v", err)
	}

	if reflect.TypeOf(adapter) != reflect.TypeOf(search.DisabledAdapter{}) {
		t.Fatalf("adapter = %T, want search.DisabledAdapter", adapter)
	}
}

func TestSearchAdapterFromConfigAllowsExplicitMock(t *testing.T) {
	adapter, err := searchAdapterFromConfig(config.Config{SearchProvider: "mock"})
	if err != nil {
		t.Fatalf("search adapter from config: %v", err)
	}

	if reflect.TypeOf(adapter) != reflect.TypeOf(search.MockAdapter{}) {
		t.Fatalf("adapter = %T, want search.MockAdapter", adapter)
	}
}
