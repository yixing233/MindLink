# 微信卡顿定点修复（2026-11-15）

## 现场依据

当前1e6a668b候选安装后用户报告严重卡顿。PID30348已有系统卡顿堆栈多次进入HostDatabaseBridge的queryMethod/findRawQuery/directDatabase/captureKernel，并在底栏点击的宿主Hook停于callbackEvidence类锁。完整脱敏证据见`build/runtime-evidence-repair-20261115/lag-evidence-30348-001.md`。原功能验收已暂停；本地或安装成功不能当实机可用通过。

## 三族定点修改

- HostDatabaseBridge：宿主高频Hook只读取已pin固定instance字段及worker学得的有界holder证据，合并单槽worker捕获。反射发现、数据库扫描和isOpen均不在主线程/宿主Hook或发布锁内运行。Class身份正负metadata缓存，诊断计数独立小锁。发布前二次检查epoch/version/account/Ready/实际holder；不主动Kernel getter、不读宿主静态singleton。宿主引用保持weak。
- HostProfileBridge：仅身份key触发通知，节流先于捕获工作，模块自读抑制嵌套Kernel/config回调。实际账号已验证的holder由worker发布。
- SelfProfile：loading/cooldown前置合并，仍保留轻量账号/版本门禁；所有SQL读取结束后在worker锁外执行open/sourceVersion/token最终检查，再提交MAIN成功结果。
- actual DB字段证据由worker根据实际Class学习，覆盖declared interface、wrapper/基类与继承字段；主线程只读学得Field，最多8。

不改primary、loader、输入区、通讯录、底栏、顶栏、气泡、桌面或原侧栏。新候选以已装1e6a668b为基线，原primary3ac566保持不变。

## 验证记录（不是实机验收）

- 新增旧production RED：6例真实失败，1例原已通过。
- 首次27例专项26通过，optional昵称Object字段误被holder探针接管回归已定点修正；相关5例通过，未为报告重复全套。
- 审阅发现actualClass DB字段不在declared探针和最后query中关闭缺末尾workeropen两项真实缺口；新增两例修改前均RED，修后新2+原受影响5共7例GREEN。
- 稳定200次Ready回调scan/miss/open/submission增量均0；400次rapid最大pending1、扫描1。worker扫描阻塞时callback仍能完成；自读嵌套、lateDB/replacement/Ready撤销/DBclosed/sourceVersion拒绝已针对验证。
- `lag-regression-001` exit1、47.218秒、4234源码测试期间不变；失败为独立database测试fixture缺Looper及新资料桥接口，未签包。
- 独立database接缝适配后原27例25通过、2失败；interface例补先真实Ready观察，任意Object捕获负例揭示生产范围扩大。恢复已签基线declaredObject默认拒绝，合法typed接口/wrapper/继承字段学习不变；新增integration RED→相关7例GREEN，原两失败定点复查通过。
- profile-config独立fixture增加真实MAIN/worker与显式drain/weakref/Ready tuple，旧38 bridge+3 metadata均已有GREEN；本人资料独立fixture补readCaptureCurrent后台接口后profile全suite通过。fixture适配不允许生产同步fallback或Ready放宽。
- `lag-regression-002` exit1，剩余resolver独立fixture缺Looper/readInProgress等，补接口并等待真实worker后定点通过；`lag-regression-003` exit1，仅profile-config metadata复用resolver新Looper导致重复类，已排除重复输入，未修改生产。
- metadata重复Looper接缝修正后，metadata及最终三桥联合专项通过。
- `lag-regression-004/result.json`：exit0、passed=true、77.343秒、4237源码测试期间不变；完整本地回归通过，仍非ART/Xposed/IME/资料显示或性能实机验收。

## 当前冻结及交付边界

- HostDatabaseBridge SHA256：`fd86b4e9718bc92ae1b4fa7210842159469b568a3faf246203bff2a1f7b35c45`
- HostProfileBridge SHA256：`ebf59a3f7eaa82b22dcf493a8bfab64e5f474343e7a4aec5e1caf16d4a39756d`
- SelfProfile SHA256：`268dbd6e9660fa8d72e050286250e8440424227e44b1ba759fd0a274748ae366`

`lag-build-tools/prepare_lag_candidate.ps1 -CheckTools`已完成预检。随后`lag-candidate-run-001`实际exit0，窄编译三族并完成一次actual signed feature+原primary联合ABI、资源/原压缩流/原证书v3/zipalign/体积门禁。

## 最终候选（已安装，实机仍严重卡顿）

- 路径：`build/runtime-evidence-repair-20261115/lag-candidate-20261115-043812-eb355e0b/candidate-001/MindLink-1.0-bottom-bar-repair.apk`。文件名沿用工具名称，内容是本轮卡顿修复完整候选。
- APK SHA256：`0f8a6d569403ae935f207cd0154ae35247cb687db161b1c2cf71fccd1d81edf9`。
- 大小26,111,437 bytes，相对1e6a668b增量0 bytes；size-gate passed=true。
- primary SHA256仍为`3ac56670fbccada8160c6b8e32614e19f1f116a32dc7d430cb6d10bed29aaba6`，整个classes.dex原样不重编。
- feature SHA256：`d7de06ed9702728113b62f0542d47ae0ca4a3652b4d829d5dbb1f01e9b39f86d`；630个非目标feature类反汇编字节完全相同。
- ABI incoming30/outgoing368，missing0/incompatible0；仅classes2.dex/runtimeinventory变化，所有其他原压缩流、桌面/原侧栏/资源/manifest/JNI/输入区/通讯录/底栏/顶栏气泡保持基线。
- 证据位于该候选目录的`candidate-001/build-result.json`和`final-gate-001`各门禁JSON。

用户针对此包确认“安装”后，USB设备8d5e85d9核对手机仍为1e6a668b基线，覆盖安装返回Success；手机实际base.apk哈希核对为`0f8a6d569403ae935f207cd0154ae35247cb687db161b1c2cf71fccd1d81edf9`。安装证据保存在该候选目录的`device-install-001.json`。未清数据、未自动重启微信、未触摸/截图。

**0f8a候选卡顿实机验收失败**：用户反馈依旧超级卡，并提供新端口192.168.1.148:37893。当前包哈希核对仍0f8a，PID31940在采样时处于后台系统冻结，不能据CPU0判正常。现有11:34 PID29296与11:35 PID30391两份ANR均指出触摸超时，并在会话分组field/unread/matches→ConversationPositions.build→ConversationListBridge.snapshot→getCount热路径；历史PID与当前PID严格分开，历史独立包hash未记录。脱敏证据见`build/runtime-evidence-repair-20261115/lag-evidence-after-0f8a-001.md`。继续现场证据驱动调查分组链，不重复盲修资料桥，不以本地测试数量否认用户结果。

后续每个新候选仍单独确认，安装前复核手机基线与并行更新；不自动重启/降级/清数据。
