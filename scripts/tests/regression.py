"""本地回归检查：python -I scripts/tests/regression.py [--jdk <JDK根目录>]。"""
import argparse
import base64
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[2]


def read(relative):
    return (ROOT / relative).read_text(encoding="utf-8-sig")


def method(smali, name):
    return next(block for block in smali.split(".end method")
                if re.search(r"^\.method[^\n]* " + re.escape(name) + r"\(", block, re.M))


def check_smali_registers(relative):
    text = read(relative)
    for block in text.split(".end method"):
        header = re.search(r"^\.method (?P<flags>[^\n]*?) (?P<name>[^\s(]+)\((?P<args>[^)]*)\)", block, re.M)
        if header is None:
            continue
        types = re.findall(r"\[+(?:L[^;]+;|[ZBCSIFJD])|L[^;]+;|[ZBCSIFJD]", header["args"])
        parameters = (0 if "static" in header["flags"].split() else 1)
        parameters += sum(2 if item in ("J", "D") else 1 for item in types)
        declaration = re.search(r"^\s*\.(locals|registers) (\d+)", block, re.M)
        if declaration is None:
            continue
        count = int(declaration[2]) + (parameters if declaration[1] == "locals" else 0)
        instructions = re.sub(r'"(?:\\.|[^"\\])*"', '""', block)
        instructions = re.sub(r"#[^\n]*", "", instructions)
        for kind, index in re.findall(r"\b([pv])(\d+)\b", instructions):
            assert int(index) < (parameters if kind == "p" else count), (
                relative, header["name"], kind + index, "register out of bounds")
        for line in block.splitlines():
            call = re.match(r"\s*invoke-(\S+)\s+\{([^}]*)\},\s+[^;]+;->[^\(]+\(([^)]*)\)", line)
            if call is None:
                continue
            kind, registers, args = call.groups()
            slots = re.findall(r"\[+(?:L[^;]+;|[ZBCSIFJD])|L[^;]+;|[ZBCSIFJD]", args)
            expected = sum(2 if item in ("J", "D") else 1 for item in slots)
            expected += 0 if kind.startswith("static") else 1
            if ".." in registers:
                first, last = [item.strip() for item in registers.split("..")]
                absolute = lambda reg: int(reg[1:]) + (count - parameters if reg[0] == "p" else 0)
                actual = absolute(last) - absolute(first) + 1
            else:
                actual = len([item for item in registers.split(",") if item.strip()])
            assert expected == actual, (relative, header["name"], "invoke argument slots", line)


