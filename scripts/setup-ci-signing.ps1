# latest 채널 자동 배포용 서명 키를 GitHub Actions 시크릿으로 등록합니다.
# 한 번만 실행하면 main 푸시마다 CI가 APK를 빌드해 latest에 올립니다.
$ErrorActionPreference = "Stop"
. "$PSScriptRoot\env.ps1"
$Root = Split-Path $PSScriptRoot -Parent
Set-Location -LiteralPath $Root

$jks = Join-Path $Root "lotte-release.jks"
$propsPath = Join-Path $Root "keystore.properties"
if (-not (Test-Path $jks)) { Write-Error "lotte-release.jks 없음" }
if (-not (Test-Path $propsPath)) { Write-Error "keystore.properties 없음" }

$props = @{}
Get-Content $propsPath | ForEach-Object {
  if ($_ -match '^\s*([^#=]+)=(.*)$') { $props[$Matches[1].Trim()] = $Matches[2].Trim() }
}

$b64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes($jks))
Write-Host "Uploading GitHub Actions secrets..." -ForegroundColor Cyan
$b64 | gh secret set SIGNING_KEYSTORE_BASE64 -R bossxor/INV_LotteGiants
$props["storePassword"] | gh secret set SIGNING_STORE_PASSWORD -R bossxor/INV_LotteGiants
$props["keyAlias"] | gh secret set SIGNING_KEY_ALIAS -R bossxor/INV_LotteGiants
$props["keyPassword"] | gh secret set SIGNING_KEY_PASSWORD -R bossxor/INV_LotteGiants
Write-Host "Done. main 푸시 시 .github/workflows/publish-latest.yml 이 latest 채널을 갱신합니다." -ForegroundColor Green
