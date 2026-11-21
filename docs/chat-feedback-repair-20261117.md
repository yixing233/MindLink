# 聊天实机反馈续修（2026-11-17）

## 范围

接续「梳理项目已实现功能」中的未通过实机验收项：聊天返回回顶、
分体顶栏阴影及返回按钮、快捷工具栏首次进入缺席、普通/小窗输入栏。
保留表情面板的重排与高度算法，不修改底栏指示器、状态栏、桌面或侧栏。
本轮未安装、未停止或重启微信，未操作消息或支付，未清理用户数据。

## 修复

- 分组：宿主 ConversationListView 的 changed-layout 分支会自行 setSelection(1)。
  对已启用分组、已滚动且仍属于 Launcher 窗口的已识别列表，保留布局分支中的
  现有位置。显式切组、首次布局、非目标列表、关闭分组和布局外主动定位仍走原逻辑。
  保护独立于诊断日志的 48 次预算，不因日志耗尽而失效。
- 顶栏：软件阴影画布按实际圆形与标题的联合边界分配，不再仅扩展 root 矩形。
  同步使用负边距、绘制坐标和祖先裁剪边界；祖先 outline clipping 可恢复释放。
  递归定位返回图标，将左圆与原生返回按钮点击容器一起移入顶栏内部，
  退出时恢复原生水平位移；原生部件替换后重新建立分体状态。
- 工具栏：区分输入容器已发现与工具栏实际挂载。
  同一输入容器内的工具栏被宿主移除或隐藏后，触发一次有上限的恢复。
  稳定布局不持续重启重试，也不重复添加工具栏。
- 输入栏：宿主 scroll/layout/window 身份变化时释放旧滚动映射、遮罩快照与窗口所有权，
  重新绑定当前对象，清除旧键盘空间预算。先处理系统底部避让，再计算边距。
  尚未发生面板切换的普通原生分支也修正实际裁剪祖先底界，保留原生面板树、
  可见性、自然高度与负面板边距。

## 验证

- `python -I scripts/tests/regression.py` 最后一轮退出码 0。
  完整输出：`build/chat-feedback-20261117/regression-final.txt`。
- 顶栏/工具栏：missing 55、present 52、真实分体几何 69 项断言通过。
  包含同容器移除/隐藏恢复、实际圆形阴影预算、原生返回按钮同步位移及恢复、
  祖先 outline 裁剪释放与恢复。
- 输入栏：missing 108、present 467 项断言通过。
  包含 layout/scroll/window 重绑定、初次普通输入与短裁剪祖先、
  原生加权内容预算、表情面板、原生 IME 请求、键盘关闭和连续重排。
- 分组：实际布局回顶分支、日志预算耗尽后保护、显式切组与关闭回退均通过。
- 五族源码使用 JDK 21、真实 Android SDK 37 编译；入向/出向/继承成员 ABI 门禁通过。
  569 个范围外功能类反汇编逐字节保留；新增 1 个返回按钮位置恢复内部类。
- 独立 APK 门禁通过 ZIP/CRC、DEX 头及校验和、类唯一性、原证书/v3、zipalign、
  唯一桌面入口和两个隔离载荷检查。

## 候选

- `build/chat-feedback-20261117/candidate-002/MindLink-1.0.apk`
- 大小：44,214,615 bytes。
- SHA-256：`76b5a942054907dc2ae4f66bb27f681d230ffbdf66b02ee9a3cd50726ecfddb8`。
- 基线：2026-11-16 的 candidate-003，
  SHA-256 `23aaa804f6951abad391910a524766941d250bd0f96b5639292681adfc1c1b5d`。
- APK 中仅 `classes2.dex` 内容变化。主 DEX、资源、桌面、侧栏、其他资产及其
  原始压缩流均保留，未重建 Compose 载荷。
- 可复现构建脚本：`build/chat-feedback-20261117/build_candidate.py`，
  传入该目录下新的 `--output` 子目录即可，旧候选不覆盖。
