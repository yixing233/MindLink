# MindLink APK 分发（固定域名）

密码保护的构建产物页面（Ant Design），经过已有的命名隧道 `wb-cloudflared` 对外提供。

## 地址

- 固定地址：<https://apk.157342.xyz>
- 访问密码：`xcb157342`

## 链路

```
浏览器 → Cloudflare → 命名隧道 f2212e16-… (容器 wb-cloudflared)
        → http://host.docker.internal:8098 → 容器 mindlink-apk-web:8098
```

## 页面组织方式

- 按**日期 → 批次 → 安装包**三级归档，不再把 300+ 个包平铺
  - 日期标题：`11月22日 · 15 个批次 · 1.11 GB`
  - 批次卡片：批次名 + 短哈希 + 构建时间 + 包数 + 总大小，点击展开
  - 最新批次默认展开并标「最新」；已有日志的批次显示版本标签
  - 批次内 `MindLink-1.0.apk` 标「主包」，`*unsigned*` 标「未签名」，`.zip` 标「压缩包」
- 顶部日期筛选、搜索、全部展开/收起
- 两个页签：「安装包」/「更新日志」（`#log` 可直接深链到日志页）

## 更新日志 / 版本对比 / 回档

数据源两处，可同时使用：

1. `tools/apk_share/changelog.json`（主数据源，推荐）
   ```json
   {
     "versions": [
       {
         "batch": "full-build-20261122-162447-928b3760",
         "version": "2026.11.22-1624",
         "title": "侧栏编辑样式 + 图标替换 + 置顶卡片修复",
         "summary": "一句话概述",
         "changes": ["改了点 A", "修了点 B"],
         "tags": ["侧栏", "图标"],
         "date": "2026-11-22"
       }
     ]
   }
   ```
2. 构建目录内自带的说明文件：`build/<批次>/CHANGELOG.md`（或 `NOTES.md`、`更新日志.md`、`changelog.txt`），最多读取 8KB

页面能力：

- 批次展开即显示该批次的更新日志（标题、摘要、改动列表、标签）
- 「更新日志」页签：搜索版本/标题/改动，勾选任意两个版本 → 就地对比（新增/移除的改动、增删文件、体积变化、A/B 直接下载）
- 每个版本都有「回档下载」（主包直下）与「查看批次」

写日志的快捷方式（自动取最新一批构建）：

```powershell
pwsh -File tools/apk_share/add-changelog.ps1 `
  -Title "侧栏编辑样式优化" `
  -Changes "侧栏拖拽排序修复","图标尺寸统一" `
  -Tags "侧栏","图标"
```

可选参数：`-Version`（默认时间戳版本号）、`-Batch`（指定批次目录）、`-Summary`。

## 文件

- `server.py`：标准库 HTTP 服务，Cookie 会话 + 登录限速 + Range 下载
  - `/api/files` 文件清单、`/api/changelog` 已合并文件信息的更新日志
  - 文件列表后台每 15 秒扫描并缓存；HTML/JSON/静态资源 gzip + ETag/304
- `static/app-login.html`、`static/app-files.html`：页面模板（改完重启容器生效）
- `static/*.min.js`、`antd-reset.css`：本地自托管，不依赖外网 CDN
- `changelog.json`：更新日志数据源
- `restart.ps1`：重建 web 容器；`add-changelog.ps1`：追加一条更新日志
- `C:\code\cloudflared\config.yml`：ingress `apk.157342.xyz → host.docker.internal:8098`

## 重启

```powershell
pwsh -File tools/apk_share/restart.ps1
```

改完 `C:\code\cloudflared\config.yml` 后需重启隧道：`docker restart wb-cloudflared`

## 说明

- 新增构建产物无需重启，后台 15 秒内刷新，页面点「刷新」即可。
- 扫描范围：`/data` 下最多 3 层目录的 `.apk/.apks/.zip`。
- 服务端重启后 HMAC 会话密钥重建，需要重新输入密码。
