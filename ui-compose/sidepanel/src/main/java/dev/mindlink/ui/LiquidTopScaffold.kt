package dev.mindlink.ui

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import kotlin.math.ceil
import kotlin.math.roundToInt

private const val TOP_MASK = """
    uniform shader content;
    uniform float band;
    half4 main(float2 coord) {
        return content.eval(coord) * (1.0 - smoothstep(band * 0.64, band, coord.y));
    }
"""

/** The list uses the header height as content padding so it can scroll behind the glass. */
@Composable
internal fun LiquidTopScaffold(
    modifier: Modifier = Modifier,
    header: @Composable ColumnScope.() -> Unit,
    content: @Composable (Dp) -> Unit,
) {
    val statusInset = WindowInsets.safeDrawing.only(WindowInsetsSides.Top).asPaddingValues().calculateTopPadding()
    val density = LocalDensity.current
    var headerPx by remember { mutableIntStateOf(0) }
    val headerHeight = if (headerPx > 0) with(density) { headerPx.toDp() } else statusInset + 64.dp
    val backdrop = rememberGraphicsLayer()
    val blur = rememberGraphicsLayer()
    val tint = MaterialTheme.colorScheme.surface
    Box(modifier.fillMaxSize()) {
        Box(Modifier.matchParentSize().drawWithContent {
            if (!drawContext.canvas.nativeCanvas.isHardwareAccelerated) {
                drawContent()
            } else {
                backdrop.record { this@drawWithContent.drawContent() }
                drawLayer(backdrop)
            }
        }) { content(headerHeight) }
        Box(Modifier.matchParentSize().drawWithCache {
            val band = (headerHeight.toPx() * 1.18f).coerceAtMost(size.height)
            if (band <= 0f || size.width <= 0f) return@drawWithCache onDrawBehind { }
            val brush = Brush.verticalGradient(
                0f to tint.copy(alpha = .92f), .54f to tint.copy(alpha = .82f),
                .72f to tint.copy(alpha = .54f), 1f to tint.copy(alpha = 0f),
                endY = band,
            )
            val radius = 10.dp.toPx()
            val layerSize = IntSize(size.width.roundToInt(), ceil((band + radius * 2f).coerceAtMost(size.height)).toInt())
            val effect = runCatching {
                val mask = RuntimeShader(TOP_MASK).apply { setFloatUniform("band", band) }
                RenderEffect.createChainEffect(
                    RenderEffect.createRuntimeShaderEffect(mask, "content"),
                    RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP),
                ).asComposeRenderEffect()
            }.getOrNull()
            onDrawBehind {
                if (effect != null && drawContext.canvas.nativeCanvas.isHardwareAccelerated) {
                    blur.renderEffect = effect
                    blur.record(size = layerSize) { drawLayer(backdrop) }
                    clipRect(bottom = band) { drawLayer(blur) }
                }
                drawRect(brush, topLeft = Offset.Zero, size = Size(size.width, band))
            }
        })
        Column(
            Modifier.fillMaxWidth().onSizeChanged { headerPx = it.height }.pointerInput(Unit) {
                // The scrolled content behind empty parts of the header isn't a touch target.
                awaitPointerEventScope { while (true) awaitPointerEvent() }
            }.padding(top = statusInset),
            content = header,
        )
    }
}
