# 会话分组高频回调卡顿修复（2026-11-15）

## 实际依据与验收边界

用户在安装0f8a完整候选后仍报告微信超级卡顿，明确要求继续修复。当前手机base.apk核对为`0f8a6d569403ae935f207cd0154ae35247cb687db161b1c2cf71fccd1d81edf9`。现有11:34 PID29296及11:35 PID30391两份微信ANR均指出LauncherUI触摸事件等待5000ms，主线程位于ConversationGroupingFeature字段反射异常、未读判断、ConversationPositions全表构建、ConversationListBridge.snapshot/getCount回调。证据见`build/runtime-evidence-repair-20261115/lag-evidence-after-0f8a-001.md`。

当前PID31940采样时是后台系统冻结，CPU0不代表不卡或死锁。历史ANR进程与当前进程严格分开；历史进程独立包hash未记录。两份ANR与当前完整候选的相同分组代码一致，支持定点修复这条热点，不宣称已证明所有卡顿来源。

## 定点范围

仅两个生产Java族及其内部类：

- ConversationListBridge：把每次getCount强制整表rebuild改成按adapter数据代际、分组代际、原始count复用不可变映射；宿主changed/invalidated通知前同步失效，数量不变的未读变化/排序也不漏。所有accessor共用同映射，首次新代际同步建立。点击保留行identity防错，不靠关闭分组功能解决。
- ConversationGroupingFeature：字段metadata按实际Class/字段名正负缓存，别名/继承查找优先级保持。每次读取当前实例真实值，不能缓存未读最终值或Map键不存在结论。

不改ConversationPositions稳定顺序算法、SnapshotReader backing-list读取方式、用户分组配置或消息正文；保留最新资料桥、primary、桌面/原侧栏、输入区、通讯录、底栏、顶栏及气泡。不把分组栏描述成已新增未读角标。

## 验证要求

原完整回归未执行ConversationPositionsChecks，且未覆盖真实getCount高频重建。此次需把真实生产Hook的计数专项接入既有runner，先复现旧代码RED，再改：稳定代际多次count/item/type只建立一次，缺字段反射异常有界；同count未读清零/新增、排序、替换、invalidated、切组/配置变化正确；headers与点击identity、非目标adapter和通知Hook失败默认拒绝保持。

不新写Python设备编排框架，不为报告重复全套。所有测试仍是JVM模拟，不代替ART/Xposed/微信实机性能验收。

## 当前状态

用户此前中断后停止未执行步骤；2026-11-15 最新明确要求修改代码完成修复，本轮已保留缓存修改并补充切组同步修复。手机仍是最后核对的0f8a，不能将本地源码当成手机已加载修复。未制作或安装新APK。

### 切组闪退补充（12:15现场）

只读crash-buffer确认PID22847主线程`IndexOutOfBoundsException: Index 12 out of bounds for length 1`，位于`HeaderViewListAdapter.getView → ListView/FlingRunnable`。原adapter的getView越界保护到不了wrapper自己的footer分支。旧select先发布active/generation，再经refreshViews、ListBridge.refresh两次post通知，留下旧fling按旧位置读取新count的窗口。

本轮仅两族生产代码：

- `ConversationListBridge.changeGroup`在MAIN停止旧fling（`smoothScrollBy(0,0)`），再发布分组，立即dirty/notify并selection=0；常规`refresh`仍保持异步，避免bind/click回调内递归通知。
- `ConversationGroupingFeature`把enabled/active/members/generation一起通过该入口提交；相同规则及成员不变时不改代际、不打断滚动。SQL请求序号与可见映射代际分离，等待/失败保留当前列表，过期成功或失败均不能覆盖新选择。关闭SQL过滤不再等待查询。

新增真实生产Hook测试`transition`重现同样Index12/length1（修复前RED），覆盖wrapper header/footer、惯性滑动、缩小/扩大/空分组、stop-before-publish、同步notify/selection及保存失败；`selection`覆盖同组reload、SQL延迟/过期/失败和启停。另补`reentry`：endFling同步scroll-state回调内的新选择必须胜过外层旧请求，嵌套提交排队且真正publish前再次核验请求序号，废弃提交不发送notify。两项时序缺陷均先RED复现再GREEN验证。既有缓存/字段专项保持。

最终验证证据在`build/grouping-switch-repair-20261115-68c5b164/`：