- candidate-001 保留为失败记录：证书本身正确，脚本未识别新版 apksigner 的
  `V3.0 Signer:` 标签。修正解析后 candidate-002 的完整门禁通过。

## 实机待验

本轮没有连接的 ADB 设备；此前 `192.168.1.148:39659` 已拒绝连接。
合同测试不证明真实 ART Hook、OEM 小窗缩放、软件阴影像素、原生遮罩绘制时机
或首次进入的完整宿主时序。待提供当前无线调试端口后继续观察验收，
不将实机反馈项标记为已验收完成。

## 第二轮（candidate-004）

### 上一版为什么"小窗反而更小"

实机安装的 candidate-003 只替换了 `FloatingChatFooterFeature`，
`FloatingFooterLayout` 仍是基线包里的旧类：基线宿主的
`getGlobalVisibleRect()` 返回 root 坐标，却与屏幕坐标的 `getLocationOnScreen()`
直接求交，自由窗口原点非 (0,0) 时整个可见矩形偏移，右/下 margin 被多算上百像素，
outline 退化成空矩形，卡片只剩一小块。新建的 footer 特性按"已是屏幕坐标"的新约定
计算边距，与旧 helper 组合后比之前更窄。也就是说上一版并没有真正修到小窗。

### 本轮修复

- `FloatingFooterLayout` 正式纳入受控族：可见矩形统一转成屏幕坐标
  （`visibleInScreen()` 加 root 屏幕偏移），窗口矩形改由 root 视图推导，
  不再使用 `getCurrentWindowMetrics()`（自由窗口下它是以窗口自身为原点的，
  会把求交整体错位）；窗口矩形为空时不参与求交。
- `FloatingChatFooterFeature.margins()`：窄窗口下先丢弃装饰性左右边距，
  再丢弃裁剪补齐量，保证卡片宽度不低于 140dp 的可见下限；
  宽度足够时行为与原来完全一致（测试里 12/12dp 不变）。
- `FloatingHeaderStyle`：阴影裁剪祖先链每帧重新遍历（`syncClips()`）。
  宿主在通知进入、面板切换时会重新挂载顶栏，旧实现只在创建时抓一次祖先，
  新插入的裁剪祖先会把模糊阴影切成直边矩形。

### 构建与验证

- 五族改为八族同批编译/D8/静态校验：ChatToolbarFeature、ChatToolbarLifecycle、
  ChatToolbarViews、ConversationGroupingFeature、ConversationListBridge、
  FloatingHeaderStyle、FloatingChatFooterFeature、FloatingFooterLayout。
- `python -I scripts/tests/chat-footer-checks.py`：missing 115、present 479 全绿，
  含 freeform 非零窗口原点、窄窗口回退原生边距、outline 非空三项断言。
- `python -I scripts/tests/chat-header-toolbar-checks.py`：missing 56、present 53、
  separated-geometry 69 全绿，两个 RED 回放仍能复现原缺陷。
- `python -I scripts/tests/regression.py`：全部 PASS。
- 八族源码对基线包的入向/出向 ABI 交叉核对：除继承成员外无缺失方法
  （`build/chat-feedback-20261117/diag-abi.py`、`diag-abi2.py`）。
- 独立 APK 门禁通过：ZIP/CRC、DEX 头与校验和、类唯一性、原证书/v3、zipalign、
  唯一桌面入口、两个隔离载荷；仅 `classes2.dex` 变化，主 DEX 原字节保留。

### 候选

- `build/chat-feedback-20261117/candidate-004/MindLink-1.0.apk`
- 大小：44,218,711 bytes。
- SHA-256：`eb69a137d1d2b53f7c48e5961d7b40a392fd883b7bafa740f52773d56e1115fd`。
- 基线：candidate-003，SHA-256
  `23aaa804f6951abad391910a524766941d250bd0f96b5639292681adfc1c1b5d`。
