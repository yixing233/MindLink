# 底栏三项修复（2026-11-15）

## 范围与基线

依据 [只读排查](bottom-bar-readonly-diagnosis-20261115.md)，本轮只处理徽标同步、整栏宽白光、背景采样与恢复。完整 APK 基线为已安装的四项候选 `2d1ab71f42135a968e85bfa59826c2168016be6d6228f2726f5dd208f0cbcb25`。当前本地修复、测试与构建授权不包括安装、重启微信、截图、触摸、清数据、发布或提交源码。

## 修改

- **真实徽标对账**：异步 Hook 成功注册后补读；同 Activity 的首页重新可见、确认切页和宿主徽标事件合并一次读取。读取 count 与 point 的失败隔离；真实 `0` 清除旧数字。只有状态变化才更新子 View 与父组合快照。不点击强制清零，不每帧轮询。
- **宽白光**：删除 `NavigationBarViews.onDraw` 对整栏 `decoration.touch` 的调用；保留阴影、细边缘高光、弹性运动和指示器折射。指示器前景先录制并确认成功才提交；软件 Canvas、组合录制、指示器或节点绘制失败时使用普通指示器和选中颜色。
- **背景恢复**：实际 ConversationListView 的首项位置、top 与空状态去重后独立通知 source 内容修订，与 autoHide 分离。捕获中的自身回调不通知、不污染去重状态。保留三次失败预算和 80/240ms 退避；普通重绘不补预算。source 节点丢失撤销成功凭据；安全门禁暂态失败有限重检。背景层录制/effect 失败安排独立有限重建，不因 source 缓存有效而失去唤醒。隐藏、停止和关闭取消恢复任务。

只重编 `NavigationBarFeature`、`NavigationBarViews`、`NativeTabBridge`、`ViewBackdropSampler`、`BackdropCaptureState`、`LiquidGlassLayer` 六个明确家族。通讯录回收、输入面板、系统栏、资料早期捕获、桌面与原 WeKit 侧栏、顶栏与气泡保留。

## 已执行的本地证据

- `build/bottom-bar-repair-20261115/navigation-evidence/02-red.log`：真实生产 Feature/Views/Bridge 联合负例，修复前失败已复现。第一次执行在测试错误地向 AdapterView.addView 后停止；修正 fixture 为宿主原生行注入后再次确认 RED，没有放宽生产逻辑。
- `navigation-evidence/06-final-config-key.log`：57 项 Frosted、54 项通讯录、98 项导航断言通过。新增覆盖 N→0、迟注册、可见恢复、事件合并、实际组合快照文本移除、独立 getter 失败、软件/录制/指示器节点失败降级，以及真实列表/空状态去重和捕获抑制；使用生产的完整 autoHide 配置键。
- `backdrop-evidence/05-final-production.txt`：状态机 113 项断言；实际采样器与背景层 30 组、611 项断言通过，含自动有限门禁重检、source 节点丢失、layer 重建及持续失败停止。

- `backdrop-evidence/10-joint-check.txt`：实际 Feature、Views、NativeTabBridge、采样器、状态机和背景层共同执行，27 项断言、17 个自动安排的正常帧通过。覆盖 pager 不 dirty 的真实 Xposed onScroll → 内容修订 → 耗尽后的恢复；实际背景层录制/effect 故障 → NavigationBarViews 定时唤醒 → source 缓存复用、不追加捕获；隐藏和 detach 取消。联合 fixture 仅放在 build 中，不替代真实 Android/GPU 验收。
- `regression-001/regression.log` 与 `result.json`：现有集中完整静态/运行回归实际 exit 0。调用使用 execute-only 路径，未执行 wrapper 的前后全源码 snapshot；不声称这次有全仓输入稳定性门禁，也不为补报告字段重跑整套。六个冻结生产文件实际 SHA-256 已记录；随后仅修正测试 autoHide 键并复跑对应导航专项通过。

上述均为本地 JVM/模拟 Android 接口证据，不是手机 HWUI、RenderThread、Xposed 运行或人类视觉验收。只读文档中的设备根因候选仍不因此自动变成实机结论。

## 后续交付状态

集中完整回归及实际导航与采样/背景层的最小联合链已通过；六族窄编译、签名和实际最终 APK 校验完成。

构建过程保留失败记录：build-001 在输入检查因工具误将原本 ZIP_STORED 的 payload index.txt 也要求 DEFLATED 而失败；build-002 因依赖 JAR 的目录和 META-INF/MANIFEST.MF 被误当成非 class 错误而失败。两次均在编译/签包前停止；修正工具的实际输入合同，未扩大功能改动或改变压缩策略。build-003 实际 exit 0，第一次生成的 signed APK 完整门禁通过，没有为报告字段再跑全验。

### 最终候选

- APK：`C:/code/MindLink/build/bottom-bar-repair-20261115/candidate-001/MindLink-1.0-bottom-bar-repair.apk`
- SHA-256：`14b231211b313c22d22593d47ee4372fe9eb8167dd8ae88996c08a699df10ce8`
- 大小：26,095,053 bytes，相对完整 2d1 基线 **0 bytes 增量**。
- 实际 feature DEX：`990642be986c95630c297f86b49a222db9b177ffcac484a848247f6370f7d7c3`。
- 冻结源码清单：`b8d83b557d2697d49ab23d9d5d9e2e7e4f4b5bd52fc7a91cb3940fcbc86fb269`。
- 599 个非目标 feature 类 smali byte-exact；primary DEX 原样保留。实际 mixed DEX ABI：incoming 5、outgoing 761，缺失和不兼容均 0。
- 实际最终 ZIP 只 classes2.dex 与 runtime inventory 改变；其他条目内容和原压缩流一致。桌面、原侧栏 payload、资源、manifest、JNI 与 provenance 保留；根 DEX039/STORED 和 payload bin DEFLATED/index STORED 保持。
- 实际原证书/v3、zipalign、payload 有序索引及 checksum、资源和两个已证宿主导航 ID 合同通过。
- 新证据：`candidate-001/build-result.json`、`final-gate-001/verification.json`、`strict-final-zip.json`、`strict-class-diff.json`、`external-family-ABI-check.json`、`feature-resource-check.json`。校验后再次读取当前 APK hash 和大小，与结果一致。

**本轮尚未安装，也未重启微信；三项实机验收未完成。** 新候选需要单独确认，安装前重新核对手机包身份；并行更新时暂停，不用旧候选覆盖。
