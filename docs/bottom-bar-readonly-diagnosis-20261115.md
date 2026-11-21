# MindLink 底栏三项问题：只读排查总结

- 日期：2026-11-15
- 范围：徽标更新、切换时指示器周围白色高亮、内容到顶后背景模糊失效。
- 状态：**完成源码只读排查，尚未修复，也未完成对应实机归因或验收。**
- 本轮授权：排查并编写本文档；不修改功能代码、不构建、不安装、不重启微信、不改设备配置。
- 证据：用户反馈、用户提供的一张白光截图，以及本会话读取到的源码和现有测试实现。

> 本文区分“已确认的代码行为”和“能够解释现象的原因候选”。未核实当前安装 APK、当前进程加载链与所读源码完全一致，不能将候选原因写成已确认的设备根因。源码定位对应排查时读取的版本；工作区存在并行修改，后续修改前需核对对应版本。

## 一、结论总览

| 问题 | 已确认的主要代码行为／缺口 | 当前判断 | 尚缺证据 |
| --- | --- | --- | --- |
| 红点、未读数字出现或消失不及时 | 依赖宿主事件读取；异步 Hook 注册后不补读；同 Activity 内首页重新可见、切页时不对账 | 优先怀疑清零等更新未被有效同步，自绘底栏继续使用旧状态 | 宿主实际更新方法是否命中、读取时序和 getter 值是否与原生徽标一致 |
| 切换时指示器左右大片白亮 | 白光裁剪到整条底栏，使用 PLUS 加亮；非触摸切换动画也可能触发 | 整栏交互白光层最吻合截图，不是普通 ripple 或仅边缘高光 | 截图对应包、该帧动画进度；去掉单一白光层的对照结果 |
| 内容到顶后背景模糊失效，滚动／等待／切页才恢复 | 采样或背景层失败可退成不透明纯色；若干恢复路径依赖外部内容变化或下一次绘制 | 优先怀疑失败后恢复触发不足，而非顶部位置主动关闭模糊 | 到顶瞬间具体失败原因、是否保留旧帧、后续是否产生内容修订和恢复帧 |

三项不是同一条故障链：**徽标属于状态同步；白光属于绘制合成；背景模糊属于采样与恢复。** 指示器仍有液态效果，不能证明底栏背景模糊正常。

## 二、当前排查到的底栏实现

本会话所读入口为：

```text
HookEntry
  → FeatureBootstrap
  → NavigationBarFeature
  → NavigationBarViews（Java 自绘）
       ├─ NativeTabBridge：读取原生徽标
       ├─ LiquidGlassDecoration / LiquidGlassMotion：交互光效和动画
       └─ ViewBackdropSampler / BackdropCaptureState / LiquidGlassLayer：背景采样和模糊
```

旧 `BottomBarComposeFeature` 初始化已从该入口移除。因此，不能将仓库保留的旧 Compose／FloatingTabBar 实现直接当成当前问题来源；这也不等于已确认设备加载的是上述版本。

定位：
- [HookEntry.smali](../smali/wex/ui/core/xposed/HookEntry.smali)，行 260、296。
- [FeatureBootstrap.java](../java_src/wex/ui/core/feature/FeatureBootstrap.java)，行 65–69。
- [NavigationBarFeature.java](../java_src/wex/ui/core/feature/NavigationBarFeature.java)，行 343–356。

## 三、问题 1：徽标更新不及时

### 用户现象

红色徽标出现或消失不及时，尤其是未读数字在消息已读后不能即时消失。

### 已确认的行为和缺口

1. 自绘底栏保存本地数字／红点状态，依赖刷新和宿主 Hook 重新读取，不是 Compose state 更新问题。
2. 异步安装徽标 Hook 后，没有立即补读一次。注册前发生的变化可能遗漏。
3. 同一 Activity 内返回首页、底栏从隐藏恢复、切页和 preDraw，没有重新核对徽标；重新绘制不等于重新读取。
4. **真正的 Activity `onResume` 会刷新。** 不能笼统说“所有返回路径都不刷新”。
5. 成功读取到 `0` 会覆盖旧数字，没有发现“忽略零值”的逻辑。
6. 反射读取失败返回 `-1,false`，倾向于隐藏徽标，而不是保留旧数字；这可能解释不出现，但不能直接作为已读后数字残留的原因。

核心定位：
- [NavigationBarFeature.java](../java_src/wex/ui/core/feature/NavigationBarFeature.java)，行 237–255：异步 Hook 安装；行 258–289：Activity 恢复刷新。
- [NavigationBarViews.java](../java_src/wex/ui/core/feature/NavigationBarViews.java)，行 408–415、440–464、575–589、619–646：读取与恢复／切页路径。
- [NativeTabBridge.java](../java_src/wex/ui/core/feature/NativeTabBridge.java)，行 110–124：读取结果和失败返回。

### 最吻合的候选链

```text
自绘底栏缓存未读数 N
  → 微信已读后实际变为 0
  → 清零事件未被有效 Hook 覆盖，或读取时宿主值尚未更新
  → 本地状态未重新读取
  → 继续显示 N，直到下一次有效事件／refresh／真正的 onResume
```