- 本轮构建期间没有连接的 ADB 设备，候选未安装到手机；所有实机反馈项仍为待验收。

## 第三轮（candidate-005）

### 通知进入聊天页：顶栏与快捷工具栏都无效

两条相互独立的断点，加在一起才让整条链路失效：

1. **入口回调不全。** 三个特性都只钩了 `Activity.onResume`。通知点击复用已有任务时，
   聊天页可能只走 `onNewIntent`（任务栈被前台拉起）或只走 `onStart`（从后台恢复），
   没有新的 `onResume`；此时 `resumed` 从未指向这个聊天 Activity，`refresh()` 也就不会
   被调用。现在 `FloatingChatHeaderFeature`、`ChatToolbarFeature`、
   `FloatingChatFooterFeature` 三处共用同一个 `entered` 回调，同时挂
   `onResume` / `onNewIntent(Intent)` / `onStart`，命中后写 `stage=entry trigger=<方法>`
   再 `refresh()`。
2. **发现器会提前退休。** `Discovery.run()` 里有 `activity != resumed.get()` 与
   `!chatActivity(...)` 的硬判，`ToolbarDiscovery.run()` 同样以 `resumed` 为门闸。
   通过通知进入时 `resumed` 可能是 LauncherUI 或已失效的引用，查找直接返回；
   而旧实现在 8 次尝试后 `cancel()` 永久放弃，退到下一级页面再返回才重新开始。
   现在停止条件只看装饰视图身份（decor 未被替换、Activity 未 finishing/destroyed），
   8 次之后改为 500ms 慢速重试而不注销；`onViewAttachedToWindow()` 把 `attempts` 清零
   并立刻重跑一轮。
3. **配置库冷启动。** 配置存在外部存储，通知冷启动可能早于挂载完成，读到 enabled=false
   就整页不装饰，直到离开再回来。三处 `init()` 末尾各加一个有界的 `probeEnable()`：
   `300/700/1500/3000/6000/9000ms` 重读一次，读到 enabled 立即停止，最多 6 次。

### 悬浮输入区底部间距

上一轮为了"内容能压到手势条下方"，`stripHostNavigation()` 把宿主看到的
`navigationBars` inset 改写成 0，于是卡片自己 `getRootWindowInsets()` 读回来也是 0，
`remainingBottom()` 归零，只剩 `config.gap` 那点间距——未呼出键盘和"更多(+)"面板时
卡片底边就压在系统手势小横条上。表情面板之所以正常，是因为它由宿主 `AppPanel`
的 resize 回写高度，走的是另一条基准。

- `FloatingFooterLayout` 新增手势带记忆：`stripHostNavigation()` 在改写之前先
  `rememberBand(view, nav)`，遇到 IME 帧或三键栏时 `forgetBand()`；
  `pillClearance()` 读不到 nav 时回落到记忆值（按 `getRootView()` 身份匹配，
  换窗口即失效），再减去窗口已经避让的部分，得到卡片必须让出的高度。
- `FloatingChatFooterFeature.margins()` 用 `max(remaining, clearance)` 作为下限；
  `panelMode == PANEL_SMILEY` 时显式取 0，表情面板布局算法完全不被覆盖。
  `diagnoseGeometry()` 的日志增加 `pillClear=` 与按同一下限计算的 `targetBottom`。

### 验证

- `chat-footer-checks.py`：missing 128、present 498 断言全绿，新增
  `gesturePillBand()`（纯逻辑 + 真实树 + strip 后靠记忆仍让位 + 表情面板基线不动）；
  三条 RED 回放均可复现：`observe/state` 递归栈溢出、reparent 未置 GONE 的 360px 折叠、
  去掉手势带下限后 `plain=992 stripped=992 margin=8`。
