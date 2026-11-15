param(
  [int]$Rounds = 40,
  [int]$DelayMs = 60
)
# Spams taps across the gym lanes to break falling rocks.
$adb = "C:\Users\Saiciton\AppData\Local\Android\Sdk\platform-tools\adb.exe"
$xs = @(150, 320, 470, 620, 780, 930)
$ys = @(1450, 1300, 1150, 1000, 1250, 1550)
for ($i = 0; $i -lt $Rounds; $i++) {
  $x = $xs[$i % $xs.Count]
  $y = $ys[($i * 3) % $ys.Count]
  & $adb shell input tap $x $y | Out-Null
  Start-Sleep -Milliseconds $DelayMs
}
Write-Output "spammed $Rounds taps"
