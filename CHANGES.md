# WEX (wex.ui.core) 1.2.27 反编译修改工程

## 2026-11-22 聊天顶栏：标题尽量完整显示，只省略放不下的部分

- **根因**（反编译 `NoMeasuredTextView`）：`setText` 时就按 `min(maxWidth, 当时的测量宽度)` 把省略号写进存储的文本，原文不再保留；
  群聊还会先用 `g(String,int,int)` 设后缀（如「(211)」，字段 L/M/N），由布局回调 P 只截名字。之前只把 maxWidth 抬到视图实际宽度，
  对已截好的文本无效，于是悬浮卡片里出现「🏓…(211)」。
- 修复：新增 `FloatingHeaderStyle.TitleSource`。
  - 在 `FloatingChatHeaderFeature.init` 就 hook `NoMeasuredTextView` 内部的设文本方法（按签名找 `(CharSequence, 枚举)`，
    找不到用 `setText(CharSequence)`）和 `onLayout`：记录微信传入的**原文**与后缀；视图自己在 onLayout 里的延迟截断不算原文。
    只记录位于 ActionBarContainer/Toolbar 下（或尚未挂上窗口）的视图，会话列表里的同类视图只多一次短的父链遍历。
  - 每次设完检查：显示的文本比原文短（被截过）就标记宽度未知。
  - `NoMeasuredWidth.apply`（分体式、整体式每帧都会调）：视图宽度与上次截断所用宽度不同、且不在布局中时，按实际宽度**自己截一次**：
    原文+后缀放得下就整段显示；放不下只把名字截到「宽度 − 后缀宽度」，后缀完整保留；清掉 L 避免回调再截。每次宽度变化只做一次。
  - 诊断：LSPosed 日志 `WEX: 聊天顶栏: title re-cut width=… text=… suffix=… shown=…`（最多 4 条）；hook 失败会打 `title source hook unavailable`。
- 测试：header 套件的样式替身加 `TitleSource.install` 空实现；separated-geometry 变体编译真实样式，补最小 `android.text.TextPaint`/`TextUtils` 替身。
- **未验证**：未构建、未跑回归。若后缀其实已含在原文里（日志 `suffix=none`），截断会从末尾吃掉「(211)」，需要再按日志调整。

## 2026-11-22 会话分组：选中态改为平移动画

- 选中胶囊不再是每个标签自己的背景（只能淡入），改为标签行 `LinearLayout` 背景上的一个 `ConversationGroupCard.SelectionPill`，
  画在标签和按压波纹下面。切换分组时从上次绘制的位置平移到新标签，280ms；前进方向的边缘先走、另一边稍慢跟上，有轻微拉伸。
- 胶囊每次绘制都读选中标签的实时边界，所以改名、等宽切换、新增分组只会让它跟着移动；首次构建、主题/强调色变化直接就位不做动画。
  旧选中标签先处理时只清目标、保留绘制位置，所以与循环顺序无关。
- `styleTab` 签名不变（测试替身无需改），只更新了 tab-updates 两条断言的说明文字。原 160ms 淡入（`FADE_MS`/`fadeIn`）已删除。
- 文字颜色与粗细仍是立即切换。**未构建、未跑回归。**

## 2026-11-22 聊天顶栏返回按钮波纹被裁；首页顶栏透明、内容延伸、资料卡卡片

- **返回按钮波纹被裁成矩形**：波纹是可点击视图的前景，父布局 clipChildren 会把它裁到该视图的矩形。返回键的可点击视图比圆窄
  （右侧菜单按钮够宽，所以正常）。`FloatingHeaderParts.unclip()` 对目标到顶栏 root 的父链放开 clipChildren/clipToPadding
  （最多 8 层，记录原值，`restore` 还原）；root 的父级仍按顶栏高度限制，圆直径 ≤ 顶栏高度。
- **首页左侧资料卡没有卡片**：资料卡是 Compose 组件，没有 ImageView/TextView 子视图，`extent()` 返回空；而「隐藏标题」默认开，
  于是左卡内容为空被丢弃。新增 `Cards.profileExtent()`：找不到原生子视图时按资料卡自身外框扣掉 Compose 行的 16dp 起始/约 10dp 末尾内边距，
  取中心 36dp 高的带。
- **首页顶栏透明 + 内容延伸到卡片下方**（新键 `top_cards_overlay`，默认开，设置在「首页顶栏 › 悬浮卡片」）：
  - `Cards` 透明时不画 Toolbar 原背景、轮廓为空（无阴影）；ActionBarContainer 背景置空（保存最新一份用于还原）。
  - 复用聊天顶栏的 `FloatingHeaderStyle.applyWindowHeader`：ActionBarOverlayLayout 进入 overlay 模式、content 顶部内边距清零。
  - 只在首页分页容器（WxViewPager/CustomViewPager，优先用底栏已找到的那个）内扫描，跳过 ChattingUILayout：
    被顶栏遮住的 ListView/ScrollView/RecyclerView/NestedScrollView（宽 ≥ 60%）加「顶栏底边 − 视图顶边」的顶部内边距并关闭 clipToPadding，
    首行在卡片下方，上滑时从卡片下面滚过。内边距只在需要值变化时写（不每帧写）；顶栏位置变化时立即扫描，否则最多每 400ms 一次，
    打开 overlay 后前 6 帧不节流（等页面重新布局）。找不到分页容器时什么都不改。会话分组标签栏的置顶位置按列表 paddingTop 计算，会随之下移。
  - 关闭开关或停用卡片时全部还原（内边距、clipToPadding、overlay 模式、content 内边距、两处背景）。
  - 诊断：LSPosed 日志一次 `首页顶栏卡片: underlay barBottom=… overlay=… chain=… padded=[…] covered=[…] statusBar=…`，
    `covered` 列出被顶栏挡住的非列表视图（横幅等），`chain` 是顶栏往上 5 层的类名/位置/顶部内边距。
- **已知限制 / 未验证**：状态栏区域未处理——若微信用不透明的状态栏颜色或 EdgeToEdgeWrapperLayout 画状态栏，内容只延伸到状态栏下沿。
  毛玻璃/液态玻璃是否采样到下方列表未确认。全部未构建、未跑回归。

## 2026-11-22 聊天页：上滑历史消息时周期性卡顿

- **推断的根因**：`ChatTailSpacer` 为修「新消息后空白」，在 adapter 条数**任何**变化时都 `D0`（invalidateItemDecorations）+跳过一帧。
  上滑时微信每页加载一批历史消息，条数也会变，于是每页都触发整列表重新布局并丢一帧，就是「隔一段抽搐一下」。
- 修复：记录拿到底部留白的那条消息视图（`latestTail`/`settledTail`）。条数变化后只在**旧的末条仍挂着、且已不是末条、
  且其留白没被重算**时才重新布局（新消息追加）；历史消息加在上方时末条不变，不再重新布局。
  旧末条不在屏幕上时延后判断（`UNKNOWN`），等它重新挂上再判；被回收复用为别的行时视为已重算。拿不到位置时回退为原来的整列表重排。
- 诊断：每次真正触发会在 LSPosed 日志打 `WEX: tail-spacer relayout stale-tail`（每个列表最多 4 次）。上滑时若仍出现此日志，说明判定有误。
- 测试：`decorationLifecycle` 改为真实的多视图场景（新消息、历史分页、末条离屏/重新挂上、末条被回收），
  RecyclerView 替身 `u0` 支持按视图返回位置（`positions`）。
- **未验证**：未构建、未跑回归。若卡顿依旧，需换方向排查（顶栏/底栏每帧工作、材质模糊），可先关「悬浮输入栏」对比。

## 2026-11-22 首页侧栏：左边缘右滑热区加大

- `HomeSidePanelFeature` 左边缘起滑的判定宽度由 32dp 改为 96dp（新常量 `EDGE_DP`，中途试过 56dp），约为常见手机屏宽的四分之一。其余判定不变：横向位移须大于纵向的 1.25 倍，
  所以从这条区域开始的上下滚动仍交给会话列表；只在「微信」标签页生效。
- 代价：从左侧 96dp 内开始向右拖会话分组标签栏，会改为拉出侧栏（此前是 32dp）。侧栏测试的按下位置都在 200dp 以上，不受影响。
- **未验证**：需构建与真机确认手感，以及与系统返回手势（全面屏手势的左边缘）是否冲突。

## 2026-11-22 首页顶栏：左右两张悬浮卡片

- 新增 `HomeTopBarCards`（`FeatureBootstrap` 里最后初始化）：首页（LauncherUI）顶栏画两张胶囊形悬浮卡片。
  - 左卡片包住资料卡（头像、昵称、状态，`TopBarFeature` 插入的 `wеx_top_profile`）；关闭资料卡时包住标题文字（「微信(1)」「通讯录」等）。
    资料卡和标题同时显示时，两者放进同一张左卡片（取两者的并集）；任一方不在时只包住另一方。
    宽度按实际文字排版计算，不按 TextView 的盒子。
  - 「隐藏标题「微信」」（`top_cards_hide_title`，默认开，随卡片开关生效）：会话页标题（「微信」「微信(3)」「WeChat」或自定义的
    `top_title`）设为 INVISIBLE，不用 GONE，顶栏布局不变、不会每帧重排；左卡片因此只包住资料卡。同一个标题 TextView 切到
    通讯录 / 发现 / 我时恢复显示；关闭开关、关闭卡片或首页销毁时还原。未读数随标题一起隐藏（底栏角标仍在）。
    资料卡也关着时左边没有内容，左卡片不画。
  - 右卡片只包住动作按钮（搜索和「+」）：顶栏右侧 45% 范围内可点击的小按钮，从第一个按钮中心到最后一个，两端是半圆。
    打开「显示搜索框」隐藏原生搜索按钮时，右卡片只剩「+」（变成圆形）；其它标签页按当时的按钮自动适配。
  - 两张卡片同高（默认顶栏高减 8dp，最高 52dp，至少容得下资料卡），居中，左右距屏幕边 8dp，互不重叠。
- 绘制：给原生 Toolbar 换一个背景 drawable，先画 Toolbar 原来的背景，再画卡片阴影和材质；内边距、轮廓、状态都取自原背景，
  所以顶栏布局和点击完全不变。材质复用卡片材质（普通 / 毛玻璃 / 液态玻璃 + 模糊 + 不透明度），颜色与会话分组卡片、悬浮聊天顶栏一致。
  卡片阴影会超出 Toolbar，所以放开 Toolbar 往上 3 层父容器的 `clipChildren`，关闭时还原。
- 每帧预绘制按当时的 View 重新计算位置（切标签、资料卡出现、标题未读数变化都会跟着变），变化时才重画；
  预绘制不读设置，开关与材质只在首页 resume、设置改动和材质改动时读取。微信替换 Toolbar 背景时自动重新包一层。
- 设置：首页顶栏页新增「悬浮卡片」区块，开关 `top_floating_cards`（默认开，即时生效）并链接到卡片材质；总览摘要显示开关状态。
  材质改动时 `SettingsPanelModel.refreshGlassSurfaces()` 也刷新这两张卡片。
- 测试：`database-capture/bootstrap` 的 `BootstrapDoubles` 补 `HomeTopBarCards` 替身并把 `home-top-cards` 加进启动顺序。
- 备份：`build/home-top-cards-backup-20261122`。**未验证**：没有微信首页顶栏的真机 View 树，按资料卡标签、`android.R.id.text1` 标题和
  「右侧可点击小按钮」识别；需构建、回归与真机确认，重点看四个标签页、资料卡开关、搜索框开关、深浅色与三种材质。
  若卡片位置不对，日志里 `首页顶栏卡片: geometry ...` 会给出两张卡片的坐标。

## 2026-11-22 悬浮顶栏：标题被提前省略、左右按钮按压波纹；工具栏图标显示完整

**标题在有空间时仍显示省略号**
- 原因（jadx 反编译 8.0.76 `com.tencent.mm.ui.base.NoMeasuredTextView` 确认，输出在 `build/host-pulldown-20261122/`）：
  聊天标题不是系统 TextView，而是继承 X2CView 的 `NoMeasuredTextView`。它在 `setText` 时用 `min(maxWidth, measuredWidth)`
  直接把省略号截进保存的文字里，maxWidth 来自微信按原生顶栏布局算的值。放进悬浮卡片后这个上限可能比实际可用宽度窄，
  于是连「对方正在输入…」都被截断。原来的 `TitleState` 只调整 `android.widget.TextView`，漏了这个类。
- 修改（`FloatingHeaderStyle.TitleState`）：新增 `NoMeasuredWidth`，把它的 maxWidth 固定为它自己的实际宽度（它的测量宽度总等于父布局给的宽度，
  所以不会溢出）；字段 `v` / `w` 可读时用于精确还原，读不到时只是不还原。标题区每次应用都重新扫描（最多 64 个 View），
  微信后加的输入状态 / 副标题也会被覆盖。分体式由原有的标题宽度逻辑执行；整体式新增 `TitleState.lift(..., false)`，只放开
  `NoMeasuredTextView`，系统 TextView 仍交给微信。
- 限制：已经截进文字里的省略号要等微信下一次 `setText` 才会恢复（如输入状态出现 / 消失）。

**左右按钮按压波纹**（新增 `HeaderButtonRipple`、`FloatingHeaderParts`）
- 返回按钮与每个可见的菜单按钮（跳过微信保留的空位）各装一个前景 drawable：从按下的位置向外扩散的波纹，裁剪在按钮圆形内，
  底下叠一层淡的按压态；松手时快速补完波纹再淡出。按压状态与触点由系统传给前景，不拦截点击。
- 圆形：分体式与画出来的胶囊完全重合（直径 = 顶栏高度，圆心在返回图标 / 菜单按钮中心）；整体式按 M3 图标按钮，半径最大 20dp。
- 浅色 16%、深色 24% 不透明度；关闭功能或切换顶栏时还原原前景。`FloatingChatHeaderFeature.State.apply()` 在样式之后调用，`restoreHeader()` 中还原。

**输入区上方快捷工具栏图标显示不全**
- Lucide 图形占 2..22 栅格，圆头描边会伸到 1..23，而渲染按 20 单位的安全区铺满方框，边缘最多被裁 1 个单位。
- `LucideIcons.bitmap` / `drawableDp` 新增带内边距的重载（栅格单位，线宽像素不变）；工具栏图标用 1 个单位内边距（`ICON_PADDING`），
  图形在 18dp 方框内缩到约 16.4dp，四边完整显示。其它地方的图标不变。
- 测试：`chat-header-toolbar-checks.py` 补 `FloatingHeaderParts` 替身与 `LucideIcons.drawableDp` 四参数重载。
- 备份：`build/header-title-ripple-backup-20261122`。**未验证**：需构建、回归与真机确认（两种顶栏样式下的标题、输入状态、按钮波纹，深浅色）。

## 2026-11-22 聊天页：底部继续上拉的回弹先落到输入卡片后面

- 现象：停在最后一条消息时继续上拉，松手回弹会先落到屏幕最底部（被悬浮输入卡片盖住），随后才出现在可见区域。
- 原因（已用 jadx 反编译宿主 8.0.76 `com.tencent.mm.ui.base.MMPullDownView` 确认，输出在 `build/host-pulldown-20261122/`）：
  聊天列表外层的 `MMChattingListView extends MMPullDownView`，它把顶部加载区 / 内容 / 底部加载区竖向排列，
  拉动和回弹都是对自身 `scrollTo`（静止时 scrollY = 内容子 View 的 top，即顶部加载区可见时它的高度、不可见时 0）。
  `ChatFooterOverlay.updateTail()` 用列表的屏幕坐标计算被输入卡片遮住的高度，屏幕坐标包含了这段拉动位移，
  于是上拉时末尾留白变小、回弹先按小留白停在卡片后面，静止两帧后留白恢复才被 `followEnd` 推上来。
- 修改：新增 `ChatTailSpacer.pullOffset(list, stop)`，沿列表到输入卡片父容器之间的祖先查找 `MMPullDownView`（按类名，含子类），
  把 `scrollY - 内容子 View.getTop()` 这段临时拉动加回去；`updateTail()` 的遮挡计算用它抵消。输入法抬升与视口缩放仍按屏幕坐标计入，不受影响。
- 测试：`ChatFooterOverlayChecks.pullBounceKeepsTail`：列表放在带 40px 顶部加载区的 `MMPullDownView` 里，静止、上拉 260px、回弹三种状态留白都保持 126。
  `chat-footer-checks.py` 的 View 替身补 `scrollY` / `getScrollY()`，`getLocationOnScreen` 计入祖先滚动；新增 `MMPullDownView` 替身。
- 顺带修正：`getTop()` 原来只在 `chat-footer-checks.py` 的 `main()` 里临时加到 View 替身，复用这份替身的
  `chat-header-toolbar-checks.py` 拿不到；而本轮早些时候置顶卡片高度（`tipsCardHeight`）用到了 `View.getTop()`，
  会导致顶栏测试编译失败。现在 `getTop()` 改在模块级补进替身，两个测试套件都能用。
- **未验证**：需构建、回归与真机确认：底部上拉回弹、顶部下拉加载历史、键盘弹出时的留白。

## 2026-11-22 会话分组卡片：加强选中态

- 选中的分组：标签后面加一个强调色胶囊（浅色约 14%、深色约 24% 不透明），文字改为强调色加粗；切换时胶囊 160ms 淡入。
  首次构建、数据通知、主题刷新不重放动画（`ConversationGroupCard.styleTab` 按「选中 / 强调色 / 深浅色」缓存外观，未变化就不重建 drawable）。
- 所有标签与「＋」都有胶囊形按压水波纹（此前没有按压反馈）。
- 尺寸：标签改为 36dp 高的胶囊，左右 14dp 内边距、间隔 4dp；标签行内缩 6dp，卡片仍是 48dp 高的胶囊，胶囊不会贴到卡片圆角。
  「＋」由 48×48 改为 36×36。等宽模式保留权重。
- 分组较多、标签行可横向滚动时，新选中的标签会平滑滚动到中间。
- 实现集中在 `ConversationGroupCard`（`styleTabs` / `tabMargins` / `styleTab` / `styleAdd` / `reveal`），`ConversationGroupBar` 只调用它，
  分组测试里的 Android 替身因此不需要 drawable / Typeface。测试：`tab-updates` 校验点击切换时淡入只触发一次、滚动定位一次、数据通知不重放。
