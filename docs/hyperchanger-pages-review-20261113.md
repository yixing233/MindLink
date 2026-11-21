# HyperChanger 设置/关于参考核对

## 核对性质

这是本地源码/布局契约核对，不是设备截图、Android 测量或像素级视觉验收。
旧设计被用户指出没有参考到指定页面；本轮不能再把编译/字符串检查当成视觉通过。
新包准备好后，需明确确认覆盖安装，再仅观察模块页面进行实机核对。

## 参考与落地点

参考根目录：`C:/code/HyperDuo/work/hyperchanger/HyperChanger-main`。
主参考文件：`app/src/main/java/btm/m/os4/systemuihook/MainActivity.kt`。

| 项目 | HyperChanger 源码依据 | MindLink 落地点 | 核对结果 |
|---|---|---|---|
| 设置列表而非状态仪表盘 | `SettingsHome:1909–2013` | `ModuleDashboardPage.settings` | 删除大状态卡、双统计卡与内联关于；状态移入次级页 |
| 分组标题在面板外 | `Group:3307–3315` | `ModulePreferenceViews.group` | 小标题＋单组共享表面，不给每行另套卡 |
| 留白/容器 | `AppList:10142–10148`、`Group` | `ModulePreferenceViews` | 16dp 外边距、12dp 组间距、22.5dp 连续曲率角 |
| 列表行 | MIUIX preference/component 引用 | `ModulePreferenceViews.row` | 17sp 主文/14sp 副文、16dp 行内边距、56dp 最小高，说明在标题下，尾部 10×16dp 箭头 |
| 标题收起 | `AppPage:9315–9432` | `ModuleDashboardPage.render` | 32sp 常规字重大标题，52dp 收起栏居中标题；Java 滚动实现，不复制 Compose |
| 独立关于页 | `About:8964–9034` | `ModuleAboutPage` | 设置行进入，返回设置；无新底部标签栏 |
| 品牌头部 | `AboutAdvancedMaterialHeader:9435–9445` | `ModuleAboutPage.build` | 310dp 最小头部，图标→名称→版本，整体上移40dp；大字体可增高 |
| 品牌归属 | HyperChanger 自有矢量资产 | MindLink 自有包图标与文本 | 不复制其 logo、字标、作者头像或虚构作者 |
| 设备信息顺序 | `DeviceInfoItem:8951–8960` | `ModulePreferenceViews.info` | 值在上、标签在下；17/14sp，3dp 间距，16/12dp 行边距 |
| 材质层次 | `AboutMaterialBackground`、`aboutCardColors` | `ModuleAboutBackdrop`＋半透明分组 | 原创绿色系动画渐变，深浅面板约60%/65%；不是实时模糊 |
| 法律/致谢 | `About` 分组结构 | assets/mindlink 本项目声明 | 本地 GPL-3.0、隐私和第三方声明；不假称基于 MIUIX UI 构建 |
| 生命周期 | 原页面组合生命周期 | smali Activity＋页面状态 | 实例导航、保存滚动、现代返回回调、暂停停止渐变、关闭后取消日志结果 |

## 明确保留的差异

- Java View 的标题收起与圆角数学实现，不是 MIUIX Compose 或它的像素级复刻。
- 顶栏使用滚动后表面渐显，不实现 HyperChanger 私有/背景采样材质；不称为毛玻璃。
- 关于背景是原创绿色径向渐变叠加，不是 HyperChanger 的噪声网格着色器或 MIUI 私有混色。
- 不复制 HyperChanger 的 LSPosed 连接结论；MindLink 独立进程只能报告未知，不伪造状态。
- 不引入隐藏图标、预设、捐赠、更新、社群等不存在的功能。
- 主页面保留功能设置入口说明，详细功能仍在微信内，不搬入宿主配置读写。

## 本地验证

- 严格 View 构建/导航测试：非空与父级匹配 LayoutParams，设置→关于→法律→返回，
  页间滚动恢复、Activity 重建、52dp 收起栏、实例隔离、动画暂停与销毁。
- 缺宿主 store/diagnostics/picker/Xposed 类仍能构建并点击页面；配置只读与损坏库不删除。
- 本地声明资产与源码一致；不联网获取作者、头像、贡献者或文档。
- 这些结果不验证真实屏幕大字体测量、字体视觉、截图构图或现代手势返回。

## 待确认后的实机步骤

仅覆盖安装新候选，保留用户配置、图片、日志；不重启微信、不发送消息、不操作支付。

1. 桌面冷启动：设置页应为分组列表，无旧统计卡；观察标题和面板边距。
2. 滚动：大标题收起后居中标题可见，无状态栏遮挡。
3. 点击关于：自有图标、版本和渐变背景；设备行的值/标签层级清楚。
4. 关于→许可证/隐私/致谢→返回：返回正确页面且滚动不丢失。
5. 后台/返回/重建：无闪退、动画泄漏或过期日志弹窗。
6. 使用模块页面截图做本地核对；不抓微信画面、不公开截图或日志。

## 完整候选

- 文件：`build/MindLink-1.0.apk`；9,554,609 bytes。
- SHA-256：`573c9e83c9c1a7609a8cbfbd87c0a5742a3403ec685f8f36a6bd111ebbcff177`。
- 完整回归/构建/独立校验通过，原签名不变，本地声明与源码逐字匹配。
- 独立报告：`build/verify-hyperchanger-pages-20261113/verification.txt`。
- 当前设备仍是之前已安装的遮罩修复包，不是此页面重设计候选；新包尚未安装。

公网 Release 不自动替换，旧附件不代表新页面版本。