- `chat-header-toolbar-checks.py`：missing 68、present 65、separated-geometry 69 全绿；
  新增 `notificationEntry()`：先让任务进入后台（`onPause` 后无待跑发现），再把已挂载的
  聊天树通过 `onNewIntent`（以及独立一轮 `onStart`）送达，断言顶栏与快捷工具栏都完成装饰，
  并检查两个特性都在 `Activity.onNewIntent(Intent)` / `onStart` 上各注册一次。
  新增 `pre-fix-entry` 变体做 RED 回放：删掉这两行钩子后同一场景保持未装饰。
- `regression.py`：全部 PASS（日志 `build/chat-feedback-20261117/regression-r3.txt`）。
- 独立 APK 门禁：ZIP/CRC、DEX 头与校验和、类唯一性、原证书/v3、zipalign、
  唯一桌面入口、两个隔离载荷全部通过；仅 `classes2.dex` 变化，其余条目原字节保留。

### 候选

- `build/chat-feedback-20261117/candidate-005/MindLink-1.0.apk`
- 大小：44,222,807 bytes。
- SHA-256：`acb7d2929333e0ddedd0b5fc344a1a2e5d9ed624880ad7bbec4e8ba0e2ff2f9b`。
- 证书 SHA-256：`c7a67d45660e019746675f99c14156191550f08fc4398b5cef552c9808c9bbda`。
- 基线：candidate-003，SHA-256
  `23aaa804f6951abad391910a524766941d250bd0f96b5639292681adfc1c1b5d`；受控族扩到九族
  （新增 `FloatingChatHeaderFeature`），`classes2.dex` 678 类。
- 构建期间 `adb devices` 为空，候选未安装到手机；通知入口、底部手势带间距、表情面板
  一致性与"返回消息列表不回顶"四项实机反馈均为**待验收**。

## 第五轮（candidate-007）：顶栏与输入区使用首页底栏的同一材质

### 做法

首页悬浮底栏的材质是一条链：`ViewBackdropSampler` 把下方真实内容录进 `RenderNode`，
`LiquidGlassLayer.drawBase()` 上模糊/折射 `RenderEffect`，`LiquidGlassDecoration`
再补双光源高光。这一轮把同一条链搬到聊天页两张悬浮卡上，做成**卡片的背景 Drawable**，
而不是叠一层 View：宿主自己的 `View.draw` 顺序、`elevation` 阴影和 outline 裁剪全部保留，
只有填充物换了。

- 新增 `ChatGlassSurface`（`Drawable`）。`PLAIN / FROSTED / LIQUID` 与首页底栏共用
  `nav_bar_glass_mode`、`nav_bar_blur_radius` 两个键（液态默认 8dp、磨砂默认 25dp），
  所以用户在首页把材质关掉或改磨砂，聊天页两张卡同步跟随。
- `bind()` 里 host 取卡片的父容器，content 取**面积最大的非本卡可见兄弟**（聊天页是
  消息列表）。采样只录兄弟列表，卡片本身永远不进录制；`stop()` 幂等，切回纯色或卡片
  被摘除时释放 sampler / render effect / 高光。
- 环境不可用时统一退回原来的实心卡（`PLAIN`、软件画布、没有兄弟内容、
  `UNSAFE_BOUNDS` 等）：tint 保持配置色不透明，不会出现半透明空壳。
- `FloatingFooterFeature`：`Config` 增加 `glass/glassBlur`；`style()` 在玻璃档复用同一个
  `ChatGlassSurface`，并把「自己装上去的那一个」记在 `owned` 里，`restore()` 只还原真正的
  原生背景（否则会把死掉的玻璃背景当成原生背景存起来）。
- `FloatingHeaderStyle.applyCard()` 增加 6 参重载（另一处 `applyExtras` 仍走实心卡）；
  `FloatingChatHeaderFeature.State.apply()` 两处调用传材质参数。分体式（`SEPARATED`）顶栏
  不做玻璃：软件层画不了 `RenderEffect`。

### 修掉的一处真 bug