- **未验证**：需构建、回归与真机确认（深浅色、三种材质下胶囊的对比度，分组多时的滚动定位）。

## 2026-11-22 会话分组栏改为悬浮卡片

- 新增 `ConversationGroupCard`：把分组标签包进一张卡片，左右内缩 12dp、上 6dp / 下 8dp 留白，24dp 圆角（48dp 高的标签栏即胶囊形），
  4dp 阴影，材质复用卡片材质（普通 / 毛玻璃 / 液态玻璃，含模糊与表面不透明度），做法与悬浮聊天顶栏的 `FloatingHeaderStyle.applyCard` 相同。
- 分组栏原本就是叠在会话列表上的覆盖层（下面垫一个等高的列表头），并可固定在标题栏下方；这次只改外观：
  留白算在覆盖层高度里，列表头跟着变高，第一条会话不会被卡片和阴影盖住；固定时会话从玻璃卡片下面滚过。
  玻璃采样的是会话列表本身，列表滚动会触发重采。
- `ConversationGroupBar` 去掉自带的不透明底色（原 `#F7F7F7` / `#191919`），底面交给卡片。
- 刷新：覆盖层每次 `refresh()`（标签变化、会话数据通知）重新套材质；卡片材质设置变更时 `SettingsPanelModel.refreshGlassSurfaces()`
  也会触发 `ConversationGroupingFeature.refreshHostLabels()`，改材质即时生效。覆盖层销毁时停止玻璃采样。
- 卡片材质页说明改为「首页底栏、会话分组、聊天顶栏与输入栏共用」。
- 测试：`grouping-hooks` 的 `GroupingDoubles` 补 `ConversationGroupCard` 替身（标签栏仍是覆盖层的第一个子 View，原有断言不变）。
- 备份：`build/group-card-backup-20261122`。**未验证**：需构建、回归与真机确认：卡片位置与阴影、固定 / 不固定两种滚动、
  三种材质、深色模式、点按 / 长按标签与在卡片两侧留白处上下滑动列表。

## 2026-11-22 会话分组：有标签栏但切换分组后列表不变

- 现象对应的路径：标签栏能显示，说明会话列表钩子已装上；切换后要先用「绕过钩子调用原方法」读出每行会话做快照。
  这一步在 libxposed 迁移后由兼容层 `xposed_compat/.../XposedBridge.invokeOriginalMethod` 实现，测试里一直是替身，真机从未验证。
  它一旦报错，快照返回 null，所有过滤钩子放行，列表保持原样，且这条路径没有任何日志。
- 对照 libxposed 102.0.0 的 `api-102.0.0.aar`（javap），`getInvoker(Method)` / `Invoker.setType(Type)` / `Type.ORIGIN` /
  `invoke(Object, Object...)` 的签名与兼容层一致，所以具体失败点尚未确认；本次修改按「兜底 + 诊断」处理。
- `XposedBridge`（兼容层）：反射查找只做一次并缓存（每个方法一个 ORIGIN invoker），不再每行反射；框架侧失败抛新的
  `OriginalUnavailable`（目标方法自己抛的异常照旧原样抛出），首次失败记一条日志 `invokeOriginalMethod unavailable for ...`。
- `ConversationListBridge.original()`：收到 `OriginalUnavailable` 后记一次「原方法调用不可用，改为直接调用」，此后直接
  `method.invoke`。安全性：调用期间 `RAW_DEPTH` 已置位，分组的所有钩子都会放行，直接调用得到的就是微信自己的结果。
- 诊断（每种原因只记一次，只记类型不记宿主异常内容）：分组快照失败、快照与适配器条数不一致、切换分组时尚无可刷新的列表 / 适配器。
  日志前缀 `WEX: 会话列表桥:`，在 LSPosed 管理器的日志里查看（不在 Mdl 设置的日志页）。
- 测试：`grouping-hooks` 的 XposedBridge 替身新增 `OriginalUnavailable` / `originBroken`，并改为宿主分发不经过 `invokeOriginalMethod`；
  新增用例 `origin-fallback`（未读分组在 ORIGIN 不可用时仍过滤为 2 行、只尝试一次、切回全部恢复 3 行），已加入 `regression.py`。
- **未验证**：需构建、回归与真机确认。若仍不生效，请把 LSPosed 日志里 `会话列表桥` 与 `invokeOriginalMethod` 相关的行发来。

## 2026-11-22 从系统通知进入聊天：输入卡片样式补上旧补丁的兜底

- 对照：旧补丁 `GlassPatch.java` 本身没有写「通知进入」的逻辑；11-20 手机包里顶栏 / 输入栏 / 样式三个类的方法与日志字符串，
  现在的源码全部都有（通知进入相关的 `onNewIntent` / `onStart` 挂钩、外部存储未挂载时的配置重读、顶栏不提前放弃的发现逻辑都已在）。
- 差别在旧补丁挂在顶栏每帧预绘制上的 `GlassPatch.onFrame`（手机包 `FloatingChatHeaderFeature$State.onPreDraw` 第一行）：
  它从顶栏所在窗口找到 `ChatFooter`，直接把输入卡片的叠层样式套上去。也就是说，输入栏是由顶栏「带起来」的。
  这条路径在把补丁并入源码时没有带过来。
- 为什么需要：输入栏自己的发现逻辑比顶栏弱，要求聊天 Activity 仍是当前 resumed 的那个，且 8 次（约 1 秒）后就放弃；
  而通知进入时 resume 可能落到 LauncherUI 上，聊天树也可能在过渡之后才到。顶栏的发现逻辑在窗口存在期间不会放弃，所以由顶栏兜底。
- 修改：
  - `FloatingChatHeaderFeature.State.apply()` 末尾新增 `adoptFooter(layout)`：在本页聊天布局里找唯一的 `ChatFooter`（缓存，
    丢失后最多每 8 帧重找一次），交给 `FloatingChatFooterFeature.adopt()`。
  - `FloatingChatFooterFeature.adopt(View)`：已在监听且监听挂在当前视图树上时只做一次比较就返回；否则创建 / 重新挂上监听并立即套用样式。
    若输入栏自己的配置还是冷启动时读到的「关闭」，最多每秒重读一次开关，用户真的关掉功能时不会每帧读存储。
    新增 `State.watching(View)`。日志 `stage=adopt source=header` 最多 4 条，可用于确认兜底是否被触发。
  - 输入栏原有的发现逻辑、`stage=discover exhausted` 诊断及其测试都保持不变。
- 顶栏本身：这次没有找到旧补丁里针对顶栏的额外修复（旧补丁对顶栏的 `onApply` / `onRestoreHeader` / `onDiagnose` 只是写日志）；
  如果通知进入后顶栏仍异常，需要真机日志里的 `悬浮顶栏: stage=entry` / `stage=discover` / `diagnose` 几行来继续查。
- 测试：`ChatHeaderToolbarChecks` 校验顶栏预绘制把本页 `ChatFooter` 交给输入栏；`ChatFooterHookChecks` 新增「发现已放弃后
  聊天树才到」的场景：交接后输入栏被监听，重复交接不重复注册。输入栏替身补 `adopt`。
- 备份：`build/notification-entry-backup-20261122`。**未验证**：需构建、回归，并真机从通知进入单聊 / 群聊（冷启动和微信在后台两种）。

## 2026-11-22 悬浮输入栏：收到新消息后列表中间出现空白

- 对照：旧补丁（`phone-integration-20261119/active-src/mdl/patch/GlassPatch.java` 的 `TailSpacer` / `updateChatViewport`，
  以及 11-20 手机包 `GlassPatch$TailSpacer.smali`）与现在的 `ChatTailSpacer` 是同一套做法：给聊天列表最后一项加一个底部
  item offset，让消息能滚到悬浮输入栏上方。两边都没有针对「新消息」的单独处理，旧补丁源码与手机包里也找不到别的相关代码。
- 原因：RecyclerView 只给重新绑定的项重算 item offset。新消息追加到末尾时，新的最后一项拿到了底部留白，
  但原来的最后一项没有重新绑定，仍保留缓存的留白，于是两条消息之间空出一个输入栏高度的空白。
- 修改（`ChatTailSpacer`）：`getItemOffsets` 时记下 adapter 的条目数；`update()` 发现条目数变了（且留白高度大于 0），
  调一次 `invalidateItemDecorations`（宿主混淆名 `D0`），下一次布局所有项重算，留白只留在真正的最后一项上。
  第一次看到的条目数只记录不重排；`release()` 时清空。
  `ChatFooterOverlay.updateTail()` 在触发重排的那一帧设 `changed = true`，让 `onPreDraw` 跳过这一帧，不把带空白的画面画出来。
- 范围：条目数增减都会触发（新消息、撤回 / 删除、上滑加载历史），每次只多一次布局；
  条目数不变的内容更新（如消息状态变化）不受影响，也不需要处理。
- 测试：`ChatFooterOverlayChecks.decorationLifecycle` 新增三条：首次记录条目数不重排；条目数从 4 变 5 时重排一次并跳过一帧；
  条目数稳定后不再重排。
- **未验证**：需构建、回归与真机确认。重点看：停在底部时收到新消息、自己发消息、上滑加载历史时有没有卡顿。

## 2026-11-22 置顶卡片展开高度适配；桌面主页可重启微信

**置顶卡片展开后高到屏幕底部**
- 原因：多条置顶消息展开时，微信在 `ChatTipsBarGroup` 内显示一张全尺寸遮罩（纯 `View`，match_parent × match_parent，
  #80000000，点它会收起）。在 wrap_content 的组里，这个 View 会占满父容器给的全部高度，组跟着撑到屏幕底部，
  组上的玻璃卡片也一起。我们之前只去掉了它的底色，它仍然占位。
- `FloatingChatHeaderFeature.suppressTipsScrim()`：按结构（类正好是 `android.view.View` 且宽高都是 MATCH_PARENT）找到遮罩，
  设为 GONE、alpha 0、去底色，做法同 WeKit `suppressTipsBarDim`。不依赖混淆 id。隐藏后该帧的 `onPreDraw` 返回 false
  跳过一次绘制，避免闪一帧全高卡片；不会连续两帧跳过。关闭功能时还原底色与 alpha，可见性留给微信下次展开时自己设置
  （强行改回 VISIBLE 可能在已收起时盖住聊天）。
- 兜底：`tipsCardHeight()` 取卡片体（组内唯一的 `RelativeLayout`，WeKit 的 hyi）在组内的底边，
  `FloatingHeaderStyle.clampCard()` 把卡片轮廓（裁剪和阴影）与玻璃（`ChatGlassSurface.visibleHeight`）限制在这个高度，
  即使组本身仍然偏高（折叠动画的占位层、微信重新点亮遮罩的那一帧）。`CardState` 新增 `heightLimit`，`restore` 时清除。
- 代价：展开后点卡片外的空白不再收起列表（遮罩不再拦截点击），需点卡片上的收起按钮。
- 测试：置顶用例加一个全尺寸遮罩，校验隐藏、卡片体和行不受影响、关闭后还原；校验卡片高度取卡片体底边
  （样式替身记录 `clampCard` 的值，separated-geometry 变体用真实样式时自动跳过这条）。

**桌面主页「重启微信」**
- `DesktopUi.kt` 首页在「打开微信」下加「重启微信」：确认后在后台线程执行 `su -c "am force-stop com.tencent.mm"`
  （等待 30 秒，root 管理器首次会弹窗），成功后从模块里重新打开微信，让 hook 在冷启动时重新注册。
  模块不是微信，没有 root 无法结束微信进程。失败时分别提示：没有 su、授权被拒或失败（带退出码）、超时；
  并给出「打开微信应用信息，手动强行停止」。不新增权限，不用 `killProcess`（`material3_checks` 禁止）。
- **未验证**：需构建、回归与真机确认：多条置顶展开 / 收起的高度与阴影、群公告、桌面重启（含拒绝 root 授权的提示）。

## 2026-11-22 置顶卡片：补上旧手机补丁里漏移的「去内层白卡」

- 对照旧补丁 `build/phone-integration-20261119/active-src/mdl/patch/GlassPatch.java`（11-20 手机包实际挂载的钩子见
  `release-20261120-phone-sidebar-001/baseline-smali`）。和置顶条相关的四项里，前三项现在的源码已有：
  每个矩形独立 `LiquidGlassLayer`（`ChatGlassSurface.regions`）、采样源向上找 5 层（`bind()`）、
  置顶条随滚动或每 120ms 重采（`keepFresh()`）。
- 漏掉的是 `FloatingHeaderStyle.applyCard` 入口的 `GlassPatch.onCardApplied`：置顶条套上玻璃后，
  每条置顶消息（`ChatTipsBar`）还留着微信自己的圆角白底，成了「玻璃在外、白卡在内」。
  现在的 `detachTipsPanel()` 只清三个写死 ID 的面板底色，而且特意不进入条目列表 `0x7f095a10`。
- 修改：`detachTipsPanel()` 改为带深度的遍历，在面板 chrome 之外，置顶组往下 4 层内（组 → 包装 → 面板 → 条目列表 → 条目卡）
  的每个容器都去掉底色，和旧补丁的覆盖范围一致；照旧记进 `tipsChrome`，关闭功能时还原。
  与旧补丁的差别：叶子 View 的背景不动（图标、关闭按钮可能把自己画在背景上），自己的 `ChatGlassSurface` 不动，
  而且可还原（旧补丁直接换成透明 ColorDrawable，不还原）。
- 旧补丁里没有移入、也不需要移入的：`frosted()` 与 `pickContent()` 有定义但从未被调用；
  `applyBlurOverride`（顶栏专属模糊 `floating_chat_header_blur_dp`）被默认模式位 256 关闭，实际不生效。
- 测试：`ChatHeaderToolbarChecks` 的置顶用例加一层带白底的条目容器，校验它被清空、叶子背景保留、关闭后还原。
- 备份：`build/tips-material-backup-20261122`。**未验证**：需构建、回归，并在有置顶消息 / 群公告的群里真机看效果，
  留意条目按下时的反馈是否消失（若条目背景是带按压态的 selector，会一并去掉）。

## 2026-11-22 卡片材质：表面不透明度可调

- 新键 `nav_bar_surface_opacity`（0–100%，留空 = 按材质默认），首页底栏、悬浮聊天顶栏与输入栏共用，即时生效（`nav_bar_` 前缀不需重启）。
- 调的是盖在模糊背景上的那层底色：`LiquidGlassLayer.drawBase` 新增带 `opacity` 参数的重载（旧签名转调 `AUTO_OPACITY`），
  `tintAlpha()` 在未设置时沿用原值（液态玻璃 102 = 40%，模糊为 0 时 0；毛玻璃 166 = 65%），设置后按百分比换算。
  背景无法采样时仍画不透明实色，不会出现没有模糊的半透明卡片。
- 接线：`NavigationBarFeature.Config.opacity` → `NavigationBarViews`；`ChatGlassSurface.configure()` 读 `opacity()`，
  因此聊天顶栏 / 输入栏的调用方不用改。设置写入后照旧 `refreshGlassSurfaces()` 刷新三处。
- 面板：卡片材质页选毛玻璃或液态玻璃时出现「玻璃」区块（背景模糊 + 表面不透明度）；普通表面不再显示这两个无效滑块。
  未设置时滑块显示当前材质的默认值并标「默认」，拖动后出现「恢复默认」（写空值）。`Setting.Range` 新增可选 `fallback`。
  总览与各页的「卡片材质」行摘要加上不透明度。
- 测试：`ChatGlassSurfaceChecks` 校验默认 alpha 不变、显式值覆盖、越界钳制；`SidePanelBoundaryChecks` 校验各材质默认值与普通表面不显示；
  `backdrop-recovery` 的 `NavigationBarFeature` 替身、`navigation-hooks` 的 `LiquidGlassLayer` 替身补对应常量与重载。
- 备份：`build/material-opacity-backup-20261122`。**未验证**：需构建、回归与真机确认三处卡片随滑块同步变化。

## 2026-11-22 Mdl 设置：改为「总览 → 详细设置」两级

原面板是「首页 / 聊天 / 更多」三个标签 → 功能卡片 → 点「功能配置」展开 → 设置项，四层；
功能关闭时配置整块隐藏；「更多」混放外观、隐私与诊断；卡片材质与它影响的底栏 / 聊天栏分在不同标签；
「首页面板」把会话分组和侧滑面板两个无关开关硬凑一卡。

- `SettingsSpec.kt` 重写数据结构：`PageGroup`（首页 / 聊天 / 外观 / 隐私与诊断）→ `SettingsPage`（每个功能一页）
  → `Section`（页内卡片）。页面级 `master` 是功能开关，同时出现在总览行和详情页顶部；
  区块级 `master` 用于一页里放了互相独立的功能（首页顶栏的资料卡 / 快捷入口、背景与壁纸的全局背景 / 首页壁纸）。
  新增 `Setting.Link`：底栏、聊天顶栏、输入栏的设置页各有一行「卡片材质 ›」，直接跳到共用的材质页。
- 总览页：顶部状态卡显示版本号与激活状态（读已有的 `version` / `activation`，此前没有展示）；
  纯开关功能（会话分组、侧滑面板）点行即切换；带配置的功能点文字进入详情，开关放在分隔线后面，避免误触。
  有状态的入口显示当前值，如「资料卡 开 · 快捷入口 关」「液态玻璃 · 模糊 8 dp」。
- 详情页：固定顶栏带「← 返回」，页内按「外观 / 间距 / 外形 / 行为」等分卡。功能关闭时设置项置灰但保持可见，
  不再整块隐藏；悬浮顶栏「分体组件间距」只在分体式时显示。
- 系统返回：新增 `SettingsBack.java`，详情页先退回上一页，在总览时才关闭面板。宿主开启预测性返回时走
  `Dialog.getOnBackInvokedDispatcher()`（随面板挂载注册 / 注销），未开启时走 `setOnKeyListener` 的 KEYCODE_BACK，
  两条路径不会同时生效。`SettingsUi.back(view)` / `SettingsComposeBridge.back(view)` 负责转发。
