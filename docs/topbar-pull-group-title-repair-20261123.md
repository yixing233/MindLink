# 首页下拉 / 切组下移 / 状态栏实色 / 聊天标题宽度（2026-11-23）

## 现象

1. 切换会话分组时，列表莫名下拉一段距离。
2. 列表到顶后继续下拉，首页顶栏悬浮卡片消失、玻璃模糊丢失，需要等待或向下滑动才恢复。
3. 列表内容不延伸到系统状态栏下方，状态栏是一块实色。
4. 分体式聊天顶栏中间标题仍不能尽量显示完整。

## 根因（宿主 8.0.76 `ConversationListView` 反编译对照）

- 会话列表本身铺满整页，首行靠宿主 scrollOffset（状态栏 + 顶栏高）停在顶栏下方：
  `setSelection(0)` → `setSelectionFromTop(getFirstHeaderVisible(), scrollOffset)`，
  `onLayout` / `checkEmptyFooter` / 关闭下拉页都走 `setSelection`。
  `onScroll` 在首位可见时按 `child(1).top - scrollOffset` 平移顶栏，并把状态栏遮罩 alpha 置 0。
- `HomeTopBarCards` 的「内容延伸到顶栏下」对会话列表也加了 paddingTop = 顶栏底。
  `ListView.setSelectionFromTop` 的 y 叠加在 padding 之上，于是每次宿主复位（切组、
  收起下拉页、布局变化）都低一个顶栏高，宿主随即把顶栏也平移同样距离，
  遮罩保持透明、下拉页判定半开 —— 对应现象 1、2；向下滑动触发 `resetActionBarView` 才复原。
- HomeUI 会反复把窗口状态栏刷成不透明色（切页、下拉、顶栏变色），列表在其下方被截断 —— 现象 3。
- 分体标题胶囊按左右较大的预留对称居中，长标题在较窄一侧仍空出一段 —— 现象 4。

## 修改

- `HomeTopBarCards`：会话列表不再加 padding、也不下探（旧包残留的 padding 会还原），
  其他页面的滚动容器保持原逻辑。叠加模式下首页显示期间维持窗口状态栏透明，
  并清除只覆盖状态栏条带的全宽遮罩背景；会话内（LauncherUI 中的聊天）不接管。
  关闭叠加/释放时恢复原状态栏色与遮罩背景。顶栏停止移动或重新显示时立即重采样玻璃，
  320ms 后再补一次（覆盖下拉页的延迟隐藏）。
- `ChatGlassSurface.refresh()`：作为一次宿主内容修订，重置采样预算并重绘。
- `ConversationGroupTabsHost` / `ConversationGroupCard`：分组卡片从隐藏恢复可见时重采样。
- `FloatingHeaderStyle`：标题能放进居中胶囊时保持对称；放不下时（按居中宽度判定，
  不会来回切换）胶囊从左圆延伸到右圆，标题宿主平移到胶囊中心，退出时还原宽度、位移和限宽。

## 验证

- `chat-header-toolbar-checks.py`：82 项通过，新增长标题加宽、居中平移、短标题回到对称、
  还原原生宽度/位移/限宽。测试替身补了 `TextView.getPaint()` 与 `View.requestLayout()`。
- `regression.py` 分组全部用例、settings/systembar、backdrop-recovery、database-capture、
  resolver、text-status、profile-config、scoped-build 通过。
- 既有未通过（与本次改动文件无关，来自同晚其他未提交改动）：`chat-footer-checks.py` 的
  `pre-fix-observe` 变体缺 `FloatingChatFooterFeature.adopt`；`side_panel_checks.py` 的 View 替身
  `getTop()` 重复定义。
- 候选：`build/full-build-20261123-002751-topbarpull/MindLink-1.0.apk`，
  SHA-256 `0a36cb6617b6b034da97368d363748abd277dfc672713c3e1b3a93cd935c4d60`，未安装。

## 实机待验

切组无下移；到顶下拉再松手，卡片与模糊即时恢复；状态栏下可见列表内容；长群名标题加宽显示。