`ViewBackdropSampler(View source, View consumer)` 的构造器把 content 种成了 source 本身。
首帧如果只 `new` 不 `source(host, backer)`，`source == content` 会走 `source.draw()`
整父容器录制分支——既把卡片自己录进去（自捕获），也绕过了 `UNSAFE_BOUNDS` 安全门禁。
现在 `bind()` 无条件 `sampler.source(host, backer)`，`ChatGlassSurfaceChecks` 加了
`host.drawCalls == 0` 的断言盯死这一点。

### 已知观感项（需实机确认）

输入区展开面板时，输入条和面板是同一个 `ChatFooter` 的上下两段、共用一层背景，
所以玻璃会覆盖整张卡（含面板）。这与首页底栏"一块材质"的观感一致，但和之前
「面板自带底色」不同，需要真机看一眼是否接受。

### 验证

- `syntax_check.py`：PASS（仅 deprecation 提示）。
- `backdrop_recovery_checks.py`：原 30 组 611 断言全绿，新增
  `GREEN chat glass surface: 22 assertions`（共用键、真实采样、纯色/软件画布/无兄弟/
  宿主被拒四种退化、圆角 outline、幂等释放）。`getOutline()` 报的是圆角矩形，
  万一某些路径没装自己的 `ViewOutlineProvider`，`clipToOutline` 与投影也不会方角。
- `chat-footer-checks.py`：missing 139、present 509 断言全绿；新增
  `pre-fix-glass` RED 回放——把材质配置钉回 `PLAIN` 后卡片退回不透明渐变填充、
  从不构造材质，`RED confirmed`。
- `chat-header-toolbar-checks.py`：missing 68、present 65、separated-geometry 75 全绿，
  三条 RED 回放保持；玻璃块用真实 `FloatingHeaderStyle` 验证复用同一实例与还原。
- `regression.py`：全部 PASS，新加文本门禁（共用键、`sampler.source(host, backer)`、
  两张卡都接 `ChatGlassSurface`）。
- 独立 APK 门禁：ZIP/CRC、DEX 头与校验和、类唯一性、原证书/v3、zipalign、唯一桌面入口、
  两个隔离载荷全部通过；`missing_referenced_types_or_members` 与
  `incompatible_access_or_static_changes` 均为空；仅 `classes2.dex` 变化。
  顺带修了 `apk_checks.py` 里只认旧版 `V3.0 Signer:` 行的证书正则，新版
  `Signer #1 certificate SHA-256 digest:` 现在也能解析。

### 候选

- `build/chat-feedback-20261117/candidate-007/MindLink-1.0.apk`
- 大小：44,226,903 bytes。
- SHA-256：`ad396c82b94e880f37fd0962a576933c2d078055c4e076c44a0fb711311e16f5`。
- 证书 SHA-256：`c7a67d45660e019746675f99c14156191550f08fc4398b5cef552c9808c9bbda`。
- 基线：candidate-003；受控族扩到十族（新增 `ChatGlassSurface`），`classes2.dex`
  679 类 / 784,980 bytes。
- 说明：中途的 candidate-006（`52f1769d…`）只差 `ChatGlassSurface.getOutline()`，
  未安装未验收，构建目录已删除，以 007 为准。

## 第六轮（candidate-008）：群聊置顶消息卡片

### 先定位：宿主实证，不靠推测

仓库里有真实宿主包
`build/profile-capture-repair-20261114-01603b9b/host-apk-input-001/WeChat-8.0.76-static.apk`
（17 个 dex、11505 个混淆 XML、完整 `resources.arsc`）。用纯标准库工具解出聊天页主布局
`r/p/ss.xml` 与相关层级后，置顶消息卡片的位置是确定的：