- `grouping-final-10-cases.log`：Positions 300随机映射及10个真实Hook专项连续3轮全部PASS（30次case）。
- `final-regression/result.json`与`final-regression/regression.log`：最终源码的完整本地回归退出0、passed=true，4252个源文件在测试期间无变化。外层脚本完成结果保存后向终端打印时出现GBK UnicodeEncodeError，外层命令退出1；业务测试和源码完整性验证本身已通过，不重跑测试或改变业务源码来掩盖输出错误。
- `production-final/compile.log`及`production-final/classes`：最终两个生产族用真实Android37 jar及冻结宿主/Xposed依赖按source/target11编译成功，未引入测试替身。仅输出两个族及内部类。
- 最终生产SHA256：Grouping=`9c6e7cf01a68c072ae9e2d4a0968e72d013ed98a8c368c52b847c8a6aa337c10`，ListBridge=`bd7cfd898e8e694416fb90f514903379ff29dc8a42fc3959095cd81efdd8645f`。
- 只读代码复核发现的endFling重入问题已修复；最终未发现剩余证据明确的blocking findings。JVM替身不能证明完整Android ListView生命周期已验收。

这些是本地JVM与Android API编译检查，不代替ART/Xposed/实机分组滑动验收。本轮未触碰手机配置/聊天数据库、未清数据、未重启微信、未安装或发布、未commit/push。

## 手势条白底透明（2026-11-15 下午，独立于分组修复）

用户报告：面板收起后悬浮输入卡与系统手势条后面的白色背景融为一体。实机证据：无障碍树存在DecorView `navigationBarBackground` [0,2610][1200,2670]（窗口2670）；模块几何日志显示ChattingUILayout bottom=2610——内容整树未延伸到手势条后面，白条即导航色#F7F7F7（SystemBarsCoordinator未透明路径的surface色），与卡片同色。旧`canExposeBottom`要求内容已覆盖、祖先无裁剪等七条件，真机聊天页因内容未延伸而永远false。

本轮改动（仅三个生产族）：

- `ChatWindowInsetsCoordinator`：`canExposeBottom`替换为`shouldExposeBottom`（接管+手势导航+无键盘即透明，不做覆盖/裁剪检查；未覆盖时露出的是窗口背景而非页面色，属用户明确要求）。新增`reassertBottom`：宿主可能在两次apply之间恢复fit状态，每次apply重新断言`setDecorFitsSystemWindows(false)`。
- `SystemBarsCoordinator`：透明判定改用新入口；三键导航与键盘打开仍保持原色与对比度。
- `FloatingChatFooterFeature`：新增`View.onApplyWindowInsets`钩子，接管窗口内手势导航无键盘时把分发给宿主消费者的`WindowInsets`剥掉导航条分量（`WindowInsets.Builder`副本，不改调用方对象），使任何祖先消费插页的微信视图都让内容延伸到手势条后面；edge块每次apply调用`reassertBottom`。

测试：chat-footer新增`navStrip`专项（接管时消费方收到nav=0、幂等、不改调用方对象、IME/三键/释放保持原生；present 330断言、missing 101断言全绿）；SystemBarsChecks按新规则更新（未覆盖条带改为透明、reassert/release往返；30断言全绿）。三文件按真实Android37依赖编译通过。证据目录`build/nav-transparent-20261115/`。未构建签包候选，未安装；安装另行确认。

候选已制作并安装（2026-11-15 13:22）：基于手机已装分组修复包6fb92976作为新基线的窄构建`build/nav-transparent-20261115/prepare_nav_candidate.ps1`（钉住分组候选产物哈希，families=三族，一次签包门禁全过：非目标smali字节不变、primary 3ac56670不变、体积预算内）。候选APK `build/runtime-evidence-repair-20261115/nav-candidate-20261115-132122-b4ad2c2a/candidate-001/MindLink-1.0-bottom-bar-repair.apk`，SHA-256 `719cef2daea450b5076dc441c5f310b0e94f3bf3d78ac9734059d14e1ad2331b`。安装前核对手机包为6fb92976、安装后指纹与候选一致（13:21:59），微信重启28450→28825且新进程映射新APK路径。实机透明效果待用户验收。

构建工具`build/runtime-evidence-repair-20261115/lag-build-tools/prepare_grouping_candidate.ps1`沿用已验证签包链，预检已通过；基线固定0f8a、feature d7de06ed...、primary3ac566...。最终仅classes2.dex与runtime inventory可变，primary及非目标原压缩流保持。等待代码与专项完成再集中回归和实际signedAPK一次门禁。具体新候选安装须重新确认，不自动重启/清数据/降级/截图/触摸，不提交或公开。
