# WEX (wex.ui.core) 1.2.27 反编译修改工程

## 来源
- 设备：小米 24122RKC7C（Android 17，KernelSU root，LSPosed）
- 原始 APK：/data/app/~~5-iLLMgZ5Jt3bNWLUSb_Jg==/wex.ui.core-Itdo8yp_9_4E7ZU-hZpOfw==/base.apk
- 包名：wex.ui.core  版本：1.2.27 (versionCode 27)
- 宿主：微信 com.tencent.mm 8.0.76

## 构建方式
工程为 apktool 2.9.3 反编译产物，可直接回编译：

```
java -jar apktool_2.9.3.jar b wex_apktool -o wex_rebuilt.apk
zipalign -p -f 4 wex_rebuilt.apk wex_aligned.apk
apksigner sign --ks wex_fix.jks --ks-pass pass:wexfix123 --key-pass pass:wexfix123 \
  --ks-key-alias wexfix --out wex_final.apk wex_aligned.apk
adb install -r -d wex_final.apk
```

注意：本工程使用新签名证书，原版证书指纹为
e2da276f149378b5ee4017a37c43964a6f1808c220d697bd2ad1fec15f33129e。
因此必须保留下面的「签名校验绕过」修改，否则模块会拒绝启用功能。

## 已做修改

### 1. 绕过重打包签名校验（必需）
文件：`smali/wex/ui/core/xposed/HookEntry.smali`
方法：`access$verifyModule` 开头直接 `return true`，跳过 APK 签名指纹比对。
原因：重打包后签名必然改变，原逻辑会输出「签名校验失败，功能已禁用」并停止初始化。

### 2. 修复首页顶栏（头像/昵称/状态）不显示
文件：
- `smali/wex/ui/core/feature/TopBarFeature.smali`
- 新增 `smali/wex/ui/core/feature/TopBarHomeHook.smali`

原因：`TopBarFeature.init` 用 `x4/f0` 的索引 0xE 注册 `Activity.onResume` hook，
但 `x4/f0` 的 `beforeHookedMethod` sparse-switch 只实现了 0x2/0x5/0x6/0xA/0xC/0xF，
0xE 落入默认分支，导致 `insertProfile` 从不执行。

修复：新增独立 hook 类 `TopBarHomeHook`，在 onResume 后读取 `top_profile_enabled`，
为 true 时调用 `TopBarFeature.access$applyTopBar`，并把 `TopBarFeature.init` 的
hook 实例替换为该新类。

实测：首页标题栏左侧已显示自定义头像与状态圆点/文字。

### 3. 修复 WexStore 配置读取的“空缓存”问题
文件：`smali/wex/ui/core/WexStore.smali`
方法：`readAll`

原因：`openDb()` 失败或查询抛异常时，仍把空 Map 写入静态 `cache`，
导致整个微信进程生命周期内所有配置都读成默认值（表现为顶栏/四页背景等
功能时好时坏、偶发不生效）。

修复：失败路径不再写入 `cache`，下次读取会重新尝试打开数据库。

### 4. 四页背景（更换背景）绘制修复 + 透明度
文件：
- `smali/wex/ui/core/feature/TabBackgroundFeature.smali`
- 新增 `smali/wex/ui/core/feature/TabBgDrawHook.smali`
- 新增 `smali/wex/ui/core/feature/TabBgAlphaBtn.smali`
- `smali/wex/ui/core/xposed/SettingsPanelHelper.smali`

原因与结论：
- 四个页面的容器是 `com.tencent.mm.ui.FrostedContentView`（父容器
  `com.tencent.mm.ui.base.CustomViewPager`，index 0..3）。
- 它的 `dispatchDraw` 在 `frostedEnabled=true` 时把子内容画进 RenderNode 再整体
  绘制，因此原有的 `dispatchDraw` before 画背景会被完全覆盖；after 画又会盖住内容。
- 参照 WeKit 的 `ApplyGlobalBackground` 实现，改为在 DecorView 上插入全屏
  ImageView 蒙层（tag = `wex_tab_bg_overlay`），`bringToFront` + `elevation`，
  按透明度叠加——即「半透明蒙层」效果。

透明度配置 key（字符串，取值 0~255）：
- `tab_bg_alpha_0` ~ `tab_bg_alpha_3`（对应 微信页/通讯录页/发现页/我的页）
- 默认 `"70"`（约 27%）

设置面板：在「四页背景」组末尾新增「背景透明度」按钮，点击在 10%→20%→30%→40%
之间循环切换（写入全部四个 key）。

## 本轮修复（2026-11-02）

### 5. 我的页背景（实验版）重做 —— 之前从未真正实现
文件：
- `smali/x4/f0.smali`（注入点改为调用 `MyPageBackground.apply`）
- `smali/wex/ui/core/feature/MyPageProfileFeature.smali`（`applyProfileLayout` 精简为一行委派）
- 新增 `wex/ui/core/feature/MyPageBackground.java`