```
RelativeLayout                       ← 悬浮顶栏被搬到这里
  ChattingUILayout (0x7f090f4b)
    ViewStub 0x7f090f64 → r/p/bb.xml          ActionBarContainer + Toolbar
    ViewStub 0x7f096ed0 → 0x7f0c02e5 → r/p/s5.xml   ★
    FrameLayout 0x7f09107b                    内容区
      ChattingScrollLayout 0x7f09107a
      ViewStub 0x7f096ecf/0x7f096ece/0x7f096ed1 提示条（图标+单行文案）
      ViewStub 0x7f096edc/0x7f096eda/0x7f096ed9 TalkRoom / MultiTalk / LiveTalk 提示
```

`r/p/s5.xml` 只有一行 `<com.tencent.mm.ui.tipsbar.ChatTipsBarGroup id=0x7f090e9f
visibility=2 layout_width=-1 layout_height=-2 />`。该组构造器 `View.inflate(context,
0x7f0c02e3 /* r/p/s3.xml */, this)`，`findViewById` 的 id 与 `s3.xml` 逐一对上：

- `0x7f096d83` 遮罩 View、`0x7f096d7c` 1px 分隔线；
- `0x7f093d1f` RelativeLayout（bg `0x7f080dab` = `background_tipsbar`）；
- `0x7f095a10` `MaxHeightWxRecyclerView` ← **置顶消息是这里的一条 item**；
- `0x7f090bea` + `WeImageView src=0x7f0f051d` ← 折叠/展开箭头（`icons_tipsbar_unfold`）。

页内 item 由 `com.tencent.mm.ui.tipsbar.n`（`WxRecyclerAdapter`）承载，`y()` 在
`MODE_FOLD` 时只返回一条；置顶消息与群公告、接龙、直播提示共用这条链，是一条叠层条，
不占布局空间——它靠 `setListViewPaddingTop(int)` 把消息列表 `paddingTop` 顶开，
基线存在 `View.setTag(0)`。

### 为什么它原先出现在状态栏那一条

原生 `ActionBarContainer` 是 `ChattingUILayout` 的第一个垂直子项，占真实布局高度。
`scheduleReparent` 把它摘出来搬去外层 `RelativeLayout` 的 `ALIGN_PARENT_TOP`，内容
`FrameLayout` 因此上移到 y=0；`applyWindowHeader` 又切了 overlay 模式并清掉 content 的
`paddingTop`。置顶组实际来自 `ChattingUILayout` 的独立 stub，不是内容区的首个子 View；
其父容器在顶栏移走后上移，`ChatTipsBarGroup` 就落在
状态栏带里、压在悬浮顶栏卡上方——这是本模块自己造成的位移，不是宿主行为。

### 做法（方案 A：独立第二张卡）

- `applyTips()`：009 在内容区按类名 `com.tencent.mm.ui.tipsbar.ChatTipsBarGroup` 找它，搜索范围
  与上面的真实布局不符，是该候选失败的静态缺陷之一（stub 懒
  inflate，重新进聊天会换实例，所以每帧校验 `isAttachedToWindow()` 与父级；缺失时每 8 帧
  才重扫一次，避免空聊天页每帧 BFS）。左右边距取 `side`，`topMargin = insets.top +
  topGap + 顶栏高 + extraGap`，把它停在玻璃卡正下方；折叠/展开改高度时逐帧重算，不缓存。
- 材质：走同一个 `FloatingHeaderStyle.applyCard(view, state, corner, elevation, glass,
  glassBlur)`，即 `ChatGlassSurface` 背景 + 圆角 outline + `clipToOutline` + elevation，
  与顶栏、输入区、首页底栏共用同一套 `nav_bar_glass_mode` / `nav_bar_blur_radius`。
- `detachTipsPanel()`：宿主在折叠/展开时会重刷面板底色（`ChatTipsBarGroup.c()` 里
  `setBackgroundColor`），所以逐帧把该组直接子 View 的背景摘成 `null`（遮罩、分隔线、
  `background_tipsbar` 面板），首次见到的背景存进 `Chrome` 以便还原。