这条链能够解释反馈，但宿主是否漏事件或存在读取时序问题尚未实测。

### 后续最小方向（未实施）

- Hook 注册完成后补读；首页重新可见时对账。
- 验证 `N→0`、注册前清零、隐藏期间清零和正常新增未读。
- **不要用“点击微信页即强制清零”代替真实状态同步。**

## 四、问题 2：指示器周围异常白色高亮

### 用户现象

切换过程中，灰色选中胶囊左右出现大片白亮区域。截图中的宽白区应与胶囊边缘细描边分别分析。

### 已确认的绘制行为

`LiquidGlassDecoration.touch()` 在整条底栏胶囊内绘制白光：

- 裁剪对象是整个 `surface`，不是选中指示器。
- 第一层为全栏白色，强度 `0.06 × progress`。
- 第二层为跟随指示器中心的径向白光，强度 `0.12 × progress`。
- 使用 `BlendMode.PLUS` 加亮，在接近白色的背景上容易饱和。
- 径向半径为 `min(width,height) × 1.2`。按默认约 64dp 栏高，光团直径约 153.6dp，明显宽于单个选中胶囊。
- `animateTo()` 在目标改变时会执行 `press → target → release`，因此不是只有手指持续按住才可能发光，也不是所有分页回调都必然重新触发。

中间仍灰、两侧变白也符合绘制顺序：先画整栏白光，后画不包含这层白光的指示器快照，指示器另有黑色 tint；两侧白光保留，中间被覆盖。

核心定位：
- [LiquidGlassDecoration.java](../java_src/wex/ui/core/feature/LiquidGlassDecoration.java)，行 96–110：整栏和径向白光。
- [LiquidGlassMotion.java](../java_src/wex/ui/core/feature/LiquidGlassMotion.java)，行 18–40：切换触发及释放进度。
- [NavigationBarViews.java](../java_src/wex/ui/core/feature/NavigationBarViews.java)，行 520–571：整栏装饰与指示器绘制顺序。
- [LiquidGlassLayer.java](../java_src/wex/ui/core/feature/LiquidGlassLayer.java)，行 78–85：指示器黑色 tint。

### 判断边界

整栏交互白光是最吻合截图的来源。当前自绘点击 View 未安装普通 ripple，不应简单归因于 Material／Compose 点击波纹。

正常动画收尾会将白光进度归零。截图不能证明永久残留；若长时间静止仍有宽白区，需另查进度、背景采样、静态高光以及包版本。

### 后续最小方向（未实施）

- 优先单独处理整栏交互白光，缩小范围、降低加法强度，或与非触摸切换动画分离。
- 保留背景模糊、指示器折射、弹性动画和必要边缘高光。
- 单变量对照可只去掉 `decoration.touch()`，其余效果不变；本轮未制作或安装对照包。

## 五、问题 3：内容到顶后背景模糊失效

### 用户现象

内容滑到最顶部时，底栏背景模糊经常失效。需要再次滚动、等待，并切换不同页面，才会正常恢复。

### 已确认的基础行为

- 没有发现 `top == 0` 或到顶时主动关闭 blur 的分支；滚动／切页回调不修改模糊配置。
- `sampler.prepare()` 不可绘制，或者背景层录制／设置效果失败时，底栏会停止画背景节点，表面 alpha 改为 255。浅色回退为 `#F7F7F7`，深色为 `#191919`，即不透明纯色。
- 正常 `REUSE`／`SKIP` 会检查同环境有效缓存和 display list；有有效缓存可继续画旧帧，不会因为本帧未采样就必然退成纯色。
- 正常缓存复用不会主动清空 blur effect。

因此要区分两种表现：**无有效背景时的纯色回退**，与**旧背景帧停更**。不能把两者混成“模糊参数被关闭”。

定位：[LiquidGlassLayer.java](../java_src/wex/ui/core/feature/LiquidGlassLayer.java)，行 28–62；[ViewBackdropSampler.java](../java_src/wex/ui/core/feature/ViewBackdropSampler.java)，行 252–293。

### 候选 A：失败预算耗尽，真实列表变化未被识别

- 当前仍有 `MAX_FAILURES=3`；失败后使用 80ms／240ms 退避，耗尽后 `retryDelay=-1`，停止自动重试。
- 同环境下，新 `contentGeneration` 才能补充预算；普通 `changed()` 增加的 generation 不足以保证补预算。
- 内容变化主要观察 pager 的 dirty／layout 上升沿、尺寸、scrollX/Y，以及部分父背景属性；没有直接观察实际列表首项和偏移。
- 列表 `position/top` 回调只处理滚动／autoHide 状态，没有直接更新采样内容修订。

可能的链是：到顶附近捕获失败并耗尽预算，随后没有被识别到的新修订；无有效缓存时纯色回退，有缓存时旧帧停更。后续滚动／切页产生可识别的变化，才重新捕获。

**必要边界：** 不能从“到顶”直接推定捕获失败；单纯切页也不保证补预算。这两步仍需设备证据。

