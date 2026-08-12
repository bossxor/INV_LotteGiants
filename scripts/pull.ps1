param([string]$Branch = "main")
$ErrorActionPreference = "Stop"
. "$PSScriptRoot\env.ps1"
$Root = Split-Path $PSScriptRoot -Parent
Set-Location -LiteralPath $Root
Write-Host "== git pull origin $Branch ==" -ForegroundColor Cyan
git fetch origin
git pull origin $Branch
git status -sb
