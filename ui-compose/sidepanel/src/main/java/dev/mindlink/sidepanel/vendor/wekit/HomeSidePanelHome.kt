// SPDX-License-Identifier: GPL-3.0-only
// Copied and modified from WeKit, Ujhhgtg and upstream contributors.
// Source: app/src/main/java/dev/ujhhgtg/wekit/features/items/beautify/home_screen_panel/HomeSidePanelHome.kt
// MindLink adaptations (2026-11-14): original LazyColumn, in-place editor pulse, 58dp header, 32dp toolbar and all card dispatch retained; drag/full editor host omitted; typed callbacks/platform Bitmap bind existing host
// 2026-11-22: icons switched to Lucide (dev.mindlink.ui.LucideIcons, ISC); see THIRD-PARTY-NOTICES.md.
// Original component geometry/typography are retained (icons are now Lucide); host hooks are not copied.
// See thirdparty/sourceprovenance/wekit-sidepanel.json and matching .patch files.
package dev.mindlink.sidepanel.vendor.wekit

import dev.mindlink.ui.LucideIcons
import android.graphics.PorterDuff
import android.widget.ImageView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import dev.mindlink.sidepanel.R

fun homeSidePanelProfileDisplayName(profile: HomeSidePanelProfile, fallback: String): String =
    profile.nickname.ifBlank { fallback }

@Composable
fun HomeSidePanelHome(
    state: HomeSidePanelUiState,
    panelState: HomeSidePanelState,
    listState: LazyListState,
) {
    val reorder = remember(listState) { SidePanelCardReorder(listState) }
    val cards = state.renderedLayout.cards
    // Own the recorded layers outside lazy items so a tall card survives scrolling
    // offscreen. The preview retains its exact pixels, dimensions and badge state.
    val cardLayers = cards.associate { card ->
        card.id to key(card.id) { rememberGraphicsLayer() }
    }
    val displayedCards = reorder.order?.mapNotNull { id -> cards.find { it.id == id } } ?: cards
    val haptics = LocalHapticFeedback.current
    val edge = with(LocalDensity.current) { 56.dp.toPx() }
    LaunchedEffect(reorder.draggedId) {
        if (reorder.draggedId != null) {
            var previous = withFrameNanos { it }
            while (reorder.draggedId != null) {
                val now = withFrameNanos { it }
                val step = reorder.scrollStep(edge) * ((now - previous) / 16_666_667f).coerceAtMost(2f)
                previous = now
                if (step != 0f) listState.scrollBy(step)
                reorder.reorder()
            }
        }
    }
    DisposableEffect(panelState) {
        onDispose { panelState.setGestureOwnership(false) }
    }
    val editorMotion = remember { Animatable(0f) }
    var editorMotionDirection by remember { mutableStateOf(1f) }
    var previousRoute by remember { mutableStateOf<HomeSidePanelRoute?>(null) }
    LaunchedEffect(state.route) {
        val previous = previousRoute
        previousRoute = state.route
        when (previous?.let { homeSidePanelTransitionKind(it, state.route) }) {
            HomeSidePanelTransitionKind.ENTER_EDITOR -> {
                editorMotionDirection = 1f
                editorMotion.snapTo(1f)
                editorMotion.animateTo(0f, tween(220))
            }

            HomeSidePanelTransitionKind.EXIT_EDITOR -> {
                editorMotionDirection = -1f
                editorMotion.snapTo(1f)
                editorMotion.animateTo(0f, tween(220))
            }

            else -> Unit
        }
    }
    val editorTranslation = with(LocalDensity.current) { 8.dp.toPx() }
    Box(Modifier.fillMaxSize()) {
    LazyColumn(
        state = listState,
        userScrollEnabled = reorder.draggedId == null,
        modifier = Modifier
            .fillMaxSize()
            .sidePanelLongPressDrag(
                onStart = { point ->
                    if (panelState.gestureInProgress) false
                    else if (!state.editing) {
                        panelState.setGestureOwnership(true)
                        panelState.enterEditing()
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        true
                    } else if (reorder.start(point.y, cards.map { it.id })) {
                        panelState.setGestureOwnership(true)
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        true
                    } else false
                },
                onDrag = reorder::drag,
                onFinish = { completed ->
                    try { reorder.finish(completed, panelState::moveCard) }
                    finally { panelState.setGestureOwnership(false) }
                },
            )
            .graphicsLayer {
                val progress = editorMotion.value
                alpha = 1f - 0.08f * progress
                scaleX = 1f - 0.015f * progress
                scaleY = 1f - 0.015f * progress
                translationY = editorTranslation * progress * editorMotionDirection
            },
        contentPadding = WindowInsets.safeDrawing
            .only(WindowInsetsSides.Top + WindowInsetsSides.Bottom)
            .asPaddingValues(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "profile-header") {
            HomeSidePanelProfileHeader(
                profile = state.profile,
                editing = state.editing,
                saveEnabled = state.imageImportingCardIds.isEmpty(),
                panelState = panelState,
                modifier = Modifier.padding(start = 18.dp, top = 14.dp, end = 18.dp),
            )
        }
        if (state.editing) item(key = "editing-hint") {
            Text("长按卡片拖动排序 · 点设置调整内容", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 18.dp))
        }
        displayedCards.forEachIndexed { index, card ->
            item(key = card.id) {
                if (reorder.draggedId == card.id) {
                    Box(Modifier.fillMaxWidth().height(with(LocalDensity.current) { reorder.height.toDp() }))
                } else {
                HomeSidePanelLayoutCard(
                    card = card,
                    editMode = state.editing,
                    imageImportingCardIds = state.imageImportingCardIds,
                    panelState = panelState,
                    modifier = Modifier
                        .animateItem(fadeInSpec = null, fadeOutSpec = null)
                        .drawWithContent {
                            val layer = cardLayers.getValue(card.id)
                            layer.record { this@drawWithContent.drawContent() }
                            drawLayer(layer)
                        }
                        .padding(horizontal = 18.dp)
                        .semantics {
                            if (state.editing) customActions = buildList {
                                if (index > 0) add(CustomAccessibilityAction("上移") { panelState.moveCard(card.id, index - 1); true })
                                if (index < cards.lastIndex) add(CustomAccessibilityAction("下移") { panelState.moveCard(card.id, index + 1); true })
                            }
                        },
                )
                }
            }
        }
        item(key = "bottom-space") {
            Box(Modifier.size(4.dp))
        }
    }
    reorder.draggedId?.let { id ->
        // A drawing has no click/resize targets and cannot replay card animations.
        Canvas(Modifier.fillMaxSize()) {
            translate(top = reorder.overlayTop()) { drawLayer(cardLayers.getValue(id)) }
        }
    }
    }
}

