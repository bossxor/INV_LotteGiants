# LotteWidget 개발 환경 로드 (네트워크 공유 경로 대응)
# JDK 17은 패치 버전이 바뀌면 폴더 이름도 바뀐다. 고정하지 않고 가장 새 것을 찾는다.
function Find-Jdk17 {
  $roots = @(
    "C:\Program Files\Microsoft",
    "C:\Program Files\Eclipse Adoptium",
    "C:\Program Files\Java",
    "C:\Program Files\Android\Android Studio\jbr"
  )
  foreach ($root in $roots) {
    if (-not (Test-Path $root)) { continue }
    if (Test-Path (Join-Path $root "bin\java.exe")) { return $root }
    $hit = Get-ChildItem $root -Directory -ErrorAction SilentlyContinue |
      Where-Object { $_.Name -match '17' -and (Test-Path (Join-Path $_.FullName "bin\java.exe")) } |
      Sort-Object Name -Descending | Select-Object -First 1
    if ($hit) { return $hit.FullName }
  }
  return $null
}

$jdk = Find-Jdk17
if ($jdk) {
  $env:JAVA_HOME = $jdk
} else {
  Write-Warning "JDK 17을 찾지 못했습니다. JAVA_HOME을 직접 설정하세요."
}
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
