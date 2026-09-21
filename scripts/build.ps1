param([ValidateSet("debug","release")][string]$Type = "debug")
$ErrorActionPreference = "Stop"
. "$PSScriptRoot\env.ps1"
if (Test-Path "L:\") { Set-Location L:\ } else { Set-Location -LiteralPath $script:RepoRoot }
$task = if ($Type -eq "release") { ":app:assembleRelease" } else { ":app:assembleDebug" }
Write-Host "== gradlew $task ==" -ForegroundColor Cyan
& .\gradlew.bat $task --stacktrace
if ($LASTEXITCODE -ne 0) {
  if ($Type -eq "release") {
    Write-Host "release 실패(NAS mergeReleaseResources 잠금일 수 있음). debug 또는 CI latest로 실기하세요." -ForegroundColor Yellow
  }
  exit $LASTEXITCODE
}
Write-Host "APK: app\build\outputs\apk\$Type\" -ForegroundColor Green
