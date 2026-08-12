param([ValidateSet("debug","release")][string]$Type = "debug")
$ErrorActionPreference = "Stop"
. "$PSScriptRoot\env.ps1"
if (Test-Path "L:\") { Set-Location L:\ } else { Set-Location -LiteralPath $script:RepoRoot }
$task = if ($Type -eq "release") { ":app:assembleRelease" } else { ":app:assembleDebug" }
Write-Host "== gradlew $task ==" -ForegroundColor Cyan
& .\gradlew.bat $task --stacktrace
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
Write-Host "APK: app\build\outputs\apk\$Type\" -ForegroundColor Green
