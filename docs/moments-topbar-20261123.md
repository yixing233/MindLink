# 朋友圈悬浮顶栏与相机裁切修复（2026-11-23）

## 效果

- 通讯录和发现页的首行在现有顶栏间距之外增加 12dp 留白。
- 朋友圈的返回、相机按钮使用圆形悬浮卡片，左右边距 12dp；顶部使用现有渐进模糊组件。
- 适配封面展开和向上滚动后的两种状态，标题显隐沿用宿主过渡。
- 设置入口为「首页与朋友圈顶栏」，新增「启用朋友圈悬浮按钮」和「顶部渐进模糊」，默认开启，并随材质设置即时刷新。

## 实现

`MomentsTopBarCards` 在朋友圈恢复时装配，在暂停、销毁或关闭功能时恢复宿主状态。优先识别当前版本的 `ImproveSnsTimelineUI`，也保留旧 `SnsTimeLineUI` 入口兼容。

实机微信 8.0.76（3141）的两种形态属于同一页面的滚动状态。宿主在封面滚动约 245dp 后切换顶栏，以约 20dp 的区间渐显标题，同时更换相机资源。顶部卡片复用 `ChatGlassSurface`，渐进模糊复用 `ProgressiveTopBlur`，采样来自顶栏之外的内容区域。

相机保留原生可点击、可长按的 ImageView、监听器及无障碍描述；仅把其 drawable 替换为透明占位，由卡片绘制统一的线框相机，避免宿主 SVG 在切换状态后重新刷成白色。退出时恢复宿主最新的 drawable。返回箭头使用原生图形和公开颜色接口。

原生 `setActionbarFrosted` 请求在装配期间被抑制，并保存最新请求以便退出时恢复。窗口状态栏、背景、裁剪、位移、文字颜色和图标状态也随生命周期恢复。

## 相机图标裁切

此前通过 `LucideIcons.drawable` 默认的 20 单位安全区域生成位图，图形位于 x=2..22 的外侧描边被图片边缘裁掉，左右边角看起来发平。

修复为 `LucideIcons.bitmap("shoot.png", 24dp, ink, 2f)`，按完整 24 单位画布渲染描边，并置于居中的 24dp 绘制框中；可见轮廓约 22dp。按钮大小、点击区域和位置保持原状。

## 验证

- `moments_topbar_checks.py`：42 项通过，直接编译生产类与 Android 替身，覆盖状态切换、几何位置、原生操作、无障碍可见性、最新 drawable 恢复、生命周期和资源释放。替身测试不代表 GPU 渲染验收。
- Bootstrap（包含失败隔离）、settings/systembar 30 项、设置状态检查通过。
- Backdrop 的 state 113、recovery 619、glass 39、wallpaper 13 项通过。
- Java 编译、D8、资源及隔离载荷合并校验、APK 签名和 zipalign 校验通过。
- 完整回归停在既有 `chat-footer-checks.py` 的 `pre-fix-observe` 变体：缺少 `FloatingChatFooterFeature.adopt`。该问题已见同日既有记录；本次未宣称全量回归通过。
- 小米 14 实机：候选 11 的封面状态与滚动状态截图均确认相机四周描边完整，悬浮按钮和渐进模糊正常，滚动后标题显示正常。
- 原生相机点击在候选 10 已确认打开「拍摄 / 从手机相册中选择 / 取消」菜单；长按确认进入 `SnsLongMsgUI`。候选 11 仅调整相机位图渲染边距。

本地证据位于 `build/moments-host-20261123/`：

- `camera-padding-cover.png`：候选 11 封面状态。
- `camera-padding-scroll.png`：候选 11 滚动状态。
- `camera-padding-comparison.png`：从左到右为修复前、修复后封面、修复后滚动的相机局部。
- `moments-checks.log`、`bootstrap-checks.log`、`backdrop-checks.log`、`settings-checks.log`、`build-11.log`。

## 安装与备份

用户同意通过 root 备份、重新安装、恢复数据来处理签名不同。首次替换前已备份原 APK、模块私有数据、LSPosed 配置和共享配置；恢复时修正新 UID 所需的所有权和 SELinux 标签。共享配置数据库前后 159 个键逐项相同，LSPosed 已启用模块并实际加载。后续候选沿用本地签名直接覆盖安装。

备份仍在本地 `build/moments-host-20261123/`：`before-module.apk`、`module-data-backup.tar.gz`、`shared-config-backup.tar.gz`、`config-before.db` 和 `config-after.db`。

最终安装包：`build/moments-topbar-20261123-11/MindLink-1.0.apk`。

- APK SHA-256：`a60a1e717c77e30655e39abd3d8815c5e625df091cd46eab77ea80fc40844110`。
- 已在设备上读取安装后的 `base.apk` 校验和，与上述构建产物一致。
- `MomentsTopBarCards.java` SHA-256：`07444f8847df0ce145e10cd34e2df20dccadd88148cc8fb3e47b03d6c3d34560`，与候选 11 构建记录一致。

代码保留在工作区，未提交或推送。
