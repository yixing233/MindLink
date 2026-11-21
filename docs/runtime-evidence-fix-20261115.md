# 基于诊断包复现的定点修复（2026-11-15）

本轮依据 `build/runtime-evidence-repair-20261115/reproduction-001.md` 的实际PID4563证据修复，而非把模拟测试当实机。当前已安装完整诊断包56750c...，本轮候选必须基于它保留其他功能。新候选安装和重启分别确认；未自动重启、触摸、截图、读取聊天正文或清日志/数据。

## 1. 通讯录：真实根节点与原栏168px占位

实际fragment.getView是FrostedContentView；WxRecyclerView通过Frame/Linear/Frame/MMWeUIBounceView到该root。root高度2610、paddingBottom168，list可见底2442，与原栏nativeReserve168精确相符。旧实现要求root=LinearLayout而静默退出，故此前回收没有运行。

NavigationContactViewport支持这一精确已确认路径，probe保存getView的root，不从列表祖父推导。只回收root真实消费的nativeheight底部padding；MATCH_PARENT各层、标题/top、弹性translationY7都不改。保留旧LinearLayout路径和未知结构不写、宿主新值/取消条件恢复。

真实路径旧实现RED已复现；最终contacts74专项通过，main现有navigation runner一次通过：Frosted57、contacts74、nav102。JVM结构/LP驱动测试仍不是手机滚动效果验收。

## 2. 输入面板：原生首开、模块节点所有权与返回输入

诊断显示ready→panelmissing，旧代码仅footer子树找panel、失效restore可重插被宿主撤掉的节点，原测试的迁移根仍在footer内且多次settle，覆盖不完整。trace没有panelparent identity，故**不能把“移到祖先后查不到”写成唯一已证设备根因**。

最终实现：未知mode保留原生布局，不提前迁移/GONE阻断首次lazy初始化；成功native switch后再迁移。只保留满足原父/现父/所属footer验证的已owned节点；宿主replace/orphan不复活旧节点；AppPanel由当前panel查找，setter数值不变时不重复触发。首次迁移后仅对本次原生已调用的scroll参数重放一次，保留boolean/17/29控制flags，只用实际remainingIME改目标高度；没有捕获原生调用则不制造调用，preDraw不重复。

replacement与过早迁移的真实生产负例先RED；最终footer missing93/present322通过，原两个历史负例保留。此前两个旧negative生成依赖/新转场契约不匹配曾失败，定点调整测试replay，不修改生产为迎合测试。源码SHA=`7fa92749f5b8462b995a59ffc6124d1d2eb40e8b6a2e31efccd6aa1e3428dd46`。四角/阴影最终必须真实复现后验收。

## 3. 资料及同名Class Hook：实际附着应用loader路由

新诊断确认config类型已注册，但ctor/instance/Ready/Core getter的after入口未观察到，需求停existingKernel=false。不能因此认定宿主未初始化。

源码与原WeKit对照确认：原版在attach后采用实际Context/Application.baseContext.loader；MindLink attach-after丢弃thisObject，bootstrap nullcontext，仍用loadPackage初始loader。footer discovery只匹配类名，因此能找到实际View不证明hook注册到了该Class。Tinker映射计数137只提供线索，不证明身份不同。

定点修复：z4/c在verifyModule成功后、attach成功且thisObject为Context时先调用FeatureBootstrap.init(load,attachedContext)，独立异常隔离后原doInit保留；resolver首次选定真实baseContext loader，后续nullcontext不覆盖。DB early pin、header/footer、导航和ConversationStorage走该loader；ConversationList仅resolver无需源码改。不主动Kernelgetter/static读或clinit、不取消Ready/账号隔离。记录Class身份布尔验证，只有真实新启动才能确认是否命中。

已通过窄启动/加载器测试：bootstrap success90/attached91/resolver-throwable91，realresolver live/nullApplication/preserve9，DBmetadata pinnedselected19/unpinned13/emptyinstance34。primary仅修改z4/c，签包要actualDEX单类diff、原验证先于bootstrap、旧/新primary+feature联合ABI检查。

## 本地回归与构建记录