两个根因（均来自设备日志，不是推测）：
1. `ClassCastException: NoMeasuredTextView cannot be cast to TextView`，
   注入在第一步就抛异常，整段逻辑中断；
2. 原代码只改 `cdw` 高度并搬移头像/昵称，**从头到尾没有读取 `mypage_bg_path`**，
   还给卡片设了纯红底 `0xFFFF0000`，卡片高度塌成 0，表现为顶部一片空白。

现在的实现：
- 卡片固定 150dp 高、18dp 圆角，图片铺满并圆角裁切；
- 背景图按 `mypage_bg_alpha`（0~255，默认 200）设置不透明度；
- 昵称 / 微信号 / 状态文字按图片平均亮度**自动反色**（亮底黑字、暗底白字），
  并加 1px 阴影，保证任何背景都看得清；
- 设置面板新增「我的页背景透明度」滑块。

### 6. 四页背景（全屏蒙层）下线
- 移除设置面板中的「四页背景」分组入口（含四个换图按钮与透明度按钮）。
- 该方案用全屏 `ImageView` 蒙层实现，没有更换入口、且会漏到扫一扫/拍摄页，改用「我的页背景」。
- `TabBgDrawHook` 保留，但新增 `isExcludedPage()`：
  扫一扫（`com.tencent.mm.plugin.scanner.*`）、拍摄/视频录制
  （`com.tencent.mm.plugin.recordvideo.*`）、图片查看（`...chatting.gallery.*`）
  这三类 Activity 不再挂壁纸蒙层。

### 7. 新增：聊天工具栏（仿 WeKit）
文件：新增 `ChatToolbarFeature.java`、`ChatToolbarSettingCard.java`；
`HookEntry.smali` 注册入口；`SettingsPanelHelper.show()` 末尾追加卡片。

- 在聊天页输入框上方插一排快捷工具（相册/拍摄/视频通话/语音通话/位置/红包/转账/
  文件/收藏/接龙/个人名片/音乐/快捷回复），横向可滚动。
- 工具不自己实现业务，而是定位 AppPanel 里的 GridView，按格子文字匹配后
  `performItemClick`，沿用微信原生逻辑。
- 「快捷回复」点击填入输入框，**长按**可直接编辑内容（由 `chat_toolbar_reply` 保存）。
- 开关复用设置面板同款蓝色胶囊样式（自绘 `PillSwitch`，不用系统 Switch）。

### 8. 会话筛选栏调整
- 白底去掉（背景色 `-1` → `0` 透明）。
- 位置移到首页卡片上方（`FilterBarPlacement.moveAboveCards`）。

1. 背景蒙层当前会拦截触摸事件，需给 overlay 设置触摸穿透
   （如 `setClickable(false)` 已设，但仍需 `setFocusable(false)` 之外的处理，
   或改为 `updateHitTestResult` 覆盖 / 使用 `ViewGroup` 的 `dispatchTouchEvent` 透明）。
2. 背景蒙层目前每帧在 `dispatchDraw` 里重复设置，建议加一次性标记减少开销。
3. 黄历接口 https://www.36jxs.com/api/Commonweal/almanac 已返回反爬 HTML，解析必失败，
   日历卡片的农历/宜忌无法刷新（本次未处理）。
4. 首页顶栏依赖配置：`top_profile_enabled=true`，且需设置
   `top_avatar_path` / `top_nickname` / `top_status` 才有内容显示。

## 本轮进度（2026-11-06）

### 9. 设置面板清理（已改完，随本轮包安装）
- 删除「毛玻璃压制」（`frost_glass_enabled`）与「自定义加号面板（实验版）」整块
  （SettingsPanelHelper.smali 13760–13860 区间整段移除，寄存器准备代码一并清理）。
- 「聊天气泡（实验版）」改名「自定义气泡」。

### 10. 新增功能（java 侧已实现）
- `AntiRevokeFeature`：消息防撤回，hook `e01.u#q7`（sysmsg 分发器 revokemsg 分支），
  `anti_revoke_enabled` 开关控制，默认关。
- `MarkAllReadFeature` + `QuickActionCard`：一键已读卡片（清公众号红点走 `na5.u#g`）。
- `ConversationCardFeature` + `ConversationCardSettingCard`：会话卡片化（置顶分组整卡、
  圆角拼接、点击返回补涂、分割线识别隐藏）。
- `FilterListUnlockHeight`：筛选列表高度解锁。
- `GlobalWallpaper` / `PlusMenuExtra`：全局壁纸、加号面板扩展项。
- `x4/e.smali`（筛选后返回刷新）注入 refreshAdapterRef + scheduleSync，
  修复筛选后返回跳回「全部」。

