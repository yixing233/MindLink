"""Copy/adapt the authorized local WeKit side-panel components; never execute reference code.

The source hashes, copied regions, modification diffs, resources and selected icon class hashes
are part of the local source-provenance record. Run with Python -I and explicit path arguments.
"""
import argparse
import difflib
import hashlib
import io
import json
from pathlib import Path
import re
import xml.etree.ElementTree as ET
import zipfile

PACKAGE = "dev.mindlink.sidepanel.vendor.wekit"
SOURCE_PACKAGE = "dev.ujhhgtg.wekit.features.items.beautify.home_screen_panel"
DATE = "2026-11-14"


def digest(data):
    return hashlib.sha256(data).hexdigest()


def function(source, name, include_annotation=False):
    match = re.search(r"(?m)^(?:private )?fun (?:<[^\n]+> )?" + re.escape(name) + r"\(", source)
    if match is None:
        raise ValueError("Missing source function " + name)
    start = match.start()
    if include_annotation and source[:start].rstrip().endswith("@Composable"):
        start = source.rfind("@Composable", 0, start)
    opening = source.index("{", match.end())
    depth = 1
    i = opening + 1
    # These functions contain no brace-bearing raw strings; strip ordinary quoted strings/comments.
    quoted = False
    escape = False
    while i < len(source) and depth:
        c = source[i]
        if quoted:
            if escape:
                escape = False
            elif c == "\\":
                escape = True
            elif c == '"':
                quoted = False
        elif c == '"':
            quoted = True
        elif c == "{":
            depth += 1
        elif c == "}":
            depth -= 1
        i += 1
    if depth:
        raise ValueError("Unbalanced source " + name)
    return source[start:i]


def remove_serialization(source):
    return re.sub(r"(?m)^\s*@(Serializable|Transient|SerialName\([^\n]*\)|OptIn\([^\n]*\)|JsonClassDiscriminator\([^\n]*\))\s*\n", "", source)


