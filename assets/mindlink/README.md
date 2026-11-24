# WeLiquid

基于 Wex 改进开发的微信娱乐模块。

## 📦 安装

1. 准备已 root 并安装 LSPosed 的设备。
2. 安装本模块 APK，在 LSPosed 中启用模块。模块以 libxposed API（standard 入口）加载，作用域已静态声明为「微信」，需 LSPosed 支持 API 101 及以上。
3. 强行停止并重启微信后生效。

## 🔒 隐私与安全

- 本模块无统计与埋点，不上传聊天记录、联系人或自定义图片。
- **读取微信本人资料**：首页顶栏与左侧栏会显示你的微信昵称、头像与状态。该功能默认开启，可在设置「读取微信个人资料」关闭。读取方式与范围：
  - 从 `com.tencent.mm_preferences` 读取本人 wxid（仅用于定位头像缓存文件）；
  - 从「我」页个人卡片已渲染的控件中读取昵称、头像位图与状态文字；
  - 回退时读取微信本地头像缓存目录中的图片文件。
  - 以上数据**仅在本进程内存中使用**，不写入模块存储、不向任何服务器发送；关闭开关后不再读取，已读取的值在下一次显示时不再使用。
  - 昵称与状态文字可能出现在模块调试日志中（仅记录「有/无」，不记录具体内容）。
- 配置、自定义图片、缓存的天气位置和调试日志保存在本地存储（/sdcard/Android/media/com.tencent.mm/Mdl/）。日志可能包含位置及功能诊断信息，请勿随意公开。
- 日历卡片按所选样式请求黄历（www.36jxs.com，携带日期）和一言（api.bi71t5.cn）。
- 「联网天气与定位」默认关闭，需在「更多」页确认开启。侧栏天气可跟随当前位置，也可在卡片设置中手动搜索并保存固定区县。跟随模式只在侧栏处于前台时更新位置，由系统地名服务识别区县；手动搜索将输入的地名发送给 Nominatim（nominatim.openstreetmap.org）。日历和侧栏天气将所用坐标或已有城市代码发送给小米天气（weatherapi.market.xiaomi.com）。没有精确定位权限或可用位置时不会回退到 IP 定位。
- 关闭「联网天气与定位」后不再发起位置相关网络请求，已有本地位置配置保留；已开始的请求无法撤回。第三方服务仍能看到请求 IP 等常规连接信息，其数据处理由各自的隐私政策约束。
- 「查看日志文件」仅在模块内只读显示日志，不调用外部应用、不分享或上传日志。
- 源码公开，可自行审计。

## 🎨 图标

界面图标统一使用 [Lucide](https://lucide.dev)（24 栅格、2px 圆头线条）。
`scripts/import_lucide_icons.py` 从 lucide-static 的 `icon-nodes.json` 生成 View 侧的 `LucideIcons.java`、
底栏的 `NavigationIcons.java` 与 Compose 侧的 `LucideIcons.kt`，矢量渲染，不需要外部依赖。

Lucide 遵循 ISC 许可：
Copyright (c) 2026 Lucide Icons and Contributors — https://github.com/lucide-icons/lucide

## 🙏 致谢

- [Ql1121 / Wex](https://github.com/Ql1121/Wex) —— 原项目。本项目是它的反编译修改工程，
  首页卡片（日历/图片/音乐）、音乐播放器、顶栏、底栏等基础功能来自 Wex。
- [Ujhhgtg / WeKit](https://github.com/Ujhhgtg/WeKit) —— 微信增强模块。以下功能移植/改编自它：

  - 聊天工具栏（`ChatToolbarFeature`，含系统拍摄与逐项启用）
  - 可编辑用户分组（`ConversationGroupingFeature`）
  - 一键已读（`MarkAllReadFeature`，数据库直查解耦版）
  - 全局背景（`GlobalBackgroundFeature`）
  - 首页底栏（`NavigationBarFeature`）
  - 首页侧滑面板（`HomeSidePanelFeature`）
  - 悬浮聊天顶栏/输入栏（`FloatingChatHeaderFeature`、`FloatingChatFooterFeature`）
  - 自定义消息气泡（`BubbleApply`）
  - 全局壁纸蒙层（`TabBgDrawHook`）
  - 首页日历/音乐侧栏（`HomeSidebar`）
  - 聊天顶栏/底栏透明（`ChatTopBarTransparent`、`ChatFooterTransparent`）

  这些实现已改写为 Java 并适配本项目的 smali 注入结构，不是逐字复制；
  但功能设计、关键算法与部分配置键名沿用 WeKit。完整的逐文件对照见
  [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md)。
- 其他第三方组件（Lucide、Backdrop、DexKit、AndroidX/Kotlin）及其许可，
  同样列于 [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md)。

## 📄 许可证

本项目基于 [GPL-3.0](LICENSE) 许可证发布。

Wex 与 WeKit 均为 GPL-3.0 项目，本项目作为两者的衍生作品，同样以 GPL-3.0 发布，
并按该许可证保留原作者署名与许可声明。若需再分发，请一并遵守 GPL-3.0 的源码开放要求。
## 界面与设置

桌面入口、微信内设置与首页侧栏统一使用 Compose Material Design 3，共用主题和基础组件。
桌面入口显示应用信息、项目说明与第三方声明，并提供打开微信的操作。

功能配置从微信右上角“+”→“Mdl 设置”进入；首页侧栏的“Mdl 设置”快捷项打开同一个面板。
桌面页不读取微信配置。模块不创建或迁移微信配置库、不写微信日志，也不申请“所有文件访问”等宽泛权限。
