param(
  [Parameter(Mandatory = $true)][string]$Title,
  [string[]]$Changes = @(),
  [string]$Version = "",
  [string[]]$Tags = @(),
  [string]$Batch = "",
  [string]$Summary = ""
)
# 给最新一批构建补一条更新日志，写入 tools/apk_share/changelog.json
$root = "C:\code\MindLink"
if (-not $Batch) {
  $newest = Get-ChildItem "$root\build" -Recurse -Depth 2 -File -ErrorAction SilentlyContinue |
    Where-Object { $_.Extension -in ".apk", ".apks", ".zip" } |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1
  if (-not $newest) { throw "build 目录下没有找到安装包" }
  $rel = $newest.DirectoryName.Substring("$root\build".Length).TrimStart("\")
  $Batch = $rel.Replace("\", "/")
}
if (-not $Version) { $Version = Get-Date -Format "yyyy.MM.dd-HHmm" }
$file = "$root\tools\apk_share\changelog.json"
if (Test-Path $file) {
  $text = [System.IO.File]::ReadAllText($file, [System.Text.Encoding]::UTF8)
  $data = $text | ConvertFrom-Json
} else {
  $data = [pscustomobject]@{ versions = @() }
}
$entry = [pscustomobject][ordered]@{
  batch   = $Batch
  version = $Version
  title   = $Title
  summary = $Summary
  changes = @($Changes)
  tags    = @($Tags)
  by      = "xcb"
  date    = (Get-Date -Format "yyyy-MM-dd")
}
$data.versions = @($entry) + @($data.versions)
$json = $data | ConvertTo-Json -Depth 8
[System.IO.File]::WriteAllText($file, $json, (New-Object System.Text.UTF8Encoding($false)))
Write-Host "已写入更新日志: $Version -> $Batch" -ForegroundColor Green
