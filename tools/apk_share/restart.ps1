# Recreate the APK share container only (fixed domain apk.157342.xyz goes through wb-cloudflared)
$ErrorActionPreference = "Stop"
$root = "C:\code\MindLink"
docker rm -f mindlink-apk-web | Out-Null
docker run -d --name mindlink-apk-web --restart unless-stopped -p 8098:8098 `
  -e SHARE_PASSWORD=xcb157342 -e SHARE_ROOT=/data -e SHARE_PORT=8098 `
  -v "$root\build:/data:ro" -v "$root\tools\apk_share:/app:ro" `
  python:3.12-slim python /app/server.py | Out-Null
Start-Sleep -Seconds 3
Write-Host "URL : https://apk.157342.xyz"
Write-Host "PASS: xcb157342"
