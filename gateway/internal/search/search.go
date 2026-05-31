package search

import (
	"context"
	"fmt"
	"net/url"
	"time"
)

type Result struct {
	Title       string     `json:"title"`
	Summary     string     `json:"summary"`
	URL         string     `json:"url"`
	Source      string     `json:"source"`
	PublishedAt *time.Time `json:"publishedAt"`
}

type Response struct {
	Query     string    `json:"query"`
	FetchedAt time.Time `json:"fetchedAt"`
	Results   []Result  `json:"results"`
}

type Adapter interface {
	Search(ctx context.Context, query string) (Response, error)
}

type MockAdapter struct {
	Now func() time.Time
}

func (adapter MockAdapter) Search(ctx context.Context, query string) (Response, error) {
	if err := ctx.Err(); err != nil {
		return Response{}, fmt.Errorf("search canceled: %w", err)
	}
	now := time.Now().UTC()
	if adapter.Now != nil {
		now = adapter.Now().UTC()
	}
	escaped := url.QueryEscape(query)
	return Response{
		Query:     query,
		FetchedAt: now,
		Results: []Result{
			{
				Title:       "Mock result for " + query,
				Summary:     "Mock search adapter result. Configure a real adapter before relying on this source.",
				URL:         "https://example.com/search?q=" + escaped,
				Source:      "Mock Search",
				PublishedAt: &now,
			},
		},
	}, nil
}
