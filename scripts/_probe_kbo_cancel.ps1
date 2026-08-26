$r = Invoke-RestMethod -Uri "https://www.koreabaseball.com/ws/Main.asmx/GetKboGameList?leId=1&srId=0,1,3,4,5,6,7,8,9&date=20250717" -Headers @{ Referer = "https://www.koreabaseball.com/" }
$r.game | Where-Object { $_.GAME_STATE_SC -eq 4 -or $_.CANCEL_SC_ID -ge 1 } |
    Select-Object G_ID, CANCEL_SC_ID, CANCEL_SC_NM, GAME_STATE_SC, AWAY_ID, HOME_ID |
    Format-List
