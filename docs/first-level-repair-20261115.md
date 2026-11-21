# f91bd 实机三项失败后的定点修正（2026-11-15）

## 实际反馈与范围

用户确认在新包上仍有以下问题：

1. 消息列表、通讯录、发现和我四个一级页状态栏文本/图标反色失效。
2. 从会话返回列表时垂直滚动位置丢失，直接回到顶部。
3. 微信→发现时，指示器在中间通讯录位置停顿抽搐。

三项均按实机验收失败处理。当前只读设备核对为 `f91bd8b3cc8362c3714de8cecc1cf8177dd17562745e63b6bd102849e6e75382`，PID25927 映射对应安装 APK，焦点 LauncherUI。系统栏当前请求 LIGHT_STATUS_BARS / LIGHT_NAVIGATION_BARS，说明请求暗色图标，不等同于实际背景对比正确。

没有本次操作的完整 ART 回调/列表选择时序。下述修正有静态宿主及真实生产方法测试证据，不把本地复现称为该 PID 的真实事件。保留其它已装功能；不新增作用域或侧滑，不改面板方向、图片/用户配置，不自动安装、重启、触摸、截图、发布或提交源码。

## 已复现并修正的缺陷

### 一级页状态栏

静态宿主 `LauncherUI.e7() → NewChattingTabUI.k()` 可判断真实聊天前台，缓存聊天布局存在不等于正在聊天。四页共享同一 Activity，`MainTabUI$TabsAdapter.onPageSelected → HomeUI.w/v` 会改写 legacy 状态栏位。此前只 resume/focus 与全树检查漏掉切页事件，主题或 content 色也不能代替实际 status surface。

本轮 `SystemBarsCoordinator` 优先用已核验宿主前台接口与精确 HomeUI/Launcher 事件，UI线程合并重算，统一 legacy8192 和 API controller 的状态栏位。对比优先使用已存在的不透明宿主 status surface；透明情况下仅使用可确认的既有不透明 content/decor 色，否则保留宿主请求，不假造壁纸亮度。

`ChatWindowInsetsCoordinator` 仅恢复仍属模块的 TOP status/layout 掩码，不再整份重放旧 systemUiVisibility；保留宿主后来改变的状态位，并在 TOP 释放后重算首页。BOTTOM 独立契约、已有透明尝试和 SettingsShell/Compose 保留。

**边界：未知图像壁纸的自动反色未得到证明，实机四页仍待验收。**

证据：`build/systembars-f91bd-repair-20261115/`。`red-host-001` 为 f91bd 两协调器六条真实生产合同 RED；最终 systembar/missing getter、旧nav30、home50、review25、宿主事件与完整 SettingsShell30 通过。部分名为 green 的中间日志实际失败，保留不作成功证据。最终冻结在 `freeze-final-001/source-hashes.json`。

### 跨标签指示器

真实宿主点击与逐帧 `position+offset` 是不同信号；点击确认目的页后，旧实现仍让分数/中间整数采样拉回 motion 目标。

本轮 `NavigationBarFeature` 与 `NavigationBarViews` 为真实 setter 每次调用保存独立 token，只有宿主 selected/getter 确认后才固定点击终点，手势仍按分数跟随；native DRAGGING/IDLE、失败、改道、缺 state hook 和 teardown 释放上下文。

同步 selected 回调体内新切换的重入采用代际判定，过期外层不覆盖最新选择。嵌套失败仅在 getter 仍确认旧目的页时恢复旧上下文，same-index 不靠位置冒充同一请求。

旧19 idle 专项保留，最终专项 194 hooks +74 contacts +57 Frosted通过。真实玻璃/模糊绘制、spring参数与 NativeTabBridge 未修改。专项早期 RED/GREEN在代理工具输出，最终完整回归将其持久保存，不宣称存在并未创建的独立日志目录。

**边界：没有 PID25927 实际 navigation 回调序列，待实机复测。**

### 列表回顶：只修已证 SQL 路径，普遍问题未结案

真实生产方法复现 `Activity.onResume → reload → 同一SQL规则成员更新 → changeGroup → refreshNow(true) → setSelection(0)`，同组数据刷新也被当作选择新组。

本轮 `ConversationGroupingFeature` / `ConversationListBridge` 在同一 SQL 规则仅 resolved 成员变化时不显式归顶，仍 stop-fling→请求序号核验→同步发布/dirty/notify；真正选择或规则改变、manual成员编辑和启停保留归顶。没有简单 save/restore 旧位置；成员缩小时原 anchor 不存在，原生 observer可合法调整。

