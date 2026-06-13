# 配置测试API环境变量
# 用于快速设置测试API密钥和端点

param(
    [string]$BaseUrl = "https://zzshu.cc",
    [string]$ChatApiKey = "sk-xW43yPpkainQ3czsKectZAMGkGygtHN2ACV3IXkZ4BXeww6K",
    [string]$ImageApiKey = "sk-0WNIZP36vYIjvUDfF91NCVmYHH1Ndo3HCPpvzVdigquKT8xH",
    [string]$ImageEndpoint = "/v1/images/generations",
    [string]$BraveSearchApiKey = "BSArmnzPP8OQnMNX0BP3bL6QyzyJoaT",
    [switch]$Persistent
)

$ErrorActionPreference = "Stop"

Write-Host "配置测试API环境变量..." -ForegroundColor Green

# 设置环境变量
[Environment]::SetEnvironmentVariable("AI_CHAT_PROVIDER_TYPE", "custom", "Process")
[Environment]::SetEnvironmentVariable("AI_CHAT_PROVIDER_BASE_URL", $BaseUrl, "Process")
[Environment]::SetEnvironmentVariable("AI_CHAT_PROVIDER_API_KEY", $ChatApiKey, "Process")
[Environment]::SetEnvironmentVariable("AI_CHAT_IMAGE_API_KEY", $ImageApiKey, "Process")
[Environment]::SetEnvironmentVariable("AI_CHAT_IMAGE_ENDPOINT_PATH", $ImageEndpoint, "Process")
[Environment]::SetEnvironmentVariable("NEWAPI_API_KEY", $ChatApiKey, "Process")
[Environment]::SetEnvironmentVariable("NEWAPI_IMAGE_KEY", $ImageApiKey, "Process")
[Environment]::SetEnvironmentVariable("BRAVE_SEARCH_API_KEY", $BraveSearchApiKey, "Process")

Write-Host "环境变量已设置:" -ForegroundColor Yellow
Write-Host "  AI_CHAT_PROVIDER_TYPE: custom"
Write-Host "  AI_CHAT_PROVIDER_BASE_URL: $BaseUrl"
Write-Host "  AI_CHAT_PROVIDER_API_KEY: $($ChatApiKey.Substring(0, 10))..."
Write-Host "  AI_CHAT_IMAGE_API_KEY: $($ImageApiKey.Substring(0, 10))..."
Write-Host "  AI_CHAT_IMAGE_ENDPOINT_PATH: $ImageEndpoint"
Write-Host "  NEWAPI_API_KEY: $($ChatApiKey.Substring(0, 10))..."
Write-Host "  NEWAPI_IMAGE_KEY: $($ImageApiKey.Substring(0, 10))..."
Write-Host "  BRAVE_SEARCH_API_KEY: $($BraveSearchApiKey.Substring(0, 10))..."

if ($Persistent) {
    Write-Host "设置持久化环境变量..." -ForegroundColor Yellow
    [Environment]::SetEnvironmentVariable("AI_CHAT_PROVIDER_TYPE", "custom", "User")
    [Environment]::SetEnvironmentVariable("AI_CHAT_PROVIDER_BASE_URL", $BaseUrl, "User")
    [Environment]::SetEnvironmentVariable("AI_CHAT_PROVIDER_API_KEY", $ChatApiKey, "User")
    [Environment]::SetEnvironmentVariable("AI_CHAT_IMAGE_API_KEY", $ImageApiKey, "User")
    [Environment]::SetEnvironmentVariable("AI_CHAT_IMAGE_ENDPOINT_PATH", $ImageEndpoint, "User")
    [Environment]::SetEnvironmentVariable("NEWAPI_API_KEY", $ChatApiKey, "User")
    [Environment]::SetEnvironmentVariable("NEWAPI_IMAGE_KEY", $ImageApiKey, "User")
    [Environment]::SetEnvironmentVariable("BRAVE_SEARCH_API_KEY", $BraveSearchApiKey, "User")
    Write-Host "环境变量已持久化到用户级别" -ForegroundColor Green
}

Write-Host ""
Write-Host "测试API配置完成!" -ForegroundColor Green
Write-Host "运行测试: .\scripts\test\newapi_smoke.ps1" -ForegroundColor Cyan