- 移除：标签页与 `panel_tab` / `SettingsShell.currentTab`；顶栏的昵称、状态文字、状态圆点颜色三项
  （`top_nickname` / `top_status` / `top_dot_color` 已无功能读取，只是不再显示，已保存的值不动）；
  日志弹窗里重复的「复制诊断」按钮（详情页仍有「复制脱敏诊断」）。
- `MindLinkSwitchRow` 增加 `enabled` 参数（默认 true，现有调用不受影响）。
- 总览图标：每个功能行左侧加 40 dp 圆角色块图标（secondaryContainer 底、22 dp Lucide 线性图标），
  映射在 `SettingsScreen.kt` 的 `pageIcon()`：顶栏 panel-top、底栏 panel-bottom、会话分组 folders、侧滑面板 panel-left、
  一键已读 check-check、聊天顶栏 app-window、工具栏 toolbox、输入栏 keyboard、气泡 message-circle、材质 layers、
  背景与壁纸 wallpaper、联网数据 globe、日志与诊断 bug。`import_lucide_icons.py` 的 COMPOSE 表加这 13 个并重新生成
  `LucideIcons.kt`（43 → 56 个 ImageVector，全模块 77 → 86 个图标，THIRD-PARTY-NOTICES 两份同步）；
  `MindLinkListRow` / `MindLinkSwitchRow` 增加可选 `leading` 参数。`NavigationIcons.java` 生成结果不变。
- 测试：`SidePanelBoundaryChecks` 改为校验新页面结构（每个键只出现一次、页面 id 唯一、材质链接、
  已移除的键、导航栈的入栈 / 去重 / 返回）；`settings_systembar_checks.py` 补 `SettingsBack` 替身。
- 备份：`build/settings-hierarchy-backup-20261122`。**未验证**：需完整构建、回归与真机确认，
  重点看详情页返回手势、总览开关与「同意并开启」联网确认、置灰状态。

## 2026-11-22 「+」菜单：一键已读 / Mdl 设置图标与原生对齐

- `LucideIcons.matchNative(ImageView, name, boxW, boxH)`：测量同菜单原生图标的不透明区域与自身墨迹，
  按长边等比缩放、中心对齐，结果按尺寸缓存；测量失败返回 null。
- `PlusMenuExtra`：`Style` 记录原生图标，`MenuRow.apply` 优先用对齐后的尺寸与内边距，否则沿用旧逻辑。
  测试替身 `scripts/tests/menu/.../LucideIcons.java` 补 `matchNative` 返回 null。
- **未验证**：需真机看大小与线宽；对齐失败时日志有「原生图标对齐失败」。

## 2026-11-22 全模块图标统一为 Lucide

此前三套图标混用：View 侧 Tabler（`TablerIcons.java`，49 键）、首页底栏 Material Symbols
（`NavigationIcons.java`，960 栅格填充路径）、Compose 侧栏 Material Symbols（composeicons 选定类 JAR，47 个）
外加编辑页 3 个手绘箭头，线条粗细与端点风格不一致。现全部改为 Lucide 1.77.0（ISC），共 77 个不同图标。

- 新增 `scripts/import_lucide_icons.py`：读 lucide-static 的 `icon-nodes.json`（只当数据解析），
  把 path/circle/rect/line/polyline 等元素（含相对命令、H/V/S/Q/T 与椭圆弧）统一转换为绝对 M/L/C/Z，
  一张映射表同时生成三处：
  - `java_src/.../LucideIcons.java`（原 `TablerIcons.java` 改名，键名不变，渲染器保留；数据块在 GENERATED 标记间）。
    实心态（播放、暂停、已收藏、`*-filled`）改为「填充闭合轮廓 + 同色描边」，Lucide 没有官方实心套。
  - `java_src/.../NavigationIcons.java`：底栏 house / book-user / compass / user，24 栅格；
    `NavigationBarViews` 改为 2 单位圆头描边，选中态 2.5 单位 + 强调色（原为实心图形）。
  - `ui-compose/sidepanel/src/main/java/dev/mindlink/ui/LucideIcons.kt`：43 个 `ImageVector`，描边 2、圆头圆角，由 `Icon()` 着色。
- 生成后逐图与原 SVG 光栅化对比：77 个图标像素一致（仅抗锯齿边缘差异）。对照图：会话工作区 `lucide-icon-map.png`。
- Compose：9 个文件的 `MaterialSymbols.Outlined/OutlinedFilled.*` 全部换成 `LucideIcons.*`，删除 composeicons import；
  编辑页手绘的返回、上移、下移箭头改用 Lucide arrow-left / chevron-up / chevron-down。
  `build.gradle.kts` 去掉 `material-symbols-selected-2.2.1.jar` 依赖与其校验（JAR 文件仅作历史保留）。
  6 个改动的 WeKit 复制源码同步更新 `wekit-sidepanel.json` 的 `targetSha256` 与对应 `.patch`，`icons` 记录改为 Lucide。
- 类名改动 `TablerIcons` → `LucideIcons`：Java 调用方 6 个文件、smali 3 个文件（u0 / TopBarFeature / PlusCardFeature，
  仅类描述符）、测试替身 3 处（chat-header-toolbar、side_panel、menu）；`NavigationRenderingDoubles` 补 `GRID` 常量。
- 许可：新增 `assets/mindlink/licenses/LUCIDE-ISC.txt`（含 Feather MIT 段），删除 `COMPOSEICONS-MIT.txt`；
  `apk_checks` 改查 Lucide 许可，`complete_candidate_checks` 允许该替换及 README 资产变化。
  THIRD-PARTY-NOTICES 第 3、7 节与 README 图标段改写，并同步到 `assets/mindlink/` 的打包副本（此前 README 副本已过期）。
- 删除 `scripts/import_navigation_icons.py`（Material Symbols 生成器）。
- 需要你确认的语义选择：红包 → gift、礼物 → package、群工具 → users、接龙 → list-ordered、
  朋友圈 → aperture、视频号 → clapperboard、通讯录 → book-user；改映射后重跑生成脚本即可。
- 备份：`build/lucide-migration-backup-20261122`。**未验证**：需完整构建（含侧栏 Gradle 的 verifyVendoredSources）、
  回归测试与真机确认，重点看底栏选中态、工具栏与「+」菜单图标大小、侧栏卡片图标。

## 2026-11-22 侧栏：编辑中关闭不再丢弃改动

- 原行为：左滑、点遮罩、系统返回、切换首页页签等任何关闭方式都会调用 `discardEditor()`，
  编辑中的布局草稿与导入的图片直接丢弃，没有提示。`close(true)` 更是在动画开始前就丢弃，
  即使在收起动画中途把抽屉抓回来，草稿也已经没了。
- 现在：关闭时比较草稿与已保存布局（新增 `HomeSidePanelLayout.sameLayout`，按持久化内容逐字段比较）。
  没改动的草稿照旧丢弃；有改动的草稿保留，抽屉收起时提示一次「侧栏编辑未保存，已保留，再次打开可继续」，
  再次打开直接回到编辑页。只有点「× 放弃」或 Activity 销毁才会真正丢弃。不拦截手势，左滑仍全程跟手。
- 一并修正：拖动识别时的重新定位改为只扣除触摸阈值（原来扣掉整段首次位移，单次大幅 MOVE 时会少走一截）；
  快甩距离改为从按下位置算起。
- 测试：`side_panel_checks.py` 补上 `VelocityTracker`、`ViewConfiguration`、`MotionEvent.obtain(MotionEvent)` /
  `setLocation`、`ValueAnimator.isRunning` 的替身（上一轮手势改动用到了它们，测试源码此前会编译失败）。
  `gestures` 用例里的未改动草稿仍按原断言在左滑关闭后被丢弃。
- 限制：一言页里还没点「应用」的改动只存在于页面内，不在布局草稿里，不受此保护。
- 备份：`build/sidepanel-draft-keep-backup-20261122`。**未验证**：需构建并运行回归、真机确认。

## 2026-11-22 修复：侧栏左滑不跟手、松手才关闭

**根因**：开始拖动时向 DecorView 补发 `ACTION_CANCEL`，让下层视图放弃这次触摸。但 DecorView 会把事件
转回 `Activity.dispatchTouchEvent`，也就是本模块自己的 hook。`onTouch` 把这次补发当成「手指抬起」：
`dragging` 被清零、触发回弹、并吞掉了这个 CANCEL。结果只跟了一帧，后续 MOVE 全部漏给页面。
打开状态下手指若落在右侧遮罩上，遮罩没收到 CANCEL，抬手时被当成一次点击，于是「松手才关闭」。
早期版本（`if (dragging)` 判断）同样受影响，这就是一直「不够跟手」的原因。

- 补发 CANCEL 期间置 `injectingCancel`，`onTouch` 直接放行，让它真正送达下层视图。
- 新增 `gestureDragged`：本次手势一旦变成拖动，遮罩的点击关闭不再生效，由拖动自己决定开合。
- **未验证**：需构建装机确认左滑全程跟手、从左缘打开同样跟手、轻点遮罩仍能关闭。

## 2026-11-22 侧栏：图片 / 快捷操作编辑页，往左关闭改为跟手

### 跟手关闭（`HomeSidePanelFeature.DrawerSession.onTouch`）
- 越过触摸阈值（系统 `ScaledTouchSlop`，原为固定 10dp）时把起点重置为当前手指位置，
  抽屉从此刻贴手移动，不再先跳一个阈值距离。
- 打开状态向左滑：横向位移 ≥ 竖向即接管（45°），打开方向仍保留 1.25 倍比例，避免抢边缘附近的竖向滚动。
- 按下时若开/关动画正在进行，立即停在手指下；没有拖动就松手则继续原动画。
- 松手速度改用 `VelocityTracker`（原为最后两次 MOVE 的差分），快甩阈值 400dp/s；
  收尾动画按手速计算时长（90–260ms），无速度时按剩余距离 140–240ms，不再固定 200ms。
- 拖动开始前若 `close()` 已执行（关闭动画中被抓住），回弹到打开时重新订阅资料并恢复 Compose 生命周期。
- Compose 一旦声明手势（`gesture_start`），Java 立即放弃本次追踪。

### 缺失回调补齐（Java）
- `SidePanelComposeBridge` 注释里写了 `image_height` / `image_scale` / `delete_action` / `add_action`，
  但 `callbacks()` 从未注册：编辑首页图片角标的缩放下拉、拖动调高度、动作删除与「+」此前点击无效。
  现已在编辑态注册；新增 `move_action:<card>:<action>:<index>` 与 `add_action:<card>:<KIND>`。
  `add_action:<card>`（编辑首页「+」）改为进入该卡片的设置页。
- 图片缩放模式持久化：`HomeSidePanelLayout.Card.imageScaleMode`（crop / fit / fill_bounds / auto_ratio），
  编码进布局 JSON 的 `scaleMode`；旧布局无此键按 crop 读取，非法值保存时拒绝。快照不再写死 `"crop"`。

### 编辑页（`HomeSidePanelEditorPages.kt`）
- 图片：预览；选择/替换图片（导入中禁用）；四种缩放模式单选（带说明）；高度滑块 80–800dp，
  按 8dp 吸附，松手才提交；「适应比例」下隐藏滑块并说明高度跟随图片。滑块按下即声明手势，抽屉不会把横拖当关闭。
- 横向 / 纵向动作：预览；已添加列表（上移 / 下移 / 删除）；全部 11 种动作可添加，已有的标注「已添加」（允许重复，与原模型一致）。
- 图片卡片编辑角标新增「编辑」入口；上移/下移箭头本地绘制（选定图标包没有 Expand 图标）。
- 中英文字符串各新增 17 条。

### 备份与验证
- 备份：`build/sidepanel-image-actions-gesture-backup-20261122-120033`。
- **未验证**：本轮无法执行 javac / Gradle。静态核对：括号配对、所用字符串中英文均已定义且无重复、
  所用图标均在选定图标包内、新回调键与 Kotlin 调用一致。需构建并真机确认：跟手手感与快甩阈值、
  动画中途抓取、滑块拖动不触发关闭、图片缩放模式保存后重开仍生效。

## 2026-11-22 侧栏卡片编辑页布局重做

只改 `ui-compose/sidepanel/.../HomeSidePanelEditorPages.kt` 与侧栏字符串（中英各新增 24 条），
Java 回调键、草稿写入方式与存储格式不变。

- 统一页面框架 `HomeSidePanelEditorScaffold`：顶栏固定（不随内容滚动），安全区上下各只计算一次
  （修复日期/钱包页顶部留白翻倍）；顶栏与内容统一 16dp 边距（原为 18dp 外再套 TopAppBar 内边距）；
  卡片设置页返回键改为「←」（本地绘制，选定图标包里没有 Arrow_back），添加卡片页仍为「× 关闭并放弃更改」。
  需要确认的操作放在底部固定操作栏。
- 每个设置页顶部加实时预览（复用卡片组件，`interactionEnabled = false`）：钱包按「默认隐藏余额」开关渲染，
  一言按草稿的出处/作者开关渲染；预览下注明「更改写入编辑中的布局，回到编辑页保存后生效」。
- 分组列表：标题 + 一张卡片 + 行间分隔线，不再每个开关单独一张卡片。
- 一言：分类显示「已选 n / 12」并有「清空 / 全选」；新增最少/最多字数输入框（留空 = 不限，对应 null）；
  本地校验「至少一个分类」「最多不小于最少」；底部「恢复默认 / 应用」，有未应用更改时点返回改为底部确认
  「放弃 / 应用并返回」（不用 Dialog 窗口）。原「保存」按钮误用了「保存侧栏布局」文案，改为「应用」。
- 天气：显示当前城市（城市 + 省份 · 代码，未设置时显示「未设置」）；城市代码输入框只收数字、数字键盘，
  「应用」放在输入框右侧，代码未变化时不可点。
- 钱包：开关下加说明「打开侧栏时余额显示为 ******，点一下钱包卡片可临时显示」。
- 备份：`build/sidepanel-editor-backup-*`。
- **未验证**：本轮无法执行 Gradle 编译，只做了静态检查（括号配对、所用字符串中英文均已定义、
  `material3_checks` 依赖的 `MindLinkSwitchRow(` / `MindLinkCard(` 仍在）。需构建后真机确认：
  侧栏宽度下数字输入框与按钮的排布、软键盘弹出时底部操作栏的位置、系统返回手势（仍走 Java 侧逻辑，
  不会触发一言的未应用确认）。

## 2026-11-22 改为 libxposed（standard）入口，静态声明作用域，移除 legacy

参照本地 WeKit `app/src/standard`：作用域不再靠 Manifest，而是 `META-INF/xposed/` 三件套，
LSPosed 据 `module.prop` 的 `staticScope=true` 把 `scope.list` 作为固定作用域。按用户要求只保留 standard，不再出 legacy 包。

- 新增 `unknown/META-INF/xposed/`（`apktool.yml` 的 `unknownFiles` 同步登记，原样打包）：
  `java_init.list` = `dev.mindlink.xposed.ModuleEntry`；`scope.list` = `com.tencent.mm`；
  `module.prop` = `minApiVersion=101` / `targetApiVersion=102` / `staticScope=true`。
  WeKit 写的是 `minApiVersion=51`，这里取 101：入口用了 101 起才有的无参构造与拦截链 API，低版本框架应直接拒载。
- 删除 legacy：`assets/xposed_init` 与 Manifest 中 `xposedmodule` / `xposeddescription` / `xposedminversion` / `xposedscope`。
  `res/values/arrays.xml` 的 `xposed_scope` 资源保留（public.xml 固定了 0x7f010000，删掉会牵动共享资源表），只是不再被引用。
- 新增 `xposed_compat/`（编入 classes2.dex）：`dev.mindlink.xposed` 下与 de.robv 同形的
  `XposedBridge` / `XposedHelpers` / `XC_MethodHook` / `XC_MethodReplacement` / `IXposedHookLoadPackage` /
  `IXposedHookZygoteInit` / `callbacks.XC_LoadPackage`，底层只走 libxposed（参照 WeKit `LxpHookWrapper`：
  每个回调一个拦截器，before → `chain.proceed(args)` → after，`ExceptionMode.PASSTHROUGH`）。
  `ModuleEntry extends XposedModule`：`onModuleLoaded` 以模块 `sourceDir` 调 `HookEntry.initZygote`，
  `onPackageReady`（微信、首包）组装 `LoadPackageParam` 调 `HookEntry.handleLoadPackage`，功能启动顺序不变。
- 全量包名改写 `de.robv.android.xposed` → `dev.mindlink.xposed`：smali 36 个文件、java_src 43 个文件、
  测试夹具 50 个文件（夹具的 fake 目录同步移到 `dev/mindlink/xposed`）。`java_stub/de/robv` 删除，
  改为 `java_stub/io/github/libxposed/api` 编译桩（不打包）。
- 编译桩只声明已在 WeKit 源码中确认的成员（`Chain`、`Hooker`、`XposedModule` 的
  `getApiVersion/getModuleApplicationInfo/log`、两个回调）；hook 构建器、`HookHandle`、`Invoker`、
  `PackageReadyParam` 的取值一律按名称反射，避免猜错签名导致 `NoSuchMethodError`。
- 构建：`build.ps1` / `build.sh` 第 2 步把 `xposed_compat` 加入源码；`merge.py`、`build_effects_candidate.py`、
  `build_scoped_features.py` 与测试的「桩不得打包」前缀加入 `Lio/github/libxposed/`。
- 测试：`apk_checks` / `effects_candidate_checks` 改为校验 `META-INF/xposed/*` 且禁止 `assets/xposed_init`；
  `regression.py` 改为断言 legacy 元数据已移除、三件套内容与 `apktool.yml` 登记、`HookEntry` 已改写。
- 备份：改动前的文件在 `build/standard-migration-backup-*`。
- **未验证**：本轮环境无法执行 javac/构建/回归，只做了静态核对（引用的类与成员在兼容层中齐全、
  源码与夹具中无残留 `de.robv`）。必须先完整构建并真机确认：LSPosed 识别为 API 101+ 模块、作用域自动带出微信、
  日志出现 hook 链、无 `ClassNotFoundException` / `NoSuchMethodError`。`build_scoped_features.py`
  的已验证基线 stub 仍是旧 de.robv 版本，限定范围构建需重新准备基线。

## 2026-11-20 删除旧 View 设置面板与 12 张遗留卡片