def static_checks():
    shell = read("java_src/wex/ui/core/feature/SettingsShell.java")
    model = read("java_src/wex/ui/core/feature/SettingsPanelModel.java")
    spec = read("ui-compose/sidepanel/src/main/java/dev/mindlink/compose/SettingsSpec.kt")
    # 首页新分组：用户分组/悬浮底栏/侧滑面板 开关接线
    for key in ["conversation_filter_enabled", "bottom_bar_enabled", "home_side_panel_enabled"]:
        assert 'flag("%s"' % key in model, key
    # 聊天新分组：顶栏和输入栏均由专用可调卡接线。
    assert 'flag("floating_chat_header_enabled", false, FloatingChatHeaderFeature::refresh)' in model
    assert "floating_chat_header_enabled" in read("java_src/wex/ui/core/feature/FloatingChatHeaderFeature.java")
    assert 'flag("floating_chat_footer_enabled", false, FloatingChatFooterFeature::refresh)' in model
    assert "floating_chat_footer_enabled" in read("java_src/wex/ui/core/feature/FloatingChatFooterFeature.java")
    # 聊天卡片材质：顶栏和输入栏共用首页底栏那套玻璃，且只作为卡片自身背景填充。
    glass = read("java_src/wex/ui/core/feature/ChatGlassSurface.java")
    assert "NavigationBarFeature.KEY_EFFECT" in glass and "NavigationBarFeature.KEY_BLUR" in glass
    assert "sampler.source(host, backer)" in glass, "卡片只能采样兄弟列表，不能整父容器采样"
    for name in ["FloatingHeaderStyle", "FloatingChatFooterFeature"]:
        assert "ChatGlassSurface" in read("java_src/wex/ui/core/feature/" + name + ".java"), name
    # 旧分组已下线：不再接线 imageStyleGroup/calendarStyleGroup/cardOrderGroup/musicGroup
    for legacy in ['"imageStyleGroup"', '"calendarStyleGroup"', '"cardOrderGroup"', '"musicGroup"',
                   "home_image_card", "home_calendar_card", "home_music_card"]:
        assert legacy not in shell, legacy
    for entry in ["conversation_filter_enabled", "updateLocation(activity)"]:
        assert entry in model, entry
    assert 'setNegativeButton("取消", null)' in shell
    # 联网定位的同意文案由 M3 面板的确认对话框承担。
    assert "Nominatim" in spec and "不上传聊天、联系人或资料正文" in spec
    # 旧 View 拼装入口必须已经删干净；开关写入走 model 的 flag()/writer 并回读钳制值。
    for gone in ["addSettingSwitch", "addJavaCard", "fillGroup", "buildLegacy", "PillSwitch"]:
        assert gone not in shell, gone
    assert "boolean saved = SettingsChanges.saveBool(key, next);" in model
    assert "restartWechat" not in model and "hotRestart" not in model
    # 旧设置卡片类必须全部下线，只留仍需被 smali 调用的静态入口。
    for gone in ["BubbleStyleCard", "AboutCard", "HomeSidePanelCards", "ChatToolbarSettingCard",
                 "TabBgSettingsCard", "MyPageAlphaCard", "GlobalBackgroundCard", "BottomBarCard",
                 "NavigationBarCard", "FloatingChatFooterCard", "BottomBarSliderCard",
                 "FloatingChatHeaderCard"]:
        assert not (ROOT / "java_src/wex/ui/core/feature" / (gone + ".java")).exists(), gone
    for kept in ["HomeSidebarCards", "QuickActionCard", "TabBgAlphaPanel"]:
        assert (ROOT / "java_src/wex/ui/core/feature" / (kept + ".java")).exists(), kept
    consent = read("java_src/wex/ui/core/feature/LocationPrivacy.java")
    assert 'getBool(KEY_ENABLED, false)' in consent and 'return false;' in consent
    location = read("smali/wex/ui/core/feature/LocationHelper.smali")
    calendar = read("smali/wex/ui/core/feature/CalendarData.smali")
    assert "ip-api.com" not in location and "->ipLocate(" not in location
    for text, name, send in [(location, "httpGet", "URL;->openConnection"),
                             (location, "updateLocation", "Thread;->start"),
                             (location, "updateLocation$lambda$0", "->getLastLocation"),
                             (calendar, "httpGet", "URL;->openConnection"),
                             (calendar, "fetchWeatherInfo", '"wp_lat"')]:
        block = method(text, name)
        assert block.index("LocationPrivacy;->isEnabled()Z") < block.index(send), name
    # 验证改动方法的跳转和 catch 范围引用均能解析；不代替 ART 实机验证。
    for text in [location, calendar]:
        for block in text.split(".end method"):
            if "LocationPrivacy;" not in block:
                continue
            labels = re.findall(r"^\s*(:[\w]+)\s*$", block, re.M)
            assert len(labels) == len(set(labels)), "duplicate labels"
            references = re.findall(r"^\s*(?:if-[\w/-]+|goto(?:/\d+)?)\s+[^\n]*?(:\w+)\s*$", block, re.M)
            references += re.findall(r"^\s*\.catch[^\n]*?\{(:\w+) \.\. (:\w+)\} (:\w+)", block, re.M)
            for ref in references:
                for label in ((ref,) if isinstance(ref, str) else ref):
                    assert label in labels, label
    # 关于页（日志/激活状态）已并入 M3 面板：默认开启、只读日志、不发起外链。
    assert 'flag("log_enabled", true)' in model
    assert "Uri.fromFile" not in model and "startActivity" not in model
    assert "LogPreview.read" in model and "LOG_PATH" in model and "active()" in model
    build = read("scripts/build.ps1")
    assert "-ArgumentList $commandLine" in build
    assert '"--output", $DEX, $classesJar' in build
    assert '"-C", $OUT, "."' in build
    assert (ROOT / "scripts/build.ps1").read_bytes().startswith(b"\xef\xbb\xbf"), "PowerShell BOM"
    for token in ["MINDLINK_SIDEPANEL_GRADLE", "$env:ANDROID_HOME = $SDK", "$env:ANDROID_SDK_ROOT = $SDK",
                  "build_scoped_features.py", "--sidepanel-ui-apk", "CandidateSkipSidePanelLint"]:
        assert token in build, token
    assert "preserve_desktop_payload.py" not in build and ":app:assembleRelease" not in build, "桌面载荷必须不再编译或复用"
    assert r"$BUILD\resources\full-stable-ids.txt" in build
    for name in ["ImagePicker", "TopBarFeature", "TopBarHomeHook", "CalendarData", "LocationHelper", "BubbleFeature"]:
        check_smali_registers("smali/wex/ui/core/feature/" + name + ".smali")
    picker = read("smali/wex/ui/core/feature/ImagePicker.smali")
    assert "->getMethod(Ljava/lang/String;)" not in method(picker, "forceStopSelf")
    assert "const-wide/16 p0" not in method(picker, "hotRestartWechat")
    topbar = read("smali/wex/ui/core/feature/TopBarFeature.smali")
    top_init = method(topbar, "init")
    assert "top_profile_enabled" not in top_init
    for flag in ["resumeHookRegistered", "titleHookRegistered"]:
        assert "sget-boolean" in top_init and "->" + flag + ":Z" in top_init
    assert "isHomeActivity" in method(topbar, "applyTopBar")
    top_apply = method(topbar, "applyTopBar$lambda$0")
    assert "isHomeActivity" in top_apply and "->removeOwnedView(" in top_apply
    assert "->updateSearch(" in top_apply and "->updateTitle(" in top_apply
    insert_profile = method(topbar, "insertProfile")
    assert "->buildProfile(" in insert_profile and "->removeOwnedView(" in insert_profile
    assert "->indexOfChild(" in insert_profile and "->removeView(" in insert_profile
    top_hook = read("smali/wex/ui/core/feature/TopBarHomeHook.smali")
    assert "top_profile_enabled" not in method(top_hook, "afterHookedMethod")
    assert "refreshTopBar(activity);" in shell
    assert "onViewDetachedFromWindow" in shell
    toolbar = read("java_src/wex/ui/core/feature/ChatToolbarFeature.java")
    init = toolbar[toolbar.index("public synchronized void init"):toolbar.index("static boolean isEnabled")]
    assert "getBool(KEY_ENABLED" not in init and "HOOKED.contains(ctor)" in init
    assert "observeFooter" in init and "restoreBackgrounds(null);" in toolbar
    assert "param.args[0] = null" not in toolbar
    assert "param.thisObject instanceof ViewGroup" in init
    assert "param.thisObject instanceof FrameLayout" not in init
    assert "observeFooter(final ViewGroup footer)" in toolbar
    assert "attach(ViewGroup footer)" in toolbar
    assert "WeakHashMap<FrameLayout" not in toolbar and "WeakReference<FrameLayout" not in toolbar
    assert "return ChatToolbarViews.findInputContainer(root);" in toolbar
    assert 'flag("chat_toolbar_enabled", false, ChatToolbarFeature::refresh)' in model
    manifest = read("AndroidManifest.xml")
    assert manifest.count("android.intent.category.LAUNCHER") == 1, "保留唯一桌面入口供宿主发现模块资源"
    assert 'android:name="wex.ui.core.MainActivity"' in manifest, "桌面入口应使用现有 MainActivity"
    for legacy in ['android:name="xposedmodule"', 'android:name="xposedscope"', 'android:name="xposedminversion"', 'android:name="xposeddescription"']:
        assert legacy not in manifest, "standard 入口不再声明 legacy 元数据: " + legacy
    assert not (Path(__file__).resolve().parents[2] / "assets/xposed_init").exists(), "legacy assets/xposed_init 应已删除"
    assert read("unknown/META-INF/xposed/java_init.list").split() == ["dev.mindlink.xposed.ModuleEntry"]
    assert read("unknown/META-INF/xposed/scope.list").split() == ["com.tencent.mm"]
    assert "staticScope=true" in read("unknown/META-INF/xposed/module.prop").split()
    apktool_meta = read("apktool.yml")
    for packed in ["java_init.list", "module.prop", "scope.list"]:
        assert f"META-INF/xposed/{packed}:" in apktool_meta, packed
    hook_entry = read("smali/wex/ui/core/xposed/HookEntry.smali")
    assert "Lde/robv/android/xposed/" not in hook_entry and ".implements Ldev/mindlink/xposed/IXposedHookLoadPackage;" in hook_entry
    dashboard = read("java_src/wex/ui/core/feature/ComposePayloadBridge.java")
    state = read("java_src/wex/ui/core/feature/ModuleDashboardState.java")
    facade = read("java_src/wex/ui/core/feature/AboutPageBuilder.java")
    assert "ComposePayloadBridge.build" in facade and "ModuleDashboardPage" not in facade
    payload_loader = read("java_src/wex/ui/core/feature/ComposePayloadLoader.java")
    assert "Application.getProcessName()" in dashboard and "Compose UI is desktop-process only" in dashboard
    assert "ComposePayloadLoader.entry" in dashboard and '"sidepanel-compose-payload"' in dashboard
    assert '"dev.mindlink.compose.DesktopUi"' in dashboard
    assert "InMemoryDexClassLoader" in payload_loader
    assert "Activity.class.getClassLoader()" in payload_loader and "getSystemClassLoader" not in payload_loader
    assert 'prefix + "/index.txt"' in payload_loader and "MessageDigest" in payload_loader
    assert '"sidepanel-compose-payload"' in payload_loader and "LOADERS.get(prefix)" in payload_loader
    assert "((java.nio.Buffer) buffer).flip()" in payload_loader and "buffer.put(bytes).flip()" not in payload_loader
    for relative in ["MainActivity", "FeatureSettingsActivity"]:
        activity = read("smali/wex/ui/core/" + relative + ".smali")
        assert ".super Landroid/app/Activity;" in activity
        assert "AboutPageBuilder;->save" in activity and "AboutPageBuilder;->back" in activity
        check_smali_registers("smali/wex/ui/core/" + relative + ".smali")
    assert "com.tencent.mm" in state and '"8.0.76"' in state and "宿主状态未知" in state
    assert "WexStore" not in state and "OPEN_READONLY" in state and "NO_LOCALIZED_COLLATORS" in state
    assert "WexStore;" not in read("smali/wex/ui/core/MainActivity.smali")
    check_smali_registers("smali/wex/ui/core/MainActivity.smali")
    bubbles = read("java_src/wex/ui/core/feature/BubbleApply.java")
    glass = read("java_src/wex/ui/core/feature/BubbleGlass.java")
    bubble_hook = read("smali/x4/f.smali").split(":pswitch_f\n", 1)[1].split("return-void", 1)[0]
    assert "->onBackground(Ljava/lang/Object;)V" in bubble_hook
    assert "getBlurRadius" not in bubble_hook and "DoneViews" not in bubble_hook
    assert "bubble_enabled" not in method(read("smali/wex/ui/core/feature/BubbleFeature.smali"), "init")
    assert "cond_bubble_fallback" not in read("smali/x4/l.smali")
    check_smali_registers("smali/x4/l.smali")
    assert "BubbleGlass.capture(view, BubbleSettings.blurDp())" in bubbles
    assert "mode == 2" in bubbles and "mode == 1" in bubbles
    assert "APPLYING.remove()" in bubbles and "generation.accepts" in bubbles
    assert "work.cancel(true)" in bubbles and "setBackground(view, original)" in bubbles
    assert "removeOnPreDrawListener" in bubbles and "BubbleApply.refresh();" in model
    assert "setMinimumWidth(0)" not in bubbles and "setElevation(0f)" not in bubbles
    assert "BubbleBlur.blur(" in glass and "drawable.draw(canvas)" in glass
    assert "source.draw(" not in glass and "root.draw(" not in glass and "bubble.draw(" not in glass
    assert "setRenderEffect" not in glass and "FileOutputStream" not in glass
    assert "BubbleGlass.isCurrent(view, sourceStamp)" in bubbles and "sourceStamp = sample.source" in bubbles
    assert "MAIN.removeCallbacks(inspectSource)" in bubbles and "sourceCheckQueued" in bubbles
    restore = bubbles[bubbles.index("void stop(boolean restore)"):bubbles.index("public void onViewAttachedToWindow")]
    assert "if (restore && view != null)" in restore
    assert "getMinimumWidth()" not in restore and "getElevation()" not in restore
    assert "getLocalVisibleRect" in glass and "region.extendEdges" in glass
    assert "getGenerationId()" in glass and "getImageMatrix().getValues" in glass
    assert "contains(view, bubble)" not in glass
    hooks = read("smali/wex/ui/core/xposed/HookEntry.smali")
    assert "ChatTopBarTransparent;->init" not in hooks and "ChatFooterTransparent;->init" not in hooks
    header = read("java_src/wex/ui/core/feature/FloatingChatHeaderFeature.java")
    footer = read("java_src/wex/ui/core/feature/FloatingChatFooterFeature.java")
    assert 'optionalHook(layout, "onLayout"' in header
    assert 'inherited(type, name, parameters)' in header
    assert 'hookAllMethods(layout, "onAttachedToWindow"' not in header
    assert 'findHeader(layout, activity)' in header and 'findActionBar(root)' not in header
    assert "HeaderParent.capture" in header and "originalHeaderParent.restore" in header
    assert "FloatingHeaderStyle.applySeparated" in header and "applyListPadding(layout, header)" in header
    assert "floating_footer_refresh" in footer and "floating_footer_switch" in footer
    assert "PanelState.capture" in footer and "FloatingFooterLayout.siblingsHeight" in footer
    assert "floating_footer_config" in footer and "floating_footer_keyboard" in footer
    assert "setPortHeighPx" in footer and "yFromBottomWithoutPanel" in footer
    assert "floating_footer_fullscreen" in footer and "FULLSCREEN_TRANSLATIONS" in footer
    assert "StripState.capture" in footer and "panel.setVisibility(visibility)" in footer
    assert 'flag("floating_chat_header_enabled", false, FloatingChatHeaderFeature::refresh)' in model
    assert 'flag("floating_chat_footer_enabled", false, FloatingChatFooterFeature::refresh)' in model
    settings_checks(shell)
    filter_checks()
    sidebar_checks()
    menu_checks()
    theme_checks()
    print("PASS: configuration mapping, restored entries, consent gates, log default, build wiring, bubble routing and hook register bounds")


