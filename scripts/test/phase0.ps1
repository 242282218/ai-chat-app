$ErrorActionPreference = "Stop"

Write-Host "Checking Android toolchain..."
if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    throw "java is not on PATH. Install JDK 17+ before running Android Gradle tasks."
}

if (-not (Test-Path "$PSScriptRoot\..\..\gradlew.bat")) {
    throw "gradlew.bat is missing."
}

Write-Host "Running Android checks..."
& "$PSScriptRoot\..\..\gradlew.bat" testDebugUnitTest lint assembleDebug
