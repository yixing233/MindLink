> 本文件按 GPL-3.0 第 5 条与各第三方许可的署名要求，列出 MindLink 使用或改编的第三方作品。
> 「使用范围」一项如实说明该作品在本项目中的具体位置，便于审计与追溯。

## 一、上游项目（GPL-3.0）

MindLink 是 [Wex](https://github.com/Ql1121/Wex) 的反编译修改工程，并移植、改编了
[WeKit](https://github.com/Ujhhgtg/WeKit) 的部分功能实现。两者均为 GPL-3.0 项目，
本项目同样以 GPL-3.0 发布，完整许可文本见 [LICENSE](LICENSE)。

### 1. Wex

- 项目：https://github.com/Ql1121/Wex
- 作者：Ql1121
- 许可：GNU General Public License v3.0
- 使用范围：本项目的基础工程。以下模块源自 Wex，经反编译后修改：
  `CalendarCardFeature`、`ImageCardFeature`、`HomeCardManager`、`MusicCardFeature`、
  `MusicPanels`、`MediaSessionFeature`、`FloatLyricFeature`、`TopBarFeature`、
  `ImagePicker`、`LocationHelper`、`BottomBarComposeFeature`、`WexStore`、
  `HookEntry`、`SettingsPanelHelper`、`MainActivity` 及 `FeatureSettingsActivity`。

### 2. WeKit

- 项目：https://github.com/Ujhhgtg/WeKit
- 作者：Ujhhgtg（WeKit Fork 维护者）及其上游贡献者
  （WeKit 自身声明其上游为 https://github.com/cwuom/WeKit）
- 许可：GNU General Public License v3.0
- 使用范围：以下功能为参照 WeKit 的实现思路改写为 Java 并适配本项目：

  | 本项目文件 | WeKit 参考实现 | 说明 |
  |---|---|---|
  | `ChatToolbarFeature`、`ChatToolbarViews`、`ChatToolbarLifecycle`、`ChatToolbarSettingCard` | `features/items/chat/ChatToolbar.kt` | 工具名表、系统拍摄、面板尺寸喂入算法、工具排序与逐项启用 |
  | `BubbleApply`、`BubbleStyleCard`、`BubbleSettings` | `features/items/beautify/CustomMessageBubbles.kt` | 九宫格图片与消息气泡挂载思路 |
  | `ConversationGroupingFeature`、`ConversationGroupTabsHost`、`ConversationAdapterSnapshotReader` | `features/items/chat/ConversationGrouping.kt`、`ConversationGroupTabsHost.kt` | 可编辑用户分组、标签栏与会话列表位置映射 |
  | `MarkAllReadFeature`、`QuickActionCard`、`PlusMenuExtra` | `features/items/home_screen_menu/MarkAllAsRead.kt` | 「+」菜单注入入口与批量已读流程 |
  | `TabBgDrawHook`、`GlobalWallpaper`、`TabBgSettingsCard` | `features/items/beautify/ApplyGlobalBackground.kt` | DecorView 全屏 `ImageView` 蒙层、`bringToFront` + `elevation`、扫一扫/拍摄/图片查看页排除 |
  | `HomeSidebar`、`HomeSidebarCards`、`HomeSidebarSlot`、`HomeSidebarState` | `features/items/beautify/home_screen_panel/HomeSidePanel*.kt` | 顶栏入口呼出、按需构建卡片、默认收起 |
  | `NavigationBarFeature`、`NavigationBarViews`、`ViewBackdropSampler`、`LiquidGlassMotion` | `features/items/beautify/ReplaceNavigationBar.kt`、`ui/content/FloatingBottomBar.kt`、`ui/content/ViewBackdrop.kt` | 四个原生页面及点击语义、真实宿主内容采样、滚动显隐、弹性选中胶囊与底栏布局 |
  | `ChatTopBarTransparent` | `features/items/chat/FloatingChatHeader.kt` | 聊天顶栏透明档位与头像/昵称同步 |
  | `ChatFooterTransparent` | `features/items/chat/FloatingChatFooter.kt`、`DisableChatBackgroundDimming.kt` | 输入栏背景清理与持续透明守卫 |

  以上 Java 文件为 **改写/适配**，不是逐字复制：WeKit 使用 Kotlin + Compose + DexKit 声明式
  hook，本项目这些宿主功能为 apktool 反编译工程下的 Java 实现 + smali 注入。
  功能设计、关键算法与部分配置键名沿用 WeKit，特此声明。

- **直接复制并修改的侧栏 Compose 源码（2026-11-14）**：
  `ui-compose/sidepanel/src/main/java/dev/mindlink/sidepanel/vendor/wekit/` 中的
  `HomeSidePanelHome.kt`（Home LazyColumn、58dp 资料头、32dp 顶栏资料）、
  `HomeSidePanelContent.kt`（右侧 28dp 圆角 Surface）、`HomeSidePanelCardFrame.kt`、
  `HomeSidePanelDataCards.kt`（日期/农历、天气、钱包、一言）、`HomeSidePanelActionCards.kt`、
  `HomeSidePanelImageCard.kt`、`HomeSidePanelLunarDate.kt` 与对应 card/content/weather/wallet/hitokoto
  数据模型直接源自本地 WeKit 同名源码，不是重新仿制的标题/文本行。
  改动限于包名和资源前缀、平台 `Bitmap` 图片输入、Java 快照/`Runnable` callbacks、
  宿主编辑器边界以及移除不移植的完整 drag/editor/hook/network/service 外壳；
  原卡片布局尺寸、字体、图标、loading/error/cached 展示与原默认绿色主题保留。
  不复制 `HomeSidePanel.kt` 的宿主 hook/抽屉动画，不新增天气/支付服务。
- WeKit 图片卡使用的 `ExpressiveOptionDropdown` 同样直接复制并修改，来源
  `ui/content/m3/DropDownMenuWidget.kt`，保留其原有
  `Copyright (C) 2023-2026 iamr0s, InstallerX Revived contributors` 与 `GPL-3.0-only` 声明。
- 侧栏资源只复制所用的 104 项 WeKit strings/arrays（含简体中文），遵循项目 GPL-3.0；
  使用 `wekit_sp_` 前缀，不搬运品牌 logo、作者头像、banner、用户图片或天气城市数据库 assets。
  唯一品牌文本替换为模块设置标签中的 MindLink。
- 每个复制源文件的 SHA-256、修改后 SHA-256、资源逐项来源与 unified diff 见
  `ui-compose/sidepanel/thirdparty/sourceprovenance/wekit-sidepanel.json` 与相邻 `.patch`。
  本地重现脚本为 `ui-compose/sidepanel/tools/import_wekit_sidepanel.py`，仅读取已授权参考与缓存，
  不执行参考源码，也不下载第三方数据。完整 GPL 许可见本项目 `LICENSE`。

## 二、桌面页面设计参考

- HyperChanger（https://github.com/ColdP/HyperChanger，Copyright 2026 btm_m，Apache-2.0）：
  `ui-compose/` 的真实 Kotlin 设置与关于页沿用其分组与页面结构；
  `DesktopScaffold.kt` 的顶栏/渐进 Backdrop 模糊和 `AboutMaterialBackground.kt` 的
  AGSL 网格噪声材质由其实现移植、适配。保留来源与许可，不复制其 logo、字标、
  作者头像或 MIUI 私有混色接口。
  上游 NOTICE 保留的历史署名为「HyperChanger，Copyright 2026 btm_m」；
  上游曾以 MIT 分发，当前使用版本按 Apache-2.0 分发。此处保留其历史许可说明。
  本项目桌面不使用该 NOTICE 另列的 HyperMusicCover 锁屏实现。
- 桌面实际使用 Material3 的 Theme、Scaffold、TopAppBar、Card、ListItem、
  IconButton/Text 与 Compose LazyColumn，以及独立的 Compose/Kotlin 运行时；
  页面所需的少量线性图标由本地 ImageVector 提供，不引入整套图标包。
  依赖版本、锁文件与校验和见 `ui-compose/`。

## 三、第三方库

### 3. Lucide

- 项目：https://lucide.dev （https://github.com/lucide-icons/lucide）
- 许可：ISC License；其中源自 Feather 的图标另附 MIT 许可（Copyright (c) 2013-present Cole Bemis），
  两段许可原文均随 APK 分发于 `assets/mindlink/licenses/LUCIDE-ISC.txt`
- Copyright (c) 2026 Lucide Icons and Contributors
- 版本：lucide-static 1.77.0 的 `icon-nodes.json`
- 使用范围：全模块唯一的线性矢量图标来源，共 86 个不同图标。由 `scripts/import_lucide_icons.py`
  把 SVG 元素（path/circle/rect/line/polyline 等，含弧线）统一转换为绝对坐标的 M/L/C/Z，生成：
  - `java_src/wex/ui/core/feature/LucideIcons.java`：View 侧 49 个键（聊天工具栏、「+」菜单、顶栏、
    音乐播放器、旧设置面板、Java 侧栏），运行期用 `Path` + `Canvas.drawPath` 描边渲染；
    播放/暂停/收藏等实心态为同一轮廓的填充 + 同色描边（Lucide 无官方实心套）。
  - `java_src/wex/ui/core/feature/NavigationIcons.java`：首页底栏 4 个页签（house、book-user、compass、user）。
  - `ui-compose/sidepanel/src/main/java/dev/mindlink/ui/LucideIcons.kt`：侧栏与「Mdl 设置」Compose 界面的
    56 个 `ImageVector`。
  生成时逐图与原 SVG 光栅化比对，像素一致（仅抗锯齿边缘差异）。
- 完整许可文本：https://github.com/lucide-icons/lucide/blob/main/LICENSE

### 4. Backdrop (AndroidLiquidGlass)

- 项目：https://github.com/Kyant0/AndroidLiquidGlass
- 作者：Kyant (Kyant0)
- 许可：Apache License 2.0
- 使用范围：Wex 的 Gradle 依赖 `io.github.kyant0:backdrop:2.0.0`，
  在本工程的 `smali/com/kyant/backdrop/` 中以反编译 smali 形式随 APK 分发（液体玻璃/模糊效果）。
- 完整许可文本：https://www.apache.org/licenses/LICENSE-2.0.txt

### 5. compose-miuix-ui / miuix-blur

- 项目：https://github.com/compose-miuix-ui/miuix
- 许可：Apache License 2.0
- 使用范围：`LiquidGlassShaders.java` 中的颜色控制与双光源 Bloom 高光 AGSL，来源版本为 WeKit 使用的 `miuix-blur-android 0.9.4-rc01`；`LiquidGlassDecoration.java` 按相同参数使用这些公式。
- 完整许可文本：https://www.apache.org/licenses/LICENSE-2.0.txt

### 6. AndroidLiquidGlass / InstallerX-Revived 液态玻璃组件

- 项目：https://github.com/Kyant0/AndroidLiquidGlass 、https://github.com/InstallerX-Revived/InstallerX-Revived
- 许可：Apache License 2.0
- 使用范围：`LiquidGlassShaders.java` 的圆角矩形折射与七点色散 AGSL、`LiquidGlassDecoration.java` 的交互高光和内阴影。源码经 WeKit `ui/content/liquid/` 搬入，生成脚本记录每段着色器 SHA-256。
- 完整许可文本：https://www.apache.org/licenses/LICENSE-2.0.txt

### 7. 已移除的图标来源（2026-11-22）

- Tabler Icons（MIT）、Google Material Symbols（Apache-2.0）及其 Compose 包装 composeicons 2.2.1（MIT）
  此前分别用于 View 侧、首页底栏与侧栏，现已全部替换为 Lucide，不再随 APK 分发其图标数据。
  `ui-compose/sidepanel/thirdparty/material-symbols-selected-2.2.1.jar` 不再是构建依赖，仅为历史记录保留。

### 8. DexKit

- 项目：https://github.com/LuckyPray/DexKit
- 许可：Apache License 2.0
- 使用范围：`smali/org/luckypray/dexkit/`（Java API）与
  `lib/*/libdexkit.so`（native 库），供 `DexKitAutoAdapt` 动态定位宿主类。
- 完整许可文本：https://www.apache.org/licenses/LICENSE-2.0.txt

### 9. AndroidX / Jetpack Compose / Kotlin 标准库

- 项目：https://github.com/androidx/androidx、https://github.com/JetBrains/kotlin
- 许可：Apache License 2.0
- 使用范围：随反编译工程保留的运行时依赖
  （`smali/androidx/`、`smali/kotlin/`、`lib/*/libandroidx.graphics.path.so`），
  主要服务于 Wex 原有的 Compose 界面与协程运行时。
  桌面 `ui-compose/app` 的真实 Material3 1.9.0（AndroidX 1.4.0）/Compose 1.11.1 payload 与侧栏
  `ui-compose/sidepanel` 的缓存 WeKit Material3 1.5.0-alpha28 / Compose 1.13.0-alpha01
  payload 使用独立 boot-parent classloader，不混入原反编译运行时或互相混用 ABI。
  侧栏离线依赖完整版本锁定与原始字节校验见 `ui-compose/sidepanel/gradle.lockfile`、
  `ui-compose/sidepanel/runtime-checksums.json`，不包含 Coil 或新网络/支付客户端。
- 完整许可文本：https://www.apache.org/licenses/LICENSE-2.0.txt

## 四、免责与合规说明

- 本项目不含任何来自微信（腾讯）的源代码；所有针对宿主的行为均通过 Xposed hook 在运行时完成。
- 本项目不包含上述第三方项目的商标。Lucide 的矢量路径按其 ISC 许可随源码分发。
- 若您是上述作品的权利人，认为本项目的署名方式不准确，请通过
  https://github.com/lanqin-mimi/MindLink/issues 联系，我们会在核实后更正。
