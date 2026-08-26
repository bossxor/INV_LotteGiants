$headers = @{
  "Content-Type" = "application/json; charset=utf-8"
  "Accept" = "application/json, text/javascript, */*; q=0.01"
  "X-Requested-With" = "XMLHttpRequest"
  "Origin" = "https://m.koreabaseball.com"
  "Referer" = "https://m.koreabaseball.com/Kbo/PlayerAdd.aspx"
  "User-Agent" = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36"
}
$outDir = $env:TEMP
function Dump($date, $fmt) {
  $body = "{`"season_id`":`"2026`",`"g_dt`":`"$date`",`"t_id`":`"LT`"}"
  $path = Join-Path $outDir ("roster_" + $fmt + ".txt")
  try {
    $resp = Invoke-WebRequest -Method Post -Uri "https://m.koreabaseball.com/ws/Kbo.asmx/GetRoster" -Headers $headers -Body ([System.Text.Encoding]::UTF8.GetBytes($body)) -UseBasicParsing
    [System.IO.File]::WriteAllText($path, $resp.Content, [System.Text.UTF8Encoding]::new($false))
    Write-Host "OK $date len=$($resp.Content.Length) file=$path"
    Write-Host $resp.Content.Substring(0, [Math]::Min(500, $resp.Content.Length))
    Write-Host "-----"
  } catch {
    Write-Host "ERR $date $($_.Exception.Message)"
  }
}
Dump "2026-08-12" "iso"
Dump "20260812" "ymd"
