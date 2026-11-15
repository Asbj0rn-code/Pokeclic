param(
  [Parameter(Mandatory = $true)][string]$Until,
  [int]$MaxTaps = 40,
  [int]$X = 540,
  [int]$Y = 1682,
  [int]$DelayMs = 700
)
# Taps a fixed point (the dialogue box) until the dumped UI contains $Until.
$adb = "C:\Users\Saiciton\AppData\Local\Android\Sdk\platform-tools\adb.exe"
$remote = "/sdcard/ui.xml"
$local = Join-Path $PSScriptRoot "ui.xml"

for ($i = 0; $i -lt $MaxTaps; $i++) {
  & $adb shell uiautomator dump $remote | Out-Null
  & $adb pull $remote $local 2>&1 | Out-Null
  $raw = Get-Content -Raw -Path $local
  if ($raw -match [regex]::Escape($Until)) {
    Write-Output "reached '$Until' after $i taps"
    exit 0
  }
  & $adb shell input tap $X $Y | Out-Null
  Start-Sleep -Milliseconds $DelayMs
}
Write-Output "NOT REACHED '$Until' after $MaxTaps taps"
exit 1
