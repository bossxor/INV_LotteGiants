$ids = @{}
for ($m = 3; $m -le 8; $m++) {
    for ($d = 1; $d -le 28; $d++) {
        $date = "2025{0:D2}{1:D2}" -f $m, $d
        try {
            $r = Invoke-RestMethod -Uri "https://www.koreabaseball.com/ws/Main.asmx/GetKboGameList?leId=1&srId=0,1,3,4,5,6,7,8,9&date=$date" -Headers @{ Referer = "https://www.koreabaseball.com/" }
            foreach ($g in $r.game) {
                $id = [int]$g.CANCEL_SC_ID
                if ($id -ge 1 -and -not $ids.ContainsKey($id)) {
                    $ids[$id] = $g.CANCEL_SC_NM
                }
            }
        } catch {}
    }
}
$ids.GetEnumerator() | Sort-Object Name | ForEach-Object { "{0}={1}" -f $_.Name, $_.Value }
