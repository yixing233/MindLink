# 一级页标题 / 通讯录发现延伸 / 首页横线 / 设置页过渡 / 聊天标题上限（2026-11-23 续）

## 再追加：「我」页顶部空白（14:30 实机 dump + 日志，已装 142330 构建）

- 长标题问题用户确认已解决。
- 「我」页 dump：`android:id/list` 从 0 开始，但第一项（个人资料卡 gxv）从 278 开始 = 顶栏底；
  原生顶栏容器 ei（134..278）在「我」页仍是显示状态，只是没有标题、搜索、「+」，
  上一版「顶栏隐藏才还原」的条件不成立，扫描把当前页（「我」）的列表当成被顶栏覆盖的列表加了 278px。
- 修改：顶栏卡片为空（无标题/资料、无搜索和「+」）视为无顶栏页：不扫描、不加内边距，
  并还原该页已加的内边距；此时也不画渐进模糊带。`sync()` 改为先布局卡片再判断。
- 顺带：下拉页顶栏副本日志显示每 0.4s 被「还原再隐藏」一次（列表滚动时头部脱离/重挂），
  可能造成闪现；改为见到即隐藏、只在关闭叠加时统一还原，日志限 2 条。
- 候选 `build/full-build-20261123-143427-mepage2/MindLink-1.0.apk`，
  SHA-256 `54a3cde3fcf6e38da62d5d639e9e0926261fb7d73c20fed06552e3ef5cdf926b`，未安装（ADB 已断开）。

## 再追加：长标题时好时坏（14:20 实机实时日志，已装 125754 构建）

- 9 次打开聊天的 `title tree` 都是同一模式：打开后约 100ms 标题内容区达到胶囊宽（678），
  约 400ms 后又缩回 342（= 678 − 2×168），之后不再变化；日志读到的宿主 pad 始终为 0。
- 结论：微信在布局时重新写入标题宿主左右各 168px 内边距；上一版在绘制前清零，宿主下一次
  测量又写回，二者竞争，多数时候以宿主的内边距测量收尾，偶尔赶上清零那一帧才完整。
- 修改：不再清内边距，改为把宿主宽度设为「胶囊内宽 + 当前左右内边距」，内容区恰好等于胶囊；
  左右不等时按差值平移保持居中。上一版清掉的内边距会还给宿主。
- 专项 89 项通过。候选 `build/full-build-20261123-142330-titlehost/MindLink-1.0.apk`，
  SHA-256 `b73a5f4bc0b426f3e3e8ae77ce2e85ae269e95bbeb5b0fa01e20f1d375980bc8`，未安装。

## 再追加：「我」页顶部空白（12:51 反馈）

- 推断原因（未实机确认）：分页容器把相邻页面并排布置，「我」页紧挨「发现」页一直处于已显示状态；
  内容延伸扫描只看纵向位置，把从窗口顶开始、本就没有顶栏的「我」页滚动容器也加了顶栏高的内边距。
- 修改：扫描只处理水平中心在屏幕内的当前页；顶栏隐藏（「我」页）时，还原屏幕上那页已加的
  内边距和被移除的容器内边距，回到有顶栏的页面后立即重扫。
- 候选 `build/full-build-20261123-125327-mepage/MindLink-1.0.apk`，
  SHA-256 `bda5a0c16a4b8cae700b3bbb9ed0e8b463e82aeced2cf9ab7a23881732ebfdb3`，未安装。

## 再追加（10:13 实机日志，已装包 = progressive 候选）

- 长群名：`title tree pill=678 |0:LinearLayout w=678 |1:LinearLayout w=342 … TextView w=282 max=342 need=784`。
  胶囊已有 678 px，但标题宿主左右各 168 px 内边距（微信按原生两侧按钮宽度预留），
  文字只得到中间 342 px。分体期间清除宿主左右内边距，退出恢复；诊断行增加 `pad=`。
  该群名完整需要 784 px，按新宽度约能显示到 618 px，仍会省略结尾。
- 「微信」大标题：UI dump 中原生顶栏的 text1 已不在（隐藏生效）；前一天的 dump 显示会话列表
  首个头部（下拉「最近」页）带一份顶栏副本（actionbar_up_indicator / text1「微信」/ 搜索 / +），
  位置与原生顶栏重合，透明顶栏后透出。叠加模式下将该副本 alpha 置 0，释放时恢复。未实机确认。
