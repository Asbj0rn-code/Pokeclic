param(
  [Parameter(Mandatory = $true)][int]$Taps,
  [int]$X = 540,
  [int]$Y = 1700,
  [int]$DelayMs = 90
)
$adb = "C:\Users\Saiciton\AppData\Local\Android\Sdk\platform-tools\adb.exe"
for ($i = 0; $i -lt $Taps; $i++) {
  & $adb shell input tap $X $Y | Out-Null
  Start-Sleep -Milliseconds $DelayMs
}
Write-Output "tapped $Taps times at $X,$Y"