def settings_checks(shell):
    changes = read("java_src/wex/ui/core/feature/SettingsChanges.java")
    screen = read("ui-compose/sidepanel/src/main/java/dev/mindlink/compose/SettingsScreen.kt")
    # 未生效横幅由 M3 面板渲染，且排在内容列表之上。
    assert "存在未生效改动，需要重启微信" in screen
    assert screen.index("PendingBar {") < screen.index("LazyColumn(")
    assert "SettingsChanges.subscribe(changes)" in shell and "SettingsChanges.unsubscribe(changes)" in shell
    assert "public static void confirmRestart" in shell
    assert "getSettingChecked(key)" in changes and "Setting write not confirmed" in changes
    assert "WeakReference<Listener>" in changes and "MessageDigest.getInstance(\"SHA-256\")" in changes
    helper = read("smali/wex/ui/core/xposed/SettingsPanelHelper.smali")
    music = read("smali/z4/x.smali")
    for text in [helper, music]:
        assert "->hotRestartWechat(" not in text
        assert not re.search(r"Lwex/ui/core/WexStore;->(?:put|get)(?:String|Bool)\(", text)
        decoded = re.sub(r"\\u([0-9a-fA-F]{4})", lambda match: chr(int(match[1], 16)), text)
        assert "正在重启微信" not in decoded and "一键重启生效" not in decoded
    assert "->confirmRestart(Landroid/app/Activity;)V" in method(helper, "show$lambda$9")
    assert helper.count("->toast(Landroid/app/Activity;Ljava/lang/String;)V") == 3
    assert "->toast(" not in music
    for relative in ["smali/wex/ui/core/WexStore.smali", "smali/wex/ui/core/xposed/SettingsPanelHelper.smali", "smali/z4/x.smali"]:
        check_smali_registers(relative)
    checked = method(read("smali/wex/ui/core/WexStore.smali"), "getSettingChecked")
    assert "->rawQuery(" in checked and "->readAll(" not in checked and "throw" in checked
    picker = method(read("smali/wex/ui/core/feature/ImagePicker.smali"), "handleResult")
    assert picker.index("->imageBefore(") < picker.index("FileOutputStream;-><init>")
    assert "->saveImage(" in picker and ":image_setting_saved" in picker
    for name in ["QuickActionCard", "PickCallback"]:
        text = read("java_src/wex/ui/core/feature/" + name + ".java")
        assert "一键重启" not in text and "重启微信后生效" not in text
    print("PASS: fixed pending banner, checked settings bridge, no save-triggered restart and preserved error/operation feedback")