**普通 ALL/preset 的普遍实机回顶未找到完整根因。** 真实 resume/bind/mount/通知/长detach对照未重复 header或稳定尺寸 relayout；宿主有 onLayout(changed)、actionbar offset、footer高度改变后 setSelection(1) 的静态路径，但没有此次触发源证据，不盲拦原生 selection。

两族增加 `schema=group-scroll-v2` 的只读结构诊断：每list生命周期最多48事件，不因resume/release/remount重置；只在核对Launcher/window/decor归属后记录list identity、adapterChanged、generationSame、selection参数及固定origin标签，无正文、用户名、SQL或任意调用栈，不加逐帧日志。

证据：`build/grouping-resume-scroll-20261115/01-red.log`（SQL同组position13/top−17被强制归0）与 `06-final-grouping-12-cases.log`（Positions300随机＋12专项×3轮全通过，旧防闪退/重入/缓存保留）。两族 `changeGroup` 参数一起替换；TabsHost与Bar不变。普遍回顶必须在新候选加载后收集诊断再修，当前不能勾选或宣称已消失。

## 验证进度

- `build/first-level-repair-20261115/full-regression-final-001/result.json`：`passed=true`、`exit=0`，4270 文件测试期间无变化；完整日志包含最终导航、分组与系统栏专项。无新实机验收。
- 6个明确生产族 source冻结在 `ready-final-001.json`。
- `production-final-001` 在 javac 前预检因 Windows 超长路径 FileNotFoundError 失败；不是业务源码/API编译失败。失败输出保留 `compile-wrapper-001.log`。
- 工具只修 `actual_tree` 的Windows长路径前缀读取/枚举，完整hash/ABI门禁不变。`production-final-002` 又在 javac 前因新比较器误比较 `final` 而拒绝11项方法；逐项核对为public instance访问/静态一致，只恢复原审计access/static比较契约，保留签名、父类及代表字节校验。
- `production-final-003` 编译入口将 `-J` 选项误放入 javac argument-file而失败，未产出类；日志保留。改为 `-J` 单独传给进程并正确读取退出码，不改生产源码。
- **最终 `production-final-004/result.json`：passed=true、exit=0，6个明确生产族、70个目标类，完整依赖/源/pin前后一致；真实Android37/source-target11通过，保留source11模块路径warning和deprecated API提示。** 与完整回归、live源码逐文件hash一致。没有为了输出或工具问题重跑业务回归。
- 编译依赖基于当前 f91bd 完整快照，用完整九族替换去掉旧 classpath 匿名类残留，不推定该残留是手机APK缺陷。真实Android37、source/target11、空sourcepath、no implicit、只输出6目标族；不全源或测试桩生产编译。

## 候选制作与确认安装

用户授权"打包，然后安装"。基于 f91bd 完整候选制作六族窄修复候选：

- APK：`build/runtime-evidence-repair-20261115/firstlevel-candidate-20261116-003703-ca26ddc8/candidate-001/MindLink-1.0-bottom-bar-repair.apk`。
- SHA-256：`a12dc7c10e778a071caac0aca5ff786f4bc539eabe5221efc7652ad8b5db8756`，26,144,205 bytes，较基线 +16,384。
- 主 DEX 保持 `3ac56670…`；feature DEX 为 `6a6e57ac82fc11f73e7763d76b0f9b27580adfe59d45e431a7c58a9b0a32ec5a`。
- 实际 membership 683 = 677 − 92（六族旧类，从实际 DEX 枚举）+ 98（70 javac 类逐字节等于 production-final-004，另 28 个 D8 合成产出与既有候选同规则）。一次签包门禁全绿：原钥/v3/zipalign、非目标 112 条目 raw byte-exact、实际 DEX ABI missing0/incompatible0、资源契约与 payload provenance 保留。
- 早期两次尝试因 freeze_copy 约束冲突中止，保留 `blocked-attempt-*` 目录；打包代理最初误报 membership 655，终检按 683 修正并记录在 `size-gate.json`。

手机基线安装前核对仍为 `f91bd8b3…`。中途无线端口 37857 失效，用户提供 `192.168.1.148:38861` 重连成功。`adb install -r` 返回 Success，安装后 APK SHA-256 与授权候选完全一致，路径 `/data/app/~~0hnIV9AVIlxmS1cMQw8_lQ==/dev.mindlink-e_WHPLMPIagR3vEzAfcLww==/base.apk`。

**没有重启微信、清数据、触摸、截图、公开发布或 Git commit/push。** 普通分组的普遍回顶仍未定位，新包内含限量结构诊断；四页状态栏、跨页指示器和 SQL 同组归顶的实际效果均待重启微信后用户实测，不能因安装成功宣称已修复。
