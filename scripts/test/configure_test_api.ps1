# Configure test API environment variables.
# Secrets must come from arguments or the current environment.

param(
    [string]$BaseUrl = "https://zzshu.cc",
    [string]$ChatApiKey = $env:NEWAPI_API_KEY,
    [string]$ImageApiKey = $env:NEWAPI_IMAGE_KEY,
    [string]$ImageEndpoint = "/v1/images/generations",
    [switch]$Persistent
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($ChatApiKey)) {
    throw "ChatApiKey is required. Pass -ChatApiKey or set NEWAPI_API_KEY."
}

if ([string]::IsNullOrWhiteSpace($ImageApiKey)) {
    throw "ImageApiKey is required. Pass -ImageApiKey or set NEWAPI_IMAGE_KEY."
}

Write-Host "Configuring test API environment variables..." -ForegroundColor Green

# Set environment variables for the current PowerShell process.
[Environment]::SetEnvironmentVariable("AI_CHAT_PROVIDER_TYPE", "custom", "Process")
[Environment]::SetEnvironmentVariable("AI_CHAT_PROVIDER_BASE_URL", $BaseUrl, "Process")
[Environment]::SetEnvironmentVariable("AI_CHAT_PROVIDER_API_KEY", $ChatApiKey, "Process")
[Environment]::SetEnvironmentVariable("AI_CHAT_IMAGE_API_KEY", $ImageApiKey, "Process")
[Environment]::SetEnvironmentVariable("AI_CHAT_IMAGE_ENDPOINT_PATH", $ImageEndpoint, "Process")
[Environment]::SetEnvironmentVariable("NEWAPI_API_KEY", $ChatApiKey, "Process")
[Environment]::SetEnvironmentVariable("NEWAPI_IMAGE_KEY", $ImageApiKey, "Process")

Write-Host "Environment variables configured:" -ForegroundColor Yellow
Write-Host "  AI_CHAT_PROVIDER_TYPE: custom"
Write-Host "  AI_CHAT_PROVIDER_BASE_URL: $BaseUrl"
Write-Host "  AI_CHAT_PROVIDER_API_KEY: set"
Write-Host "  AI_CHAT_IMAGE_API_KEY: set"
Write-Host "  AI_CHAT_IMAGE_ENDPOINT_PATH: $ImageEndpoint"
Write-Host "  NEWAPI_API_KEY: set"
Write-Host "  NEWAPI_IMAGE_KEY: set"

if ($Persistent) {
    Write-Host "Persisting user-level environment variables..." -ForegroundColor Yellow
    [Environment]::SetEnvironmentVariable("AI_CHAT_PROVIDER_TYPE", "custom", "User")
    [Environment]::SetEnvironmentVariable("AI_CHAT_PROVIDER_BASE_URL", $BaseUrl, "User")
    [Environment]::SetEnvironmentVariable("AI_CHAT_PROVIDER_API_KEY", $ChatApiKey, "User")
    [Environment]::SetEnvironmentVariable("AI_CHAT_IMAGE_API_KEY", $ImageApiKey, "User")
    [Environment]::SetEnvironmentVariable("AI_CHAT_IMAGE_ENDPOINT_PATH", $ImageEndpoint, "User")
    [Environment]::SetEnvironmentVariable("NEWAPI_API_KEY", $ChatApiKey, "User")
    [Environment]::SetEnvironmentVariable("NEWAPI_IMAGE_KEY", $ImageApiKey, "User")
    Write-Host "Environment variables persisted at user level" -ForegroundColor Green
}

Write-Host ""
Write-Host "Test API configuration complete." -ForegroundColor Green
Write-Host "Run smoke test: .\scripts\test\newapi_smoke.ps1" -ForegroundColor Cyan
