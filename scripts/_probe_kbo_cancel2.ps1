$r = Invoke-RestMethod -Uri "https://www.koreabaseball.com/ws/Main.asmx/GetKboGameList?leId=1&srId=0,1,3,4,5,6,7,8,9&date=20250717" -Headers @{ Referer = "https://www.koreabaseball.com/" }
$out = $r.game | Where-Object { $_.CANCEL_SC_ID -ge 1 } | ForEach-Object { [PSCustomObject]@{ id = $_.CANCEL_SC_ID; nm = $_.CANCEL_SC_NM } } | Sort-Object id -Unique
$out | Format-Table -AutoSize
[System.IO.File]::WriteAllText("L:\kbo_cancel_ids.json", ($r.game | ConvertTo-Json -Depth 5), [System.Text.UTF8Encoding]::new($false))