- 可见性、数据、点击、折叠状态全部仍由宿主拥有，本模块只改边距与外观。

### 顺带修掉的真冲突：列表 padding 被整值覆盖

原来 `applyListPadding` 每帧写 `originalPadding.top + insets.top + header.height + topGap`
这个**绝对值**，而宿主每帧用 `getTag(0)` 基线 + 自身高度写回，两边互相覆盖——置顶条出现、
消失、折叠、展开时列表必然跳一下或被压住。现在改成增量模型：

```java
int host = current.getPaddingTop() - listExtra;   // 剥掉我们上次加的那一份
int top = host + inset;                            // 只把差值叠回宿主的当前值
listExtra = inset;
```

以上无条件扣减模型有错误：宿主覆盖 padding 后会扣掉已不存在的模块增量，
原生首次捕获基线时也可能把模块增量存进去。009 的测试只有静态文本契约，
没有证明这些时序正确。本模块不写原生 `View.setTag(0)`；后续修正见第七轮。

### 已知观感项（需实机确认）

- 置顶条内部 item 的排版由宿主 item converter 决定，摘掉面板底色后是「文字/图标直接浮在
  玻璃上」，对比度需真机看一眼；若不满意，退路是只把面板换成半透明色而不是完全透明。
- 卡片阴影在内容 `FrameLayout` 内，父级仍 `clipChildren`，下滑投影可能被裁掉一点；
  没有为此放开父级裁剪（放开会让列表内容溢出到输入区）。
- 展开态高度变大时，第二张卡整体变高，顶栏卡不动（方案 A 的既定行为）。

### 验证

- `syntax_check.py`：PASS（仅 deprecation 提示）。
- `backdrop_recovery_checks.py`：原 30 组 611 断言全绿，`GREEN chat glass surface:
  22 assertions` 保持。
- `chat-header-toolbar-checks.py`：missing / present / 三条 RED 回放 / separated-geometry
  75 断言全绿；新增 `tips card` 静态契约 8 条（宿主类名、停在顶栏下方、共用材质、
  摘除宿主 chrome、增量 padding、禁止整值写回、调用顺序、释放时还原）。
- `regression.py`：全部 PASS。
- 独立 APK 门禁：ZIP/CRC、DEX 头与校验和、类唯一性、原证书/v3、zipalign、唯一桌面入口、
  两个隔离载荷全部通过。

### 候选

- `build/chat-feedback-20261117/candidate-009/MindLink-1.0.apk`
- 大小：44,226,903 bytes。
- SHA-256：`9470468d3e693e005d81631cf934da553e37cb9c2839367b618df752bbb5f184`。
- 证书 SHA-256：`c7a67d45660e019746675f99c14156191550f08fc4398b5cef552c9808c9bbda`。
- 基线：candidate-003；`classes2.dex` 786,624 bytes / 680 类。
- 说明：中途的 candidate-008（`6150eb19…`）列表顶部少算了一个 `extraGap`，第一行消息会被
  卡片压住 8dp，未安装未验收，构建目录已删除，以 009 为准。
- 说明：本机构建时 `adb devices` 为空，**未装机**。
- 构建期间 `adb devices` 仍为空，候选未安装到手机。以下实机项为**待验收**：
  顶栏与输入区材质是否与首页底栏一致、面板展开时整卡同材质的观感、小窗/分屏下的退化、
  通知进入路径下的表现、日志里 `WEX: 背景采样 reason=…` 是否停在 `READY`。

## 第七轮（2026-11-19）：009 实机失败后的续修

### 已取得的实机证据

- 用户反馈：置顶适配没有效果，聊天工具栏也失效。
- 后续已确认 009 覆盖安装成功，微信进程已加载该包；不是只覆盖安装而保留旧进程。
- LSPosed 日志显示工具栏开关为 true、原生 AppPanel 快照为 13 项，但没有采集到
  “观察输入组件”或“工具栏已插入”记录。快照成功不等于工具栏挂载成功，
  本轮发现的静态缺陷仍需新日志对应到真实失败位置。
