package dev.gzclp.tracker.ui

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * A Liquid Glass lens over this content: inside [pill] (in this content's coordinates) the content magnifies a little and bends
 * outward along the rim, as content under a held iOS 26 control does; [amount] (0 to 1) fades it in, and at 0 nothing changes.
 * On API 33 and up it is an AGSL shader ([LENS]), compiled once per lens; below, the content inside the pill is only magnified.
 */
@Composable
fun Modifier.glassLens(pill: () -> RoundRect?, amount: () -> Float): Modifier =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val shader = remember { RuntimeShader(LENS) }
        graphicsLayer {
            val a = amount()
            val r = pill()
            // Each effect takes the uniforms as they are when it is made, so one shader serves every frame.
            renderEffect = if (a <= 0f || r == null) null else shader.run {
                setFloatUniform("box", r.left, r.top, r.right, r.bottom)
                setFloatUniform("radius", r.bottomRightCornerRadius.y)
                setFloatUniform("amount", a)
                setFloatUniform("rim", LensRim.toPx())
                RenderEffect.createRuntimeShaderEffect(this, "content").asComposeRenderEffect()
            }
        }
    } else drawWithContent {
        val a = amount()
        val r = pill()
        if (a <= 0f || r == null) return@drawWithContent drawContent()
        val path = Path().apply { addRoundRect(r) }
        clipPath(path, ClipOp.Difference) { this@drawWithContent.drawContent() }
        clipPath(path) { scale(1f + LENS_MAGNIFY * a, androidx.compose.ui.geometry.Offset((r.left + r.right) / 2, (r.top + r.bottom) / 2)) { this@drawWithContent.drawContent() } }
    }

/** How much a full lens magnifies at its center. */
const val LENS_MAGNIFY = 0.08f

/** How far in from the rim the bending reaches. */
val LensRim = 10.dp

/** The signed distance from p to the edge of the rounded rect [box] with corners of [radius] (negative inside), and its outward normal. */
private const val EDGE = """
float edge(float2 p) {
    float2 c = (box.xy + box.zw) * 0.5;
    float2 q = abs(p - c) - ((box.zw - box.xy) * 0.5 - radius);
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
}

float2 normal(float2 p) {
    float2 g = float2(edge(p + float2(0.5, 0.0)) - edge(p - float2(0.5, 0.0)), edge(p + float2(0.0, 0.5)) - edge(p - float2(0.0, 0.5)));
    return g / max(length(g), 0.0001);
}
"""

/**
 * Samples the content nearer the pill's center than each pixel (the magnify), and within [rim] of the edge, the more the nearer it,
 * out past the edge along the rim's normal: so the rim gathers in what lies just outside the pill, squeezed, as a glass edge bends it.
 */
const val LENS = """
uniform shader content;
uniform float4 box;
uniform float radius;
uniform float amount;
uniform float rim;

$EDGE

half4 main(float2 p) {
    float d = edge(p);
    if (d >= 0.0) return content.eval(p);
    float2 c = (box.xy + box.zw) * 0.5;
    float2 n = normal(p);
    float t = clamp(1.0 + d / rim, 0.0, 1.0);
    float2 s = c + (p - c) / (1.0 + $LENS_MAGNIFY * amount) + n * t * t * rim * amount;
    return content.eval(s);
}
"""
