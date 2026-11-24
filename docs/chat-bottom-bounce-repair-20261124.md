# 聊天底部回弹落点修复（2026-11-24）

## 现象与证据

聊天列表到最后一条消息后继续上拉，松手时消息先沉到悬浮输入卡片后面，再跳回正常位置。

小米 14、微信 8.0.76 实机录屏复现。临时有界几何日志显示列表祖先链为：

`ScrollControlRecyclerView → MMChattingListView → FrameLayout → MMWeUIBounceView → ChattingContent`。

旧逻辑仅抵消 `MMPullDownView` 的 scrollY。实际触发的是 `MMWeUIBounceView` 平移其内容 FrameLayout：上拉时 translationY 到 -676px，输入卡片保持 y=2219、列表高 2670px；计算的底部遮挡从 451px 缩为 0。回弹过程中尾部留白仍为 0，最后一条消息落到 y=2652，等留白恢复后才回到 y=2201。

## 修改

`ChatTailSpacer.pullOffset()` 在原有滚动补偿之外，识别 `com.tencent.mm.ui.widget.pulldown.MMWeUIBounceView`，抵消其直接内容子节点的临时 translationY。只处理列表到输入卡片公共父容器之间的路径，保留键盘抬升、窗口缩放以及公共祖先移动的真实几何关系。

此处仅读取回弹位移并稳定尾部留白，不改写宿主动画或触摸处理。类识别结果按类型缓存；现有 decoration 释放逻辑继续适用。临时 `MdlBounce` 日志已从最终代码移除。

## 验证

- 新测试使用实机祖先结构，放置最后一条消息，逐帧检查上拉、保持和回弹时的屏幕底边及留白；另覆盖两种回弹同时存在、分数位移、键盘抬升、顶部下拉、历史阅读位置和释放。
- 修复前新增用例在 translationY=-30.5 时失败；修复后通过。
- 输入栏：missing 142、present 512、overlay 167，共 821 项断言通过。
- 共用替身的顶栏测试：missing 116、present 117、分体几何 107 及 tips contracts 通过。
- Tail bridge DEX 完整性及宿主加载边界检查通过。
- Java 全量编译、D8、资源与隔离载荷合并、APK v3 签名及 zipalign 校验通过；复用前轮已验证的资源和 Compose 载荷。
- `git diff --check` 通过；生产源码哈希与构建记录一致。
- 最终包已覆盖安装并重启微信。用户实机反馈：「可以的正常了」，作为本次回弹问题验收结果。键盘和表情面板另有自动回归覆盖，本轮未完成安装后单独实机复验。
- 本轮运行相关专项回归；已知历史 `pre-fix-observe` 用例缺 `FloatingChatFooterFeature.adopt` 的编译问题保持原状，未宣称全量回归通过。

## 产物

- 安装包：`build/chat-bounce-20261124-01/MindLink-1.0.apk`。
- SHA-256：`6a5782d7497c6b9c97aada16aa34e2cf4c676cac354ac96e3f1be2ffc6d57117`。
- 源码哈希：同目录 `bounce-source-hashes.json`。
- 证据目录沿用开始排查时创建的 `build/chat-bounce-20261123/`：`before2.mp4`、`before2-contact.png`、`bounce-trace2.txt`、`checks-before.log`、`checks-after.log`、`footer-checks.log`、`header-checks.log`、`build-01.log`。

工作区同时保留此前通讯录、发现页留白和朋友圈顶栏改动。代码尚未提交或推送。