- 顶栏记录为 SEPARATED；这一样式仍采用软件装饰层，尚未接入首页玻璃材质。
- 输入区祖先几何异常仍在；本轮保留输入区与表情面板布局，不宣称它们已恢复。

### 修改与诊断

- 工具栏增加 footer onLayout 补观察入口、AppPanel 快照后的父链补观察，
  以私有所有权表识别 lifecycle，保留外来 keyed tag。
- 移除挂载前全 footer 的 512 节点门禁。输入搜索跳过隐藏节点、原生面板和列表，
  优先真实输入列；每次搜索最多入队 512 个有效节点、检查 2048 个孩子。
  decor 发现器跳过隐藏大树，仍拒绝可见超预算和多聊天歧义。
- 新日志 `stage=panel-owner`、`stage=mount`、`stage=mount-node`、
  `stage=mount-child` 记录类名、数字 ID、尺寸、坐标、可见性等结构特征，
  不读取正文、用户名、聊天标识或输入内容；事件数与层级均有上限。
- 置顶搜索改为 ChattingUILayout 范围，跳过消息列表与输入区；位置扣除父容器
  屏幕 Y 和 paddingTop。只摘已证实的深层 chrome，保留 item 自身背景。
- 列表只在当前 padding 仍等于模块上次写入时剥离旧增量。针对实证的
  `ChatTipsBarGroup#setListViewPaddingTop(int)` 增加 scoped before/after hook：
  原生捕获 tag 基线前先释放模块增量，写完后加回一次；隐藏或结束的页面不加回。

### 测试边界

- 新行为用例覆盖大隐藏面板、隐藏兄弟预算、layout-only 入口、foreign tag、
  真实 13 项 adapter 快照入口、置顶父 Y/padding 与实际屏幕坐标、
  深层 chrome、原生同值覆盖、折叠隐藏、关闭恢复以及隐藏聊天的原生回调。
- 009 全树门禁和无条件扣 padding 的 RED 回放保留。
- 尚未证明所有原生 writer 都经过已定位钩点；其他路径直接写相同数字时，
  以及新置顶组尚未建立父链就写列表时，仍需实际调用链证据。
- JVM fixtures 与 APK 门禁不代表 ART Hook、通知入口、真实像素或小窗已验收。
- candidate-010 为中间冻结包，未安装；后续候选使用独立 candidate-011 目录。

### 最终本地结果与候选

- `python -I -B scripts/tests/regression.py --jdk "C:/Program Files/Microsoft/jdk-21.0.12.101-hotspot"`
  退出码 0；完整日志：`build/chat-feedback-20261117/regression-r7-final.txt`。
- 顶栏/工具栏 missing 91、present 92、真实分体几何 75 项断言通过；旧缺陷 RED 回放命中
  指定失败断言。输入区 missing 139、present 509 保持通过。
- 十族冻结源码真实 Android 编译、D8、成员 ABI 与范围外类逐字保留门禁通过。
- 独立 `apk_checks.py` 通过 ZIP/CRC、DEX/checksum/类唯一性、原证书/v3、
  zipalign、入口与两个隔离载荷；报告见 `candidate-011/apk-verification/verification.txt`。
- 包：`build/chat-feedback-20261117/candidate-011/MindLink-1.0.apk`，44,235,095 bytes。
- SHA-256：`0e0b9ae8ca8b38cd65a510689897e3e92fbb721166a1603fc4b94b110d5a03da`。
- 包内仅 `classes2.dex` 变化；主 DEX、资源、载荷与其他压缩流保留。
- 最终两次 `adb devices -l` 均为空，mDNS 也无在线调试端点；011 尚未安装，
  实机反馈仍待验收。临时手机 XML 留待设备重连后清理，不清应用数据。
