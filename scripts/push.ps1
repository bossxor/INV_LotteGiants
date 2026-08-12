param(
  [string]$Message,
  [string]$Branch = "main"
)
$ErrorActionPreference = "Stop"
. "$PSScriptRoot\env.ps1"
$Root = Split-Path $PSScriptRoot -Parent
Set-Location -LiteralPath $Root
if (-not $Message) {
  Write-Error "Usage: .\scripts\push.ps1 -Message 'commit message'"
}
Write-Host "== status ==" -ForegroundColor Cyan
git status -sb
git add -A
git status -sb
git commit --trailer "Co-authored-by: Cursor <cursoragent@cursor.com>" -m $Message
git push -u origin HEAD
git status -sb