- `fix-regression-001` 失败于共享测试桩新增泛型findViewById后，header runner仍重复注入旧方法。只删除测试中的重复注入，未改聊天顶栏/工具栏生产代码；该runner定点重查通过。
- `fix-regression-002/result.json`：exit=0、passed=true，检查4234个源码文件，测试期间source_changes为空，耗时151.922秒。这是完整本地回归，不是Android/Xposed/IME/GPU或资料实机验收。
- `repair-candidate-run-001`：七Java族编译/D8/feature组装成功，复制原primary快照时遇Windows长路径失败；保留失败目录，未签包。
- `repair-candidate-run-002`：长路径修正后primary组装成功，但工具的源代码精确匹配检查失败，未签包。已改为通过标准输入传递短适配器，避免PowerShell向`python -c`传多层引号时改变字符串；不编译的对接检查确认两个替换位置各匹配一次。
- `repair-candidate-run-003`：全新目录构建完成，PowerShell实际exit=0。七Java族与primary单类编译/组装、原证书v3、zipalign、实际签名APK解包后的primary/feature联合ABI、资源、原压缩流、payload及体积门禁全部通过。完整本地回归未因工具传参修改而重复执行。

## 最终候选

- 路径：`build/runtime-evidence-repair-20261115/repair-candidate-20261115-033250-b26f13ee/candidate-001/MindLink-1.0-bottom-bar-repair.apk`。basename沿用工具名称，实际内容为本轮三个方向的七Java族及attach入口修复。
- APK SHA256：`1e6a668b9642bd63646596ea1bb7a7d19c647c83eb21174cc6da816ea9aec87e`。
- 实际大小26,111,437 bytes；相对当前诊断基线56750c增量0 bytes，`size-gate.json` passed=true。
- 实际primary SHA256：`3ac56670fbccada8160c6b8e32614e19f1f116a32dc7d430cb6d10bed29aaba6`；单类差异门禁只允许`Lz4/c;`，其余3588类反汇编字节完全相同。
- 实际feature SHA256：`8aa5b5fa60865c9af3a30c07098ba356986ba4477894816c387f46aeea64c563`；571个非目标feature类反汇编字节完全相同。
- 联合ABI：incoming112/outgoing1018，missing0/incompatible0。实际新primary和新feature共同输入，z4/c的出入引用包含在门禁内。
- 实际仅变更`classes.dex`、`classes2.dex`和runtime inventory；桌面/原侧栏payload、资源、manifest、JNI、顶栏气泡及所有非目标压缩流保持原样；签包完整门禁执行一次。
- 证据：该候选目录的`candidate-001/build-result.json`及`final-gate-001/{strict-primary-diff,strict-class-diff,external-family-ABI-check,strict-final-zip,feature-resource-check,size-gate}.json`。

## 当前交付边界

新候选已完成本地回归与最终APK校验。用户针对此包确认“安装”后，经USB设备8d5e85d9核对手机仍为56750c诊断基线，覆盖安装返回Success；实际手机base.apk SHA256已核对为`1e6a668b9642bd63646596ea1bb7a7d19c647c83eb21174cc6da816ea9aec87e`。安装证据保存在候选目录的`device-install-001.json`。未清数据、未自动重启微信、未触摸或截图。

**安装成功不是实机功能验收完成**。用户随后报告“微信卡爆了”，原功能验收已暂停，该候选不作为可用实机通过版本。PID30348现有系统卡顿堆栈多次进入HostDatabaseBridge同步capture/元数据反射，并在底栏点击的宿主Hook进入callbackEvidence类锁处被采样。资料read阶段布尔结果首次命中，但不等于UI显示成功。现场脱敏证据见`build/runtime-evidence-repair-20261115/lag-evidence-30348-001.md`，正在定点排查捕获热点与锁竞争；未自动停进程、重启或降级安装。

资料是否实际捕获/读取/发布、第一次表情/更多内容、返回输入四角和通讯录滚动过栏的完整验收推迟到卡顿修复后。后续安装仍需按具体候选确认；如有并行更新则暂停，不自动覆盖新版或重启微信。
