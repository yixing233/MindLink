# 剩余问题修复（2026-11-16）

## 范围

用户确认底栏指示器与状态栏明暗问题已修复；本轮只处理分组跳位/闪烁、
悬浮输入栏键盘/抖动、分体顶栏裁切、外部 .9 气泡适配。
不修改导航指示器、SystemBarsCoordinator、SettingsShell/SettingsTheme、
聊天系统栏所有权接口或侧栏；不安装、不重启微信、不操作消息/支付、不清数据。

## 源码修复

- 分组：DexKit 解析原生 checkEmptyFooter；仅真正切组执行
  notifyDataSetChanged → checkEmptyFooter → setSelection。
  同组数据更新与聊天返回保持原有不重置滚动逻辑。
- 分组浮层：通过 resetActionBarView 的唯一 View 字段读取真实顶栏，不执行该方法。
  区分 header 位于视口上方与下方；位置、裁剪和 alpha 跟随顶栏。
  解析等待/失败时隐藏未锚定浮层，垂直手势转发期间只清绘制区域。
- 输入栏：导航/IME 剩余量考虑父容器和裁剪祖先的未滚动布局底界，
  保留整张自然测量卡片的净占高抵消，避免回退到历史底角越屏问题。
  同一 IME/空间预算下保留实际原生键盘偏移；预算变化、IME 关闭、恢复时失效。
  不拦截原生进入键盘请求。
- 分体顶栏：绘制补回 shadowPadding；按实际阴影越界识别需要解除裁剪的祖先，
  不以祖先高度倍数推断。可见但内容为空的 WXActionMenuView 不占标题空间。
  标题容器宽度与文字 maxWidth 跟随胶囊内部预算，退出后恢复原值；
  先释放分体快照，再释放整体卡片所有权，包含标题替换路径。
- .9：外部导入图片按目标密度解释原始像素，避免 Resources 构造 Drawable
  将固定区域和内容 padding 再乘设备密度。保留 raw/compiled、多段伸缩、
  内容 padding（含零）、透明度与普通图片的中心裁切/采样。

## 验证

测试使用实际生产 Java 类与私有 Android/Xposed 合同替身。
本轮先观察新增负例失败，再修源码；没有新增设备控制或临时诊断 Python 脚本。

- .9 RED：480 dpi 下 compiled 内容边距被放大；GREEN：原始与编译图片同像素边距，
  全部 BubbleImage 113 项断言通过；其他四个既有气泡专项保留。
  留存：build/remaining-repairs-20261116/bubble-red、bubble-green。
- 顶栏 RED：负边距后实际绘制偏左上 10 px。修复后的分体几何 63 项断言通过，
  包括可见空槽、实际居中标题、121 px 高祖先顶部阴影、宽度恢复与整体/分体退出。
- 输入栏 RED：裁剪祖先已避让键盘仍重复扣导航；原生 lift=100 切面板后被改220。
  原生键盘请求、同预算偏移、IME 高度返回旧值、短祖先/IME 连续20帧均纳入回归。
- 分组 RED：短组 notify 后未调用填充校正；header 在视口下方时错误吸顶。
  12 类分组专项通过，保留同组无回顶、SQL缓存、临时detach身份、公众号标签复用。

完整 `python -I scripts/tests/regression.py` 返回 0；分组切换/返回首页/公众号标签专项
额外连续运行三轮通过。全部生产 Java 使用 JDK 21、真实 Android SDK 37 编译通过。
两个 Compose 运行时的 release 编译、依赖锁、资源检查和 Lint 均通过。

## 最终候选

- APK：`build/remaining-repairs-20261116/candidate-003/MindLink-1.0.apk`
- 大小：44,210,519 bytes。
- SHA-256：`23aaa804f6951abad391910a524766941d250bd0f96b5639292681adfc1c1b5d`。
- 原证书 SHA-256：`c7a67d45660e019746675f99c14156191550f08fc4398b5cef552c9808c9bbda`。
- 独立门禁：`candidate-003/apk-verification/verification.txt`。
  ZIP/CRC、DEX 头和校验和、类唯一性、原证书/v3、zipalign、唯一入口、
  本地声明和两个隔离载荷的 hash/类定义检查全部通过。
- 根 DEX：primary 3589 类、feature 673 类；feature 无测试或 Android/宿主编译替身。

构建过程保留以下失败记录，未把失败命令描述为通过：

1. candidate-001 的侧栏离线 Lint 缺少 32.3.2 工具依赖；联网补齐后 Lint 成功，
   candidate-002 的两个运行时均完整执行 release Lint，未使用 skip 开关。
2. 当前标准全量 `build.ps1` 在 Feature 资源 gate 把两个既有微信宿主 ID
   `0x7f090267/0x7f093c64` 当成模块 ID，因此停止。
   后续复用既有、SHA-256 已核对的 `host_resource_contract.py`：
   验证静态微信 APK 身份、两个具名资源、模块无 ID alias、反汇编中的精确导航所属类
   和指令形式，再执行原来的其余 module resource gate。
   导航源码和 ID 常量未改。证据：`candidate-002/checked-host-merge/host-resource-check.json`。
3. 全量 apktool 回编的 primary 字节与旧签包不同。实际反汇编对比 3589 类，
   新增/删除/变化均为0，最终 candidate-003 保留已验证 primary 的原始字节，
   SHA-256 仍为 `3ac56670fbccada8160c6b8e32614e19f1f116a32dc7d430cb6d10bed29aaba6`。
   之后再次执行独立 APK 门禁并通过，保留 candidate-002 的失败结果。

候选是当前工作区全量 Java 和两个现有 UI 载荷构建，不是只覆盖8族的二进制补丁。
本轮未修改桌面/侧栏源码，也不声明这些已有工作区改动与旧 APK 完全相同。
candidate-003 仅对已生成侧栏 DEX 资产执行既有压缩工具；其解压内容、索引、
其他 ZIP 压缩流和桌面载荷均保持与 candidate-002 的对应内容一致。
最终候选未安装、未重启微信，旧 APK 和用户数据保留。

JVM 几何、解析与合同测试不证明 ART Hook、真实 IME 动画、文字绘制或实际 .9 图片效果。
新候选的实机验收尚未完成；这些项目暂不勾选为已完成。
