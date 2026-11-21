# 四项实机反馈修复记录（2026-11-15）

## 实机反馈与基线

用户提供的截图及描述确认以下四项未通过，安装/编译成功不是验收：

1. 表情/加号面板展开、收起后输入区底角越出屏幕，底部背景有平直断层。
2. 通讯录原栏占位使滚动内容不能经过悬浮底栏下面。
3. 头像、昵称、微信状态仍显示默认资料/加载中。
4. 更多面板高度异常，手势导航白底条遮挡阴影。

目前安装的完整基线为：
`9c52a3f4dd2cde21d363b83ba568ecc5c042505f1b7740acb86521c2a7548714`，
26,078,669 bytes。本会话安装后用设备 APK SHA-256 核对成功，未自动重启微信。
随后只读发现 main PID 17821，现有新资料 `stage=wait config=false database=false`
持续执行。持久日志包含本次启动附近 Config/Core/Kernel/Account 观察注册成功，
但未见实例捕获。由此不能继续把问题归因于“侧栏没刷新”或注册失败。

只读证据：
`build/profile-capture-repair-20261114-01603b9b/four-issues-readonly-20261115-001.json`、
`four-issues-readonly-20261115-002.json`。
日志输出限于固定阶段、布尔和进程元数据；没有聊天/联系人/账号/头像URL正文。
部分持久日志可能来自先前进程，不能把其全部当当前加载证明。

## 已完成布局与系统栏专项

### 输入区 / 更多面板

实际 LinearLayout 测量和 margin 负例发现：输入行高被抵消，但展开面板高度
没有抵消，旧 fixture 手工摆正 footer，漏掉真实越界。首轮 footer bottom=1292、
host bottom=1000；修正整卡净占高后同一实际布局 fixture footer bottom=992，底隙8。

- 整卡自然高度抵消，不仅裁 outline；祖先可见底界约束整体位置。
- 推荐 minimum 与硬可用上限分开，未测量 defer，窗口恢复后恢复自然高。
- 多行输入保持自然 WRAP_CONTENT，IME/导航已消费空间不重复扣除。
- AppPanel 仅使用确认的 setter，不再按相同高度泛扫后代。
- 宿主 LP 替换、reparent/直接结构、关闭失败恢复仍有覆盖。

生产范围：`FloatingChatFooterFeature.java`、`FloatingFooterLayout.java`、
`FloatingFooterGeometry.java`。专项 missing85、present256断言通过，原递归和
collapsed-panel负例 replay 仍RED confirmed。证据：
`build/four-issues-footer-20261115/01-red-margin.log`、`11-footer-freeze.log`。

**壁纸实际承载层尚未证明**。仅加入有界 class/数字几何诊断，没有猜祖先资源、
重写背景或补纯色条。实际墙纸连续经过悬浮区仍需集成实机验收。

### 通讯录

当前微信8.0.76静态证据确认：首页第1页 `MvvmAddressUIFragment.p` 是
`WxRecyclerView`（ID 0x7f090267），LinearLayout→FrameLayout→列表。
`BaseAddressUIFragment.y0()` 把原栏 ID 0x7f093c64 高度写入页根 bottom padding。
此前当前导航实现仅处理 ConversationListView，通讯录未接入占位回收。

新增 `NavigationContactViewport.java`，只读当前缓存fragment，不调用会创建
fragment的 getItem/h。仅精确当前通讯录分支、结构、资源ID及原栏高度完全相同的
显式 constraint gap 可以回收。保存已施加值并条件恢复，GONE保留原栏高度快照，
布局变更每帧最多一次，布局完成后才算尾部可达 padding。非目标页、未知类型、
LP替换/宿主改值 fail-closed，不全树 MATCH_PARENT/清 padding。

专项主代理最终执行通过：57 Frosted、54 contacts、75生产导航hooks断言。
首RED及宿主定点证据在 `build/four-issues-navigation-20261115/`。
内层wrapper的实机几何和实际最后一行点击仍待验证，不是静态测试的结论。

### 底部手势系统栏

`ChatWindowInsetsCoordinator` 的 BOTTOM 导航写入改由
`SystemBarsCoordinator` 单一处理，包括 Window 和具名微信 wrapper。
仅有 BOTTOM owner、真实聊天布局覆盖整个手势区域、无键盘/三键tappable时透明；
短布局/祖先裁剪/没有chat/三键/IME使用安全页面底色。

resume与preDraw遵守同一所有权，稳定帧不重复写值；宿主后来改色会重记恢复值。
释放时条件恢复，不覆盖宿主独立写入，观察器移除。TOP旧行为及acquire/release
签名保留，没有把整个微信统一透明。

两个actual production协调器同source set测试：27断言通过，缺少wrapper getter
时不调用setter的专项通过。原配对 acquire-wrapper 和 resume-ownership 两RED
已落盘 `build/four-issues-systembars-20261115/red/run.log`。最终专项在
`green-001/`、`missing-getter-001/`。实机系统栏/阴影/背景覆盖未验。

## 资料加载：正确方法与当前状态

本地原版 WeKit：
`C:/code/WeKit/app/src/main/java/dev/ujhhgtg/wekit/features/api/core/WeDatabaseApi.kt:91-120`，
`features/items/beautify/home_screen_panel/HomeSidePanelProfile.kt:31-49`，
`features/api/core/WeTextStatusApi.kt:94-100,228-246`。

原昵称 key4、头像 `img_flag.reserved2` 和状态 singleton→accessor→latest 链已经
在MindLink沿用。差异是原版在需求时主动取得已初始化core/config，而移植主要
依赖异步被动观察；头像/状态还受当前账号数据库一致性门禁保护。

当前只读证据支持“注册成功但实例空窗”，不能断言 ART inline 或类加载器问题。
legacy Xposed没有可引用的deoptimization API，未接私有接口；原WeKit legacy也
使用 lpparam.classLoader，未凭空改loader。

正在完成的窄修复：同一attach入口把已核验宿主的同步被动观察提前，避免异步
解析期间错过Kernel构造/就绪；只接宿主成功创建的对象，不主动Kernel getter、
静态singleton取值或初始化未知内核。已就绪existingCore可有界使用唯一config getter，
缓存随NotReady/替换失效，旧后台代际不得发布。

此部分尚未最终冻结，专项和最终编译/候选尚未完成；下一次经确认的重启才可验证
早期观察是否实际捕获。**头像、昵称、状态取得仍未通过实机验收。**

## 后续验证与权限

专项全部稳定后，集中完整回归及显式目标族窄编译，基于当前完整基线生成单个
候选。桌面/原WeKit侧栏/已有顶栏气泡/采样、压缩流、配置、图片和署名必须保留。
实际最终APK将核对目标DEX/ABI、资源、原签名及非目标原压缩流。

下一候选安装、微信重启、截图和GUI操作不在当前本地修复许可内，需另行确认。
实机由用户操作验收；不清数据/图片/日志，不发送消息/支付、不读聊天数据库正文、
不公开资料或APK，不commit/push。