@Composable
private fun HomeSidePanelLayoutCard(
    card: HomeSidePanelCardConfig,
    editMode: Boolean,
    imageImportingCardIds: Set<String>,
    panelState: HomeSidePanelState,
    modifier: Modifier,
) {
    when (card) {
        is DateTimeCardConfig -> HomeSidePanelDateTimeCard(
            card = card,
            content = DateTimeCardContent.Runtime,
            editMode = editMode,
            modifier = modifier,
            onEditCard = panelState::editCard,
            onDeleteCard = panelState::removeCard,
        )

        is WeatherCardConfig -> {
            val runtime = checkNotNull(panelState.runtimeState(card.id)) {
                "Weather card '${card.id}' has no runtime state"
            }
            require(runtime is HomeSidePanelCardRuntimeState.Weather) {
                "Weather card '${card.id}' has mismatched runtime state $runtime"
            }
            HomeSidePanelWeatherCard(
                card = card,
                content = WeatherCardContent.Runtime(runtime.state),
                editMode = editMode,
                modifier = modifier,
                onRefresh = panelState::refreshWeather,
                onEditCard = panelState::editCard,
                onDeleteCard = panelState::removeCard,
            )
        }

        is WalletCardConfig -> {
            val runtime = checkNotNull(panelState.runtimeState(card.id)) {
                "Wallet card '${card.id}' has no runtime state"
            }
            require(runtime is HomeSidePanelCardRuntimeState.Wallet) {
                "Wallet card '${card.id}' has mismatched runtime state $runtime"
            }
            HomeSidePanelWalletCard(
                card = card,
                content = WalletCardContent.Runtime(runtime.state),
                editMode = editMode,
                modifier = modifier,
                onToggleBalance = panelState::toggleWallet,
                onRunAction = panelState::runAction,
                onOpenPaymentCode = panelState::openPaymentCode,
                onEditCard = panelState::editCard,
                onDeleteCard = panelState::removeCard,
            )
        }

        is HitokotoCardConfig -> {
            val runtime = checkNotNull(panelState.runtimeState(card.id)) {
                "Hitokoto card '${card.id}' has no runtime state"
            }
            require(runtime is HomeSidePanelCardRuntimeState.Hitokoto) {
                "Hitokoto card '${card.id}' has mismatched runtime state $runtime"
            }
            HomeSidePanelHitokotoCard(
                card = card,
                content = HitokotoCardContent.Runtime(runtime.state),
                editMode = editMode,
                modifier = modifier,
                onRefresh = panelState::refreshHitokoto,
                onEditCard = panelState::editCard,
                onDeleteCard = panelState::removeCard,
            )
        }

        is ImageCardConfig -> HomeSidePanelImageCard(
            card = card,
            imageBitmap = panelState.imageBitmap(card.id),
            editMode = editMode,
            importing = card.id in imageImportingCardIds,
            modifier = modifier,
            onSelectImage = panelState::selectImage,
            onScaleModeChange = panelState::updateImageScaleMode,
            onHeightChange = panelState::updateImageHeight,
            onImageDimensionsChange = { _, _, _ -> },
            onDeleteCard = panelState::removeCard,
            onResizeGesture = panelState::setGestureOwnership,
            onEditCard = panelState::editCard,
        )

        is HorizontalActionsCardConfig -> HomeSidePanelHorizontalActionsCard(
            card = card,
            content = HomeSidePanelActionCardContent.Runtime,
            editMode = editMode,
            modifier = modifier,
            onRunAction = panelState::runAction,
            onDeleteAction = panelState::removeAction,
            onAddAction = panelState::openAddAction,
            onDeleteCard = panelState::removeCard,
        )

        is VerticalActionsCardConfig -> HomeSidePanelVerticalActionsCard(
            card = card,
            content = HomeSidePanelActionCardContent.Runtime,
            editMode = editMode,
            modifier = modifier,
            onRunAction = panelState::runAction,
            onDeleteAction = panelState::removeAction,
            onAddAction = panelState::openAddAction,
            onDeleteCard = panelState::removeCard,
        )
    }
}

