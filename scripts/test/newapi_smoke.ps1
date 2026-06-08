param(
    [string]$BaseUrl = "https://zzshu.cc",
    [string]$ImagePath = "/v1/images/generations",
    [switch]$RequireKeys,
    [switch]$AllowImageGenerate,
    [string]$ImageModel = "",
    [string]$ImagePrompt = "A minimal smoke test image"
)

$ErrorActionPreference = "Stop"

function Get-SecretFromEnvironment {
    param([string]$Name)

    $value = [Environment]::GetEnvironmentVariable($Name, "Process")
    if ([string]::IsNullOrWhiteSpace($value)) {
        $value = [Environment]::GetEnvironmentVariable($Name, "User")
    }
    if ([string]::IsNullOrWhiteSpace($value)) {
        $value = [Environment]::GetEnvironmentVariable($Name, "Machine")
    }
    return $value
}

function Join-Endpoint {
    param(
        [string]$Root,
        [string]$Path
    )

    return "$($Root.TrimEnd('/'))/$($Path.TrimStart('/'))"
}

function Invoke-Endpoint {
    param(
        [string]$Name,
        [string]$Method,
        [string]$Uri,
        [string]$ApiKey,
        [string]$Body = $null
    )

    $headers = @{
        "Authorization" = "Bearer $ApiKey"
        "Content-Type" = "application/json"
    }

    try {
        if ($Body -eq $null) {
            $response = Invoke-WebRequest -Method $Method -Uri $Uri -Headers $headers -UseBasicParsing
        } else {
            $response = Invoke-WebRequest -Method $Method -Uri $Uri -Headers $headers -Body $Body -UseBasicParsing
        }
        [PSCustomObject]@{
            Name = $Name
            StatusCode = [int]$response.StatusCode
            Result = "ok"
        }
    } catch {
        $statusCode = $_.Exception.Response.StatusCode.value__
        [PSCustomObject]@{
            Name = $Name
            StatusCode = [int]$statusCode
            Result = "http_error"
        }
    }
}

$chatKey = Get-SecretFromEnvironment -Name "NEWAPI_API_KEY"
$imageKey = Get-SecretFromEnvironment -Name "NEWAPI_IMAGE_KEY"
$modelsUri = Join-Endpoint -Root $BaseUrl -Path "/v1/models"
$imagesUri = Join-Endpoint -Root $BaseUrl -Path $ImagePath

if ([string]::IsNullOrWhiteSpace($chatKey)) {
    if ($RequireKeys) {
        throw "NEWAPI_API_KEY is required."
    }
    Write-Output "chat_models: skipped (NEWAPI_API_KEY missing)"
} else {
    $modelsResult = Invoke-Endpoint -Name "chat_models" -Method "GET" -Uri $modelsUri -ApiKey $chatKey
    if ($modelsResult.StatusCode -ne 200) {
        throw "chat_models failed: HTTP $($modelsResult.StatusCode)"
    }
    Write-Output "chat_models: ok (HTTP 200)"
}

if ([string]::IsNullOrWhiteSpace($imageKey)) {
    if ($RequireKeys) {
        throw "NEWAPI_IMAGE_KEY is required."
    }
    Write-Output "image_endpoint: skipped (NEWAPI_IMAGE_KEY missing)"
} else {
    if ($AllowImageGenerate) {
        if ([string]::IsNullOrWhiteSpace($ImageModel)) {
            throw "ImageModel is required when AllowImageGenerate is set."
        }
        $body = @{
            model = $ImageModel
            prompt = $ImagePrompt
            n = 1
            size = "1024x1024"
        } | ConvertTo-Json -Compress
        $imageResult = Invoke-Endpoint -Name "image_generate" -Method "POST" -Uri $imagesUri -ApiKey $imageKey -Body $body
        if ($imageResult.StatusCode -lt 200 -or $imageResult.StatusCode -ge 300) {
            throw "image_generate failed: HTTP $($imageResult.StatusCode)"
        }
        Write-Output "image_generate: ok (HTTP $($imageResult.StatusCode))"
    } else {
        $imageResult = Invoke-Endpoint -Name "image_endpoint" -Method "POST" -Uri $imagesUri -ApiKey $imageKey -Body "{}"
        if ($imageResult.StatusCode -eq 401 -or $imageResult.StatusCode -eq 403) {
            throw "image_endpoint auth failed: HTTP $($imageResult.StatusCode)"
        }
        Write-Output "image_endpoint: auth accepted or request validation reached (HTTP $($imageResult.StatusCode))"
    }
}