- `SettingsShell` 从 894 行收缩到 342 行：删掉 `buildLegacy`、`refreshTabs`、`fillContent`、
  `fillGroup`、`settingRow`、`addSettingSwitch`、`addInvoke/addInvoke0`、`addJavaCard`、
  `addLocationCard`、`saveLocationConsent`、`attachInsets`、`navIcon`、`iconParams`、`w3aClass`、
  `dpCallback`、`round`、`hairFor`、`iconFor` 与 `Group` 记录类。
  只保留宿主侧必须的四件事：`build`（Compose 载荷 + 失败兜底）、
  `windowBackground/applyBarAppearance/bindSettingsBars`（系统栏）、
  `confirmRestart/restartWechat`、`updateLocation/toast`，外加 `loadModuleClass` 与 `currentTab`。
- 删除 12 张再无调用路径的设置卡片（约 99 KB）：`BubbleStyleCard`、`AboutCard`、
  `HomeSidePanelCards`、`ChatToolbarSettingCard`、`TabBgSettingsCard`、`MyPageAlphaCard`、
  `GlobalBackgroundCard`、`BottomBarCard`、`NavigationBarCard`、`FloatingChatFooterCard`、
  `BottomBarSliderCard`、`FloatingChatHeaderCard`。
- 三个类不能整份删，按依赖收敛为最小形态：
  `HomeSidebarCards` 保留（首页侧栏 `HomeSidebar` 实例化它，`CalendarStyle*` 与 `x4/u0` 还以
  `invoke-static` 调 `width()/hasWidthOverride()`）；`QuickActionCard` 只留 `isEnabled()`
  （`PlusMenuExtra` 读一键已读开关）；`TabBgAlphaPanel` 只留 `show()` 空壳（`TabBgAlphaAction`
  仍有一条 `invoke-static` 指向它，那条链路随旧面板一起失去入口）。
- `AboutCard.isActive()/lastLogTime()` 内联进 `SettingsPanelModel`，激活状态与日志预览不变。
- **补回一处遗漏的功能入口**：旧 `TabBgSettingsCard` 是全屏壁纸（`tab_bg_enabled` /
  `tab_bg_global` / `tab_bg_alpha_0..3`）唯一的 UI，而 `TabBackgroundFeature` 与
  `TabBgDrawHook` 仍在 `HookEntry` 里注册。M3 面板新增「全屏壁纸」分组
  （开关 + 换壁纸 + 蒙层不透明度 0–100% → 0–255 四键同写 + `refreshOverlays`），
  面板与旧卡片能力对齐。
- 测试同步：`regression.py` 的旧 shell 结构断言（`addSettingSwitch` 切片、`body, null`、
  卡片文件读取）改写为对 `SettingsPanelModel` / `SettingsSpec.kt` 的等价断言，
  并新增「12 个类必须已删除 / 3 个类必须保留」的守卫；
  `settings_systembar_checks.py` 删除 `ChatToolbarSettingCard.PillSwitch`、`TablerIcons` 两份失效夹具；
  `side_panel_checks.py` 删除 `HomeSidePanelCards` 夹具。
- 验证：完整回归通过；`settings_systembar_checks` 仍 30 项全绿（真实 SettingsShell）；
  `apk_checks` 通过。`classes2.dex` 821,696 → 760,456 B（638 类），包体 12.34 → 12.28 MB。
- 未验证：真机渲染。Compose 面板新增的「全屏壁纸」分组与旧面板行为一致性需实机确认。

## 2026-11-20 微信内设置面板改为 Material3（Compose 载荷）

- `SettingsShell.build` 不再手工搭 View 树，只保留窗口/系统栏/生命周期契约：界面由
  `dev.mindlink.compose.SettingsUi` 渲染，与首页侧栏共用同一个隔离 Compose 载荷。
- 跨边界只用平台类型：`Bundle values` + `Map<String, Function<String,String>> writers`
  （写入后回读钳制值，失败回滚）+ `Map<String, Runnable> actions`（重启/选图/更新定位/
  一键已读/复制诊断）+ `Map<String, Function<String,String>> readers`（日志正文、激活状态、版本）。
  Compose 侧不接触 WexStore、feature 类或宿主对象。
- 三个标签（首页 / 聊天 / 更多）与原有设置项 1:1 迁移：开关、滑杆、三选一、文字输入、选图、
  一键已读、更新定位、日志与脱敏诊断、未生效横幅、重启确认；并记住上次选中的标签。
- 写入仍走 `SettingsChanges.saveBool/saveString`：保留保存确认、失败回滚与「需要重启」横幅；
  `nav_bar_*` / `chat_toolbar_*` / `floating_chat_*` / `bubble_*` 的即时刷新钩子不变。
- 载荷复用 `sidepanel-compose-payload`（不新增第三个载荷、不引入第二张资源表）；
  R8 增加 `-keep class dev.mindlink.compose.SettingsUi`，否则反射入口会被裁掉。
- 验证：完整回归通过；`settings_systembar_checks` 30 项（真实 SettingsShell 的窗口/焦点/对比度契约）；
  `apk_checks` 通过并新增「侧栏载荷必须包含 SettingsUi」断言；
  载荷 2,185,112 → 2,297,852 B，包体 12.27 → 12.34 MB。
- 未验证：微信内真机渲染。Compose 在 Dialog 中的排版、insets、键盘、选图回调与返回键需要在实机确认。

## 2026-11-20 移除独立桌面页，模块回到纯寄生形态

- 删除整个独立 APK 入口的桌面 UI：`ui-compose/app` 的 16 个 Compose 文件、
  桌面数据读取（`DesktopData.kt` / `DesktopEnvironment.kt`）、
  `assets/compose-payload/` 载荷，以及 `MainActivity` / `FeatureSettingsActivity`
  两个 Activity 声明与桌面专用测试、`preserve_desktop_payload.py`、
  `package_compose_desktop.py`。
- 产品上只保留微信进程内的功能与侧栏载荷；功能设置继续从微信右上角「+」→「Mdl 设置」
  进入。侧栏「Mdl 设置」快捷项改为直接打开该面板（复用 `ProfileView.openSettings`），
  不再依赖已删除的桌面 Activity。
- 构建链改为单载荷：`build.ps1` 不再执行 `:app:assembleRelease`，
  `merge.py` / `check_compose_resources.py` 支持「只有侧栏载荷」的校验分支；
  `:app` 仍保留用于共享资源表引导（`processReleaseResources` → `full-stable-ids`），
  但不再产出任何桌面代码。
- 验证：完整回归通过（含 29 项三桥集成、侧栏 12 组真实 Java 断言）；
  `apk_checks.py --allow-desktop-removal` 通过——无 LAUNCHER、无桌面 Activity、
  `xposedmodule/xposedscope/com.tencent.mm` 与全部权限保留、只有一个侧栏载荷
  （`classes.bin` 2,185,112 B、SHA-256 不变）。
- 包体：13.19 MB → 12.27 MB（去掉桌面载荷与其资源引用）。

## 2026-11-13 桌面页面按 HyperChanger 设置/关于重新设计

- 上一版只是通用状态卡堆叠，没有准确落实指定参考；本轮以本地 HyperChanger 的
  `SettingsHome` / `Group` / `About` 页面源码为结构与尺寸依据，而非泛称 HyperOS 风格。
- 桌面入口改为“设置”：32sp 常规字重大标题、52dp 收起标题栏、16dp 外边距、
  12dp 组间距、约 22.5dp 连续曲率分组表面和组内箭头行（17sp 标题/14sp 说明）。
  删除双统计卡、内联关于和大状态卡；诊断信息移到次级页面。
- “关于”是独立页面：约 310dp 品牌头部、MindLink 自有图标/名称/真实包版本，
  设备信息采用“值在上、标签在下”；项目链接、GPL-3.0、隐私和开源声明独立分组。
  不复制 HyperChanger logo/作者头像、不添加假功能开关或不存在的作者/社群信息。
- 原创绿色系渐变背景按生命周期启停并尊重系统关闭动画；不是实时模糊，不依赖
  MIUI 私有混色 API。圆角和标题布局为 Java View 适配，不宣称 MIUIX 像素级复刻。
- Activity 通过现有 smali 入口保存页面历史/各页滚动、处理系统返回并释放动画。
  保留桌面配置只读、损坏库不删除、激活未知、脱敏诊断和日志异步生命周期边界。
- 本地 LICENSE、README 隐私段落和 THIRD-PARTY-NOTICES 随 APK 打包，页面不联网。
- 完整回归、Java/D8/apktool 构建与独立包校验通过：676 项桌面行为断言，缺宿主类
  执行，原证书/v3/zipalign/唯一 Launcher/双 DEX/声明资产匹配。候选大小 9,554,609 bytes，
  SHA-256：`573c9e83c9c1a7609a8cbfbd87c0a5742a3403ec685f8f36a6bd111ebbcff177`。
- **验收范围**：严格页面行为与缺宿主类测试已通过；实机视觉、返回手势和大字体
  测量待新候选包安装后确认。无线已连通但用户中断后的设备测试仍暂停；不得把
  原有遮罩包安装成功当作这一版设计或底栏实机验收成功。

## 2026-11-13 桌面闪退与首页底部遮罩专项修复

- 删除桌面统计卡的 `setLayoutParams(null)`。先在严格 View 契约下执行旧主页，复现
  `NullPointerException: Layout parameters cannot be null`，修复后完整页面构建通过。
- 桌面 MainActivity 不再触发 WexStore 配置迁移或写微信日志。开启项统计仅对已存在、
  可读配置库执行 `OPEN_READONLY | NO_LOCALIZED_COLLATORS` 查询；损坏库使用不删文件的
  错误处理器，查询失败显示未知。
- 桌面诊断使用独立快照与原生 Toast，不加载宿主资料/Xposed；日志异步读取，Activity
  销毁或弹窗关闭后不更新旧界面。重复 inset 回调与重建不累加安全边距。
- 首页原生 Frosted 底部高度仅在当前 Launcher 首页窗口接管。覆盖 Pager 后代、祖先与
  首页兄弟节点，排除聊天、其它窗口及不与底部交叠的节点；不改顶部、半径、全局启用
  或正常系统导航色。
- 配置后置、底高 setter 与绘制前置共同处理宿主重设；模块写入有重入保护，保存最新
  宿主底高（包括显式零）。暂停、进入聊天、禁用和销毁时条件恢复，回首页重新接管。
  胶囊自动隐藏不恢复整宽旧遮罩；真实玻璃采样/折射管线及四个原生页面未重写。
- 本地完整回归通过：604 项桌面行为断言、宿主/Xposed 类缺失执行、57 项遮罩所有权
  与恢复断言、17 项真实 Feature Hook 接线断言，以及既有回归。模拟契约测试不等同于
  Android 实机测量/绘制验收；旧“footer panel ordering”输出仅覆盖纯几何。
- Java → D8 → apktool → 合并 → zipalign → v3 签名全量构建通过；独立检查唯一
  Launcher、双 DEX 校验和、ZIP/类定义无重复、无测试/宿主 stub 打包，证书与上一包一致。
  本轮 APK 为 9,517,517 bytes，SHA-256：
  `14e13a3628cef2d2ffe4eab610ce6f07e652f3090a5e60789e7c4c6a33524b56`。
- **设备验收待确认**：当前无 ADB。桌面冷启动、原生遮罩是否消失、进聊天返回和关闭
  悬浮栏恢复需安装完整候选包后手动确认。此轮不宣称其它聊天/资料/分组移植已完成。

## WeKit 对齐重构（2026-11-12）

按用户要求对齐 WeKit 当前本地工作区，全部以 MindLink Java/smali 架构等价移植：

### 移除
- `AntiRevokeFeature`（防撤回）与 `ConversationCardFeature`/`ConversationCardSettingCard`
  （会话卡片）：删除源码、HookEntry 初始化接线、设置入口与关于页统计；
  旧配置键（`anti_revoke_enabled`、`conversation_card_*`）仅停止读取，不做清理。
- 聊天工具栏快捷回复（`chat_toolbar_reply`、编辑器、派发分支）。
- 首页固定四分类筛选栏（`FilterBarPlacement`/`FilterBarStyle`/`FilterBarMount`）运行接线，
  旧 `ConversationFilterFeature.smali` 保留为转发入口。
- 旧胶囊底栏（`BottomBarComposeFeature` init 不再调用）、四页背景回退 UI、
  旧 Dialog 侧栏与顶栏按钮入口、`ChatTopBarTransparent`/`ChatFooterTransparent` 运行接线。

### 新增（均参照 WeKit 对应实现，GPL-3.0）
- 共享桥接层：`FeatureBootstrap`（HookEntry 单点启动）、`HostTargetResolver`
  （DexKit 解析 + 指纹缓存）、`HostDatabaseBridge`（只读 SQL 守卫）、
  `ConversationStorageBridge`、`ConversationListBridge`（MVVM getView/count Hook）。
- 聊天工具栏：工具表加入系统拍摄（借用相册格子的原生长按回调）、礼物、语音输入；
  动作缓存按 AppPanel 隔离（`PANEL_ACTIONS`）；`chat_toolbar_enabled_items` 逐项启用；
  设置卡提供每项开关与上下移动排序。
- 用户分组：`ConversationGroupingFeature` + `ConversationGroupStore`（JSON 持久化）
  + `ConversationGroupBar` + `ConversationAdapterSnapshotReader`（Java 版快照读取）；
  支持手动/未读/群聊/好友/公众号/SQL 分组，count/getView 位置重映射；
  SQL 只接受只读语句（`HostDatabaseBridge.isReadOnly`）。
- 一键已读：`MarkAllReadFeature` 直查 `rconversation` 未读会话后调用原生
  updateUnreadByTalker，不再依赖筛选栏 adapter；后台线程执行，主线程反馈。
- 全局背景：`GlobalBackgroundFeature`（Activity onResume/焦点 Hook + DecorView 蒙层
  + WeKit 黑名单 + 1–80% 不透明度）；设置卡 `GlobalBackgroundCard`。
- 首页底栏：`NavigationBarFeature`（TabsAdapter getCount/getItem/onPageSelected/onTabClick
  索引重映射 + pager setCurrentItem 映射）+ `NavigationTabModel`（启用/排序规范化）
  + `NavigationBarViews`（自绘胶囊栏）+ 设置卡 `NavigationBarCard`。
- 首页侧栏：`HomeSidePanelFeature`（左缘拖动手势 + 遮罩面板）+
  `HomeSidePanelLayout`/`HomeSidePanelCards`/`HomeSidePanelActions`/`HomeSidePanelImages`
  （WeKit 卡片类型与 11 种动作，天气/日历/一言复用 Wex 缓存键）。
- 悬浮顶栏/输入栏：`FloatingChatHeaderFeature`/`FloatingChatFooterFeature`
  + `FloatingHeaderStyle`（圆角/边距/阴影/edge-to-edge，整体式/分体式开关）。

### 设置页重排
- 首页：用户分组、悬浮底栏、侧滑面板、顶栏、一键已读。
- 聊天：悬浮聊天顶栏、聊天工具栏、悬浮聊天输入栏、自定义气泡。
- 更多：全局背景、联网天气与定位、关于/日志。

### 验证
- `python -I scripts/tests/regression.py` 全部通过（含更新后的静态检查）。
- `scripts/build.ps1` 全量构建通过：javac → D8 → apktool → classes2.dex 合并 → zipalign → v3 签名。
- **未实机验证**：设备未连接。分组位置映射、底栏切页、侧栏手势、悬浮栏样式与
  各卡片的宿主兼容性需上机确认。

## 来源
- 设备：小米 24122RKC7C（Android 17，KernelSU root，LSPosed）
- 原始 APK：/data/app/~~5-iLLMgZ5Jt3bNWLUSb_Jg==/wex.ui.core-Itdo8yp_9_4E7ZU-hZpOfw==/base.apk
- 包名：wex.ui.core  版本：1.2.27 (versionCode 27)
- 宿主：微信 com.tencent.mm 8.0.76

## 构建方式
工程为 apktool 2.9.3 反编译产物，可直接回编译：

```
java -jar apktool_2.9.3.jar b wex_apktool -o wex_rebuilt.apk
zipalign -p -f 4 wex_rebuilt.apk wex_aligned.apk
apksigner sign --ks wex_fix.jks --ks-pass pass:wexfix123 --key-pass pass:wexfix123 \
  --ks-key-alias wexfix --out wex_final.apk wex_aligned.apk
adb install -r -d wex_final.apk
```

注意：本工程使用新签名证书，原版证书指纹为
e2da276f149378b5ee4017a37c43964a6f1808c220d697bd2ad1fec15f33129e。
因此必须保留下面的「签名校验绕过」修改，否则模块会拒绝启用功能。

## 已做修改

### 1. 绕过重打包签名校验（必需）
文件：`smali/wex/ui/core/xposed/HookEntry.smali`
方法：`access$verifyModule` 开头直接 `return true`，跳过 APK 签名指纹比对。
原因：重打包后签名必然改变，原逻辑会输出「签名校验失败，功能已禁用」并停止初始化。

### 2. 修复首页顶栏（头像/昵称/状态）不显示
文件：
- `smali/wex/ui/core/feature/TopBarFeature.smali`
- 新增 `smali/wex/ui/core/feature/TopBarHomeHook.smali`

原因：`TopBarFeature.init` 用 `x4/f0` 的索引 0xE 注册 `Activity.onResume` hook，
但 `x4/f0` 的 `beforeHookedMethod` sparse-switch 只实现了 0x2/0x5/0x6/0xA/0xC/0xF，
0xE 落入默认分支，导致 `insertProfile` 从不执行。

修复：新增独立 hook 类 `TopBarHomeHook`，在 onResume 后读取 `top_profile_enabled`，
为 true 时调用 `TopBarFeature.access$applyTopBar`，并把 `TopBarFeature.init` 的
hook 实例替换为该新类。

实测：首页标题栏左侧已显示自定义头像与状态圆点/文字。

### 3. 修复 WexStore 配置读取的“空缓存”问题
文件：`smali/wex/ui/core/WexStore.smali`
方法：`readAll`

原因：`openDb()` 失败或查询抛异常时，仍把空 Map 写入静态 `cache`，
导致整个微信进程生命周期内所有配置都读成默认值（表现为顶栏/四页背景等
功能时好时坏、偶发不生效）。