@Composable
private fun HomeSidePanelProfileHeader(
    profile: HomeSidePanelProfile,
    editing: Boolean,
    saveEnabled: Boolean,
    panelState: HomeSidePanelState,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        HomeSidePanelProfileAvatar(
            profile = profile,
            size = 58.dp,
            textStyle = MaterialTheme.typography.titleLarge,
            contentDescription = if (editing) {
                null
            } else {
                stringResource(R.string.wekit_sp_home_side_panel_open_profile)
            },
            onClick = if (editing) null else panelState::openPersonalProfile,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .then(
                    if (editing) {
                        Modifier
                    } else {
                        Modifier.clickable(onClick = panelState::openStatusEditor)
                    },
                )
                .padding(horizontal = 6.dp, vertical = 5.dp),
        ) {
            Text(
                homeSidePanelProfileDisplayName(profile, stringResource(R.string.wekit_sp_home_side_panel_wechat_user)),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                HomeSidePanelStatus(
                    status = profile.status,
                    panelState = panelState,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (!editing) {
                    Icon(
                        LucideIcons.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (editing) {
            Row {
                val discardDescription = stringResource(R.string.wekit_sp_home_side_panel_discard_editing)
                IconButton(
                    onClick = panelState::discardEditing,
                    modifier = Modifier
                        .size(40.dp)
                        .semantics { contentDescription = discardDescription },
                ) {
                    Icon(
                        LucideIcons.X,
                        contentDescription = null,
                    )
                }
                val saveDescription = stringResource(R.string.wekit_sp_home_side_panel_save_editing)
                IconButton(
                    onClick = panelState::saveEditing,
                    enabled = saveEnabled,
                    modifier = Modifier
                        .size(40.dp)
                        .semantics { contentDescription = saveDescription },
                ) {
                    Icon(
                        LucideIcons.Save,
                        contentDescription = null,
                    )
                }
                val addDescription = stringResource(R.string.wekit_sp_home_side_panel_add_card)
                IconButton(
                    onClick = panelState::openAddCard,
                    modifier = Modifier
                        .size(40.dp)
                        .semantics { contentDescription = addDescription },
                ) {
                    Icon(
                        LucideIcons.Plus,
                        contentDescription = null,
                    )
                }
            }
        } else {
            IconButton(onClick = panelState::openPanelSettings) {
                Icon(
                    LucideIcons.Settings,
                    contentDescription = stringResource(R.string.wekit_sp_home_side_panel_settings),
                )
            }
        }
    }
}

@Composable
private fun HomeSidePanelStatus(
    status: HomeSidePanelStatusUiState,
    panelState: HomeSidePanelState,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(
            if (status == HomeSidePanelStatusUiState.NoStatus) 5.dp else 3.dp,
        ),
    ) {
        when (status) {
            HomeSidePanelStatusUiState.Loading -> CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                strokeWidth = 2.dp,
            )

            HomeSidePanelStatusUiState.NoStatus -> {
                Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF31B36B)))
                Text(
                    stringResource(R.string.wekit_sp_home_side_panel_online),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }

            is HomeSidePanelStatusUiState.Ready -> {
                HomeSidePanelTextStatusIcon(status.status, 22.dp)
                Text(
                    status.status.description,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            HomeSidePanelStatusUiState.Error -> {
                Icon(
                    LucideIcons.X,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    stringResource(R.string.wekit_sp_home_side_panel_fetch_failed),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 1,
                )
                IconButton(onClick = panelState::refreshStatus, modifier = Modifier.size(24.dp)) {
                    Icon(
                        LucideIcons.RefreshCw,
                        contentDescription = stringResource(R.string.wekit_sp_home_side_panel_refresh_status),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun HomeSidePanelToolbarContent(
    profile: HomeSidePanelProfile,
    onAvatarClick: () -> Unit,
    onStatusClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxHeight()
            .widthIn(max = 280.dp)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        HomeSidePanelProfileAvatar(
            profile = profile,
            size = 32.dp,
            textStyle = MaterialTheme.typography.labelLarge,
            contentDescription = stringResource(R.string.wekit_sp_home_side_panel_open_panel),
            onClick = onAvatarClick,
        )
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onStatusClick)
                .padding(horizontal = 5.dp, vertical = 3.dp),
        ) {
            Text(
                text = homeSidePanelProfileDisplayName(profile, stringResource(R.string.wekit_sp_home_side_panel_wechat_user)),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                HomeSidePanelToolbarStatus(
                    status = profile.status,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Icon(
                    imageVector = LucideIcons.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun HomeSidePanelProfileAvatar(
    profile: HomeSidePanelProfile,
    size: Dp,
    textStyle: TextStyle,
    contentDescription: String?,
    onClick: (() -> Unit)?,
) {
    val avatar = profile.avatarBitmap?.takeUnless { it.isRecycled }
    if (avatar != null) {
        Image(
            bitmap = avatar.asImageBitmap(),
            contentDescription = contentDescription,
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .then(onClick?.let { Modifier.clickable(onClick = it) } ?: Modifier),
        )
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .then(onClick?.let { Modifier.clickable(onClick = it) } ?: Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = profile.nickname.firstOrNull()?.toString()
                    ?: stringResource(R.string.wekit_sp_home_side_panel_fallback_initial),
                style = textStyle,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun HomeSidePanelToolbarStatus(
    status: HomeSidePanelStatusUiState,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(
            if (status == HomeSidePanelStatusUiState.NoStatus) 3.dp else 2.dp,
        ),
    ) {
        when (status) {
            HomeSidePanelStatusUiState.Loading -> {
                CircularProgressIndicator(modifier = Modifier.size(9.dp), strokeWidth = 1.5.dp)
                HomeSidePanelToolbarStatusText(stringResource(R.string.wekit_sp_loading))
            }

            HomeSidePanelStatusUiState.NoStatus -> {
                Box(Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF31B36B)))
                HomeSidePanelToolbarStatusText(stringResource(R.string.wekit_sp_home_side_panel_online))
            }

            is HomeSidePanelStatusUiState.Ready -> {
                HomeSidePanelTextStatusIcon(status.status, 18.dp)
                HomeSidePanelToolbarStatusText(status.status.description)
            }

            HomeSidePanelStatusUiState.Error -> {
                Icon(
                    imageVector = LucideIcons.X,
                    contentDescription = null,
                    modifier = Modifier.size(11.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
                HomeSidePanelToolbarStatusText(
                    stringResource(R.string.wekit_sp_home_side_panel_fetch_failed),
                    MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun HomeSidePanelTextStatusIcon(status: TextStatus, size: Dp) {
    val iconTint = MaterialTheme.colorScheme.onSurface.toArgb()
    key(status.iconId) {
        AndroidView(
            factory = { context ->
                ImageView(context).apply {
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    contentDescription = status.description
                    setImageBitmap(status.iconBitmap)
                    setColorFilter(iconTint, PorterDuff.Mode.SRC_IN)
                }
            },
            update = { imageView ->
                imageView.contentDescription = status.description
                imageView.setImageBitmap(status.iconBitmap)
                imageView.setColorFilter(iconTint, PorterDuff.Mode.SRC_IN)
            },
            modifier = Modifier.size(size),
        )
    }
}

@Composable
private fun HomeSidePanelToolbarStatusText(
    text: String,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
