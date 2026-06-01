package search

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
)

func TestSearXNGAdapterSearch(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/search" {
			t.Fatalf("path = %q, want /search", r.URL.Path)
		}
		if r.URL.Query().Get("q") != "AI news" {
			t.Fatalf("query = %q, want AI news", r.URL.Query().Get("q"))
		}
		if r.URL.Query().Get("format") != "json" {
			t.Fatalf("format = %q, want json", r.URL.Query().Get("format"))
		}
		_ = json.NewEncoder(w).Encode(map[string]any{
			"results": []map[string]any{
				{
					"title":         "AI headline",
					"content":       "AI summary",
					"url":           "https://example.com/ai",
					"engine":        "duckduckgo",
					"publishedDate": "2026-06-01T00:00:00Z",
				},
				{
					"title": "",
					"url":   "https://example.com/blank-title",
				},
			},
		})
	}))
	defer server.Close()

	response, err := NewSearXNGAdapter(server.URL).Search(context.Background(), "AI news")
	if err != nil {
		t.Fatalf("search: %v", err)
	}

	if response.Query != "AI news" {
		t.Fatalf("query = %q, want AI news", response.Query)
	}
	if len(response.Results) != 1 {
		t.Fatalf("result count = %d, want 1", len(response.Results))
	}
	result := response.Results[0]
	if result.Title != "AI headline" {
		t.Fatalf("title = %q, want AI headline", result.Title)
	}
	if result.Summary != "AI summary" {
		t.Fatalf("summary = %q, want AI summary", result.Summary)
	}
	if result.Source != "duckduckgo" {
		t.Fatalf("source = %q, want duckduckgo", result.Source)
	}
	if result.PublishedAt == nil {
		t.Fatal("publishedAt is nil, want parsed time")
	}
}

func TestSearXNGAdapterReturnsErrorForHTTPFailure(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		http.Error(w, "unavailable", http.StatusServiceUnavailable)
	}))
	defer server.Close()

	_, err := NewSearXNGAdapter(server.URL).Search(context.Background(), "AI news")
	if err == nil {
		t.Fatal("err is nil, want failure")
	}
}
