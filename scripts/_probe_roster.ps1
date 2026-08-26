$ErrorActionPreference = "Continue"
$headers = @{
  "Content-Type" = "application/json; charset=utf-8"
  "Accept" = "application/json"
  "X-Requested-With" = "XMLHttpRequest"
  "Origin" = "https://m.koreabaseball.com"
  "Referer" = "https://m.koreabaseball.com/Kbo/PlayerAdd.aspx"
  "User-Agent" = "Mozilla/5.0"
}
$out = Join-Path $env:TEMP "roster_probe.txt"
"" | Set-Content $out -Encoding UTF8
function Probe($date) {
  $body = "{`"season_id`":`"2026`",`"g_dt`":`"$date`",`"t_id`":`"LT`"}"
  try {
    $raw = Invoke-RestMethod -Method Post -Uri "https://m.koreabaseball.com/ws/Kbo.asmx/GetRoster" -Headers $headers -Body ([System.Text.Encoding]::UTF8.GetBytes($body))
    $yLen = 0; $nLen = 0
    if ($raw.tableKboY) { $yLen = $raw.tableKboY.Length }
    if ($raw.tableKboN) { $nLen = $raw.tableKboN.Length }
    Add-Content $out "DATE $date Y=$yLen N=$nLen code=$($raw.code)" -Encoding UTF8
    if ($nLen -gt 20) {
      Add-Content $out "NHEAD $($raw.tableKboN.Substring(0, [Math]::Min(900, $nLen)))" -Encoding UTF8
    }
    if ($yLen -gt 20) {
      Add-Content $out "YHEAD $($raw.tableKboY.Substring(0, [Math]::Min(400, $yLen)))" -Encoding UTF8
    }
  } catch {
    Add-Content $out "DATE $date ERR $($_.Exception.Message)" -Encoding UTF8
  }
}
foreach ($d in @("2026-08-13","2026-08-12","2026-08-11","2026-08-10","2026-08-09","2026-08-08","2026-08-07","2026-08-06","2026-08-05","2026-08-04","2026-08-03","2026-08-02","2026-08-01","2026-07-31","2026-07-30")) {
  Probe $d
}
try {
  $k = Invoke-RestMethod "https://keubo.fan/api/roster-moves?teamId=7"
  Add-Content $out "KEUBO count=$($k.moves.Count)" -Encoding UTF8
  $k.moves | Select-Object -First 20 | ForEach-Object {
    Add-Content $out ("MOVE {0} {1} {2}" -f $_.moveDate, $_.moveType, $_.playerName) -Encoding UTF8
  }
} catch {
  Add-Content $out "KEUBO ERR $($_.Exception.Message)" -Encoding UTF8
}
Get-Content $out -Encoding UTF8
