# 微信通知进入聊天后的悬浮顶栏修复（2026-11-23）

## 复现与根因

设备为小米 14，微信 8.0.76。20:07 点击系统通知，进入
`com.tencent.mm.ui.chatting.variants.ChattingMainUI`。顶栏保留返回、标题和菜单内容，
但三块玻璃卡片背景全部消失。

日志已经出现 `stage=applied`。视图树显示原生 `ActionBarContainer` 为
`36,146–1164,290`，同级装饰层却为 `-54,56–-54,56`，其中
`FloatingHeaderStyle$SeparatedDecoration` 为 `0,0–0,0`。
通知页使用的 `ActionBarOverlayLayout` 没有测量新插入的装饰层；
只写入 LayoutParams 无法赋予它实际尺寸。原生背景被清除后，装饰层仍无区域可绘制。

20:16 安装尺寸修复包后，三块卡片恢复，但录像进一步发现启动阶段短暂黑边。
当时聊天采样源从 `y=278` 开始，而顶栏从 `y=146` 开始，
采样器接受部分交集，导致玻璃折射读取背景范围外的空白像素。

## 修改

- `FloatingHeaderStyle.refresh()` 按当前标题坐标主动测量、布局模块自有装饰层，
  使首次绘制和后续尺寸变化都能得到正确区域。
- `ChatGlassSurface` 对显式独立聊天背景检查整块卡片是否落在背景范围内。
  背景尚未覆盖卡片时绘制完整普通底色；覆盖后立即恢复玻璃采样。
  该检查只作用于显式直接背景路径，普通兄弟视图采样继续沿用现有逻辑。
- 新增两组回归：只测量原生子节点的通知容器，以及背景局部覆盖、恢复、再次移动。

## 验证

- 装饰层尺寸用例在修改前失败，修改后通过；真实绘制几何共 102 项断言通过。
- 背景局部覆盖用例在修改前失败，修改后通过；聊天玻璃共 39 项断言通过。
- `chat-header-toolbar-checks.py` 完整通过，包含 missing 116 项、present 117 项、
  几何与 tips 检查以及预期的历史缺陷重放。
- `backdrop_recovery_checks.py` 完整通过：状态 113 项、恢复 619 项、
  聊天玻璃 39 项、会话下拉壁纸保护 13 项。
- 全量 Java 编译、D8、资源和载荷合并检查、签名与 zipalign 通过。
  资源及 Compose 载荷复用同日构建产物；`git diff --check` 通过。
- 最终包于 20:28 覆盖安装，后台结束微信进程后由新进程加载。
  20:29 点击另一条真实微信通知，确认仍进入 `ChattingMainUI`，
  装饰层实际尺寸为 `1308×324`。逐帧检查录像：启动时底色完整，随后恢复玻璃，
  未见此前黑边或卡片持续消失。
- 通知聊天页键盘展开、收起后卡片保持正常；返回已有聊天与会话列表正常。
  20:31 从列表重新进入带自定义壁纸的聊天，顶栏和底部玻璃卡片正常。
- 通知入口仍保留原生聊天内容的 `y=144` 起点；带自定义壁纸时顶部状态栏背景
  与 LauncherUI 内聊天存在差异。本次确认修复的是卡片消失与启动黑边，
  未将这一背景差异列为已修复项。

## 产物与记录

- 已安装 APK：`build/notification-header-20261123-202738/MindLink-1.0.apk`。
- 证据目录：`build/notification-entry-20261123/`。
- 原始复现：`entry.mp4`、`after.png`、`after-views.txt`、`wechat-logcat.txt`。
- 尺寸修复及黑边定位：`fixed-entry.mp4`、`fixed-views.txt`、
  `fixed-transition-detail.png`。
- 最终通知复测：`final-entry.mp4`、`final-header-frames.png`、`final-views.txt`、
  `final-wechat-logcat.txt`、`final-keyboard.png`、`final-keyboard-closed.png`。
- 普通入口：`final-normal.png`。
- 自动化结果：`header-tests-final.txt`、`backdrop-after.txt`、`build-final.txt`。
