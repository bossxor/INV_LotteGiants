$path = "$env:TEMP\roster_iso.txt"
$raw = [System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8)
$start = $raw.IndexOf('{')
$depth=0; $inStr=$false; $esc=$false; $end=-1
for ($i=$start; $i -lt $raw.Length; $i++) {
  $c=$raw[$i]
  if ($esc) { $esc=$false; continue }
  if ($c -eq '\' -and $inStr) { $esc=$true; continue }
  if ($c -eq '"') { $inStr = -not $inStr; continue }
  if (-not $inStr) {
    if ($c -eq '{') { $depth++ }
    elseif ($c -eq '}') { $depth--; if ($depth -eq 0) { $end=$i; break } }
  }
}
$json = $raw.Substring($start, $end-$start+1)
$obj = $json | ConvertFrom-Json
$out = "$env:TEMP\roster_parsed.txt"
$sb = New-Object System.Text.StringBuilder
[void]$sb.AppendLine("Ylen=$($obj.tableKboY.Length) Nlen=$($obj.tableKboN.Length) keys=$($obj.PSObject.Properties.Name -join ',')")
function DumpTable($name, $s) {
  [void]$sb.AppendLine("=== $name ===")
  if ([string]::IsNullOrWhiteSpace($s)) { [void]$sb.AppendLine("EMPTY"); return }
  $t = $s | ConvertFrom-Json
  foreach ($row in $t.rows) {
    $cells = @()
    foreach ($c in $row.row) { $cells += $c.Text }
    [void]$sb.AppendLine(($cells -join " | "))
  }
}
DumpTable "Y" $obj.tableKboY
DumpTable "N" $obj.tableKboN
[System.IO.File]::WriteAllText($out, $sb.ToString(), [System.Text.UTF8Encoding]::new($false))
Get-Content $out -Encoding UTF8