### 11. 构建与安装（2026-11-06 15:02）
- apktool 重建 + merge classes2.dex（STORED）+ zipalign + apksigner，dex header 校验
  class_defs_size=23625 正常。
- 已推送安装（/data/local/tmp/w.apk → pm install -r -d），重启微信后 LSPosed 日志确认：
  会话卡片/快捷工具/悬浮底栏/背景守卫全部注册成功，无崩溃。
- 首页截图确认：顶栏头像昵称状态、筛选栏（全部=绿色胶囊、#EDEDED 浅灰容器）、
  卡片化列表、悬浮底栏（胶囊 864x158px）均正常。

### 设置面板入口（备忘）
- 聊天页「+」弹窗注入的菜单项（tag `wех_plus_item`，俄文 х）→ 点击
  `SettingsPanelHelper.show(activity, y/d callback)`。
- 注入链：HookEntry.access$injectPlusMenuItem ← z4/d ← z4/e（hook PopupWindow.addView）。

### 遗留
1. 一键已读清公众号红点需真实公众号新推送验证。
2. 底栏透明残留灰带待处理。
3. 黄历接口反爬（36jxs.com），日历卡农历/宜忌不刷新。
4. 搜索栏/首页快捷工具容器色 `#FEFEFE` 反色修复待验证。

## 本轮修复与新增（2026-11-06 晚）

### 12. 设置面板闪退修复（VerifyError）
根因：删「毛玻璃压制」「自定义加号面板」两块时，把首个 `switchItem$default {v0..v13}`
前的寄存器重排块一起删了（v10 应为 float 却残留 LinearLayout 引用），
DexOpt 校验 `show()` 失败 → 打开面板即崩。
修复：从 11-02 备份（wex_src_v1.2.27_patched_v2.zip）提取原重排块插回
（v0=p0、v1=p1、v10=0f、v11=0、v12=0x600、v13=null 等 21 条 move/const），
并用寄存器数据流模拟脚本对 3 个调用点逐一比对原版，全部一致。

### 13. 自定义气泡补全（新）
- 新增 `BubbleStyleCard.java`：模式三选 chip（毛玻璃渐变/自定义图片/透明气泡）、
  更换气泡图片（`bubble_bg_path`）、图片不透明度滑块（`bubble_bg_alpha`，默认 230）。
- 新增 `BubbleApply.java`：`x4/l#pswitch_3` 入口先调 `apply()`，
  mode 0 返回 false 走原毛玻璃逻辑；mode 1 按 view 尺寸解码图片→居中裁切→
  圆角裁切（`bubble_blur_radius` 作圆角）→ BitmapDrawable 设 alpha，
  post+postDelayed(120ms) 两次刷新等布局稳定；mode 2 全透明+半透明白描边。
- 任何异常回退毛玻璃，不影响聊天页。
- `x4/l.smali` pswitch_3 开头插 6 行分发（`move-result v0` + `if-eqz` 跳回原逻辑）。

### 14. 设置面板分组重排（新）
面板尾部 java 卡片区按功能重新分组（`sectionTitle` 新标题）：
- **聊天**：聊天工具栏、自定义气泡
- **首页**：会话列表卡片、一键已读
- **背景**：卡片背景（我的页）、四页背景
- 顶部残留的空「更换背景」标题已删。
- 系统自带条目（界面美化/首页卡片/顶栏）顺序不动。

### 构建（16:40）
- classes2.dex = 101904 bytes（80 个 class，含 BubbleApply/BubbleStyleCard）
- 已安装重启验证：无 VerifyError、无 FATAL，LSPosed 日志全部正常注册。

## 第二轮（2026-11-06 17:29）

### 15. 悬浮底栏滑块事故与回滚
- LiquidSlider 直接包在 bar 外层导致 apply() 抛异常被吞 → tab 栏消失 + ANR。
- 已回滚该注入，底栏恢复正常（步骤④ 864x158px）。
- 重做方案（待做）：hook LinearLayout.dispatchDraw after 在 canvas 上画滑块，零布局改动。

### 16. 气泡图片分对方/我方（随 17:29 包生效）
- `bubble_bg_path_peer`（对方）/ `bubble_bg_path_self`（我方），BubbleApply 按
  气泡中心相对聊天列表中线判断左右归属；一侧未配置时回退旧 key `bubble_bg_path`。
- BubbleStyleCard 换图按钮拆成两个：更换对方气泡图片 / 更换我方气泡图片。

### 17. 设置面板分组重排完成
- 聊天：自定义气泡开关、顶部样式组、快捷工具组、聊天底栏透明、会话筛选栏、
  聊天工具栏卡、气泡样式卡
