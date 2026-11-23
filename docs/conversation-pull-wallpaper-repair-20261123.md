# 微信会话页下拉壁纸与分组卡片修复（2026-11-23）

## 现象与根因

- 会话列表到顶后下拉，会创建全屏 `DynamicBackgroundGLSurfaceView`。
  采样器排除此 Surface 只能维持玻璃采样，无法阻止独立 GL 画面闪现。
- 真机视图树显示 `MainUIView` 的子节点依次包含 `DynamicBgContainer`、
  `GradientColorBackgroundView`、`ConversationListView` 和分组宿主。
  下拉时前两个背景均显示。分组卡片按面积选兄弟采样源，排除动态容器后，
  仍会选中排在列表前面的全屏渐变背景，导致胶囊短暂变成深紫色。
- 排除渐变背景后仍有浅紫色残留。真机诊断确认列表及卡片祖先的 alpha 均为 1，
  列表背景仍为 `#EDEDED`，但卡片范围内的列表实际绘制结果变成 `#DEDDE2`。
  微信 8.0.76 的 `ConversationListView.onDraw` 在下拉时绘制紫色过渡层
  （日间资源色 `#7A7396`，透明度随下拉距离变化）。模块添加的透明占位行露出该层，
  使卡片周围区域及玻璃采样同时变色。

## 修改

- `ConversationPullWallpaperGuard` 精确匹配
  `MainUIView → DynamicBgContainer → DynamicBackgroundGLSurfaceView`，
  由 `ConversationGroupTabsHost` 在绘制前将目标 Surface 设为 `INVISIBLE`。
  列表隐藏、宿主脱离或释放时恢复由保护器接管的可见性。
- Surface 隐藏或移除时刷新分组玻璃采样；正常分组定位沿用宿主逻辑。
- `ChatGlassSurface` 在会话页选取兄弟采样源时跳过上述动态容器及渐变背景，
  继续采样会话列表。
- `ConversationGroupTabsHost` 在绘制前同步原生占位行与列表的 `ColorDrawable` 背景色，
  使占位行覆盖列表自绘的过渡层，浮动卡片仍实时采样列表。
  背景颜色变化时同步更新；使用独立 Drawable，避免共享列表背景的 callback 和 bounds。
  列表不再使用颜色背景时清除占位行的旧背景。
- `ViewBackdropSampler` 对已确认的会话 GL 壁纸分支保留采样例外。
- 默认分组回归加入 `pull-wallpaper`；保护器测试补齐已接管 Surface 被移除后的恢复。
  玻璃专项增加同时存在 GL 容器与渐变背景的真实节点顺序。

## 验证

- 新增渐变背景用例在修复前失败，修改采样源筛选后通过。
- 占位行背景回归在修复前失败，修复后通过；覆盖独立背景、稳定帧复用、
  原位修改夜间颜色及移除背景。
- 分组全部 14 个用例通过，含 `pull-wallpaper` 与宿主生命周期检查。
- 背板状态 113 项、背板恢复 619 项、聊天玻璃 34 项、壁纸保护 13 项通过。
- 导航回归 336 项通过；`git diff --check` 通过。
- 当前 Java 源码重新生成清单并全量编译，复用同日已构建的资源与 Compose 载荷。
  合并资源检查、APK v3 签名、zipalign 检查通过；确认最终功能 DEX 包含壁纸保护类。
- 19:38 覆盖安装并重启微信。在已启用分组的会话列表录制轻下拉和长下拉，
  检查起始连续帧未见全屏 GL 闪现或分组卡片深紫色填充。
  随后用户反馈及复核确认仍有浅紫色变色，此版本并未完整解决问题。
- 19:55:42 覆盖安装最终 APK，19:56 录制轻拉、长拉进入“最近”及返回过程。
  检查录屏帧，分组卡片及周围占位区域保持中性色，返回稳定后“全部”标签边界为
  `[60,302][228,434]`。最终包已移除临时日志和位图采样诊断。
- 用户实测反馈：“目前算是修复了这个问题”。本次以当前版本作为修复结果。

## 产物与证据

- 已安装 APK：`build/pull-header-background-20261123-195434/MindLink-1.0.apk`。
- 同目录 `pull.mp4`、`pull-first-frames.png`、`pull-long-return.png` 记录下拉及返回过程；
  `settled.png` 与 `settled-ui.xml` 记录返回稳定状态。
- 根因诊断：`build/pull-backdrop-trace2-20261123-195026/ml-pull-1.png` 与
  `ml-pull-10.png` 分别记录修复前静止及下拉时的列表采样颜色。