def write_new(path, text):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8", newline="\n")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--reference", required=True, type=Path)
    parser.add_argument("--module", required=True, type=Path)
    parser.add_argument("--gradle-cache", required=True, type=Path)
    args = parser.parse_args()
    reference = args.reference.resolve()
    module = args.module.resolve()
    side = reference / "app/src/main/java/dev/ujhhgtg/wekit/features/items/beautify/home_screen_panel"
    target = module / "src/main/java/dev/mindlink/sidepanel/vendor/wekit"
    provenance = module / "thirdparty/sourceprovenance"
    entries = []

    def source(name):
        return (side / name).read_text(encoding="utf-8")

    def save(name, body, source_path=None, modifications=""):
        path = source_path or (side / name)
        original = path.read_bytes()
        original_text = original.decode("utf-8")
        relative_source = path.relative_to(reference).as_posix()
        header = ("// SPDX-License-Identifier: GPL-3.0-only\n"
                  "// Copied and modified from WeKit, Ujhhgtg and upstream contributors.\n"
                  f"// Source: {relative_source}\n"
                  f"// MindLink adaptations ({DATE}): {modifications}\n"
                  "// Original component geometry/typography/icons are retained; host hooks are not copied.\n"
                  "// See thirdparty/sourceprovenance/wekit-sidepanel.json and matching .patch files.\n")
        body = body.replace(SOURCE_PACKAGE, PACKAGE)
        body = body.replace("dev.ujhhgtg.wekit.R", "dev.mindlink.sidepanel.R")
        body = re.sub(r"R\.(string|array)\.(\w+)", r"R.\1.wekit_sp_\2", body)
        if not body.lstrip().startswith("package "):
            body = "package " + PACKAGE + "\n\n" + body
        output = header + body.strip() + "\n"
        output_path = target / name
        write_new(output_path, output)
        patch = "".join(difflib.unified_diff(original_text.splitlines(True), output.splitlines(True),
                                             fromfile="WeKit/" + relative_source,
                                             tofile=output_path.relative_to(module).as_posix()))
        write_new(provenance / (name + ".patch"), patch)
        entries.append({"source": relative_source, "sourceSha256": digest(original),
                        "sourceLines": len(original_text.splitlines()),
                        "target": output_path.relative_to(module).as_posix(),
                        "targetSha256": digest(output.encode("utf-8")),
                        "modifications": modifications, "diff": name + ".patch"})

    data = source("HomeSidePanelDataCards.kt")
    data = data.replace("import dev.ujhhgtg.wekit.features.items.beautify.resolveBeautifyText\n", "")
    data = data.replace("import dev.ujhhgtg.wekit.i18n.LocalWeKitLocalizedContext", "import androidx.compose.ui.platform.LocalContext")
    data = data.replace("LocalWeKitLocalizedContext.current", "LocalContext.current")
    data = data.replace("localizedContext.resolveBeautifyText(weather.message)", "weather.message")
    data = data.replace("localizedContext.resolveBeautifyText(hitokoto.message)", "hitokoto.message")
    save("HomeSidePanelDataCards.kt", data, modifications="resource namespace and platform localized context/error text only; DateTime/Weather/Wallet/Hitokoto bodies retained")
    save("HomeSidePanelCardFrame.kt", source("HomeSidePanelCardFrame.kt"), modifications="package and resource names only; badge/card/animation bodies unchanged")
    save("HomeSidePanelLunarDate.kt", source("HomeSidePanelLunarDate.kt"), modifications="package only; ICU lunar calendar calculation and formatter unchanged")
    save("HomeSidePanelPreview.kt", source("HomeSidePanelPreview.kt"), modifications="package only; original content variants retained")

    layout = source("HomeSidePanelLayout.kt")
    layout_body = layout[layout.index("const val HOME_SIDE_PANEL_LAYOUT_VERSION"):layout.index("class InvalidHomeSidePanelLayoutException")]
    layout_body = remove_serialization(layout_body)
    layout_body = layout_body.replace("val city: WeatherCity,", "val city: WeatherCity? = null,")
    aspect = function(layout, "isHomeSidePanelImageAspectRatioSupported")
    save("HomeSidePanelLayout.kt", "import java.util.UUID\n\n" + layout_body + aspect,
         modifications="retain original card/action/image models and constants; serialization/codec/host migration omitted; config-only weather city nullable during loading")

    weather = source("HomeSidePanelWeather.kt")
    weather_models = weather[weather.index("@Serializable\ndata class WeatherCity"):weather.index("sealed interface WeatherResult")]
    # No fake default Beijing data is manufactured on this boundary.
    weather_models = weather_models[:weather_models.index("val DEFAULT_WEATHER_CITY")] + weather_models[weather_models.index("@Serializable\ndata class WeatherSnapshot"):]
    weather_models = remove_serialization(weather_models).replace("BeautifyText", "String")
    kinds = weather[weather.index("enum class WeatherIconKind"):weather.index("private fun buildWeatherUrl")]
    save("HomeSidePanelWeather.kt", weather_models + kinds,
         modifications="full display models and original icon-code mapping only; network/location/database/services/default city omitted; errors are platform strings")
    wallet = source("HomeSidePanelWallet.kt")
    save("HomeSidePanelWallet.kt", "import java.math.BigDecimal\nimport java.text.DecimalFormat\nimport java.text.DecimalFormatSymbols\nimport java.util.Locale\n\n" + wallet[wallet.index("data class HomeSidePanelWalletDisplayState"):wallet.index("object HomeSidePanelWalletBalanceSource")],
         modifications="original balance display state/toggle/reset/decimal formatter only; host cache service/hook not copied")
    hitokoto = source("HomeSidePanelHitokoto.kt")
    models = hitokoto[hitokoto.index("val HITOKOTO_CATEGORY_CODES"):hitokoto.index("sealed interface HitokotoResult")]
    save("HomeSidePanelHitokoto.kt", remove_serialization(models).replace("BeautifyText", "String"),
         modifications="retain full snapshot/settings/loading-ready-error models; no network client, request pool or host service copied")

    home = source("HomeSidePanelHome.kt")
    imports = home[home.index("import android.graphics.PorterDuff"):home.index("fun homeSidePanelProfileDisplayName")]
    imports = imports.replace("import coil3.compose.AsyncImage\nimport coil3.compose.AsyncImagePainter\n", "import androidx.compose.foundation.Image\nimport androidx.compose.ui.graphics.asImageBitmap\n")
    imports = imports.replace("import dev.ujhhgtg.wekit.features.api.core.TextStatus\n", "")
    imports = imports.replace("import dev.ujhhgtg.wekit.features.api.core.WeTextStatusApi\n", "")
    home_function = function(home, "HomeSidePanelHome", True)
    home_function = home_function.replace("    dragState: HomeSidePanelDragState,\n", "")
    beginning = home_function.index("    val dragSnapshot")
    ending = home_function.index("    val editorMotion")
    home_function = home_function[:beginning] + "    val displayedCards = state.renderedLayout.cards.withIndex().toList()\n" + home_function[ending:]
    home_function = home_function.replace("        userScrollEnabled = dragSnapshot == null,", "        userScrollEnabled = true,")
    home_function = home_function.replace("            }\n            .homeSidePanelDragViewport(dragState),", "            },")
    first = home_function.index("            if (cardInsertionIndex == visualIndex)")
    last = home_function.index("            val index = indexedCard.index", first)
    home_function = home_function[:first] + home_function[last:]
    first = home_function.index("            val actionAxis = when (card)")
    last = home_function.index("            item(key = card.id)", first)
    home_function = home_function[:first] + home_function[last:]
    home_function = home_function.replace("                    dragState = dragState,\n                    dragSnapshot = dragSnapshot,\n", "")
    first = home_function.index("                        .homeSidePanelCardDragTarget(")
    last = home_function.index("                )\n            }", first)
    home_function = home_function[:first] + "                        .sidePanelEditLongPress(card.id) { panelState.editCard(card.id) },\n" + home_function[last:]
    first = home_function.index("        if (cardInsertionIndex == displayedCards.size)")
    last = home_function.index('        item(key = "bottom-space")', first)
    home_function = home_function[:first] + home_function[last:]
    home_function = home_function.replace("displayedCards.forEachIndexed { visualIndex, indexedCard ->", "displayedCards.forEach { indexedCard ->")
    home_function = home_function.replace("            val index = indexedCard.index\n", "")
    dispatch = function(home, "HomeSidePanelLayoutCard", True)
    dispatch = dispatch.replace("    dragState: HomeSidePanelDragState,\n    dragSnapshot: HomeSidePanelDragSnapshot?,\n", "")
    # Original dispatch retains every card kind. Only the drag/editor-service binding is removed.
    dispatch = dispatch.replace("onEditCard = panelState::openDateTimeSettings", "onEditCard = panelState::editCard")
    for name in ["Weather", "Wallet", "Hitokoto"]:
        dispatch = dispatch.replace("onEditCard = panelState::open" + name + "Settings", "onEditCard = panelState::editCard")
    dispatch = dispatch.replace("            imageFile = panelState.imageFile(card.id),", "            imageBitmap = panelState.imageBitmap(card.id),")
    dispatch = dispatch.replace("            onImageDimensionsChange = panelState::updateImageDimensions,", "            onImageDimensionsChange = { _, _, _ -> },")
    for name in ["HorizontalActions", "VerticalActions"]:
        marker = "is " + ("Horizontal" if name.startswith("Horizontal") else "Vertical") + "ActionsCardConfig -> HomeSidePanel" + name + "Card("
        first = dispatch.index(marker)
        argfirst = dispatch.index("            insertionSnapshot = dragSnapshot,", first)
        arglast = dispatch.index("            onRunAction =", argfirst)
        dispatch = dispatch[:argfirst] + dispatch[arglast:]
    dispatch = dispatch.replace("onRunAction = { _, _, kind -> panelState.runAction(kind) }", "onRunAction = panelState::runAction")
    tail = home[home.index("@Composable\nprivate fun HomeSidePanelProfileHeader"):]
    tail = tail.replace("    var imageFailed by remember(profile.avatarUrl) { mutableStateOf(false) }\n    if (profile.avatarUrl.isNotBlank() && !imageFailed) {\n        AsyncImage(\n            model = profile.avatarUrl,", "    val avatar = profile.avatarBitmap?.takeUnless { it.isRecycled }\n    if (avatar != null) {\n        Image(\n            bitmap = avatar.asImageBitmap(),")
    tail = tail.replace("            onState = { state ->\n                if (state is AsyncImagePainter.State.Error) imageFailed = true\n            },\n", "")
    tail = tail.replace("                    WeTextStatusApi.renderIcon(this, status.iconId)", "                    setImageBitmap(status.iconBitmap)")
    tail = tail.replace("                imageView.contentDescription = status.description\n                imageView.setColorFilter", "                imageView.contentDescription = status.description\n                imageView.setImageBitmap(status.iconBitmap)\n                imageView.setColorFilter")
    save("HomeSidePanelHome.kt", imports + home[home.index("fun homeSidePanelProfileDisplayName"):home.index("@Composable\nfun HomeSidePanelHome")] + home_function + "\n\n" + dispatch + "\n\n" + tail,
         modifications="original LazyColumn, in-place editor pulse, 58dp header, 32dp toolbar and all card dispatch retained; drag/full editor host omitted; typed callbacks/platform Bitmap bind existing host")

    content = source("HomeSidePanelContent.kt")
    content_body = """import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

enum class HomeSidePanelTransitionKind { ENTER_EDITOR, EXIT_EDITOR, NONE }
fun homeSidePanelTransitionKind(from: HomeSidePanelRoute, to: HomeSidePanelRoute): HomeSidePanelTransitionKind = when {
    from == to -> HomeSidePanelTransitionKind.NONE
    to == HomeSidePanelRoute.EditHome -> HomeSidePanelTransitionKind.ENTER_EDITOR
    from == HomeSidePanelRoute.EditHome && to == HomeSidePanelRoute.Home -> HomeSidePanelTransitionKind.EXIT_EDITOR
    else -> HomeSidePanelTransitionKind.NONE
}

@Composable
fun HomeSidePanelContent(state: HomeSidePanelUiState, panelState: HomeSidePanelState) {
    val listState = rememberLazyListState()
"""
    original_surface = content[content.index("    Surface("):content.index("        HomeSidePanelDragHost(")]
    content_body += original_surface
    content_body += """        if (!state.initialized) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            HomeSidePanelHome(state = state, panelState = panelState, listState = listState)
        }
    }
}
"""
    save("HomeSidePanelContent.kt", content_body,
         modifications="retain exact fill-size Material3 surface with topEnd/bottomEnd 28dp and original loading; route/full drag host remains in Java host editor")

    actions = source("HomeSidePanelActionCards.kt")
    actions = actions.replace("    insertionSnapshot: HomeSidePanelDragSnapshot? = null,\n", "")
    for unused in ["    val draggedActionId = (insertionSnapshot?.payload as? HomeSidePanelDragPayload.ExistingAction)\n        ?.takeIf { it.cardId == card.id }\n        ?.actionId\n    val displayedActions = animatedActions.filterNot { it.id == draggedActionId }\n    val visualInsertionIndex = insertionSnapshot?.visualActionInsertionIndex(card.id, card.actions)\n"]:
        actions = actions.replace(unused, "    val displayedActions = animatedActions\n")
    # Remove the four original insertion-gap condition blocks without replacing them with stubs.
    while "if (visualInsertionIndex ==" in actions:
        first = actions.index("if (visualInsertionIndex ==")
        first = actions.rfind("\n", 0, first) + 1
        opening = actions.index("{", first)
        depth = 1
        end = opening + 1
        while depth:
            if actions[end] == "{": depth += 1
            elif actions[end] == "}": depth -= 1
            end += 1
        if end < len(actions) and actions[end] == "\n": end += 1
        actions = actions[:first] + actions[end:]
    start = actions.index("@Composable\nfun HomeSidePanelDraggedActionItem")
    end = actions.index("private data object HomeSidePanelVirtualAddKey", start)
    actions = actions[:start] + actions[end:]
    save("HomeSidePanelActionCards.kt", actions,
         modifications="original FlowRow 3-column tiles/ListItems, shapes, spacing, badges and reflow animation retained; only drag insertion gaps/dragged preview omitted")
    specs = source("HomeSidePanelActions.kt")
    imports = specs[specs.index("import androidx.annotation.StringRes"):specs.index("import dev.ujhhgtg.wekit.activity.settings.SettingsActivity")]
    save("HomeSidePanelActions.kt", imports + specs[specs.index("data class HomeSidePanelActionSpec"):specs.index("class HomeSidePanelActionExecutor")],
         modifications="original enum-label-MaterialSymbols mapping only; all native action/payment/service executor code remains in Java callbacks")

    image = source("HomeSidePanelImageCard.kt")
    image = image.replace("import coil3.compose.AsyncImage\nimport coil3.compose.AsyncImagePainter\n", "import android.graphics.Bitmap\nimport androidx.compose.foundation.Image\nimport androidx.compose.ui.graphics.asImageBitmap\n")
    for imported in ["import dev.ujhhgtg.wekit.ui.content.GlobalImageLoader\n", "import dev.ujhhgtg.wekit.ui.content.m3.DropdownOption\n", "import dev.ujhhgtg.wekit.ui.content.m3.ExpressiveOptionDropdown\n", "import java.nio.file.Path\n"]:
        image = image.replace(imported, "")
    image = image.replace("imageFile: Path?", "imageBitmap: Bitmap?").replace("imageFile", "imageBitmap")
    first = image.index("                    AsyncImage(")
    last = image.index("                } else {\n                    HomeSidePanelImagePlaceholder(", first)
    image = image[:first] + """                    Image(
                        bitmap = imageBitmap.asImageBitmap(),
                        contentDescription = stringResource(R.string.home_side_panel_card_image),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = card.scaleMode.contentScale,
                    )
""" + image[last:]
    marker = "    val bodyEnabled = editMode && !importing"
    image = image.replace(marker, """    LaunchedEffect(imageBitmap) {
        val bitmap = imageBitmap?.takeUnless { it.isRecycled } ?: return@LaunchedEffect
        if (isHomeSidePanelImageAspectRatioSupported(bitmap.width, bitmap.height)) {
            decodedAspectRatioUnsupported = false
            decodedDimensions = HomeSidePanelImageDimensions(bitmap.width, bitmap.height)
        } else {
            decodedAspectRatioUnsupported = true
        }
    }
""" + marker)
    save("HomeSidePanelImageCard.kt", image,
         modifications="original image shape/heights/scale modes/placeholders/resize/dropdown bodies retained; already-decoded platform Bitmap replaces file/Coil/global image loader (no image networking)")
    dropdown_path = reference / "app/src/main/java/dev/ujhhgtg/wekit/ui/content/m3/DropDownMenuWidget.kt"
    dropdown = dropdown_path.read_text(encoding="utf-8")
    dropdown_imports = "import androidx.compose.material3.DropdownMenuGroup\nimport androidx.compose.material3.DropdownMenuPopup\nimport androidx.compose.material3.MenuDefaults\nimport androidx.compose.material3.SelectableDropdownMenuItem\nimport androidx.compose.material3.Text\nimport androidx.compose.runtime.Composable\n\n"
    save("HomeSidePanelImageDropdown.kt", "// Copyright (C) 2023-2026 iamr0s, InstallerX Revived contributors\n" + dropdown_imports + "data class DropdownOption<T>(val value: T, val label: String)\n\n" + function(dropdown, "ExpressiveOptionDropdown", True), dropdown_path,
         modifications="retain original GPL-3.0 InstallerX Revived expressive option dropdown used by WeKit image badge; omit unrelated BaseWidget")
    drag = source("HomeSidePanelDragState.kt")
    save("HomeSidePanelPointerLifecycle.kt", drag[drag.index("enum class HomeSidePanelPointerLifecycleDecision"):drag.index("fun normalizedMoveDestination")], side / "HomeSidePanelDragState.kt",
         modifications="original pointer up/cancel decision only, required by image resize; not the drag host")

    theme_root = reference / "app/src/main/java/dev/ujhhgtg/wekit/ui/utils/theme"
    colors = (theme_root / "Color.kt").read_text(encoding="utf-8")
    defaults = colors[colors.index("val primaryLight ="):colors.index("val primaryLightMediumContrast")]
    defaults += colors[colors.index("val primaryDark ="):colors.index("val primaryDarkMediumContrast")]
    theme = (theme_root / "Theme.kt").read_text(encoding="utf-8")
    schemes = theme[theme.index("val lightScheme ="):theme.index("private val mediumContrastLightColorScheme")]
    theme_body = """import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

""" + defaults + schemes + """
@Composable
fun HomeSidePanelTheme(content: @Composable () -> Unit) {
    MaterialExpressiveTheme(
        colorScheme = if (isSystemInDarkTheme()) darkScheme else lightScheme,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}
"""
    save("HomeSidePanelTheme.kt", theme_body, theme_root / "InjectedUiTheme.kt",
         modifications="retain original WeKit injected default green color schemes and MaterialExpressiveTheme/expressive motion; no module theme preference/HostInfo dependencies")
    entries.append({"source": (theme_root / "Color.kt").relative_to(reference).as_posix(), "sourceSha256": digest((theme_root / "Color.kt").read_bytes()), "copiedSymbols": "default light/dark color constants (no contrast variants)"})
    entries.append({"source": (theme_root / "Theme.kt").relative_to(reference).as_posix(), "sourceSha256": digest((theme_root / "Theme.kt").read_bytes()), "copiedSymbols": "lightScheme, darkScheme"})

    # Copy only resource entries used by these original bodies, with an isolated name prefix.
    texts = "\n".join(path.read_text(encoding="utf-8") for path in target.glob("*.kt"))
    used = {(kind, name) for kind, name in re.findall(r"R\.(string|array)\.wekit_sp_(\w+)", texts)}
    resource_entries = []
    for locale in ["values", "values-zh-rCN"]:
        resources = reference / "app/src/main/res" / locale
        if not resources.exists(): continue
        output_root = ET.Element("resources")
        found = set()
        for resource_path in sorted(resources.glob("*.xml")):
            original_bytes = resource_path.read_bytes()
            root = ET.fromstring(original_bytes)
            copied_names = []
            for element in root:
                kind = "array" if element.tag in ("string-array", "array") else element.tag
                name = element.get("name")
                if (kind, name) not in used: continue
                element.set("name", "wekit_sp_" + name)
                # Brand-specific module-settings label is the sole visible string adaptation.
                if name == "fab_default_module_settings" and element.text:
                    element.text = element.text.replace("WeKit", "MindLink")
                output_root.append(element)
                found.add((kind, name)); copied_names.append(name)
            if copied_names:
                resource_entries.append({"source": resource_path.relative_to(reference).as_posix(), "sourceSha256": digest(original_bytes), "entries": copied_names})
        if locale == "values" and used - found:
            raise ValueError("Missing original resource entries " + repr(used - found))
        ET.indent(output_root, space="    ")
        xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<!-- GPL-3.0-only: copied/modified WeKit side-panel strings/arrays; sourceprovenance records entries. -->\n" + ET.tostring(output_root, encoding="unicode") + "\n"
        write_new(module / "src/main/res" / locale / "wekit_sidepanel.xml", xml)

    # Preserve the ORIGINAL compiled Material Symbols vector bodies, selecting only referenced icons.
    # No decompilation/imitation geometry and no whole 24MB icon set or extra Compose runtime.
    classes = {}
    icon_entries = []
    base = args.gradle_cache / "com.composables"
    groups = ["icons-material-symbols-cmp-android-debug", "icons-material-symbols-outlined-cmp-android-debug", "icons-material-symbols-outlined-filled-cmp-android-debug"]
    selected = set()
    for package, name in re.findall(r"import com\.composables\.icons\.materialsymbols\.(outlined|outlinedfilled)\.(\w+)", texts):
        selected.add("com/composables/icons/materialsymbols/" + package + "/" + name + "Kt.class")
    for group in groups:
        aar = next((base / group / "2.2.1").rglob("*.aar"))
        with zipfile.ZipFile(aar) as archive:
            jar_bytes = archive.read("classes.jar")
        with zipfile.ZipFile(io.BytesIO(jar_bytes)) as archive:
            names = [n for n in archive.namelist() if n.endswith(".kotlin_module") or (group == groups[0] and n.endswith(".class")) or n in selected]
            for name in names:
                data = archive.read(name)
                if name in classes and classes[name] != data:
                    # The base Kotlin module names overlap per artifact; make each name unique.
                    name = "META-INF/" + group + ".kotlin_module"
                classes[name] = data
                icon_entries.append({"artifact": "com.composables:" + group + ":2.2.1", "artifactSha256": digest(aar.read_bytes()), "class": name, "sha256": digest(data)})
    if not selected.issubset(classes): raise ValueError("Missing original icon classes " + repr(selected - classes.keys()))
    jar_path = module / "thirdparty/material-symbols-selected-2.2.1.jar"
    jar_path.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(jar_path, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for name, data in sorted(classes.items()):
            info = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0)); info.compress_type = zipfile.ZIP_DEFLATED
            archive.writestr(info, data)
    record = {"upstream": "https://github.com/Ujhhgtg/WeKit", "upstreamAlsoCredits": "https://github.com/cwuom/WeKit", "license": "GPL-3.0-only", "dateModified": DATE,
              "referenceRoot": str(reference), "copiedAndModified": entries,
              "resources": resource_entries, "resourceAssetsLicense": "WeKit project GPL-3.0-only; only strings/arrays, no brand/avatar/banner/image assets copied",
              "icons": {"wrapperLicense": "MIT (Composable Horizons composeicons 2.2.1, cached POM)", "geometryLicense": "Apache-2.0 (Google Material Symbols)", "file": jar_path.relative_to(module).as_posix(), "sha256": digest(jar_path.read_bytes()), "selected": icon_entries},
              "notCopied": ["HomeSidePanel.kt 1549-line Xposed/drawer host", "WeKit databases, services, network clients, weather city database, payment executors", "Coil/network/image-loader", "full drag/editor host", "logos, avatars, banners, external assets"]}
    write_new(provenance / "wekit-sidepanel.json", json.dumps(record, ensure_ascii=False, indent=2) + "\n")
    print("Copied/adapted", len(entries), "source records;", len(used), "original resource names;", len(selected), "original icon classes")
    print("Selected icon JAR", jar_path.stat().st_size, "bytes SHA256", digest(jar_path.read_bytes()))


if __name__ == "__main__":
    main()