定位：[BackdropCaptureState.java](../java_src/wex/ui/core/feature/BackdropCaptureState.java)，行 54–102；[ViewBackdropSampler.java](../java_src/wex/ui/core/feature/ViewBackdropSampler.java)，行 142–185、269–272；[NavigationBarViews.java](../java_src/wex/ui/core/feature/NavigationBarViews.java)，行 112–122、263。

### 候选 B：暂时不满足采样安全条件，恢复没有自行触发

可见交集、尺寸、父子关系、alpha、matrix、真实背景覆盖、SurfaceView／TextureView 等安全检查失败时，采样器会丢弃节点并取消重试。

恢复主要依赖下一次 `preDraw` 检查并请求绘制，没有门禁失败后的定时恢复探测。因此，条件恢复后若没有新帧，可能仍需用户滚动或切页来触发恢复。

**必要边界：** 普通列表到顶不必然改变这些属性；顶部回弹是否触发安全检查失败尚未确认。修复不应简单移除安全检查。

定位：[ViewBackdropSampler.java](../java_src/wex/ui/core/feature/ViewBackdropSampler.java)，行 199–245、296–301、330–386。

### 候选 C：背景层失败后，静止时缺少重建机会

背景层自身录制／设置效果异常后，会清理节点并退成纯色，但不自行安排恢复帧。若采样器缓存成功，它也不会启动采样失败定时器。

下一次滚动或切页产生绘制时，背景层可以重建；静止时可能一直等不到执行机会。是否实际发生 RenderNode／RenderEffect 异常尚未确认。

另有条件性风险：若缓存状态判定可复用，而 source display list 已不存在，最终可能返回不可绘制；本轮没有找到已证实的节点丢失来源，不将其作为主因。

定位：[LiquidGlassLayer.java](../java_src/wex/ui/core/feature/LiquidGlassLayer.java)，行 39–47、141–146；[NavigationBarViews.java](../java_src/wex/ui/core/feature/NavigationBarViews.java)，行 369–378。

### 后续最小方向（未实施）

- 将真实列表滚动／内容提交接入独立采样内容修订，避免只依赖 pager 的 dirty 上升沿，也避免把底栏自身重绘当成无限重试理由。
- 为暂态不可用和背景层失败提供有界恢复机会；无有效节点时不能仅判定缓存可复用。
- 保留同环境最后有效帧，区分暂时跳采样、捕获失败和结构不可用。
- 不以增大模糊强度、涂纯色背景或删除安全门禁替代恢复链修复。

## 六、现有测试为什么不能排除这些问题

**本轮只阅读测试，没有执行测试。** 以下是覆盖范围判断，不是新的通过结果。

- 徽标测试的假宿主没有有效 unread 接口，动态解析被强制失败，没有覆盖真实 `N→0` 同步链。
- 背景恢复测试显式设置 dirty 为 false／true，人工制造上升沿；不能证明实际列表变化必然反映到 pager。
- 恢复后的 `prepare()`／绘制由测试主动调用，不能证明到顶静止时会自行产生下一帧。
- View fixture 的 dirty、invalidate 和绘制是人工字段／计数／字符串记录，不等价于 Android 父子 dirty 传播、HWUI 或 RenderThread。
- 导航测试使用渲染替身，真实采样测试使用 Feature 替身，缺少真实列表到顶驱动真实导航、采样和背景层的完整联合覆盖。

测试定位：
- [徽标假宿主](../scripts/tests/navigation-hooks/com/tencent/mm/ui/LauncherUIBottomTabView.java)，行 2–14；[解析替身](../scripts/tests/navigation-hooks/wex/ui/core/feature/HostTargetResolver.java)，行 16。
- [BackdropRecoveryChecks.java](../scripts/tests/backdrop-recovery/wex/ui/core/feature/BackdropRecoveryChecks.java)，行 197–240、374–415。
- [View fixture](../scripts/tests/backdrop-recovery/android/view/View.java)，行 20–33、63–92。
- [NavigationRenderingDoubles.java](../scripts/tests/navigation-hooks/wex/ui/core/feature/NavigationRenderingDoubles.java)，行 6–21。

## 七、后续验证清单与授权边界

以下全部**待验证／未执行**：

1. 核实具体 APK 身份、当前进程实际加载链和相关配置；不沿用历史安装记录作为本轮证据。
2. 徽标：比较事件发生、原生计数读取、自绘状态更新的顺序，分别验证新增未读和已读清零。
3. 白光：对比按住、抬起、切换和完全静止阶段，区分宽交互光团与细边缘高光。
4. 模糊：对照“到顶前 → 到顶失效 → 静止等待 → 再滚动 → 切页恢复”，区分纯色回退与旧帧停更。
5. 若现有诊断可用，关联采样原因、重试状态、内容变化和下一次绘制。当前日志有次数限制且缺部分状态字段，没有错误日志不足以排除上述候选。
6. 不采集聊天正文或消息数据库；只记录必要事件、阶段、计数和时间。设备观察、代码修改、对照包构建及安装需按对应范围另行授权。

本文件的创建仅完成排查结论归档。**没有任何一项被标记为已修复或实机通过，也不扩大到其他会话已批准的修复范围。**