修复：失败路径不再写入 `cache`，下次读取会重新尝试打开数据库。

### 4. 四页背景（更换背景）绘制修复 + 透明度
文件：
- `smali/wex/ui/core/feature/TabBackgroundFeature.smali`
- 新增 `smali/wex/ui/core/feature/TabBgDrawHook.smali`
- 新增 `smali/wex/ui/core/feature/TabBgAlphaBtn.smali`
- `smali/wex/ui/core/xposed/SettingsPanelHelper.smali`

原因与结论：
- 四个页面的容器是 `com.tencent.mm.ui.FrostedContentView`（父容器
  `com.tencent.mm.ui.base.CustomViewPager`，index 0..3）。
- 它的 `dispatchDraw` 在 `frostedEnabled=true` 时把子内容画进 RenderNode 再整体
  绘制，因此原有的 `dispatchDraw` before 画背景会被完全覆盖；after 画又会盖住内容。
- 参照 WeKit 的 `ApplyGlobalBackground` 实现，改为在 DecorView 上插入全屏
  ImageView 蒙层（tag = `wex_tab_bg_overlay`），`bringToFront` + `elevation`，
  按透明度叠加——即「半透明蒙层」效果。

透明度配置 key（字符串，取值 0~255）：
- `tab_bg_alpha_0` ~ `tab_bg_alpha_3`（对应 微信页/通讯录页/发现页/我的页）
- 默认 `"70"`（约 27%）

设置面板：在「四页背景」组末尾新增「背景透明度」按钮，点击在 10%→20%→30%→40%
之间循环切换（写入全部四个 key）。

## 本轮修复（2026-11-02）

### 5. 我的页背景（实验版）重做 —— 之前从未真正实现
文件：
- `smali/x4/f0.smali`（注入点改为调用 `MyPageBackground.apply`）
- `smali/wex/ui/core/feature/MyPageProfileFeature.smali`（`applyProfileLayout` 精简为一行委派）
- 新增 `wex/ui/core/feature/MyPageBackground.java`

两个根因（均来自设备日志，不是推测）：
1. `ClassCastException: NoMeasuredTextView cannot be cast to TextView`，
   注入在第一步就抛异常，整段逻辑中断；
2. 原代码只改 `cdw` 高度并搬移头像/昵称，**从头到尾没有读取 `mypage_bg_path`**，
   还给卡片设了纯红底 `0xFFFF0000`，卡片高度塌成 0，表现为顶部一片空白。

现在的实现：
- 卡片固定 150dp 高、18dp 圆角，图片铺满并圆角裁切；
- 背景图按 `mypage_bg_alpha`（0~255，默认 200）设置不透明度；
- 昵称 / 微信号 / 状态文字按图片平均亮度**自动反色**（亮底黑字、暗底白字），
  并加 1px 阴影，保证任何背景都看得清；
- 设置面板新增「我的页背景透明度」滑块。

### 6. 四页背景（全屏蒙层）下线
- 移除设置面板中的「四页背景」分组入口（含四个换图按钮与透明度按钮）。
- 该方案用全屏 `ImageView` 蒙层实现，没有更换入口、且会漏到扫一扫/拍摄页，改用「我的页背景」。
- `TabBgDrawHook` 保留，但新增 `isExcludedPage()`：
  扫一扫（`com.tencent.mm.plugin.scanner.*`）、拍摄/视频录制
  （`com.tencent.mm.plugin.recordvideo.*`）、图片查看（`...chatting.gallery.*`）
  这三类 Activity 不再挂壁纸蒙层。

### 7. 新增：聊天工具栏（仿 WeKit）
文件：新增 `ChatToolbarFeature.java`、`ChatToolbarSettingCard.java`；
`HookEntry.smali` 注册入口；`SettingsPanelHelper.show()` 末尾追加卡片。

- 在聊天页输入框上方插一排快捷工具（相册/拍摄/视频通话/语音通话/位置/红包/转账/
  文件/收藏/接龙/个人名片/音乐/快捷回复），横向可滚动。
- 工具不自己实现业务，而是定位 AppPanel 里的 GridView，按格子文字匹配后
  `performItemClick`，沿用微信原生逻辑。
- 「快捷回复」点击填入输入框，**长按**可直接编辑内容（由 `chat_toolbar_reply` 保存）。
- 开关复用设置面板同款蓝色胶囊样式（自绘 `PillSwitch`，不用系统 Switch）。

### 8. 会话筛选栏调整
- 白底去掉（背景色 `-1` → `0` 透明）。
- 位置移到首页卡片上方（`FilterBarPlacement.moveAboveCards`）。

1. 背景蒙层当前会拦截触摸事件，需给 overlay 设置触摸穿透
   （如 `setClickable(false)` 已设，但仍需 `setFocusable(false)` 之外的处理，
   或改为 `updateHitTestResult` 覆盖 / 使用 `ViewGroup` 的 `dispatchTouchEvent` 透明）。
2. 背景蒙层目前每帧在 `dispatchDraw` 里重复设置，建议加一次性标记减少开销。
3. 黄历接口 https://www.36jxs.com/api/Commonweal/almanac 已返回反爬 HTML，解析必失败，
   日历卡片的农历/宜忌无法刷新（本次未处理）。
4. 首页顶栏依赖配置：`top_profile_enabled=true`，且需设置
   `top_avatar_path` / `top_nickname` / `top_status` 才有内容显示。

## 本轮进度（2026-11-06）

### 9. 设置面板清理（已改完，随本轮包安装）
- 删除「毛玻璃压制」（`frost_glass_enabled`）与「自定义加号面板（实验版）」整块
  （SettingsPanelHelper.smali 13760–13860 区间整段移除，寄存器准备代码一并清理）。
- 「聊天气泡（实验版）」改名「自定义气泡」。

### 10. 新增功能（java 侧已实现）
- `AntiRevokeFeature`：消息防撤回，hook `e01.u#q7`（sysmsg 分发器 revokemsg 分支），
  `anti_revoke_enabled` 开关控制，默认关。
- `MarkAllReadFeature` + `QuickActionCard`：一键已读卡片（清公众号红点走 `na5.u#g`）。
- `ConversationCardFeature` + `ConversationCardSettingCard`：会话卡片化（置顶分组整卡、
  圆角拼接、点击返回补涂、分割线识别隐藏）。
- `FilterListUnlockHeight`：筛选列表高度解锁。
- `GlobalWallpaper` / `PlusMenuExtra`：全局壁纸、加号面板扩展项。
- `x4/e.smali`（筛选后返回刷新）注入 refreshAdapterRef + scheduleSync，
  修复筛选后返回跳回「全部」。

### 11. 构建与安装（2026-11-06 15:02）
- apktool 重建 + merge classes2.dex（STORED）+ zipalign + apksigner，dex header 校验
  class_defs_size=23625 正常。
- 已推送安装（/data/local/tmp/w.apk → pm install -r -d），重启微信后 LSPosed 日志确认：
  会话卡片/快捷工具/悬浮底栏/背景守卫全部注册成功，无崩溃。
- 首页截图确认：顶栏头像昵称状态、筛选栏（全部=绿色胶囊、#EDEDED 浅灰容器）、
  卡片化列表、悬浮底栏（胶囊 864x158px）均正常。

### 设置面板入口（备忘）
- 聊天页「+」弹窗注入的菜单项（tag `wех_plus_item`，俄文 х）→ 点击
  `SettingsPanelHelper.show(activity, y/d callback)`。
- 注入链：HookEntry.access$injectPlusMenuItem ← z4/d ← z4/e（hook PopupWindow.addView）。

### 遗留
1. 一键已读清公众号红点需真实公众号新推送验证。
2. 底栏透明残留灰带待处理。
3. 黄历接口反爬（36jxs.com），日历卡农历/宜忌不刷新。
4. 搜索栏/首页快捷工具容器色 `#FEFEFE` 反色修复待验证。

## 本轮修复与新增（2026-11-06 晚）

### 12. 设置面板闪退修复（VerifyError）
根因：删「毛玻璃压制」「自定义加号面板」两块时，把首个 `switchItem$default {v0..v13}`
前的寄存器重排块一起删了（v10 应为 float 却残留 LinearLayout 引用），
DexOpt 校验 `show()` 失败 → 打开面板即崩。
修复：从 11-02 备份（wex_src_v1.2.27_patched_v2.zip）提取原重排块插回
（v0=p0、v1=p1、v10=0f、v11=0、v12=0x600、v13=null 等 21 条 move/const），
并用寄存器数据流模拟脚本对 3 个调用点逐一比对原版，全部一致。

### 13. 自定义气泡补全（新）
- 新增 `BubbleStyleCard.java`：模式三选 chip（毛玻璃渐变/自定义图片/透明气泡）、
  更换气泡图片（`bubble_bg_path`）、图片不透明度滑块（`bubble_bg_alpha`，默认 230）。
- 新增 `BubbleApply.java`：`x4/l#pswitch_3` 入口先调 `apply()`，
  mode 0 返回 false 走原毛玻璃逻辑；mode 1 按 view 尺寸解码图片→居中裁切→
  圆角裁切（`bubble_blur_radius` 作圆角）→ BitmapDrawable 设 alpha，
  post+postDelayed(120ms) 两次刷新等布局稳定；mode 2 全透明+半透明白描边。
- 任何异常回退毛玻璃，不影响聊天页。
- `x4/l.smali` pswitch_3 开头插 6 行分发（`move-result v0` + `if-eqz` 跳回原逻辑）。

### 14. 设置面板分组重排（新）
面板尾部 java 卡片区按功能重新分组（`sectionTitle` 新标题）：
- **聊天**：聊天工具栏、自定义气泡
- **首页**：会话列表卡片、一键已读
- **背景**：卡片背景（我的页）、四页背景
- 顶部残留的空「更换背景」标题已删。
- 系统自带条目（界面美化/首页卡片/顶栏）顺序不动。

### 构建（16:40）
- classes2.dex = 101904 bytes（80 个 class，含 BubbleApply/BubbleStyleCard）
- 已安装重启验证：无 VerifyError、无 FATAL，LSPosed 日志全部正常注册。

## 第二轮（2026-11-06 17:29）

### 15. 悬浮底栏滑块事故与回滚
- LiquidSlider 直接包在 bar 外层导致 apply() 抛异常被吞 → tab 栏消失 + ANR。
- 已回滚该注入，底栏恢复正常（步骤④ 864x158px）。
- 重做方案（待做）：hook LinearLayout.dispatchDraw after 在 canvas 上画滑块，零布局改动。

### 16. 气泡图片分对方/我方（随 17:29 包生效）
- `bubble_bg_path_peer`（对方）/ `bubble_bg_path_self`（我方），BubbleApply 按
  气泡中心相对聊天列表中线判断左右归属；一侧未配置时回退旧 key `bubble_bg_path`。
- BubbleStyleCard 换图按钮拆成两个：更换对方气泡图片 / 更换我方气泡图片。

### 17. 设置面板分组重排完成
- 聊天：自定义气泡开关、顶部样式组、快捷工具组、聊天底栏透明、会话筛选栏、
  聊天工具栏卡、气泡样式卡
- 首页卡片：图片卡、日历卡、音乐、卡片顺序、会话列表卡片、一键已读
- 顶栏：topBarGroup；背景：卡片背景、四页背景；其他：更新定位
- 「界面美化」标题改「聊天」；空「更换背景」「首页」标题删除。
- 关键修复：聊天卡插入点覆盖了 imageStyleGroup range 调用所需的 v3(int)，
  用 move/from16 v21 保存/恢复 v3 解决第二起 VerifyError。

### 18. 筛选栏容器色还原白色

## 第三轮（2026-11-07）

### 19. 自定义气泡支持 .9 图（NinePatch）
- BubbleApply 新增 tryNinePatch：解码时检测 npTc 块（NinePatch.isNinePatchChunk），
  命中即用 NinePatchDrawable 整体替换气泡（自动拉伸、不裁切不圆角），
  未命中回退原「缩放+居中裁切+圆角」路径。
