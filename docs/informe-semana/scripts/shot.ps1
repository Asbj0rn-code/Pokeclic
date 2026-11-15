param(
  [Parameter(Mandatory = $true)][string]$Name
)
# Headless screencap helper: captures on device, then pulls the PNG down.
$adb = "C:\Users\Saiciton\AppData\Local\Android\Sdk\platform-tools\adb.exe"
$out = Join-Path $PSScriptRoot "capturas\$Name.png"
$remote = "/sdcard/cap__$Name.png"
& $adb shell screencap -p $remote | Out-Null
& $adb pull $remote $out 2>&1 | Out-Null
& $adb shell rm -f $remote | Out-Null
if (Test-Path $out) {
  $len = (Get-Item $out).Length
  Write-Output "saved $out ($len bytes)"
} else {
  Write-Output "ERROR: no file"
}
