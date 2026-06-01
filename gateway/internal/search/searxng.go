package search

import (
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"net/url"
	"strings"
	"time"
)

type SearXNGAdapter struct {
	baseURL string
	client  *http.Client
}

func NewSearXNGAdapter(baseURL string) *SearXNGAdapter {
	return &SearXNGAdapter{
		baseURL: strings.TrimRight(baseURL, "/"),
		client:  &http.Client{Timeout: 10 * time.Second},
	}
}

func (adapter *SearXNGAdapter) Search(ctx context.Context, query string) (Response, error) {
	if err := ctx.Err(); err != nil {
		return Response{}, fmt.Errorf("search canceled: %w", err)
	}
	endpoint, err := url.Parse(adapter.baseURL + "/search")
	if err != nil {
		return Response{}, fmt.Errorf("build searxng search url: %w", err)
	}
	params := endpoint.Query()
	params.Set("q", query)
	params.Set("format", "json")
	endpoint.RawQuery = params.Encode()

	request, err := http.NewRequestWithContext(ctx, http.MethodGet, endpoint.String(), nil)
	if err != nil {
		return Response{}, fmt.Errorf("create searxng request: %w", err)
	}
	response, err := adapter.client.Do(request)
	if err != nil {
		return Response{}, fmt.Errorf("call searxng: %w", err)
	}
	defer response.Body.Close()
	if response.StatusCode < http.StatusOK || response.StatusCode >= http.StatusMultipleChoices {
		return Response{}, fmt.Errorf("searxng status %d", response.StatusCode)
	}

	var body searXNGResponse
	if err := json.NewDecoder(response.Body).Decode(&body); err != nil {
		return Response{}, fmt.Errorf("decode searxng response: %w", err)
	}
	return Response{
		Query:     query,
		FetchedAt: time.Now().UTC(),
		Results:   mapSearXNGResults(body.Results),
	}, nil
}

func mapSearXNGResults(results []searXNGResult) []Result {
	mapped := make([]Result, 0, len(results))
	for _, result := range results {
		title := strings.TrimSpace(result.Title)
		link := strings.TrimSpace(result.URL)
		if title == "" || link == "" {
			continue
		}
		source := strings.TrimSpace(result.Engine)
		if source == "" {
			source = "SearXNG"
		}
		mapped = append(mapped, Result{
			Title:       title,
			Summary:     strings.TrimSpace(result.Content),
			URL:         link,
			Source:      source,
			PublishedAt: parseSearXNGTime(result.PublishedDate),
		})
	}
	return mapped
}

func parseSearXNGTime(value string) *time.Time {
	value = strings.TrimSpace(value)
	if value == "" {
		return nil
	}
	for _, layout := range []string{time.RFC3339, time.RFC1123, "2006-01-02"} {
		parsed, err := time.Parse(layout, value)
		if err == nil {
			utc := parsed.UTC()
			return &utc
		}
	}
	return nil
}

type searXNGResponse struct {
	Results []searXNGResult `json:"results"`
}

type searXNGResult struct {
	Title         string `json:"title"`
	Content       string `json:"content"`
	URL           string `json:"url"`
	Engine        string `json:"engine"`
	PublishedDate string `json:"publishedDate"`
}