- 选图走系统文档选择器（OPEN_DOCUMENT */*），拷贝为字节流（npTc 块保留）。
- 卡片描述注明支持 .9。

### 20. 作者信息改为原作者（仅 GitHub + 跳转）
- About 页「作者信息」卡改为单行：原作者 / Ql1121 · github.com/Ql1121/Wex
  （移除 版本/框架/目标 三行）。
- 页脚仅保留 GitHub 图标跳转（跳 github.com/Ql1121/Wex）；
  Telegram 行、t.me 链接方法（SettingsPage$lambda$2$0$0$0）与分发方法一并删除，
  k.smali 的 case0 改调 GitHub 分发。

### 21. 归属性注释清理
- 移除 java/smali 注释中的「仿 xx / 参照 xx / 完全按 xx / 同款 / 照搬 / WeKit / 参考 Kyant」
  等表述（ChatToolbarFeature、ConversationCardFeature、MarkAllReadFeature、
  LiquidSlider、BottomBarDrawSlider、ChatFooterBgGuard、ChatFooterTransparent 等）。
- **此项已于「第 57 条」撤销**：清理归属性注释与 GPL-3.0 的署名要求冲突，
  现已恢复为逐文件的来源说明。

### 22. 底栏液态玻璃滑块搁置
- 拆除 dispatchDraw 钩子注册、attach 接线、x4/f 分支（sswitch_a）与设置面板开关卡。
- 源码（LiquidSlider/BottomBarDrawSlider/BottomBarPagerBridge/BottomBarSliderCard）保留
  备后续重做（纯绘制方案，零布局改动）。

### 23. 修复 SettingsPageKt VerifyError（点「我」闪退）
- 删除页脚第二块时连带删掉了 v2=int 恢复赋值，v2 残留 Modifier 引用导致
  c.<init>(I) 校验失败（模块 App 进程闪退）。已在保留块后补 const/4 v2, 0x1。

## 第四轮（2026-11-07）

### 24. 模块 App「关于」单页（首页/我的 合并）
- MainActivity / FeatureSettingsActivity 改为加载 AboutPageBuilder（纯 View），
  原 Compose 双 tab（首页/我的）不再使用。
- 布局参照 HyperDuo 关于页：
  - 「关于」大标题（无刷新图标）；
  - 绿色激活卡（已激活 + 模块版本 + 对勾图标，按 log.txt 24h 内有写入判定激活）
    + 右侧统计小卡（作用域应用 1 / 已开启显示项 N，统计功能开关 key）；
  - 信息卡：系统版本(Build.DISPLAY·Build.ID) / 应用版本 / Xposed 框架(读
    org.lsposed.manager 版本，manifest 已加 queries) / 设备型号 / 作用域 /
    Android 版本 + 脚注；
  - 动作卡：原作者（Ql1121 · github.com/Ql1121/Wex，跳转原作者仓库）/
    查看仓库（跳转链接占位，待换用户仓库地址）；
  - 日志卡：仅「调试日志」开关（log_enabled），不显示日志明细。
- 环境信息含构建时间等动态值由 PackageManager 读取。
- 修复 build_java.ps1：d8 调用改 java.exe 直调 D8 主类（绕过 cmd 命令行长度上限）。

### 25. 气泡美化模块（dev.wxbubble）机制研究（待移植）
- 图片：/sdcard/WxBubble/bubble_in.png（对方）/ bubble_out.png（我方），mtime 缓存。
- toNinePatchDrawable：任意 PNG 自动转九宫格——有 1px 边框标记则解析
  （上/左=拉伸区、右/下=padding），无标记则「guess」取中间 1/3 为拉伸区；
  手工序列化 npTc chunk（ByteBuffer 小端）→ NinePatchDrawable。
- 应用：StateListDrawable（常态原图 + 按压/选中态 HSV 压暗 0.8 副本）、
  保存/恢复 padding、tag 幂等。
- 对方/我方来自消息绑定钩子的消息模型发送标志（不靠几何位置）；
  文本消息挂 MMNeat7extView，图片/表情挂对应容器。


## 第五轮（2026-11-07）图标库统一为 Tabler Outline

### 26. 引入 Tabler Outline 替换全部手绘 PNG
- 决策依据见 `docs/icon-lib-compare.html`（五库 12 工具图对比页）。
- 数据源：`@tabler/icons` 3.31.0 · MIT · Copyright (c) 2020-2024 Paweł Kuna。
- 新增 `java_src/wex/ui/core/feature/TablerIcons.java`：把 35 个图标的 pathData
  以字符串常量编进 dex，运行期用 `Path` + `Canvas.drawPath` 自绘，
  完全不碰资源表（工程里没有 `android.util.PathParser`，故自带解析器）。
- 弧线（A 命令）入库前已全部转成三次贝塞尔，运行时只解析 M / L / C / Z。
- 校验：30 个图标与 unpkg 原版 SVG 逐像素重合（见 `.oil-ui/icon-lib-compare/verify.html`）。

### 27. 尺寸与描边归一化（关键，否则整体缩小 17%）
- Tabler 图标本体落在 24 栅格的 2..22（20 单位），而原 PNG 裁边后占满画布。
- 若直接按 24 栅格铺满，所有图标会缩小约 17% 且描边变细。
- 处理：`SAFE = 20`，让 20 单位映射到方框边长，并把描边同步回退为
  `2 × 20/24 ≈ 1.667`，使笔画相对本体的比例与原手绘一致（约 8.3%）。
  18dp 图标在 3x 屏上是 4.5px 描边，与替换前完全相同。

### 28. 实心变体保留状态语义
- 音乐卡的播放/暂停/收藏原本是实心图，靠红/白区分状态。
- 新增 `FILLED_ICONS` 表（Tabler filled）+ `SOLID` 集合 + `FIXED_COLOR`：
  - `点击播放.png` / `点击暂停.png` → Tabler filled 版，实心渲染；
  - `已收藏.png` → filled star，固定品牌红 `#FF262E`（与原图一致）；
  - `未收藏.png` → outline star（原图白实心在浅色卡上几乎不可见，改描边更好辨认）。
- `colorOf(name)`：有固定色用固定色，否则跟随主题（浅 `#1A1A1A` / 深 `#E6E6E6`）。

### 29. 五个加载点全部接入（未命中自动回退 PNG）
| 位置 | 方法 | 尺寸 |
|---|---|---|
| `ChatToolbarFeature` | `loadAssetIcon` | 18dp |
| `PlusCardFeature`（+ 面板 17 格） | `loadIcon` | 37dp |
| `TopBarFeature`（顶栏搜索） | `loadSearchIcon` | 18dp |
| `HookEntry`（设置入口） | 内联 | 复用原 v11 边长 |
| `x4/u0`（音乐卡按钮） | `n(String)` | 96px（FIT_CENTER 缩放） |
- 各点先查 `TablerIcons.has(name)`，命中即渲染；未命中回退原 assets PNG。
- `ChatToolbarFeature.trimTransparent()` 与位图缩放逻辑已删除（矢量无透明边）。
- 25 个运行时文件名全部命中，`assets/` 里的 PNG 保留作为回退。

### 30. 构建链修复（本机原先完全不可用）
- 旧脚本硬编码 `F:\AI\...` 与 `F:\Program Files (x86)\Eclipse Adoptium`，本机均不存在。
- `scripts/build.ps1` 重写：自动探测 JDK / SDK / apktool / python，
  支持 `MINDLINK_JDK` `MINDLINK_SDK` `MINDLINK_APKTOOL` `MINDLINK_PYTHON` 覆盖。
- `scripts/merge.py` 改为接收命令行参数，不再写死路径。
- 踩坑记录：
  1. apktool 3.x 需要 aapt2 在 PATH，否则报 `Cannot run program "aapt2"`；
  2. PowerShell 用 `&` 调外部控制台程序在本环境静默失败，改用 `Start-Process`；
  3. javac 的 @argfile 把反斜杠当转义符，路径要转正斜杠；d8 又不接受引号；
  4. `.NET ZipArchive` 的 NoCompression 是 deflate level 0，不是 STORED，
     所以合并 dex 仍走 Python `zipfile`；
  5. `keytool -dname` 带空格会被 `Start-Process` 拆散参数，改用无空格写法；
  6. PowerShell 5.1 按 ANSI 读脚本，`build.ps1` 必须存成 UTF-8 **带 BOM**。
- 产物：`build/MindLink-1.0.apk`（8.60 MB，v3 签名，classes2.dex 为 STORED）。
- 本机无连接设备（`adb devices` 为空），**尚未真机验证**。


### 31. LSPosed 推荐作用域声明修正
原清单用资源形式声明，LSPosed 2.x 读不到，导致新装的模块「推荐作用域」为空：
```xml
<meta-data android:name="xposedscope" android:resource="@array/xposed_scope"/>
```
参考本机可正常工作的旧模块 `wex.ui.core`，改为字面量：
```xml
<meta-data android:name="xposedscope" android:value="com.tencent.mm"/>
```
同时补上 LSPosed 2.x 的推荐作用域文件（apktool 会经 `unknown/` 原样打包）：
- `unknown/META-INF/xposed/scope.list` → APK 内 `META-INF/xposed/scope.list`，内容 `com.tencent.mm`

验证：
- `aapt2 dump xmltree` 确认清单为 `value(0x01010024)="com.tencent.mm"`
- 解包确认 `META-INF/xposed/scope.list = "com.tencent.mm\\n"`
- 签名 v3 通过，两个 dex 均 STORED

注意：以上只是「推荐」作用域。LSPosed 仍需用户在管理器里对模块点启用并确认作用域，
适配器侧的 `lspctl` 只有 `scope list`，没有写入命令；直接改
`/data/adb/lspd/config/modules_config.db` 有波及其它 32 个模块配置的风险，故不做。


### 32. 转为纯寄生模块：移除桌面入口
本模块是寄生在微信进程里的 Xposed 模块，功能全部通过 hook 注入宿主界面实现，
不需要独立的桌面启动图标。

改动：
- `AndroidManifest.xml`：`MainActivity` 移除 `MAIN`/`LAUNCHER` intent-filter，
  桌面不再出现图标；保留 activity 声明，LSPosed 管理器仍可「打开」它。
- `FeatureSettingsActivity` 改为 `android:exported="false"`（仅模块内部可调）。
- 删除 `MainActivity.ensureAllFilesAccess()` 及其调用：模块不再有独立入口，
  `/sdcard/Android/media/com.tencent.mm/Mdl/` 的读写由宿主的微信进程完成，
  宿主本身持有该媒体目录权限，模块 App 无需再申请 `MANAGE_EXTERNAL_STORAGE`。
- `AndroidManifest.xml` 移除 `MANAGE_EXTERNAL_STORAGE` 权限声明。

原本只存在于模块 App「关于」页的能力，收敛进宿主设置面板：
- 新增 `java_src/wex/ui/core/feature/AboutCard.java`，由 `SettingsPanelHelper.show()`
  末尾追加到「其他」分组（「更新定位」按钮之后）。
- 卡片内容：模块版本 + 激活状态（按 `Mdl/log.txt` 24 小时内是否有写入判定）、
  「调试日志」开关（`log_enabled`）、「查看日志文件」按钮。

验证：
- `aapt2 dump xmltree` 确认无 `LAUNCHER`、无 `MANAGE_EXTERNAL_STORAGE`
- `classes2.dex` 内含 `AboutCard`
- 用户实际使用的设置入口始终是微信「+」弹窗里的注入面板，不受影响


### 33. 修正：模块在宿主中不加载（ClassNotFoundException）
现象：装包后在微信里看不到「Mdl 设置」，LSPosed 日志报

```
ClassNotFoundException: Didn't find class "wex.ui.core.xposed.HookEntry"
No module class loaded for dev.mindlink
```

根因：我在第 31/32 轮给 APK 加了 `META-INF/xposed/` 三件套
（`module.prop` + `scope.list` + `java_init.list`），让 LSPosed 走了 **modern API**
加载路径。但该路径下 LSPosed 用 `DexPathList[[directory "."]]` 找类，
指向模块 APK 的 classloader 没有正确建立，于是找不到入口类。

而原本能工作的旧包 `wex.ui.core` 只有 `assets/xposed_init`（legacy 路径），
不触发 modern API。

修复：删掉整个 `unknown/META-INF/xposed/` 目录，只保留 `assets/xposed_init`，
回到与旧包完全一致的结构。

代价：`staticScope`（推荐作用域）依赖 `module.prop`，删掉后不再自动带出微信作用域。
作用域需在 LSPosed 管理器中手动勾选一次。功能本身不受影响。

验证（实测）：
- 重启微信后 `logcat` 出现 `(com.tencent.mm)[dev.mindlink,XposedBridge,...]` 共 11 条
- `加号注入: 映射模式命中` —— 证明 hook 链已跑通
- 无 `No module class loaded` 报错

## 审查修复（2026-11-10）

### 34. 设置面板参数与入口恢复
- `SettingsShell` 的图片、日历卡片参数改为「标题、描述、开关键、样式键」，继续使用原配置键。
- 首页恢复会话筛选栏开关，聊天页恢复底栏透明开关；新开关只保存配置，需通过顶部重启按钮重启后生效，不自动结束微信。
- 更多页恢复更新定位入口，使用模块 ClassLoader 桥接原 `LocationHelper.updateLocation` 回调。

### 35. 位置相关网络访问改为明确授权
- 新增 `LocationPrivacy`，共用 `location_network_enabled`，默认关闭，读取失败时禁止联网。
- 更多页新增「联网天气与定位」，开启前确认向 Nominatim 和 Open-Meteo 发送经纬度；取消不保存、不请求。
- 天气与定位在执行入口和 HTTP 发送入口检查授权，关闭后不再发起位置相关请求，已开始的请求无法撤回；已有本地配置保留。
- 删除明文 HTTP IP 定位回退。缺少权限时明确提示去系统设置授权，没有可用位置时返回失败，不立即申请权限后继续联网。
- README 按实际行为披露黄历、一言、天气与逆地理编码接口及数据内容。

### 36. 日志查看与默认值修复
- `AboutCard` 的日志开关默认值统一为 true，与实际日志逻辑一致。
- 日志按钮改为模块内只读弹窗，不再发送 `file://` URI 或调用外部文件管理器。
- 新增 `LogPreview`，后台最多读取末尾 64 KiB、展示 200 行；覆盖无日志、读取错误、中文与超长行，关闭弹窗后不更新控件。

### 37. 构建路径兼容与本地回归
- `Start-Process` 统一引用 Windows 参数，处理空参数、空格、中文、内嵌引号和尾部反斜杠。
- 编译产物先打为 `classes.jar` 交给 D8，不再用 D8 argfile 解析每个类文件路径；stub 不参与该 JAR。
- 回归入口：`python -I scripts/tests/regression.py --jdk "<JDK 根目录>"`，测试文件位于 `scripts/tests`，不参与模块编译。

验证（本地，不是实机）：
- 参数映射、恢复入口、隐私门禁、日志默认值静态回归通过。
- 日志读取边界及授权开关的 Java 行为测试通过；Windows 参数往返测试覆盖空格、中文、引号、空参数与尾部反斜杠。
- 独立「中文 空格 Project」副本完整构建通过（JDK 21 / SDK 37 / Apktool 3.0.3），未覆盖原工程 APK、密钥和中间产物。
- APK v3 签名、ZIP CRC、DEX 长度/SHA-1/Adler-32 校验通过；两个 DEX 均 STORED，类无重复，新 UI/隐私类齐全，无 Xposed stub，签名密钥保持一致。
- 尚未安装或重启微信。ART 运行时验证、卡片与开关持久化、定位授权/拒绝/失败、日志弹窗布局需实机验证；未用真实位置调用第三方服务。

## 顶栏与聊天工具栏修复（2026-11-10）

### 38. 设备证据与验证边界
- 上轮包已成功覆盖安装；本轮只读确认宿主为微信 8.0.76（versionCode 3141）、SDK 37，模块安装时间为 2026-11-10 11:27:01。
- 读取配置及详细 Xposed 日志时 USB 断开，未取得两个开关的实际值、资料内容或相关异常堆栈。不得将源码中的缺陷直接表述为已确认的手机故障根因。
- 本轮不自动安装或重启微信，不读取/发送聊天内容，不变更用户资料或 LSPosed 作用域。

### 39. 聊天工具栏挂载生命周期
- Hook 注册与开关分离并去重，开关关闭时也登记 ChatFooter 生命周期，不再错过进程内首次启用。
- 新增 `ChatToolbarLifecycle`：View 附着后尝试挂载，最多重试 12 次，间隔 150 ms；开关关闭或 View 分离会取消任务并移除工具栏，重新附着可恢复。
- 通过受循环保护的 ContextWrapper 解包获取 Activity，输入容器未就绪不再永久放弃。
- 保存开关后读取回实际值并刷新当前组件，不再提示点击不存在的「下方一键重启」。
- 背景守卫改为在原 setter 完成后清理已登记视图，避免将 `setBackgroundColor(int)` 等参数置为 null；关闭或离开时恢复背景、移除监听，不再对所有 BounceView 无条件设色。
- 延迟加载工具和派发回调在执行前重查开关和附着状态，避免关闭后仍执行工具动作。

### 40. 新增回归
- Smali 参数/本地寄存器边界与 invoke 参数槽数检查，能检出原 `forceStopSelf` 的越界 p1。
- JVM 行为测试覆盖：启动关闭后首次开启、容器延迟创建、重复刷新不重复插入、关闭/分离取消任务、重新附着、重试上限、包装 Context 和循环保护。
- Android Context 测试替身仅在 `scripts/tests` 中，不参与模块构建。

### 41. 顶栏与公共辅助修复
- `ImagePicker.forceStopSelf(Context)` 修正越界 p1，移除错误的隐藏 API 反射；延时重启的宽寄存器不再覆盖 Activity。确认框显示失败不退出，确认后仍终止自身进程。
- HomeCardManager、ImagePicker、TopBarFeature 分别捕获初始化异常，避免一个失败跳过顶栏。
- TopBarFeature 不再因启动时开关关闭而跳过 Hook 注册，增加幂等标记；应用入口及排队任务仅处理有效 LauncherUI。
- 开关关闭会清理已插入顶栏；修改资料后替换原 tag，空资料移除旧内容并提示。标题与搜索只修改模块识别的视图，关闭时恢复，不读取微信个人资料。
- 顶栏编辑弹窗改用设置面板 body 为锚点；开关、文字、头像、状态颜色保存及关闭设置时刷新，原 dismiss 回调保留。

本轮验证：
- 全部 Java 编译、独立中文/空格路径完整 APK 构建通过，v3 签名、ZIP CRC、DEX 头及校验和检查通过，无重复类或 Xposed stub。
- 原有回归、工具栏生命周期 JVM 行为测试、Smali 参数寄存器/调用槽数检查通过；新增检查能检出原 `forceStopSelf` 越界缺陷。
- 尚未安装本轮包或重启微信。手机断连后无法验证实际开关、ART 校验、首页 Toolbar/ListView 识别、两项功能显示及视图恢复，需再次连接后实测。

### 42. 快捷工具条容器兼容（2026-11-10）
- 最新手机证据：微信 8.0.76、模块安装时间 12:42:48；工具栏与顶栏开关均为 true，工具栏已注册 Hook，但当前进程无挂载或超时日志；自定义顶栏资料均为空。
- 工具栏构造回调、弱引用集合、挂载和清理统一接受 `ViewGroup`，不再静默跳过非 `FrameLayout` 的 ChatFooter；工具条内部布局仍使用 FrameLayout。
- 新增 `ChatToolbarViews` 复用原纵向 LinearLayout + EditText 条件，并检查传入根节点。测试覆盖通用容器、根节点、延迟子视图及横向/装饰布局排除。
- 新增低频日志记录真实父类、首次登记、附着和工具条挂载尺寸，不记录聊天文本。
- 当前未取得宿主 APK 确认 ChatFooter 实际父类，因此不能将容器兼容缺陷直接认定为这台手机的已证根因。
- 用户选择先完成工具条修复；微信本人资料读取待设备连接恢复后核对，本轮未新增资料读取或变更资料配置。
- 本地回归和独立中文/空格路径完整构建通过；新包 v3 签名、ZIP CRC、DEX 头/校验和及类重复检查通过。未安装本轮包，手机工具条实际显示仍待验证。

## 聊天气泡真实毛玻璃（2026-11-10）

### 43. 用背景取样与真实模糊替换旧渐变
- 已确认旧「毛玻璃渐变」仅绘制半透明渐变、高光和描边，没有背景采样或模糊运算；透明模式生效不代表旧毛玻璃存在真实模糊。
- 新增 `BubbleGlass`，只从明确识别的聊天壁纸或安全背景 Drawable 取样，按气泡位置裁切、降采样；不绘制整个窗口、消息 View、聊天文字或头像，不给消息 View 设置 RenderEffect。
- 新增纯 Java `BubbleBlur`，用三轮可分离 box blur 近似高斯，处理预乘透明度与边缘钳制，支持取消；模糊和自定义图片解码在单个后台工作线程执行。
- 取样尺寸、排队任务和缓存均有上限；模糊缓存最多 6 项、合计 2 MiB。位图仅用于内存绘制，不写文件、不上传。
- 边缘及超长气泡只取可见区域与背景的有效交集；采样边界延展背景边缘像素，避免把边界外的透明区域混入模糊。
- 模糊结果叠加独立圆角、低透明度染色和描边。无法识别背景时明确记录一次渐变降级；纯色背景没有纹理，模糊的视觉差别本来就不明显。

### 44. 重绘、任务失效与模式隔离
- 气泡 Hook 注册与开关分离；`x4/f` 统一交给 `BubbleApply.onBackground`，不再用 `bubble_blur_radius == 0` 阻断所有模式，也不再用永久 `doneViews` 跳过宿主后续设置背景。
- `BubbleApply` 统一管理毛玻璃、图片、透明三种模式；`x4/l` 移除旧假毛玻璃回退，保留其它功能分支。
- 临时重入保护避免模块设置背景递归；尺寸/位置变化、模式切换或参数保存使旧任务失效。异步写回检查任务版本、模式、尺寸、位置及附着状态，过期结果不得覆盖复用气泡。
- 背景源使用弱引用快照，追踪 Drawable 替换、位图 generation、绘制参数与可见区域；宿主重绘触发节流检查，壁纸延迟就绪可重新取样，写回前再次确认来源一致，不持续驱动聊天页重绘。
- 关闭或分离时取消任务、移除绘制监听并恢复模块替换前的背景；最小宽度和阴影高度独立恢复，不因宿主已替换背景而漏掉。保留原消息 padding，不改变消息点击、发送或其它业务行为。

### 45. 独立参数与保存后刷新
- 新增 `BubbleSettings`：`bubble_glass_blur_dp`（0–32dp）、`bubble_corner_radius_dp`（默认 12dp）和 `bubble_glass_tint`（0–200）分别控制模糊、圆角、染色；图片继续使用 `bubble_bg_alpha`。
- 未设置新模糊参数时兼容旧 `bubble_blur_radius`（0–6 映射至 0–24dp），不清理已有开关与双侧气泡图片。模糊为 0 只表示不模糊，不禁用透明/图片模式。
- 设置页按模式显示控件，滑块停止拖动后保存并读回确认，开关、模式和选图同样刷新已发现气泡；不自动重启微信。

本地回归与交付边界：
- 64 项配置检查通过；模糊测试覆盖纯色、透明色、纹理降反差、半径变化、独立副本、参考卷积、尺寸限制与任务取消；代际测试覆盖模式切换、宿主覆盖、分离、关闭和重新开启。
- 背景快照与取样区域测试覆盖壁纸延迟就绪/替换、来源参数变更、旧结果失效、边缘/超长气泡交集以及像素边界延展；恢复路径及来源检查的接线通过静态回归。
- 现有日志、位置授权、工具栏状态机/容器、Windows 参数往返及气泡 Smali 接线/寄存器静态检查通过；这些测试不替代 Android 视图或 ART 实机验证。
- 独立中文/空格路径的完整 Java、D8、Smali、资源构建通过；APK v3 签名、zipalign、ZIP CRC、DEX 头/校验和及类重复检查通过，新增气泡类齐全，无 Xposed stub 或回归测试类，副本源码与原签名密钥一致。
- 尚未安装本轮包或重启微信，需用有纹理的聊天壁纸验证背景识别、裁切对齐、强度、滚动、聊天切换与关闭恢复。
- 验证包：`build/MindLink-1.0-bubble-glass-20261110.apk`（9,076,789 字节），SHA-256：`1541a4f7773e43d129246d9a7101056a8c1a120e1e66e8cb49230d2f850cb8f8`；保留所有既有 APK，签名证书与前次已安装包一致。
- 本轮只修改气泡；工具栏沿用前轮代码，微信本人资料读取继续暂缓。

## 设置待生效常驻提示（2026-11-10）

### 46. 固定提示替代反复 Toast
- `SettingsShell` 在标签栏与滚动内容之间新增固定提示条：「存在未生效改动，需要重启微信」，右侧重启按钮沿用二次确认；确认后退出，由用户手动重新打开。
- 提示仅在有待应用改动时显示，切页、关闭重开及取消重启不清除；监听随面板解绑，弱引用订阅不延长 Activity 生命周期。
- 新增 `SettingsChanges` 和纯 Java `SettingsChangeState`：保存与提示分离，按 key 跟踪修改前基线，改回原值后移除对应待应用项。
- `WexStore.getSettingChecked` 为设置提供严格数据库读取，区分缺失与读取失败；原 get/put 接口与运行期缓存行为不变。写入后必须回读匹配，失败不报告保存成功。
- 图片覆盖前后比较内容指纹，同路径换图也能识别；选图取消/失败不标记，恢复默认同时撤销对应路径与内容的待应用状态。

### 47. 统一保存回调、保留即时刷新
- `SettingsPanelHelper` 与音乐滑条共 66 处设置存取改走统一桥，移除 40 处成功/保存提示及 5 处保存后的自动热重启；显式重启改用确认入口。定位结果与操作提示保留。
- 会话卡片、我页背景、四页背景、一键已读等 Java 卡片接入统一保存确认；我页不透明度改为拖动更新文字、停止拖动时保存。
- 气泡与工具栏保留原保存确认和刷新链，只移除正常成功 Toast；顶栏和壁纸不透明度等既有即时刷新同样保留，不新增重启要求。
- 保存失败、选图失败、刷新失败、权限和日志读取错误仍显示；同一设置连续保存失败的提示去重，避免滑条拖动刷屏。未全局屏蔽 Toast，也未监听音乐播放/缓存等非设置写入。

本轮验证：
- 新状态测试覆盖默认值、重复保存、多项修改及恢复、写入/回读失败、面板解绑重绑、即时项排除、同路径换图、取消/失败与图片重置。
- 原有回归、保存回调无自动重启/重复提示的静态检查，以及修改后的 Smali 寄存器与调用槽数检查通过。
- 独立中文/空格路径完整构建通过，v3 签名、zipalign、ZIP CRC、DEX 头/校验和及类重复检查通过；新增设置类齐全，无 Xposed stub 或测试类。Java/Smali 桥方法签名匹配，构建副本源码与当前源码及原密钥一致。
- 验证包：`build/MindLink-1.0-settings-pending-20261110.apk`（9,080,885 字节），SHA-256：`8a5795586f376955d831038a26a739ce8cb87cbc82b553f17c192529ecbe6f77`；保留既有 APK。
- 未安装、未重启微信、未提交 Git；提示条实际布局和 ART 行为仍需实机验证，微信本人资料读取继续暂缓。

## 首页会话筛选栏扁平化与固定布局（2026-11-10）

### 48. 原生风格文字标签
- 新增 `FilterBarStyle`，去掉外层和选中项的双层 24dp 胶囊；四项等宽，使用普通文字、选中绿色文字与短下划线，保留轻量 Ripple 和无障碍选中状态。
- 最小触摸高度 48dp，允许大字体自然增高；背景优先匹配会话页明确的不透明纯色，否则使用深浅色背景，不让列表内容透到标签文字下方。

### 49. 会话页内独立固定占位
- `FilterBarPlacement` 不再依赖首页卡片 tag 或移动 ListView 子视图，也不把筛选栏添加为 header。
- 仅在识别出的 `FrostedContentView` / `CustomViewPager` 页面结构、单个完整内容分支及合法布局参数满足条件时，添加固定兄弟行并为原内容分支预留顶部高度；不重新挂接原内容分支、不覆盖首行、不改原卡片和搜索 header 顺序。
- 记录原父容器、索引与布局参数；只撤销模块自己的改动，挂载失败回退。未知容器或固定边界不明确时不插入，并低频记录原因，不推断已兼容所有微信布局。
- 弱引用记录当前列表与延迟首页回调；复用现有有界生命周期调度，重复挂载不叠加、失败可重试、分离取消并恢复、列表替换清理旧状态。

### 50. 筛选接线与恢复
- 修正旧 `moveAboveCards` 静态方法被实例调用的错误，移除 `x4/e` 跨类直接读取私有 `listViewRef`；改用当前 ListView 和合法入口。
- 不再由进程级 `barAdded` 阻断新列表挂载；保留返回后的 `refreshAdapterRef + scheduleSync`，仅用户主动切换不同分类时归顶。
- adapter 身份变化时使筛选去重状态失效，快照尺寸阈值单独保留，避免仅因失效标记把已筛选结果当成全量；分类规则与既有同条数数据处理策略不重写。
- 筛选开关继续沿用保存后重启生效语义，不更改设置常驻提示、顶栏内容、气泡、聊天工具栏或本人资料读取。

本轮验证：
- 固定挂载事务与生命周期测试覆盖重复挂载、未知容器不修改、部分失败回退、延迟就绪、分离/重新附着、列表替换和重试上限。
- 全部既有回归及筛选静态接线检查通过；独立中文/空格路径完整构建通过，v3 签名、zipalign、ZIP CRC、DEX 头/校验和、类重复与必要类检查通过，无 Xposed stub 或测试类。Java/Smali 桥方法签名匹配，源码副本与当前源码及原签名密钥一致。
- 验证包：`build/MindLink-1.0-filter-fixed-20261110.apk`（9,093,173 字节），SHA-256：`2bc7f887b1c488886aba86f427a3f808c6a732e1fe57f97d77c1a967b0b48b51`；保留全部既有 APK。
- 尚未安装或重启微信。实际固定槽位、下拉移动层、空列表及四个主页标签切换仍需实机验证，不能将静态/纯 JVM 检查视为宿主 UI 或 ART 已验证。

## 日历与音乐左侧抽屉（2026-11-10）

### 51. 默认收起，顶栏按钮按需展开
- 新增 `HomeSidebar`，复用原生 Dialog 和既有配色/系统栏辅助，以顶栏「日历和音乐」按钮呼出左侧面板；不添加边缘手势，不包裹宿主根布局。
- 冷启动、聊天返回和页面恢复都不自动展开；遮罩、关闭按钮或系统返回可收起。切换主页标签、出现聊天覆盖层、Activity 暂停/销毁及宿主替换会关闭并使旧加载任务失效。
- 只展示原配置中已启用的日历、音乐，保留两者相对顺序；图片卡仍在原 header，空卡片容器使用 ListView 公开 API 移除。
- 顶栏入口保留至少 48dp 点击区域，在布局前后检查与原有标题、资料和操作按钮的间隔；未知结构或空间不足时保留原首页卡片承载，不隐藏唯一入口。筛选栏和设置常驻提示不改。

### 52. 复用卡片与播放状态
- `HomeCardManager` 的插卡入口委托新的承载管理，保留模块资源路径初始化并防重复注册；配置按当前进程启动快照读取，不改开关、歌单或用户排序配置。
- 新增 `HomeSidebarCards`：首次展开才构建所需卡片，按 owner、类型和可用宽度复用；反射使用模块 ClassLoader，避免重复建卡与网络加载。四种日历样式及音乐卡只在受控建卡期间使用侧栏宽度，不缩放文字。
- 播放器新增 `setUiVisible/isUiVisible/releaseUi`，隐藏时只跳过 mini 控件更新；保留播放位置采样、MediaSession、悬浮歌词及原音乐业务。打开不自动播放，关闭不暂停/重置/释放音频。
- owner 销毁或替换时清理旧 View/Activity 引用，保留播放器与歌单状态。界面更新标记与 View 显隐分离，避免侧栏再打开后播放器因自身 INVISIBLE 而无法恢复。
- 保留原日历缓存与位置网络授权门禁，不增加定位请求或第三方接口。侧栏加载等待有效尺寸，关闭会取消未完成的挂载任务；图片 header 更新不重新排序。

本轮验证：
- 新状态和几何测试覆盖默认收起、重复打开、旧任务失效、页面离开、启用项/排序以及顶栏入口避让和实际位置校验。
- 静态检查覆盖卡片按需创建、模块反射桥、尺寸覆盖、播放器 UI 接口不启停音频、共享进度续排与原隐私/筛选/设置接线；全部原有回归通过。
- 最终完整回归及独立中文/空格路径构建通过；v3 签名、zipalign、ZIP CRC、DEX 头/校验和、重复类与必要类检查通过，无 Xposed stub 或测试类。Java/Smali 桥方法签名匹配，构建副本与当前源码及原签名密钥一致。
- 验证包：`build/MindLink-1.0-home-sidebar-20261110.apk`（9,117,749 字节），SHA-256：`face498e6aacaf29b68007c7a4d5ab6a05294fc24473537bea93f2aa05e8ffaa`；保留全部既有 APK。
- 未安装、未重启微信、未提交 Git；侧栏可达性、窄屏/大字体布局及音乐连续播放仍需实机验证，微信本人资料读取继续暂缓。

## 「+」菜单统一原生行样式（2026-11-10）

### 53. 一键已读与设置使用同一份菜单模板
- `PlusMenuExtra.sync` 从已识别菜单项向上定位完整原生行，不再把文字的直接父节点当成整行；只接受 ListView 或有其他完整原生行的纵向 LinearLayout，未知容器不盲目追加。
- 两项统一采用原生实测高度、有效布局高度或 56dp 兜底，保留至少 48dp 触摸高度与 WRAP_CONTENT 的文字增高空间；复制四边 padding 和适用 margin。
- 图标槽、图文间隔从完整行坐标系计算，支持嵌套控件及 RTL；不再分别使用 85% 缩放和半行高图标。保留原 Tabler 图标，颜色跟随原生图标 tint 或文字状态颜色。
- 字号按 px 复制，保留 Typeface、ColorStateList、字距、文字缩放、font padding 和省略策略，避免大字体重复缩放。
- 每项独立复制可用背景/前景；没有原生按压态时参考 ListView selector 或使用轻量 Ripple。模块行之间的分隔线沿用原生容器，不共享可变按压 Drawable，也不复制原生业务监听。

### 54. 稳定顺序与双入口同步
- `HookEntry.a()` 保留原 `x4/x0(2)` 设置点击委派，改为调用 Java 四参桥；`e()/f()` 不再因设置 tag 已存在而整体退出。
- 一组承载「一键已读 → Mdl 设置」，已读关闭时只留下设置；去掉 500ms 后单独移动设置行的调度，不改 `h1/e3` 其他共享分支。
- 按模块 tag 与弱引用维护当前组；ListView 使用单个公开 footer，滚出可见区仍能去重和同步开关，不直接移动 AdapterView 子视图。普通容器不重挂原生行。
- 不改变弹窗关闭策略，不通过点击原生菜单项模拟关闭；一键已读仍委派原入口。侧栏、筛选栏、气泡及微信本人资料读取均未改动。

本轮验证：
- 新增独立 UI 测试替身运行实际 `PlusMenuExtra` 注入代码，78 项断言覆盖原生 WRAP_CONTENT 实测行高、未测量兜底、嵌套/RTL 对齐、大字号 px 复制、独立按压态、原生分隔、稳定顺序、开关切换、双入口与不可见 footer 去重、安全容器拒绝和旧模块行迁移。点击仅委派 mock，不执行真实已读操作。
- 全部既有回归、菜单接线和 HookEntry 寄存器/调用槽/跳转标签检查通过。独立中文/空格目录完整构建通过，Java/Smali 桥描述符匹配；构建输入与原签名密钥保持一致。
- v3 签名、zipalign、ZIP CRC、DEX 头/校验和、必要类及重复类检查通过，没有 Xposed stub 或测试替身/测试类。包检查最初误报工程已有的 Android 兼容类，核对后限定检查范围并重新通过。
- 验证包：`build/MindLink-1.0-menu-native-20261110.apk`（9,125,941 字节），SHA-256：`4cc7ba1dd64dc8fb177557b1bd7fc648efdf94ea64efb28af283e029acf6c402`；原有 APK 和密钥未覆盖。
- 未安装、未重启微信、未触发真实已读操作、未提交 Git。测试替身不包含 Android 实际测量/绘制和触摸分发；实际容器命中、相邻原生行间距、按压与大字体显示仍需实机验证。

## 设置页面主题统一（2026-11-11）

### 55. 统一卡片表面与绿色强调色
- 新增 `SettingsTheme`，集中管理设置页的深浅色页面背景、一级卡片、次级表面、正文/说明、绿色强调色、强调色上的文字及未激活状态；移除 Java 卡片各自的灰色/蓝色主题常量。
- `SettingsShell` 两个分组加载入口只统一返回根 View，不增加外层卡片；七张 Java 设置卡及 Shell 自建设置行统一白色/深色卡面、20dp 圆角、16/14dp 内边距、12dp 卡间距，无卡片阴影。
- 会话列表卡片、日历/图片/音乐分组及排序项的内部按钮、选项和说明块使用共享次级表面；不再混用半透明黑色块。深色模式的浅绿实心按钮配深色文字，避免白字对比度不足。
- 两套 Java `PillSwitch` 与 Smali `z4/w` 使用同一轨道/拇指颜色和 48×28dp、11dp 拇指几何；每次绘制读取主题，关闭态保持中性，移除旧开关阴影。
- 所有可达设置 SeekBar 显式设置进度、剩余轨道、滑块与刻度 tint；标签、按钮、数值文字和选项初始/动态刷新都使用共享主题，防止点击后恢复蓝色。

### 56. 设置弹窗与行为边界
- 模块自建 AlertDialog 使用窄 `showDialog` 外观桥，保留原 Builder 的显示/按钮/关闭业务；文本输入和设置 PopupWindow 使用统一背景、按钮、焦点、光标和选区颜色。
- Dialog 的文字遍历仅着色明确的原生标题/正文，不覆盖用户颜色选择预览；一级卡片样式不递归清除子 View 背景。
- `TabBgAlphaPanel` 保留原 Dialog、玻璃层、模糊算法与预览，只统一文字、按钮、滑条颜色。系统文件选择器和微信原生页面主题不改。
- 设置 key、默认值、保存/回读、刷新、待生效提示、授权与重启确认流程不变；仅把选项绘制中的四次重复读取合并到同一次已读状态。
- 不改首页卡片、筛选栏、气泡绘制、菜单业务、用户自定义预览色与删除操作的语义红色；当前 Shell 不可达的旧 helper 分组不做全文件颜色替换。微信本人资料读取继续暂缓。

本轮验证：
- 107 项共享主题行为/对比度断言通过：深浅色正文与强调文字至少 4.5:1、开关拇指至少 3:1、统一根卡不新增包裹、动态状态色、独立 Drawable、滑条/输入框 tint、预览保护和原 mock 点击委派；全部既有回归及菜单 78 项断言通过。
- 两个 Smali 文件全部 206 个方法通过寄存器/调用槽/高寄存器与跳转标签检查；32 处 Java 主题桥签名与编译结果一致，42 处业务监听/定位调用点与上轮源码一致。
- 真实 Android SDK Java 编译及独立中文/空格目录完整构建通过；v3 签名、zipalign、ZIP CRC、DEX 头/校验和、重复类和必要类检查通过，无 Xposed stub 或测试替身/测试类；构建输入与原签名密钥一致。
- 验证包：`build/MindLink-1.0-settings-theme-20261111.apk`（9,125,941 字节），SHA-256：`bec02e3adc323588daea7bc8b31e16b8c1c453266d15d5ae4640bf3a98c87ae2`；旧 APK 和原密钥未覆盖。
- 未安装、未重启微信、未触发真实已读/定位等操作、未提交 Git。测试替身不代替实际 Android 绘制、宿主 tint/弹窗布局与大字体显示，实机视觉验收仍待完成。

## 来源与许可合规修正（2026-11-12）

### 57. 恢复并补全上游署名
对照 WeKit（`master`）与 Wex（`main`）两个上游仓库逐项溯源，确认本项目
**不只是「部分功能参考」**：聊天工具栏、会话列表卡片、自定义气泡、会话筛选栏、
一键已读、防撤回、全局壁纸蒙层、首页侧栏、聊天顶栏/底栏透明等，均为
实现级对应（部分算法、参数、配置键名一致）。此前第 21 条「归属性注释清理」
删除来源标注的做法与 GPL-3.0 第 5 条冲突，现予撤销。

改动：
- `THIRD-PARTY-NOTICES.md` 重写：分「上游项目（GPL-3.0）」与「第三方库」两类，
  逐文件列出 Wex / WeKit 的使用范围，并补上此前完全未声明的
  **Backdrop（Apache-2.0）**、**DexKit（Apache-2.0）**、**AndroidX/Compose/Kotlin（Apache-2.0）**。
- `README.md`：致谢改为具体列出 9 项移植功能；许可证段落说明本项目同时是
  Wex 与 WeKit 的衍生作品。
- `AboutPageBuilder`：动作卡新增「功能参考 / Ujhhgtg · github.com/Ujhhgtg/WeKit」一行，
  使模块内 UI 与 README 口径一致。
- 恢复以下文件的来源注释（Java javadoc / smali 头部注释）：
  `ChatToolbarFeature`、`ConversationCardFeature`、`BubbleApply`、`MarkAllReadFeature`、
  `PlusMenuExtra`、`HomeSidebar`、`GlobalWallpaper`、`FilterBarStyle`、`FilterBarPlacement`、
  `TabBgDrawHook.smali`、`ConversationFilterFeature.smali`、
  `ChatTopBarTransparent.smali`、`ChatFooterTransparent.smali`。
- `AntiRevokeFeature`：删除指向 `wx-safe-module`（未在致谢中出现）的出处注释，
  改为 WeKit 的 `AntiMessageRecall`，并补充「本机撤回走 NetSceneRevokeMsg（scene 594）、
  本 hook 拦不到」的边界说明。

验证：
- 除 `AboutPageBuilder` 的动作卡新增一行「功能参考」外，其余改动均为注释与文档，
  未触碰任何运行逻辑、字段或 Smali 指令。
- `javac` 全量编译 45 个源文件通过（JDK 21 / SDK 37，0 错误）。
- `python -I scripts/tests/regression.py` 全部回归通过。
- apktool 3.0.3 完整回编通过，`classes2.dex` 248,216 字节、STORED，
  `classes.dex` STORED，APK v3 签名校验通过（apksigner: Verifies）。
- 产物：`build/MindLink-1.0.apk`（9,126,019 字节，8.70 MB），
  SHA-256：`f2169f681e1011f532e47b00cad1521cea41a1240aae69a5570b67ecfed30f9f`。
- 可执行串 `github.com/Ujhhgtg/WeKit` 已确认进入 `classes2.dex`。
- 未安装到设备、未重启微信；未提交 Git。

## 首页顶栏资料与左侧栏（2026-11-12）

### 58. 抽出共用的资料视图 `ProfileView`
新增 `java_src/wex/ui/core/feature/ProfileView.java`，顶栏与侧栏共用一套渲染：

- **圆形头像**：`CENTER_CROP` + `Outline.setOval` 圆形裁切；路径与 mtime 双键缓存，
  避免每次 `onResume` 重复解码。
- **首字占位**：无头像但有昵称时，用昵称首字（按 code point 取，兼容 emoji）
  放在圆形底色上，取代此前「昵称非空但无头像就只剩文字」的裸排版。
- **昵称**：加粗单行，超出省略。
- **状态行**：`7dp` 圆点 + 状态文字，圆点颜色沿用 `top_dot_color`
  （`red` → `#F44336`，其余 → `#31B36B`）；此前状态只有 6dp 圆点且颜色写死。
- **深色模式**：全部颜色改走 `SettingsTheme`，此前 `#1A1A1A` / `#999999` 写死，
  深色模式下昵称几乎不可见。
- **点击热区**：整块可点击并有 Ripple，点击打开 Mdl 设置；此前顶栏资料是纯展示。

`TopBarFeature.buildProfile` 由 320 行 smali 改为 9 行委托（`ProfileView.buildBar`），
逻辑与呈现全部收敛到 Java 侧。

### 59. 左侧栏加入资料区与快捷动作卡
`HomeSidebar` 的侧栏面板新增两块，均置于滚动内容顶部、日历/音乐卡片之前：

- **资料区**（`ProfileView.buildPanel`）：58dp 头像 + 19sp 昵称 + 状态行，
  右侧带设置齿轮。
- **快捷动作卡**（新增 `SidebarActions.java`）：扫一扫 / 收付款 / 添加朋友 /
  二维码名片 / 发起群聊，一行五格，图标 24dp + 11sp 文字。
  跳转目标与 extra 沿用项目内 `TopBarScanFeature` 的既有配置
  （`BaseScanUI`、`WalletOfflineEntranceUI`、`AddMoreFriendsUI`、
  `SelfQRCodeUI`、`MvvmContactListUI` + `scene=7`）。

侧栏标题由「日历和音乐」改为「Mdl」，因为内容已不限于这两张卡片。

### 60. 新增 5 个 Tabler 图标
`TablerIcons` 增加 `qrcode` / `wallet` / `user-plus` / `id-badge` / `users-group`。
Tabler 源 SVG 含 `A`（椭圆弧）命令，而本项目解析器只支持 M / L / C / Z，
故按 SVG 1.1 F.6.5 做 endpoint → center 参数化后转三次贝塞尔（每段 ≤90°）。

校验：与 unpkg 上 `@tabler/icons@3.31.0` 原版 SVG 做双向 Hausdorff 距离比对，
5 个图标最大偏差 0.0024 栅格单位（24 栅格视图，约 0.01%），形状一致。

### 61. 设置面板文案同步
顶栏开关描述由「仅显示自定义头像/昵称/状态；请展开填写，不读取微信个人资料」
改为「首页顶栏与左侧栏显示自定义头像/昵称/状态；无头像时用昵称首字占位，
不读取微信个人资料」，说明侧栏也会同步显示。

本轮验证：
- `javac` 全量编译 47 个源文件通过（JDK 21 / SDK 37，0 错误）。
- 全部既有回归通过（含侧栏生命周期、设置待生效、主题对比度等）。
- apktool 3.0.3 完整回编通过，APK v3 签名校验通过。
- 图标几何校验：命令参数个数、24 栅格边界、闭合段起点一致性全部通过。
- 未安装到设备、未重启微信；未提交 Git。

## 读取微信本人资料（2026-11-12）

### 62. 推翻「不读取微信个人资料」的承诺
上一轮顶栏与侧栏的资料只取自设置中手填的两个文本键与一个图片路径，
三项都为空时整块不显示。用户要求改为读取真实资料，因此本轮的隐私口径
**由「不读取」改为「读取，但可控且不外传」**。

新增 `java_src/wex/ui/core/feature/SelfProfile.java`：

| 字段 | 读取方式 | 回退 |
|---|---|---|
| wxid | `com.tencent.mm_preferences` 的 `login_weixin_username` | 无 |
| 昵称 | 「我」页个人卡片（资源 id `jlx`）内已渲染的文字 | 无 |
| 头像 | 个人卡片内 `ImageView` 的 `BitmapDrawable` | 微信头像缓存目录 `<data>/MicroMsg/avatar/<wxid>*` 取 mtime 最新 |
| 状态 | 个人卡片内「状态」行的文字 | 无 |

设计取舍：微信 8.0.76 的存储类经混淆，走 DexKit 定位 `ConfigStorage` 再反射
取字段需要额外 native 扫描且有版本漂移风险；而「我」页本身就会渲染这几个字段，
从**已渲染的 View** 取值不需要理解宿主内部结构，兼容性更好。代价是需要用户
至少打开过一次「我」页，值才会被捕获——这一点写进了空状态提示。

捕获入口：`smali/x4/f0.smali` 的「我」页个人卡片注入点，在原有
`MyPageBackground.apply` 之后追加一行 `SelfProfile.captureFrom(v5)`。
只读，不修改宿主任何控件。

### 63. 自定义值优先，真实资料补位
`ProfileView` 的取值顺序改为：设置里手填的头像/昵称/状态 **优先**，
留空的项才回落到 `SelfProfile`。这样「自定义」与「真实」两种用法可以共存：
想完全用自己的形象就三项都填，想显示真实资料就留空。

### 64. 新增开关 `top_use_real_profile`
设置「首页」页顶栏组下方新增「读取微信个人资料」，**默认开启**，
关闭后 `ProfileView` 不再调用 `SelfProfile`。
为此给 `SettingsShell.addSettingSwitch` 增加带默认值的重载
（原实现写死 `false`，与本开关默认值不符）。

### 65. 隐私条款同步改写
- `README.md` 隐私章节新增完整披露：读取哪些字段、从哪个存储/控件读、
  是否落盘、是否上传、关闭开关后的行为，并明确「昵称与状态文字不写入日志，
  日志只记录有/无」。
- 设置面板顶栏描述由「不读取微信个人资料」改为
  「留空的项自动使用微信本人资料」。
- 全仓库已无「不读取微信个人资料」表述。

**注意**：这是对既有对外承诺的实质性变更。若该项目已有用户，应视为
隐私政策变更并明确告知；本轮只改仓库内文档，未做任何对外公告。

本轮验证：
- `javac` 全量编译 48 个源文件通过（JDK 21 / SDK 37，0 错误）。
- 全部既有回归通过。
- apktool 3.0.3 完整回编通过，APK v3 签名校验通过。
- **未实机验证**：`jlx` 资源 id、个人卡片内文字/头像的实际层级、
  微信头像缓存目录结构，均基于既有代码与 WeKit 实现推断，
  需在设备上确认 `SelfProfile.summary()` 的日志输出。
- 未提交 Git。

## 实机反馈修正（2026-11-12）

### 66. 修复顶栏读不到昵称/状态：NoMeasuredTextView 不是 TextView
**现象**：装上后顶栏一直提示「请打开『我』页一次」，打开「我」页也无济于事。

**根因**：`SelfProfile` 的文本采集用的是 `view instanceof TextView`，
而微信昵称/微信号/状态用的是 `NoMeasuredTextView`，**该类不继承 TextView**。
项目自身的 `MyPageBackground` 早有注释记录过这一点（为它单独走反射设色），
本轮实现时没有沿用，导致卡片内所有文字被跳过。

**修复**：
- 新增 `textOf(View)`：先按 `TextView` 取，否则反射调用 `getText`。
- 新增 `textSizeOf(View)`：同样反射 `getTextSize`，用于在卡片内挑出字号最大的
  那行作为昵称（避免把微信号、状态、按钮文案误判为昵称）。
- `collectViews` 改为返回全部 View 而非仅 TextView。

### 67. 修复 LSPosed 推荐作用域为空
**根因**：CHANGES 第 32 条为解决 ClassNotFoundException 删除了整个
`unknown/META-INF/xposed/`，连带删掉了 `scope.list`。而 Manifest 里的
`xposedscope` 此前用的是字面量形式，部分 LSPosed 版本不识别。

**修复**：改用 LSPosed 文档推荐的形式，指向字符串数组资源：
```xml
<meta-data android:name="xposedscope" android:resource="@array/xposed_scope"/>
```
`res/values/arrays.xml` 中 `xposed_scope` = `com.tencent.mm`（该资源此前已存在，
只是 Manifest 没引用）。

**没有恢复 `META-INF/xposed/` 三件套**：查 LSPosed 文档确认，modern API 要求
入口类实现 `io.github.libxposed.api.XposedModule`，而本模块的 `HookEntry`
实现的是旧版 `IXposedHookLoadPackage`。这解释了第 32 条失败的真正原因
（不只是 classloader 问题）。因此继续走 legacy `assets/xposed_init`，
作用域通过 Manifest 声明。

### 68. 移除自定义资料，读取真实资料不再需要开关
按用户要求：
- 删除设置面板「顶栏」组下的 5 个自定义项：设置标题文字、设置昵称、
  设置状态文字、切换圆点颜色、更换顶栏头像（共 111 行 smali，`topBarGroup`
  的 `.locals 16` 与跳转标签经校验未受影响）。
- 删除上一轮新增的 `top_use_real_profile` 开关。开启「顶栏」即自动读取微信
  本人资料，不再有第二道开关。
- `ProfileView` 移除自定义分支与 `loadAvatar`（自定义图片路径），
  资料全部来自 `SelfProfile`。`top_avatar_path` / `top_nickname` / `top_status`
  三个旧键在运行期不再被读取（smali 里残留的 lambda 已无调用方，不影响运行）。

本轮验证：
- `javac` 全量编译 48 个源文件通过（JDK 21 / SDK 37，0 错误）。
- `topBarGroup` smali：`.locals 16`、最高 v15、无未定义跳转标签。
- APK 内 `META-INF/xposed/` 已确认不存在，`assets/xposed_init` 为
  `wex.ui.core.xposed.HookEntry`；Manifest 的 `xposedscope` 确认为
  `@0x7f010000`（= `array/xposed_scope`）。
- 全部既有回归通过；apktool 回编与 v3 签名校验通过。
- **仍未实机验证**：设备在安装前断开。`NoMeasuredTextView` 的取值、
  `jlx` 资源 id、头像缓存目录结构均需上机确认。

## 悬浮底栏增强（2026-11-12）

### 69. 新增 `FloatingTabBar`：真悬浮 / 滚动自动隐藏 / 胶囊表面
本项目的胶囊底栏（`BottomBarComposeFeature`）此前虽已隐藏原生底栏并自绘胶囊，
但胶囊是**普通子 View，会占据布局空间**，不是真正的悬浮层。

参照 WeKit `ReplaceNavigationBar.kt`（GPL-3.0）的悬浮思路，新增
`java_src/wex/ui/core/feature/FloatingTabBar.java`：

| 能力 | 做法 | WeKit 对应 |
|---|---|---|
| 真悬浮 | 底部居中对齐 + `elevation`/`translationZ` + 逐级 `setClipChildren(false)` | `attachBar(floating)` |
| 滚动自动隐藏 | 监听内容区 `AbsListView`/`RecyclerView` 的 `onScrollChange`，下滑收起、上滑恢复，阈值 12px、动画 220ms | `nav_bar_auto_hide_on_scroll` |
| 胶囊表面 | 半透明底 + 1px 描边，深浅色分别取色（`0xE6FFFFFF` / `0xE61C1F1D`） | `useBackdrop` / 圆角描边 |

**没有移植的部分**（与 WeKit 的差异，均为有意取舍）：
- 标签页增删与排序：WeKit 通过 hook `MainTabUI$TabsAdapter` 的 `getCount`/`getItem`/
  `onPageScrolled`/`onPageSelected`/`onTabClick`，并用 `ThreadLocal` 与优先级 100
  做索引重映射。本项目宿主版本为 8.0.76，这套重映射一旦与宿主内部状态不同步会导致
  白屏或闪退，且无法离线验证，故不做。
- 毛玻璃实时模糊：WeKit 用 Compose + Backdrop 库做实时采样；本项目改为静态半透明表面 +
  描边，视觉接近但不做逐帧采样，避免性能与兼容风险。
- 角标、滑动高亮插值、微信原生图标取色：依赖 WeKit 的 API 层与 Compose 动画，未移植。

### 70. 注入点与设置入口
- 注入：`BottomBarComposeFeature.apply` 在把胶囊栏 `addView` 之前调用
  `FloatingTabBar.attach(container, bar)`。`apply` 方法的 `.locals 14`、最高 v13
  经校验未受影响。
- 设置：新增 `BottomBarCard`（Java 卡片），挂在「首页」分组，
  三项开关 `bottom_bar_floating` / `bottom_bar_auto_hide` / `bottom_bar_blur`，
  默认全开，写入走 `SettingsChanges.saveBool` 并回读校验。
  说明文案已注明「需重启微信生效」，因为胶囊栏在 `onCreate` 阶段注入。

本轮验证：
- `javac` 全量编译 50 个源文件通过（JDK 21 / SDK 37，0 错误）。
- `BottomBarComposeFeature.apply` 寄存器边界检查通过。
- 全部既有回归通过；apktool 回编、v3 签名校验通过。
- APK 内 `FloatingTabBar` / `BottomBarCard` / 三个配置键均已确认存在。
- **未实机验证**：设备未连接。悬浮层级（`z` 序与宿主流式布局的交互）、
  滚动监听的命中率、动画时长手感均需上机确认。

## 气泡模糊实时刷新优化（2026-11-12）

### 71. 问题定位：滚动时固定约 240ms 的滞后
气泡毛玻璃本身能实时模糊，但滚动时刷新明显滞后。逐层拆解后是四处叠加的延迟，
且都在「刷新触发链」而非模糊算法本身：

| 位置 | 原实现 | 影响 |
|---|---|---|
| `onPreDraw` | `now - lastQueued >= 120L` 才排队 | 每秒最多 8 次 |
| `deliver` | 结果过期时 `queue(120L)` 重排 | 滚动中几乎必然命中，再加 120ms |
| 背景校验 | `postDelayed(..., 250 - elapsed)` | 静止判定也要等满 250ms |
| 线程池 | `1` 线程 + 每次 `WORK.purge()` | 取消/重排反复互相打断，有效吞吐低 |

第 2 条是关键：异步模糊算完时气泡早已移动，于是**每次都走「过期→再等 120ms」**，
实际周期约 240ms。

### 72. 改法
1. **位置变化立即重排**：`onPreDraw` 去掉 120ms 时间窗，改由 `queued` 标志做
   「同一帧内合并」；新增 `dirty` 标记，`deliver` 发现过期只置脏，下一帧
   `onPreDraw` 立刻接手，不再固定等待。
2. **静止后才校验背景**：新增 `IDLE_AFTER_MS = 300`，滚动期间
   （`now - lastActivity < 300`）完全跳过背景取样校验——滚动不会改变背景，
   检查是纯浪费。静止后才用 `postDelayed(..., 0)` 立即校验。
3. **线程池**：`1` → `2` 线程，队列 48 → 16，`AbortPolicy` → `DiscardOldestPolicy`
   （滚动时旧帧本就无意义，丢旧比抛异常更合适），线程优先级略降，
   核心线程 10s 超时回收。移除 `queue`/`stop` 里的 `WORK.purge()`——
   它会清掉**其它气泡**已排队的任务，滚动时造成饥饿。
4. **采样尺寸重试**：尺寸未就绪的等待由 120ms 改为 16ms（约一帧）。

### 73. 新增整屏背景缓存（`BubbleGlass.Sheets`）
仅靠上面四条还不够：`capture` 每次都重新 `newDrawable + 绘制 + getPixels`，
开销随气泡数量线性增长，滚动时重算极易被下一帧打断，等于白做。

改为把源背景（壁纸/背景 Drawable）整体按固定比例渲染成像素缓存
（最长边 ≤ 768px），滚动时只做整数倍裁切：

- 缓存键 = Drawable 身份 + 源尺寸 + alpha/level/layoutDirection + 位图 generation
  + state 哈希。任一项变化即失效，保证不会用旧背景算出「冻结」的模糊。
- **多槽 LRU**（3 槽，合计 ≤ 2 MiB）：不同气泡可能挂在不同背景上，
  单槽会互相顶掉反而更慢。
- 仅在「目标采样比例不细于缓存比例」时才走裁切，否则回退完整重绘，
  避免放大插值导致的糊化。

坐标换算经独立脚本验证：与完整路径的最大偏差 1.25 源像素，
小于一个缓存像素（1.41），属量化误差。LRU 与容量淘汰逻辑同样单独验证通过。

本轮验证：
- `javac` 全量编译 50 个源文件通过（JDK 21 / SDK 37，0 错误）。
- 全部既有回归通过（含 64 项气泡设置检查）。
- apktool 回编、v3 签名校验通过。
- **未实机验证**：设备未连接。实际滚动流畅度、sheet 缓存命中率、
  高 DPI 设备下的裁切清晰度需上机确认。
## 2026-11-13 桌面状态主页

- 恢复 MainActivity 的 MAIN/LAUNCHER 入口，沿用现有 MindLink 图标。
- 新增 HyperOS 风格状态与诊断主页；不在独立进程重复加载微信功能设置。
- 页面设计参考 HyperDuo 与 HyperChanger 的通用信息架构，保留 MindLink 自有绿色主题和资产。