- 首页卡片：图片卡、日历卡、音乐、卡片顺序、会话列表卡片、一键已读
- 顶栏：topBarGroup；背景：卡片背景、四页背景；其他：更新定位
- 「界面美化」标题改「聊天」；空「更换背景」「首页」标题删除。
- 关键修复：聊天卡插入点覆盖了 imageStyleGroup range 调用所需的 v3(int)，
  用 move/from16 v21 保存/恢复 v3 解决第二起 VerifyError。

### 18. 筛选栏容器色还原白色

## 第三轮（2026-11-07）

### 19. 自定义气泡支持 .9 图（NinePatch）
- BubbleApply 新增 tryNinePatch：解码时检测 npTc 块（NinePatch.isNinePatchChunk），
  命中即用 NinePatchDrawable 整体替换气泡（自动拉伸、不裁切不圆角），
  未命中回退原「缩放+居中裁切+圆角」路径。
- 选图走系统文档选择器（OPEN_DOCUMENT */*），拷贝为字节流（npTc 块保留）。
- 卡片描述注明支持 .9。

### 20. 作者信息改为原作者（仅 GitHub + 跳转）
- About 页「作者信息」卡改为单行：原作者 / Ql1121 · github.com/Ql1121/Wex
  （移除 版本/框架/目标 三行）。
- 页脚仅保留 GitHub 图标跳转（跳 github.com/Ql1121/Wex）；
  Telegram 行、t.me 链接方法（SettingsPage$lambda$2$0$0$0）与分发方法一并删除，
  k.smali 的 case0 改调 GitHub 分发。

### 21. 归属性注释清理
- 移除 java/smali 注释中的「仿 xx / 参照 xx / 完全按 xx / 同款 / 照搬 / WeKit / 参考 Kyant」
  等表述（ChatToolbarFeature、ConversationCardFeature、MarkAllReadFeature、
  LiquidSlider、BottomBarDrawSlider、ChatFooterBgGuard、ChatFooterTransparent 等）。

### 22. 底栏液态玻璃滑块搁置
- 拆除 dispatchDraw 钩子注册、attach 接线、x4/f 分支（sswitch_a）与设置面板开关卡。
- 源码（LiquidSlider/BottomBarDrawSlider/BottomBarPagerBridge/BottomBarSliderCard）保留
  备后续重做（纯绘制方案，零布局改动）。

### 23. 修复 SettingsPageKt VerifyError（点「我」闪退）
- 删除页脚第二块时连带删掉了 v2=int 恢复赋值，v2 残留 Modifier 引用导致
  c.<init>(I) 校验失败（模块 App 进程闪退）。已在保留块后补 const/4 v2, 0x1。

## 第四轮（2026-11-07）

### 24. 模块 App「关于」单页（首页/我的 合并）
- MainActivity / FeatureSettingsActivity 改为加载 AboutPageBuilder（纯 View），
  原 Compose 双 tab（首页/我的）不再使用。
- 布局参照 HyperDuo 关于页：
  - 「关于」大标题（无刷新图标）；
  - 绿色激活卡（已激活 + 模块版本 + 对勾图标，按 log.txt 24h 内有写入判定激活）
    + 右侧统计小卡（作用域应用 1 / 已开启显示项 N，统计功能开关 key）；
  - 信息卡：系统版本(Build.DISPLAY·Build.ID) / 应用版本 / Xposed 框架(读
    org.lsposed.manager 版本，manifest 已加 queries) / 设备型号 / 作用域 /
    Android 版本 + 脚注；
  - 动作卡：原作者（Ql1121 · github.com/Ql1121/Wex，跳转原作者仓库）/
    查看仓库（跳转链接占位，待换用户仓库地址）；
  - 日志卡：仅「调试日志」开关（log_enabled），不显示日志明细。
- 环境信息含构建时间等动态值由 PackageManager 读取。
- 修复 build_java.ps1：d8 调用改 java.exe 直调 D8 主类（绕过 cmd 命令行长度上限）。

### 25. 气泡美化模块（dev.wxbubble）机制研究（待移植）
- 图片：/sdcard/WxBubble/bubble_in.png（对方）/ bubble_out.png（我方），mtime 缓存。
- toNinePatchDrawable：任意 PNG 自动转九宫格——有 1px 边框标记则解析
  （上/左=拉伸区、右/下=padding），无标记则「guess」取中间 1/3 为拉伸区；
  手工序列化 npTc chunk（ByteBuffer 小端）→ NinePatchDrawable。
- 应用：StateListDrawable（常态原图 + 按压/选中态 HSV 压暗 0.8 副本）、
  保存/恢复 padding、tag 幂等。
- 对方/我方来自消息绑定钩子的消息模型发送标志（不靠几何位置）；
  文本消息挂 MMNeat7extView，图片/表情挂对应容器。