def filter_checks():
    feature = read("smali/wex/ui/core/feature/ConversationFilterFeature.smali")
    # 固定四分类已升级为用户分组：smali 只保留转发入口，不再有筛选栏/位置挂载逻辑。
    assert "ConversationGroupingFeature;->init(" in feature
    assert "ConversationGroupingFeature;->onHomeReady(" in feature
    assert "ConversationGroupingFeature;->releaseList(" in feature
    assert "FilterBarPlacement;->mount(" not in feature and "FilterBarStyle;->createBar(" not in feature
    grouping = read("java_src/wex/ui/core/feature/ConversationGroupingFeature.java")
    for entry in ["KEY_ENABLED = \"conversation_filter_enabled\"",
                  "KEY_GROUPS = \"conversation_groups\"",
                  "PRESET_UNREAD", "PRESET_GROUPS", "PRESET_FRIENDS", "PRESET_OFFICIALS",
                  "case MANUAL", "case SQL"]:
        assert entry in grouping, entry
    bridge = read("java_src/wex/ui/core/feature/ConversationListBridge.java")
    positions = read("java_src/wex/ui/core/feature/ConversationPositions.java")
    readonly = read("java_src/wex/ui/core/feature/ReadOnlySql.java")
    assert "ConversationPositions.build" in bridge and "raw(visible)" in bridge
    assert "visibleToRaw" in positions and "rawToVisible" in positions
    assert "static boolean accepts" in readonly
    bar = read("java_src/wex/ui/core/feature/ConversationGroupBar.java")
    assert "ConversationGroupingFeature.setActive" in bar
    reader = read("java_src/wex/ui/core/feature/ConversationAdapterSnapshotReader.java")
    assert "AccessPlan" in reader and "findRowField" in reader
    check_smali_registers("smali/wex/ui/core/feature/ConversationFilterFeature.smali")
    print("PASS: user grouping forwards legacy entries, member presets and adapter snapshot mapping")


