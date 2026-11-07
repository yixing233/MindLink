$ErrorActionPreference='Stop'
$src='F:\AI\wex_apktool'
$dst='F:\AI\.zcode\tmp\wexfix\src'
if (Test-Path $dst) { Remove-Item $dst -Recurse -Force }
New-Item -ItemType Directory $dst | Out-Null
Copy-Item "$src\*" $dst -Recurse
Remove-Item "$dst\build" -Recurse -Force -ErrorAction SilentlyContinue
Write-Host "sync ok"
