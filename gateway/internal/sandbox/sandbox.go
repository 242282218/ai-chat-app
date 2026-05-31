package sandbox

import (
	"bytes"
	"context"
	"errors"
	"fmt"
	"os/exec"
	"time"
)

const (
	defaultImage        = "python:3.12-alpine"
	defaultOutputLimit  = 16 * 1024
	defaultTimeout      = 3 * time.Second
	defaultMaxTimeout   = 10 * time.Second
	dockerUnavailableID = 125
)

var ErrUnavailable = errors.New("sandbox unavailable")

type Request struct {
	Language string
	Code     string
	Timeout  time.Duration
}

type Response struct {
	Language   string `json:"language"`
	Stdout     string `json:"stdout"`
	Stderr     string `json:"stderr"`
	ExitCode   int    `json:"exitCode"`
	DurationMs int64  `json:"durationMs"`
	TimedOut   bool   `json:"timedOut"`
	Truncated  bool   `json:"truncated"`
}

type Runner interface {
	Run(ctx context.Context, request Request) (Response, error)
}

type DockerRunner struct {
	Image       string
	OutputLimit int
	MaxTimeout  time.Duration
}

func NewDockerRunner() DockerRunner {
	return DockerRunner{
		Image:       defaultImage,
		OutputLimit: defaultOutputLimit,
		MaxTimeout:  defaultMaxTimeout,
	}
}

func (runner DockerRunner) Run(ctx context.Context, request Request) (Response, error) {
	if _, err := exec.LookPath("docker"); err != nil {
		return Response{}, fmt.Errorf("%w: docker binary not found", ErrUnavailable)
	}

	timeout := request.Timeout
	if timeout <= 0 {
		timeout = defaultTimeout
	}
	if runner.MaxTimeout > 0 && timeout > runner.MaxTimeout {
		timeout = runner.MaxTimeout
	}

	runCtx, cancel := context.WithTimeout(ctx, timeout)
	defer cancel()

	stdout := newCappedBuffer(runner.outputLimit())
	stderr := newCappedBuffer(runner.outputLimit())
	startedAt := time.Now()
	cmd := exec.CommandContext(runCtx, "docker", runner.args(request.Code)...)
	cmd.Stdout = stdout
	cmd.Stderr = stderr

	err := cmd.Run()
	duration := time.Since(startedAt)
	timedOut := errors.Is(runCtx.Err(), context.DeadlineExceeded)
	exitCode, runErr := exitCode(err, timedOut)
	if runErr != nil {
		return Response{}, runErr
	}

	return Response{
		Language:   "python",
		Stdout:     stdout.String(),
		Stderr:     stderr.String(),
		ExitCode:   exitCode,
		DurationMs: duration.Milliseconds(),
		TimedOut:   timedOut,
		Truncated:  stdout.Truncated() || stderr.Truncated(),
	}, nil
}

func (runner DockerRunner) args(code string) []string {
	image := runner.Image
	if image == "" {
		image = defaultImage
	}
	return []string{
		"run",
		"--rm",
		"--network", "none",
		"--cpus", "0.5",
		"--memory", "128m",
		"--pids-limit", "64",
		"--read-only",
		"--tmpfs", "/tmp:rw,nosuid,nodev,size=16m",
		"--cap-drop", "ALL",
		"--security-opt", "no-new-privileges",
		"--user", "65534:65534",
		image,
		"python",
		"-c",
		code,
	}
}

func (runner DockerRunner) outputLimit() int {
	if runner.OutputLimit <= 0 {
		return defaultOutputLimit
	}
	return runner.OutputLimit
}

func exitCode(err error, timedOut bool) (int, error) {
	if err == nil {
		return 0, nil
	}
	if timedOut {
		return -1, nil
	}
	var exitErr *exec.ExitError
	if errors.As(err, &exitErr) {
		code := exitErr.ExitCode()
		if code == dockerUnavailableID {
			return 0, fmt.Errorf("%w: docker run failed", ErrUnavailable)
		}
		return code, nil
	}
	return 0, fmt.Errorf("%w: %v", ErrUnavailable, err)
}

type cappedBuffer struct {
	buffer    bytes.Buffer
	limit     int
	truncated bool
}

func newCappedBuffer(limit int) *cappedBuffer {
	return &cappedBuffer{limit: limit}
}

func (buffer *cappedBuffer) Write(p []byte) (int, error) {
	remaining := buffer.limit - buffer.buffer.Len()
	if remaining <= 0 {
		buffer.truncated = true
		return len(p), nil
	}
	if len(p) > remaining {
		_, _ = buffer.buffer.Write(p[:remaining])
		buffer.truncated = true
		return len(p), nil
	}
	_, _ = buffer.buffer.Write(p)
	return len(p), nil
}

func (buffer *cappedBuffer) String() string {
	return buffer.buffer.String()
}

func (buffer *cappedBuffer) Truncated() bool {
	return buffer.truncated
}
