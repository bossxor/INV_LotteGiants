# LotteWidget 개발 환경 로드 (네트워크 공유 경로 대응)
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-17.0.20.8-hotspot"
$env:ANDROID_HOME = "C:\Android\Sdk"
$env:ANDROID_SDK_ROOT = "C:\Android\Sdk"
$env:GRADLE_USER_HOME = Join-Path $env:USERPROFILE ".gradle"
$env:Path = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:ANDROID_HOME\cmdline-tools\latest\bin;C:\Program Files\Git\bin;C:\Program Files\Git\cmd;C:\Program Files\GitHub CLI;" + $env:Path

$script:RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
# UNC/한글 경로는 Gradle이 불안정할 수 있어 L: 드라이브 문자 사용 권장
if (-not (Test-Path "L:\")) {
  cmd /c "subst L: `"$script:RepoRoot`"" | Out-Null
}
Write-Host "JAVA_HOME=$env:JAVA_HOME"
Write-Host "ANDROID_HOME=$env:ANDROID_HOME"
Write-Host "Repo: $script:RepoRoot  (L:\ mapped if possible)"
