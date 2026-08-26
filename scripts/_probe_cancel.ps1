$d = Get-Content "L:\naver_cancel_sample.json" -Raw | ConvertFrom-Json
$d.result.games | Where-Object { $_.cancel -eq $true -and $_.categoryId -eq 'kbo' } |
    Select-Object -First 5 gameId, homeTeamCode, awayTeamCode, statusInfo |
    Format-List
