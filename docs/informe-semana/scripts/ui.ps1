param(
  [string]$Text,
  [string]$Desc,
  [int]$Index = 0,
  [switch]$List
)
# Headless UI driver: dumps the view hierarchy, prints nodes, and taps one.
$adb = "C:\Users\Saiciton\AppData\Local\Android\Sdk\platform-tools\adb.exe"
$remote = "/sdcard/ui.xml"
$local = Join-Path $PSScriptRoot "ui.xml"
& $adb shell uiautomator dump $remote | Out-Null
& $adb pull $remote $local 2>&1 | Out-Null

[xml]$doc = Get-Content -Raw -Path $local
$nodes = $doc.SelectNodes("//node")
$rows = foreach ($n in $nodes) {
  $t = $n.GetAttribute("text")
  $d = $n.GetAttribute("content-desc")
  if ([string]::IsNullOrEmpty($t) -and [string]::IsNullOrEmpty($d)) { continue }
  [pscustomobject]@{
    Text        = $t
    Desc        = $d
    Clickable   = $n.GetAttribute("clickable")
    Bounds      = $n.GetAttribute("bounds")
    Node        = $n
  }
}

if ($List) {
  $rows | ForEach-Object { "{0,-12} {1,-8} {2,-24} {3}" -f $_.Clickable, "", $_.Bounds, (($_.Text, $_.Desc) -ne "" -join " | ") }
  exit 0
}

$match = $null
if ($Text) {
  $hits = @($rows | Where-Object { $_.Text -like "*$Text*" })
  if ($hits.Count -gt $Index) { $match = $hits[$Index] }
}
if (-not $match -and $Desc) {
  $hits = @($rows | Where-Object { $_.Desc -like "*$Desc*" })
  if ($hits.Count -gt $Index) { $match = $hits[$Index] }
}
if (-not $match) {
  Write-Output "NOT FOUND (text='$Text' desc='$Desc' index=$Index)"
  exit 1
}
$b = $match.Bounds -replace "[\[\]]", ","
$parts = $b -split "," | Where-Object { $_ -ne "" }
$x1 = [int]$parts[0]; $y1 = [int]$parts[1]; $x2 = [int]$parts[2]; $y2 = [int]$parts[3]
$cx = [int](($x1 + $x2) / 2); $cy = [int](($y1 + $y2) / 2)
& $adb shell input tap $cx $cy | Out-Null
Write-Output "tapped '$($match.Text)$($match.Desc)' at $cx,$cy"