- 渐进模糊高度系数 1.4926 → 1.18（顶栏底 278 px 时 415 → 328 px）。
- 聊天顶栏专项 88 项及其余相关套件通过。候选 `build/full-build-20261123-101751-titlepad/MindLink-1.0.apk`，
  SHA-256 `4c70de928dd7abf98f1f8f52c1e74dc68369c29f915548aa703e52f580b0bf2a`，未安装。

## 追加：一级页顶部渐进模糊

- 新增 `ProgressiveTopBlur`，按 HyperChanger `ProgressiveBlurLayer`（Apache-2.0）移植：
  背景整体 10dp 模糊 → AGSL 透明度遮罩 `smoothstep(h, h*0.5, y)`（上半全模糊、下半渐清）
  → 表面色渐变着色（0 / 0.72 / 1 处 alpha 0.92 / 0.18 / 0）。范围从窗口顶到顶栏底 ×1.4926。
- 由首页 Toolbar 背景 `Cards` 在卡片之前绘制（祖先不裁剪，可画到状态栏区域）；
  复用卡片玻璃的同一个采样器（`ChatGlassSurface.sharedSampler()`），页面每次变化只录制一次；
  卡片为纯色材质时用独立采样器。下拉时随顶栏位移淡出。无可用背景或无 AGSL 时只画渐变着色。
- 设置：悬浮卡片 → 「顶部渐进模糊」`top_cards_progressive_blur`，默认开启，需「内容延伸到顶栏下方」。
- 专项与构建通过；候选 `build/full-build-20261123-012330-progressive/MindLink-1.0.apk`，
  SHA-256 `3b2d27290ef8e87fc14cdc36da62adc4cfdebf657cf9b5de07e9f08c7d7faf57`，未安装。

## 修改

- 一级页标题：`HomeTopBarCards.syncTitle` 在「隐藏页面标题」开启时隐藏所有一级页标题
  （微信含未读数、通讯录、发现），不再只认「微信」。设置文案同步改为「隐藏页面标题」。
- 通讯录 / 发现延伸到状态栏下：实机日志显示页面链为
  `CustomViewPager(top=0) > FrostedContentView(top=0) > MMWeUIBounceView(top=278) > … > WxRecyclerView(top=278)`，
  页面被 `FrostedContentView` 的顶部内边距（状态栏 + 顶栏）压在顶栏下方，列表从未进入顶栏覆盖区。
  叠加模式下去掉该内边距（不含会话列表所在页），列表随后按原逻辑拿顶栏高作 paddingTop、
  clipToPadding=false。宿主重写内边距时记录最新值，关闭时恢复。
- 首页横线：宿主 `ConversationListView.dispatchDraw` 在 scrollOffset 处画分割线，
  画笔是该类唯一有描边宽度的 final Paint；叠加模式下把它的 alpha 置 0，释放时恢复。
- 设置页过渡：新增 `SettingsTransition`。所有入口都经 `SettingsPanelHelper.show → SettingsShell.build`，
  在此关闭 Dialog 自带窗口动画、窗口半透明，面板从右侧滑入（320ms），到位后恢复不透明窗口背景；
  系统返回在总览页时向右滑出（260ms）再走原关闭逻辑。
- 聊天标题：
  - 标题宿主内宽度固定且小于胶囊的容器/文字控件改为 wrap_content（图标不动），退出恢复；
  - `NoMeasuredTextView` 的存储文本若比原文短、而原文放得下，或裁得比可用宽度少两个字以上，
    就按当前宽度重裁（每个文本/宽度最多 3 次）；布局持续请求时最多等 2 帧，不再被饿死；
  - 新增 `WEX: 聊天顶栏: title tree …` 诊断（最多 8 条，只记宽度与长度，不记内容）。

## 验证

- `chat-header-toolbar-checks.py` 86 项通过（新增固定宽度解除/图标保持/恢复）。
- settings-systembar、backdrop-recovery、database-capture、分组全部用例、scoped-build 通过。
- 完整回归仍停在既有的 `chat-footer-checks.py pre-fix-observe`（缺 `adopt`，与本次无关）。
- 候选：`build/full-build-20261123-010831-pages/MindLink-1.0.apk`，
  SHA-256 `1ce34b43ed46b1cad6e4f3e7b0863f6fcef943977e56078d3ac6ba95afb54ca4`，未安装（ADB 设备已断开）。

## 实机待验

三个一级页无标题；通讯录、发现列表可滑到状态栏下；首页顶栏下无横线；
加号菜单、侧栏快捷项打开设置均从右滑入，返回向右滑出；长标题群聊显示到胶囊边缘。
若标题仍被截断，请提供 LSPosed 日志中的 `title tree` 行。
