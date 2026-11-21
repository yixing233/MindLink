# 待办修复第一轮（2026-11-15）

## 范围与验收边界

用户授权先处理 `docs/TODO.md` 中 7 项修复。本轮不实现推荐作用域或左滑侧栏，不更改面板展开方向；此前手势条白底透明尝试实机未达效，本轮不宣称该问题已解决。

以下为源码修复、真实生产方法的本地合同验证及真实 Android API 编译结果，不代表 ART/Xposed、系统输入法、Skia/HWUI 或微信实机显示验收。所有待办仍未勾选。

设备查询仅执行 `adb devices -l` 与一次旧端点连接检查：无设备，`192.168.1.148:37769` 拒绝连接。没有安装、重启、触摸、截图或读取手机用户配置/图片/聊天。本地完整候选快照 SHA-256 `719cef2daea450b5076dc441c5f310b0e94f3bf3d78ac9734059d14e1ad2331b` 用于核对生产依赖，不假定它仍是手机当前包。

## 修复内容

| 待办 | 源码修正 | 证据与边界 |
| --- | --- | --- |
| 底栏玻璃跳动 | `NavigationBarViews` 不让空闲整页旧采样覆盖已确认的动画目标；延后按原生 pager 对账，语义选择未变也可纠正视觉目标；保留触摸、分页跟随与真实玻璃绘制 | 真实方法先 RED，再 GREEN；19 条新增导航断言。尚无本轮实机帧序列，不能认定为用户跳动的唯一根因 |
| 输入栏偶发无法唤起输入法 | `FloatingChatFooterFeature` 不再将原生进入键盘状态请求替换为 mode0 关面板；保留 mode1、原生请求及异常，仅保留诊断 | 本地真实宿主单类资料证实该入口还用于文本模式与输入焦点相关状态转换。专项先 RED；两种面板设置、表情/更多→输入、既有 IME、不吞异常通过。普通文本点击的具体监听调用链未完整确认 |
| 自定义 .9 气泡 | `BubbleNinePatch` 严格区分编译后 chunk 与原始 1px 标记框，按有效 metadata 使用原尺寸/分段/padding；`BubbleApply` 只接管九宫格 padding，并恢复未被宿主覆盖的值 | 原选图器只复制字节且改名 `.png`，不靠文件名识别。合成图片 107 断言与旧四项通过；SDK aapt2 合成图验证合法全区域拉伸 cell 格式。九宫格解码预算 1,048,576 像素，普通图仍原采样；超预算九宫格拒绝，不删除/改写原图 |
| 点击会话首页下移 | `ConversationGroupTabsHost` 临时 detach 不拆分组 header；终态、显式 release、关闭分组仍释放 | 真实 Host 生命周期先 RED 后 GREEN；避免返回重复挂载的明确源码缺陷，待实机确认视觉效果 |
| 公众号分组闪烁 | `ConversationGroupBar` 在 ID/顺序未变时原位更新标签；去掉点击额外 rebuild 与 Host 无条件 requestLayout | 真实公众号标签点击先 RED 后 GREEN；筛选映射、等宽与配置更新通过，待实机确认闪烁消失 |
| 功能设置页状态栏明暗 | `SettingsShell` 在自有 Dialog 实际 attach/focus 后合并重申状态栏明暗；稳定状态不重复写入 | 确认微信内实际入口为 SettingsPanelHelper→SettingsShell，而非桌面 Compose 页面。完整生产 SettingsShell+SettingsTheme 浅深与生命周期专项通过 |
| 返回首页状态栏失效 | `SystemBarsCoordinator` 首页状态栏独立所有权，避开可见聊天/TOP 接管；具名聊天布局可见性变化与 detach 触发合并恢复 | resume/focus 与同 Activity 隐藏/移除均先 RED 后 GREEN；具名目标 global-layout 只检查布尔变化，不逐帧扫描整树。任意宿主返回路径仍需实机验证 |

`ConversationGroupingFeature`、`ConversationListBridge` 没有修改，保留用户已确认切组不再闪退的同步切换、重入序号、快照与反射缓存修复；该确认不等同全部滑动流畅度验收。

## 第一版统一验证（pre-review，后续状态栏修正前）

以下成功结果属于第一版冻结源码。最终复核在该版发现两处 SystemBars 通知覆盖缺口：首次 resume 前已创建的隐藏聊天布局未被观察，以及 alpha/可见区域变化与监听判定不一致。已补真实生产方法 RED→GREEN，具体包括隐藏自身、隐藏祖先、alpha 变化三条路径。旧结果保留不覆盖，不能作为后续修改的最终验证。

## 复核补充

`build/todo-systembars-20261115/review-blockers/` 保存新 RED/GREEN 与冻结哈希。结构发现保持 192 节点预算，可经过隐藏节点；仅登记具名 ChattingUILayout，不下钻消息或输入控件。首页与目标监听共用 32 层祖先 visibility/alpha＋可见区域判定，稳定 layout 不重复排队或写窗口。缺 getter 场景和旧所有权专项保留。

SystemBars 最终 SHA-256 为 `801befcfcc9e98497b85357e718afa85f1d4f10836807895cb48823d849c029b`。定点只读复核确认两处缺口已修复。

## 最终当前源统一验证