def sidebar_checks():
    drawer = read("java_src/wex/ui/core/feature/HomeSidePanelFeature.java")
    cards = read("java_src/wex/ui/core/feature/HomeSidebarCards.java")
    topbar = read("smali/wex/ui/core/feature/TopBarFeature.smali")
    manager = read("smali/wex/ui/core/feature/HomeCardManager.smali")
    music = read("smali/x4/u0.smali")
    assert "HomeSidebar;->sync(" not in method(topbar, "applyTopBar$lambda$0")
    entry = method(manager, "insertCards$lambda$0")
    assert "HomeSidebar;->onHomeCards(" not in entry and "return-void" in entry
    assert "HomeSidebar;->configure(" in method(manager, "init")
    assert "->initialized:Z" in method(manager, "init")
    for token in ["LauncherUI 内抽屉浮层", "SESSIONS", "dispatchTouchEvent", "moveTaskToBack",
                  "consumeBack()", "open(true)"]:
        assert token in drawer, token
    assert "showDisplay()" in drawer and "SidePanelComposeBridge.build" in drawer
    assert "SidePanelComposeBridge.update(compose" in drawer and "callbacks());" in drawer
    assert "new FrameLayout(activity)" in drawer and "MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT" in drawer
    assert "saveCompose()" in drawer and "SidePanelComposeBridge.dispose" in drawer
    assert "ROUTE_ADD_CARD" in drawer and "ROUTE_CARD_SETTINGS" in drawer and "editorRoute" in drawer
    assert "editorDraft" in drawer and "add_card:" in drawer and "card_settings:" in drawer
    assert not (ROOT / "java_src/wex/ui/core/feature/HomeSidePanelCards.java").exists(), \
        "旧 View 侧栏卡片必须已删除"
    # The Compose editor owns padding via safe-drawing insets; the Java editor shell is gone.
    assert "SystemBarsCoordinator.padOverlay" not in drawer
    assert "editorScroll" not in drawer
    panel_layout = read("java_src/wex/ui/core/feature/HomeSidePanelLayout.java")
    panel_data = read("java_src/wex/ui/core/feature/HomeSidePanelData.java")
    panel_picker = read("java_src/wex/ui/core/feature/HomeSidePanelImagePicker.java")
    panel_wallet = read("java_src/wex/ui/core/feature/HomeSidePanelWallet.java")
    actions = read("java_src/wex/ui/core/feature/HomeSidePanelActions.java")
    assert "new Dialog" not in drawer
    payment = drawer[drawer.index("private void openPaymentCode()"):drawer.index("private void enterEditor(")]
    assert drawer.count("startActivity(") == payment.count("startActivity(") == 1
    assert "WalletOfflineCoinPurseUI" in payment and "MallIndexUIv2" in payment
    assert "close(false);" in payment and "ActivityNotFoundException" in payment
    assert "MotionEvent.ACTION_CANCEL" in drawer and "return dragging;" in drawer
    assert "static boolean saveLayout" in drawer and "Layout write was not confirmed" in drawer
    # Editing is Compose-driven: the editor keeps a draft snapshot and routes, not LinearLayout rows.
    assert "editorImportedAssets" in drawer and "cleanupOrphanAssets" in drawer
    assert "swap" not in drawer and "LinearLayout editor" not in drawer
    assert "org.json.JSONObject" in panel_layout and "org.json.JSONArray" in panel_layout
    assert "LocationPrivacy.isEnabled()" in panel_data and "weatherapi.market.xiaomi.com" in panel_data
    assert "v1.hitokoto.cn" in panel_data and "ACTIVE.add(key)" in panel_data
    assert "Intent.ACTION_OPEN_DOCUMENT" in panel_picker and 'setType("image/*")' in panel_picker
    assert "50_000_000L" in panel_picker and "HomeSidePanelImages.file" in panel_picker
    assert "USERINFO_NEW_BALANCE_LONG_SYNC" in panel_wallet and '"¥ --"' in panel_wallet
    assert "if (layout == null)" in drawer and "layout == null || layout.isEmpty()" not in drawer
    assert "HomeSidePanelFeature.close(activity)" in actions
    assert "HomeSidebarCards.class.getClassLoader()" in cards and "BUILD_WIDTH.remove()" in cards
    assert "if (shared.visibilityKnown && shared.visible == visible) return" in cards
    for name in ["setUiVisible", "releaseUi"]:
        block = method(music, name)
        assert "MediaPlayer;->" not in block and "Lx4/u0;->t()" not in block
        assert "removeCallbacks" not in block
    assert "->setVisibility(I)V" not in method(music, "setUiVisible")
    assert "->setVisibility(I)V" not in method(music, "f")
    assert "->isUiVisible()Z" in method(music, "setUiVisible")
    assert "HomeSidebarCards;->width(" in method(music, "f")
    timer = read("smali/q2/l.smali").split(":pswitch_0\n", 1)[0]
    assert "->getCurrentPosition()I" in timer and "->postDelayed(" in timer and "->isUiVisible()Z" in timer
    assert timer.index("->getCurrentPosition()I") < timer.index("->isUiVisible()Z")
    assert read("smali/h1/m.smali").count("->isUiVisible()Z") >= 3
    for name in ["CalendarStyleHuangli", "CalendarStyleMinimal", "CalendarStyleProgress", "CalendarStyleThreeDay"]:
        relative = "smali/wex/ui/core/feature/" + name + ".smali"
        text = read(relative)
        assert "HomeSidebarCards;->width(" in text and "HomeSidebarCards;->hasWidthOverride()Z" in text
        check_smali_registers(relative)
    for relative in ["smali/wex/ui/core/feature/HomeCardManager.smali", "smali/x4/u0.smali", "smali/q2/l.smali", "smali/h1/m.smali"]:
        check_smali_registers(relative)
    print("PASS: lazy sidebar card routing, safe entry/close wiring, width bridge and audio-preserving UI visibility")


def menu_checks():
    source = read("java_src/wex/ui/core/feature/PlusMenuExtra.java")
    hooks = read("smali/wex/ui/core/xposed/HookEntry.smali")
    bridge = method(hooks, "a")
    assert "PlusMenuExtra;->sync(Landroid/content/Context;Landroid/view/ViewGroup;Landroid/view/ViewGroup;Landroid/view/View$OnClickListener;)V" in bridge
    assert "Lx4/x0;-><init>(I)V" in bridge and "const/4 v1, 0x2" in bridge
    assert "iput-object p1, v0, Lx4/x0;->b:Ljava/lang/Object;" in bridge
    assert "postDelayed" not in bridge and "Lh1/e3;" not in bridge and "->addMarkAllRead(" not in bridge
    for entry in ["e", "f"]:
        block = method(hooks, entry)
        assert "findViewWithTag" not in block
        assert "->a(Landroid/content/Context;Landroid/view/ViewGroup;Landroid/view/ViewGroup;)V" in block
    for token in ["offsetDescendantRectToMyCoords", "COMPLEX_UNIT_PX", "getTextColors()", "getTypeface()",
                  "getConstantState()", "result.mutate()", "getSelector()", "addFooterView(group, null, false)",
                  "removeFooterView(existing)", "LinearLayout.VERTICAL", "new WeakHashMap<ViewGroup, WeakReference<MenuGroup>>",
                  "settings.setOnClickListener(settingsClick)", "MarkAllReadFeature.markAllRead((android.app.Activity) context)"]:
        assert token in source, token
    for token in ["performClick(", "performItemClick(", ".dismiss(", "postDelayed(", "0.85f", "COMPLEX_UNIT_SP"]:
        assert token not in source, token
    assert '"w\\u0435\\u0445_plus_item"' in source
    assert '"wex_mark_all_read_item"' in source and '"wex_setting_icon.png"' in source
    check_smali_registers("smali/wex/ui/core/xposed/HookEntry.smali")
    for entry in ["a", "e", "f"]:
        block = method(hooks, entry)
        labels = re.findall(r"^\s*(:[\w]+)\s*$", block, re.M)
        assert len(labels) == len(set(labels)), entry
        references = re.findall(r"^\s*(?:if-[\w/-]+|goto(?:/\d+)?)\s+[^\n]*?(:\w+)\s*$", block, re.M)
        references += [label for match in re.findall(r"^\s*\.catch[^\n]*?\{(:\w+) \.\. (:\w+)\} (:\w+)", block, re.M)
                       for label in match]
        assert all(label in labels for label in references), entry
    print("PASS: shared native menu wiring, preserved click delegates, no delayed reorder and legal Smali registers/labels")


