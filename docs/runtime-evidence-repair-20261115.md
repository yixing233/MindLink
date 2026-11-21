# 实机断点调试：2026-11-15

## 已获批准的范围

本地必要诊断、修复、测试、构建与签名；设备只读验证仅阶段/布尔/几何/错误类型。新候选安装与微信重启仍分别确认。用户操作复现，不截图、不代触摸、不读取聊天消息数据库或正文；不清配置、图片、日志，不发布或提交源码。用户指出不应不断写 Python 脚本；本次使用直接 ADB、设备 toybox 白名单过滤和已有 sqlite3 -readonly，仅既有测试/构建工具可复用。

## 本次实际手机证据

- 端点 `192.168.1.148:38241`；微信主 PID `16358`，UID `10443`。
- 模块 actual APK SHA-256 `14b231211b313c22d22593d47ee4372fe9eb8167dd8ae88996c08a699df10ce8`。
- 微信 actual APK SHA-256 `04792eb19a566395a9fabee9a50be80245826cb774ec64637a22f7f28acd4054`，与早期观察 pin 相符。
- 当前 PID 的 MindLinkStage：`early-observe registered hostMatched=true`、config Hook registered、storage-observe registered=2、kernel/account observe registered。用户打开侧栏后实际出现 `demand waiting existingKernel=false`、`profile wait config=false database=false`。当前链尚未进入真实读取，不应从头像/状态接口开始盲改。
- 输入栏 `baseline enabled=true` 属于同 PID（LSPosed 时间窗02:29:41）；02:35:30–32 复现附近实际有 observe、discover、`styled+margins/panel-above`，随后 `styled+margins/native-panel`。几何 mode 全为 `-1`，card 从1239/169变为343/1413；后段 marginBottom=-983/-1043。mode=-1不能独自证明未Hook，也可能失败/状态替换；需要补回调进入、mode/depth、节点/门禁字段。
- 先前简化白名单输出曾截断阶段中的 `/panel-above`；后续已直接读取完整固定分支证明曾进入上方面板。不能再声称一直未进入。
- SQLite 只读模块配置：before/copy/after hash 均 `be421d49ad841525afd562c37b14a80924dad1ade45bf307df03b1bc5d179744`。footer/profile/sidebar/nav相关已存开关为 true；panelAbove键未存，生产默认true。仅输出白名单布尔及host target方法descriptor，没有配置全文。
- 实际缓存：ChatFooter `H1(IZI)V` switch、`E(IZI)V` config、`C1()V` keyboard、`a1(I)V` refresh；ChattingScrollLayout `b(IZII)V`，fullscreen `c8.u()V`。缓存证明解析曾取得对应方法，不证明当前Hook回调实际执行。
- `/proc/16358/maps` 未见模块 APK 路径；框架可能内存DEX，此项不能反证加载。当前阶段日志和复现变化为更直接执行证据。
- feature-stages.txt约26KiB，未到256KiB上限；不重构日志缓冲或清旧文件。LSPosed日志轮转到02:35:28，未将旧文件没有新行误当当前路径未执行。

## 下一步最小诊断

保持当前功能行为，不先改布局/Ready门禁：

1. Footer：Hook注册和entered/result，首次switch before/after mode/depth，panel/app节点存在/测量/自然高度/目标高度，IME、margin/translation、outline所属。转场后最多3帧；限次、去重、不取文字。
2. Profile：被动Kernel ctor/getter、Ready入口与结果（即使weak Kernel空也可见），Core getter是否命中；需求时metadata/holder布尔。HostStageLog增加PID及固定诊断代号，不改IO策略、不主动Kernelgetter或static读取。
3. Contacts：current fragment/list身份、wrapper/path/页面坐标拒绝原因，以及nativeReserveHeight和最多8层数值，限次和节流。保留未知结构不写、原恢复机制；先取真实reserve来源。

## 诊断候选准备进度

- 通讯录仅增加 silent-gate 的 reason、节流/去重、最多8祖先数值；102项导航、54项contacts和57项Frosted测试通过。诊断异常独立吞掉，布局所有权路径保持原样。
- 资料仅改 HostDatabaseBridge、HostStageLog。实际生产三桥联合17/17通过；Ready无holder仍可记录、callback总24新事件、预算耗尽不影响capture、未主动读取未知Kernel/static，日志保持queue64/256KiB/append只增加PID代号。HostProfileBridge/SelfProfile/TextStatus保持原字节源码。
- 输入栏增加注册descriptor/回调before-after/error、panelReady拒绝、键盘拦截条件和固定最多8节点转场快照；共24事件、转场后3帧、无文字。missing93/present284通过。旧pre-fix-observe负例生成因新diagnostic wrapper依赖existing helper编译失败；仅补负例lookup依赖，递归缺陷保持。两例定点复跑已确认原递归 StackOverflowError 与collapsed-panel360暴露为RED，未放宽production。
- 五族source SHA冻结在 `build/runtime-evidence-repair-20261115/ready-manifest.json`，只准备诊断包，布局/Ready功能仍未宣布修复。

## 完整诊断候选（尚未安装）

实际完整候选：`build/runtime-evidence-repair-20261115/candidate-build-20261115-025403-d9f97289/candidate-001/MindLink-1.0-bottom-bar-repair.apk`。沿用既有打包工具basename，但内容是本轮五族**诊断**，不把文件名当功能已修好。

- APK SHA-256：`56750c5d8113993492b6b7474662301f27d1985a3fa0cf4335dbab06c29fcc5f`。
- 大小26,111,437 bytes，相对已装14b231基线+16,384 bytes。
- Feature SHA-256：`529e0ec2bfbead515d6c695e1c297ccc9ee57ac916d49640763a9e723098d388`。
- 冻结清单SHA：`f45758d966d342a4bc8536bf9eb8d956206d7b4eaab7ee018f8d6f0cfca68781`。
- 594个非目标smali byte-exact、primary保持；actual混合DEX ABI incoming42/outgoing1062，missing/incompatible0，原证书/v3/zipalign、host资源合同、非目标原压缩流/桌面侧栏provenance通过。只feature与runtime inventory变化；inventory沿用旧工具更新bottom_bar_baseline_apk_sha256为实际14b231基线，其他provenance保持。
- 窄编译和最终签包门禁实际通过，size-gate=true且整个PS exit0。只复用既有Python编译/验证函数，PS编排；本轮新写的read_safe_stages.py未执行，设备取证用直接ADB过滤。
- 第一次PS输入检查因代理误报overlay45而失败，实际冻结manifest/JAR均44；按实际hash和manifest membership校正，不按猜数量放行。失败输出candidate-build-001.log保留，未编译/签包；第二次candidate-build-002.log完整成功。
- 未执行新的整仓完整回归：本轮诊断仅执行相关footer、navigation、三桥联合专项，以及一次实际最终APK完整门禁。没有将专项说成整仓回归。

本轮没有完成这些功能实机修复验收。诊断候选安装仍需明确确认；诊断数据用于确定最小修复，而非将诊断包称为修好。