- `build/todo-repairs-20261115/full-regression-final-002/result.json`：`passed=true`、`exit=0`，4267 文件测试前后无变化；完整日志在同目录 `regression.log`。新增状态栏 review25 断言连同旧 nav30/home49、缺 getter，以及其余既有专项通过。
- `build/todo-repairs-20261115/production-final-002/result.json`：9 个明确生产族真实 Android37 编译退出 0，91 个目标类、无非目标输出。编译 warning 与第一版相同，不宣称无警告。
- 最终冻结源、完整依赖与编译类 hash 在 `production-final-002/`；9 源的最终编译、回归和 live hash逐文件一致。与第一版比较，仅 SystemBarsCoordinator 改变，其他 8 源保持；已有分组防闪退两族保持原 hash。
- 本轮没有新的 APK、signed-APK gate 或设备验收结果。

- `build/todo-repairs-20261115/full-regression-001/result.json`：`passed=true`、`exit=0`，4267 个文件测试前后无变化。新增真实分组 Host/Bar、图片气泡、设置 Dialog 系统栏专项已接入既有完整 runner；旧功能回归保留。
- `build/todo-repairs-20261115/full-regression-001/regression.log`：完整输出。包含旧缺陷重放的预期 RED（用于确认负例），不属于当前代码失败。本轮外层输出使用 UTF-8，外层同样退出 0。
- `build/todo-repairs-20261115/production-final-001/result.json`：9 个明确生产族、真实 Android37、source/target11 编译退出 0，仅输出这些族及内部类，共 91 类。编译有 source11 模块路径 warning 和弃用 API 提示，未作为无警告构建报告。
- 冻结源及哈希在 `production-final-001/frozen-src/`、`source-hashes.json`；依赖与目标输出在 `compile-inputs.json`、`compiled-class-hashes.json`。
- 编译使用 Android jar、已核对的 nav 最新 35 类目录、含既有分组修复的冻结 classes.jar 与冻结 Xposed ABI；空 sourcepath、`-implicit:none -proc:none`，未拉入整仓未完成源码或测试桩作为生产依赖。

### 各专项证据

- 底栏：`build/todo-nav-motion-20261115/01-red.log`、`03-final-green.log`。
- 输入法：`build/todo-footer-ime-20261115/05-baseline-red.log` 重放精确旧候选，`04-suite.log` 为最终完整专项通过（present344、missing101）。`02-green.log` 名称虽为 green，实际是测试双未派发嵌套 hook 时的失败日志；随后修正测试边界，`03-green.log` 才通过，不引用失败文件作成功证据。
- 分组：`build/grouping-todo-repair-20261115/final-grouping-12-cases.log`，Positions300 随机＋12 专项连续3轮（36次）全通过。
- 气泡：`build/bubble-ninepatch-20261115/green-final-001/result.json`，真实 API 窄编译/D8及已知非执行 LambdaMethod 元数据记录在 `production-compile-001/`；并非 APK gate。
- 状态栏：`build/todo-systembars-20261115/` 下 home、return、settings 的 RED/GREEN 与冻结哈希。

## 候选制作与确认安装

用户随后提供 `192.168.1.148:37857` 并要求安装。连接成功，构建前与安装前手机基线均核对为 `719cef2d…`。基于该完整包制作九族窄修复候选：

- APK：`build/runtime-evidence-repair-20261115/todo-candidate-20261115-184558-d7cf212f/candidate-001/MindLink-1.0-bottom-bar-repair.apk`。
- SHA-256：`f91bd8b3cc8362c3714de8cecc1cf8177dd17562745e63b6bd102849e6e75382`。
- 大小 26,127,821 bytes，相对基线增量 0 bytes。
- 主 DEX 保持 `3ac56670…`；feature DEX 为 `615d410df0a5ac5b2da99e4386e4da7756e6577b69d4b73dfe6b058114741d6c`。
- 既有工具单次冻结九族编译与 final002 的 91 类逐字节一致；一次实际签包门禁通过，原证书/v3/zipalign、混合 DEX ABI、非目标类及原压缩流、资源、桌面/侧栏 payload 保留。仅 classes2.dex 与 runtime inventory 改变。证据见该候选目录 `final-gate-001/verification.json`、`size-gate.json`。

告知具体候选身份后用户选择“覆盖安装”。安装命令在执行前程序化比较候选哈希和手机基线；任一不符即停止。`adb install -r` 返回 Success，安装后 APK SHA-256 与授权候选完全一致，路径为 `/data/app/~~FOLEJFBdnr5vlclPKwlufg==/dev.mindlink-pC-hIEJhoYcB8xJS2qkMRQ==/base.apk`。

## 新包实机反馈与继续修复

安装后用户报告：四个一级页面（消息列表、通讯录、发现、我）状态栏文本/图标反色均失效；从会话返回列表丢失垂直滚动位置并回到顶部；微信→发现的指示器在中间通讯录处停顿抽搐。三项均按实机验收失败处理，本地测试不能抵消。

同次只读核对：手机 APK 仍为 `f91bd8b3…`，当前微信 PID25927 的 maps 映射该安装 APK，焦点为 LauncherUI；系统显示 `LIGHT_STATUS_BARS` 与 `LIGHT_NAVIGATION_BARS`。这仅确认暗色图标请求，不证明与实际背景匹配，也不能证明每个模块钩子已执行。既有日志未提供这三条用户操作的完整时序，不能把缺少日志当作未执行或把静态猜测当现场根因。

继续修复限定这三条失败，保留其它功能；不自动重启、安装、截图、触摸、读取用户配置或消息。先核对真实宿主静态路径与生产方法专项，必要诊断仅结构/布尔/数值，无正文。
