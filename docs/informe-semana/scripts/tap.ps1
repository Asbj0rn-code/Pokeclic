param(
  [Parameter(Mandatory = $true)][int]$X,
  [Parameter(Mandatory = $true)][int]$Y
)
$adb = "C:\Users\Saiciton\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb shell input tap $X $Y | Out-Null
Write-Output "tap $X $Y"
