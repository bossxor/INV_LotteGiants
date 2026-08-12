param(
  [string]$ApkPath = "",
  [string]$Notes = "자동 배포 (latest 채널)"
)
$ErrorActionPreference = "Stop"
. "$PSScriptRoot\env.ps1"
$Root = Split-Path $PSScriptRoot -Parent
Set-Location -LiteralPath $Root

if (-not $ApkPath) {
  $ApkPath = Join-Path $Root "app\build\outputs\apk\release\app-release.apk"
  if (-not (Test-Path $ApkPath) -and (Test-Path "L:\app\build\outputs\apk\release\app-release.apk")) {
    $ApkPath = "L:\app\build\outputs\apk\release\app-release.apk"
  }
}
if (-not (Test-Path $ApkPath)) {
  Write-Error "APK not found: $ApkPath  (먼저 scripts\build.ps1 -Type release 실행)"
}

# aapt로 versionCode/Name 읽기
$bt = Get-ChildItem "C:\Android\Sdk\build-tools" -Directory | Sort-Object Name -Descending | Select-Object -First 1
$aapt = Join-Path $bt.FullName "aapt2.exe"
$badging = & $aapt dump badging $ApkPath 2>$null | Select-Object -First 1
if ($badging -notmatch "versionCode='(\d+)'") { Write-Error "versionCode parse failed" }
$versionCode = [int]$Matches[1]
$versionName = if ($badging -match "versionName='([^']+)'") { $Matches[1] } else { "$versionCode" }

$work = Join-Path $env:TEMP "lotte-latest-publish"
Remove-Item -Recurse -Force $work -ErrorAction SilentlyContinue
New-Item -ItemType Directory $work | Out-Null
$apkOut = Join-Path $work "LotteGiants.apk"
Copy-Item $ApkPath $apkOut -Force

$manifest = @{
  versionCode = $versionCode
  versionName = $versionName
  apkFileName = "LotteGiants.apk"
  notes       = $Notes
} | ConvertTo-Json -Compress
$manifestPath = Join-Path $work "update.json"
[System.IO.File]::WriteAllText($manifestPath, $manifest, [System.Text.UTF8Encoding]::new($false))

$body = @"
versionCode: $versionCode

## latest 채널
- versionName: $versionName
- 앱이 시작 시 이 채널을 확인해 자동 업데이트합니다.
- $Notes
"@
$bodyFile = Join-Path $work "notes.md"
[System.IO.File]::WriteAllText($bodyFile, $body, [System.Text.UTF8Encoding]::new($false))

Write-Host "== publish latest channel ==" -ForegroundColor Cyan
Write-Host "versionCode=$versionCode versionName=$versionName"

$prevEA = $ErrorActionPreference
$ErrorActionPreference = "Continue"
gh release view latest -R bossxor/INV_LotteGiants 2>$null | Out-Null
$hasLatest = ($LASTEXITCODE -eq 0)
$ErrorActionPreference = $prevEA

if ($hasLatest) {
  gh release upload latest $apkOut $manifestPath -R bossxor/INV_LotteGiants --clobber
  if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
  gh release edit latest -R bossxor/INV_LotteGiants --notes-file $bodyFile --title "latest ($versionName)"
  if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
} else {
  gh release create latest $apkOut $manifestPath -R bossxor/INV_LotteGiants --notes-file $bodyFile --title "latest ($versionName)"
  if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

Write-Host "OK: https://github.com/bossxor/INV_LotteGiants/releases/tag/latest" -ForegroundColor Green
