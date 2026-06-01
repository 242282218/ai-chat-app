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
				Title:       "Mock 搜索结果：" + query,
				Summary:     "Mock search adapter 结果。正式依赖前请配置真实 adapter。",
				URL:         "https://example.com/search?q=" + escaped,
				Source:      "Mock Search",
				PublishedAt: &now,
			},
		},
	}, nil
}