def theme_checks():
    shell = read("java_src/wex/ui/core/feature/SettingsShell.java")
    theme = read("java_src/wex/ui/core/feature/SettingsTheme.java")
    spec = read("ui-compose/sidepanel/src/main/java/dev/mindlink/compose/SettingsSpec.kt")
    screen = read("ui-compose/sidepanel/src/main/java/dev/mindlink/compose/SettingsScreen.kt")
    # 面板外观改由 Material3 组件承担；Shell 只保留窗口背景色这一处主题耦合。
    assert "SettingsTheme.page(dark)" in shell
    for gone in ["SettingsTheme.applyCard", "SettingsTheme.tab("]:
        assert gone not in shell, gone
    for token in ["setStateListAnimator(null)", "setElevation(0f)", "setTranslationZ(0f)",
                  "setProgressTintList", "setSecondaryProgressTintList", "setProgressBackgroundTintList",
                  "setThumbTintList", "setTickMarkTintList", "setTextCursorDrawable", "setTextSelectHandle",
                  "titleId != 0", "android.R.id.message"]:
        assert token in theme, token
    for token in ["WexStore", "SettingsChanges", "setOnClickListener", "setOnSeekBarChangeListener"]:
        assert token not in theme, token
    # 卡片行由 Material3 组件渲染：开关用 Switch、滑杆用 Slider，不再手绘主题色。
    components = read("ui-compose/sidepanel/src/main/java/dev/mindlink/ui/MindLinkComponents.kt")
    assert "MindLinkSwitchRow(" in screen and "Switch(checked = checked" in components
    for token in ["Slider(", "Setting.Flag(", "Setting.Range(", "Setting.Photo("]:
        assert token in screen or token in spec, token
    # 全屏壁纸（四页蒙层）的开关/换图/不透明度必须仍在面板里可调。
    for key in ["tab_bg_enabled", "tab_bg_global", "tab_bg_alpha"]:
        assert key in spec, key
    # 硬编码色值只能出现在主题文件里，不得回到 settings 相关 Java。
    for name in ["SettingsShell", "SettingsPanelModel"]:
        text = read("java_src/wex/ui/core/feature/" + name + ".java")
        for color in ["#1976D2", "#E8F0FE", "#7FB2F0", "#2B3A4D", "#2A2C2B", "#F5F5F5"]:
            assert color not in text, (name, color)
    helper = read("smali/wex/ui/core/xposed/SettingsPanelHelper.smali")
    switch = read("smali/z4/w.smali")
    for name in ["calendarStyleGroup$refreshBtns", "imageStyleGroup$refreshBtns", "topbarStyleGroup$refreshBtns"]:
        assert "SettingsTheme;->option(Landroid/widget/TextView;Z)V" in method(helper, name), name
    assert helper.count("new-instance v6, Landroid/widget/SeekBar;") == 1
    assert "SettingsTheme;->styleSeekBar(Landroid/widget/SeekBar;)V" in method(helper, "musicGroup")
    assert "SettingsTheme;->styleInput(Landroid/widget/EditText;)V" in helper
    assert "SettingsTheme;->showDialog(Landroid/app/AlertDialog$Builder;)Landroid/app/AlertDialog;" in helper
    assert "AlertDialog$Builder;->show()Landroid/app/AlertDialog;" not in helper
    assert "Paint;->setShadowLayer" not in method(helper, "buildBlueSwitch")
    assert "SettingsTheme;->switchTrack(Landroid/content/Context;Z)I" in switch
    assert "SettingsTheme;->switchThumb(Landroid/content/Context;Z)I" in switch
    for relative in ["smali/wex/ui/core/xposed/SettingsPanelHelper.smali", "smali/z4/w.smali"]:
        check_smali_registers(relative)
    print("PASS: settings theme routes, explicit slider tint, native/Java state styles and preserved business callbacks")


def run(command):
    subprocess.run([str(value) for value in command], check=True)


def grouping_checks(jdk, cases=None, repeat=1, output=None):
    """Compile real grouping hooks and tab views against small Android/Xposed host doubles."""
    with tempfile.TemporaryDirectory(prefix="mindlink-grouping-") as temp:
        out = Path(temp) / "out" if output is None else output
        out.mkdir(parents=True, exist_ok=True)
        sources = sorted((ROOT / "scripts/tests/grouping-hooks").rglob("*.java"))
        sources += [ROOT / "scripts/tests/android/content/Context.java",
                    ROOT / "scripts/tests/menu/android/os/Looper.java"]
        sources += [ROOT / ("java_src/wex/ui/core/feature/" + name + ".java") for name in
                    ["ConversationGroupingFeature", "ConversationListBridge",
                     "ConversationGroupTabsHost", "ConversationGroupBar",
                     "ConversationAdapterSnapshotReader", "ConversationPositions"]]
        sources += [ROOT / "scripts/tests/wex/ui/core/feature/ConversationPositionsChecks.java"]
        subprocess.run([str(jdk / "bin/javac.exe"), "-encoding", "UTF-8", "-d", str(out),
                        *map(str, sources)], check=True, timeout=60)
        subprocess.run([str(jdk / "bin/java.exe"), "-cp", str(out),
                        "wex.ui.core.feature.ConversationPositionsChecks"], check=True, timeout=30)
        selected = cases or ["epoch", "mutations", "click", "guards", "notify-failure",
                             "snapshot-failure", "fields", "transition", "selection", "reentry",
                             "host-lifecycle", "tab-updates", "origin-fallback"]
        for iteration in range(repeat):
            for case in selected:
                print(f"GROUPING {iteration + 1}/{repeat}: {case}", flush=True)
                subprocess.run([str(jdk / "bin/java.exe"), "-cp", str(out),
                                "wex.ui.core.feature.ConversationGroupingHookChecks", case],
                               check=True, timeout=45)


def runtime_checks(jdk):
    grouping_checks(jdk)
    with tempfile.TemporaryDirectory(prefix="mindlink-regression-") as temp:
        tmp = Path(temp)
        out = tmp / "out"
        out.mkdir()
        run([jdk / "bin/javac.exe", "-encoding", "UTF-8", "-d", out,
             ROOT / "scripts/tests/wex/ui/core/WexStore.java",
             ROOT / "java_src/wex/ui/core/feature/LocationPrivacy.java",
             ROOT / "java_src/wex/ui/core/feature/LogPreview.java",
             ROOT / "scripts/tests/RegressionChecks.java"])
        run([jdk / "bin/java.exe", "-cp", out, "RegressionChecks", tmp])
        run([jdk / "bin/javac.exe", "-encoding", "UTF-8", "-d", out,
             ROOT / "scripts/tests/android/content/Context.java",
             ROOT / "scripts/tests/android/content/ContextWrapper.java",
             ROOT / "scripts/tests/android/app/Activity.java",
             ROOT / "java_src/wex/ui/core/feature/ChatToolbarLifecycle.java",
             ROOT / "scripts/tests/wex/ui/core/feature/ToolbarLifecycleChecks.java"])
        run([jdk / "bin/java.exe", "-cp", out, "wex.ui.core.feature.ToolbarLifecycleChecks"])
        run([jdk / "bin/javac.exe", "-encoding", "UTF-8", "-classpath", out, "-d", out,
             ROOT / "java_src/wex/ui/core/feature/FilterBarMount.java",
             ROOT / "scripts/tests/wex/ui/core/feature/FilterBarMountChecks.java"])
        run([jdk / "bin/java.exe", "-cp", out, "wex.ui.core.feature.FilterBarMountChecks"])
        run([jdk / "bin/javac.exe", "-encoding", "UTF-8", "-d", out,
             ROOT / "java_src/wex/ui/core/feature/HomeSidebarState.java",
             ROOT / "java_src/wex/ui/core/feature/HomeSidebarSlot.java",
             ROOT / "scripts/tests/wex/ui/core/feature/HomeSidebarChecks.java"])
        run([jdk / "bin/java.exe", "-cp", out, "wex.ui.core.feature.HomeSidebarChecks"])
        run([jdk / "bin/javac.exe", "-encoding", "UTF-8", "-d", out,
             ROOT / "scripts/tests/android/view/View.java",
             ROOT / "scripts/tests/android/view/ViewGroup.java",
             ROOT / "scripts/tests/android/widget/LinearLayout.java",
             ROOT / "scripts/tests/android/widget/EditText.java",
             ROOT / "scripts/tests/android/widget/AbsListView.java",
             ROOT / "java_src/wex/ui/core/feature/ChatToolbarViews.java",
             ROOT / "scripts/tests/wex/ui/core/feature/ToolbarViewChecks.java"])
        run([jdk / "bin/java.exe", "-cp", out, "wex.ui.core.feature.ToolbarViewChecks"])
        run([jdk / "bin/javac.exe", "-encoding", "UTF-8", "-d", out,
             ROOT / "scripts/tests/wex/ui/core/WexStore.java",
             ROOT / "java_src/wex/ui/core/feature/BubbleSettings.java",
             ROOT / "java_src/wex/ui/core/feature/BubbleGeneration.java",
             ROOT / "java_src/wex/ui/core/feature/BubbleSourceStamp.java",
             ROOT / "java_src/wex/ui/core/feature/BubbleRegion.java",
             ROOT / "java_src/wex/ui/core/feature/BubbleBlur.java",
             ROOT / "scripts/tests/wex/ui/core/feature/BubbleSettingsChecks.java",
             ROOT / "scripts/tests/wex/ui/core/feature/BubbleGenerationChecks.java",
             ROOT / "scripts/tests/wex/ui/core/feature/BubbleBackgroundChecks.java",
             ROOT / "scripts/tests/wex/ui/core/feature/BubbleBlurChecks.java"])
        for test in ["BubbleSettingsChecks", "BubbleGenerationChecks", "BubbleBackgroundChecks", "BubbleBlurChecks"]:
            run([jdk / "bin/java.exe", "-cp", out, "wex.ui.core.feature." + test])
        run([sys.executable, "-I", "-B", ROOT / "scripts/tests/bubble_checks.py", "--jdk", jdk,
             "--out", tmp / "bubble-images"])
        run([jdk / "bin/javac.exe", "-encoding", "UTF-8", "-d", out,
             ROOT / "java_src/wex/ui/core/feature/BackdropCaptureState.java",
             ROOT / "java_src/wex/ui/core/feature/LiquidGlassMotion.java",
             ROOT / "java_src/wex/ui/core/feature/NavigationScrollState.java",
             ROOT / "scripts/tests/wex/ui/core/feature/BackdropCaptureStateChecks.java",
             ROOT / "scripts/tests/wex/ui/core/feature/LiquidGlassMotionChecks.java",
             ROOT / "scripts/tests/wex/ui/core/feature/NavigationScrollStateChecks.java"])
        for test in ["BackdropCaptureStateChecks", "LiquidGlassMotionChecks", "NavigationScrollStateChecks"]:
            run([jdk / "bin/java.exe", "-cp", out, "wex.ui.core.feature." + test])
        run([jdk / "bin/javac.exe", "-encoding", "UTF-8", "-d", out,
             ROOT / "java_src/wex/ui/core/feature/FloatingFooterGeometry.java",
             ROOT / "scripts/tests/wex/ui/core/feature/FloatingFooterLayoutChecks.java"])
        run([jdk / "bin/java.exe", "-cp", out, "wex.ui.core.feature.FloatingFooterLayoutChecks"])
        run([jdk / "bin/javac.exe", "-encoding", "UTF-8", "-d", out,
             ROOT / "scripts/tests/wex/ui/core/WexStore.java",
             ROOT / "java_src/wex/ui/core/feature/SettingsChangeState.java",
             ROOT / "java_src/wex/ui/core/feature/SettingsChanges.java",
             ROOT / "scripts/tests/wex/ui/core/feature/SettingsChangesChecks.java"])
        run([jdk / "bin/java.exe", "-cp", out, "wex.ui.core.feature.SettingsChangesChecks", tmp])
        menu_out = tmp / "menu-out"
        menu_out.mkdir()
        menu_sources = sorted((ROOT / "scripts/tests/menu").rglob("*.java"))
        run([jdk / "bin/javac.exe", "-encoding", "UTF-8", "-d", menu_out,
             ROOT / "java_src/wex/ui/core/feature/PlusMenuExtra.java",
             ROOT / "shared-ui/src/main/java/dev/mindlink/ui/MindLinkColors.java",
             ROOT / "java_src/wex/ui/core/feature/SettingsTheme.java", *menu_sources])
        run([jdk / "bin/java.exe", "-cp", menu_out, "wex.ui.core.feature.PlusMenuChecks"])
        run([jdk / "bin/java.exe", "-cp", menu_out, "wex.ui.core.feature.SettingsThemeChecks"])
        run([sys.executable, "-I", ROOT / "scripts/tests/desktop_checks.py", "--jdk", jdk])
        run([sys.executable, "-I", ROOT / "scripts/tests/compose_merge_checks.py"])
        run([sys.executable, "-I", ROOT / "scripts/tests/compose_union_checks.py"])
        run([sys.executable, "-I", ROOT / "scripts/tests/compose_resource_checks.py"])
        run([sys.executable, "-I", ROOT / "scripts/tests/scoped_build_checks.py"])
        run([sys.executable, "-I", ROOT / "scripts/tests/chat_tail_bridge_checks.py"])
        run([sys.executable, "-I", "-B", ROOT / "scripts/tests/recompression_checks.py"])
        run([sys.executable, "-I", ROOT / "scripts/tests/navigation_checks.py", "--jdk", jdk])
        run([sys.executable, "-I", ROOT / "scripts/tests/chat-footer-checks.py", "--jdk", jdk])
        run([sys.executable, "-I", ROOT / "scripts/tests/chat-header-toolbar-checks.py", "--jdk", jdk])
        run([sys.executable, "-I", "-B", ROOT / "scripts/tests/settings_systembar_checks.py", "--jdk", jdk,
             "--out", tmp / "settings-systembars"])
        run([sys.executable, "-I", "-B", ROOT / "scripts/tests/systembar_checks.py", "--jdk", jdk,
             "--out", tmp / "systembars"])
        run([sys.executable, "-I", "-B", ROOT / "scripts/tests/systembar_checks.py", "--jdk", jdk,
             "--out", tmp / "systembars-missing-getter", "--missing-getter"])
        run([sys.executable, "-I", ROOT / "scripts/tests/backdrop_recovery_checks.py", "--jdk", jdk])
        run([sys.executable, "-I", ROOT / "scripts/tests/database_capture_checks.py", "--jdk", jdk])
        run([sys.executable, "-I", ROOT / "scripts/tests/resolver_checks.py", "--jdk", jdk])
        run([sys.executable, "-I", ROOT / "scripts/tests/text_status_checks.py", "--jdk", jdk])
        run([sys.executable, "-I", ROOT / "scripts/tests/profile_config_checks.py", "--jdk", jdk])
        run([sys.executable, "-I", "-B", ROOT / "scripts/tests/profile_integration_checks.py", "--jdk", jdk,
             "--pinned-apk", ROOT / "build/profile-capture-repair-20261114-01603b9b/host-apk-input-001/WeChat-8.0.76-static.apk"])
        run([sys.executable, "-I", ROOT / "scripts/tests/side_panel_checks.py", "--jdk", jdk])
        values = ["", "plain", "path with spaces", "C:\\测试 目录\\文件.jar", 'say "hello"',
                  "C:\\folder with space\\", '\\"', "a\\\\\"b", "@C:\\space path\\args.txt"]
        expected = [base64.b64encode(v.encode("utf-8")).decode("ascii") for v in values]
        build = read("scripts/build.ps1")
        quote_function = build[build.index("function ConvertTo-NativeArgument"):build.index("function Invoke-Tool")]
        literal = lambda v: "'" + str(v).replace("'", "''") + "'"
        command = "\n".join([
            "$ErrorActionPreference = 'Stop'", quote_function,
            "$values = @(" + ", ".join(literal(v) for v in values) + ")",
            "$arguments = @('-cp', " + literal(out) + ", 'RegressionChecks', '--arguments') + $values",
            "$line = ($arguments | ForEach-Object { ConvertTo-NativeArgument $_ }) -join ' '",
            "$p = Start-Process -FilePath " + literal(jdk / "bin/java.exe") + " -ArgumentList $line "
            "-NoNewWindow -Wait -PassThru -RedirectStandardOutput " + literal(tmp / "args.txt") +
            " -RedirectStandardError " + literal(tmp / "errors.txt"),
            "if ($p.ExitCode -ne 0) { throw 'native argument probe failed' }",
        ])
        probe = tmp / "arguments.ps1"
        probe.write_text(command, encoding="utf-8-sig")
        run(["powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", probe])
        actual = (tmp / "args.txt").read_text(encoding="utf-8-sig").splitlines()
        assert actual == expected, (actual, expected)
        print("PASS: native arguments including empty, spaces, Chinese, quotes and trailing backslashes")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jdk", type=Path)
    parser.add_argument("--grouping-only", action="store_true")
    parser.add_argument("--grouping-case", action="append", choices=["epoch", "mutations", "click",
                        "guards", "notify-failure", "snapshot-failure", "fields", "transition", "selection", "reentry",
                        "host-lifecycle", "tab-updates"])
    parser.add_argument("--grouping-repeat", type=int, default=1)
    parser.add_argument("--grouping-out", type=Path)
    args = parser.parse_args()
    if args.grouping_repeat < 1 or args.grouping_repeat > 20:
        parser.error("--grouping-repeat must be between 1 and 20")
    jdk = args.jdk
    if jdk is None:
        javac = shutil.which("javac")
        jdk = Path(os.environ["JAVA_HOME"]) if "JAVA_HOME" in os.environ else (Path(javac).resolve().parents[1] if javac else None)
    if jdk is None or not (jdk / "bin/javac.exe").exists():
        parser.error("Provide --jdk or JAVA_HOME pointing to a Windows JDK")
    if args.grouping_only:
        grouping_checks(jdk, args.grouping_case, args.grouping_repeat, args.grouping_out)
        return
    if args.grouping_case or args.grouping_repeat != 1 or args.grouping_out:
        parser.error("grouping case/repeat/out options require --grouping-only")
    static_checks()
    runtime_checks(jdk)


if __name__ == "__main__":
    main()
