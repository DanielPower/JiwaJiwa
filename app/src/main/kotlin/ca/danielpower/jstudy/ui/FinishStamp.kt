package ca.danielpower.jstudy.ui

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Room kept for [FinishStamp] under the last line of horizontal text, with a wide gap above it. */
internal val STAMP_ACROSS_HEIGHT = 160.dp

/** Room kept for [FinishStamp] beside the last column of vertical text. */
internal val STAMP_DOWN_WIDTH = 128.dp

/** Gap between the last line of horizontal text and [FinishStamp]. */
internal val STAMP_GAP = 40.dp

/**
 * The end of a story's text: an empty dashed outline with "Finish" under it.
 * Tapping it stamps a 読了 ("finished reading") seal in vermilion, grained like
 * the app icon and a little askew, then marks the story read. In [vertical]
 * text the seal stands upright, one column wide.
 */
@Composable
internal fun FinishStamp(vertical: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val seal = MaterialTheme.colorScheme.primary
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    // Milliseconds into the stamping, from 0 (just the outline) to STAMP_END.
    val clock = remember { Animatable(0f) }
    var stamped by remember { mutableStateOf(false) }
    // A hand stamp never lands quite straight: 2 to 7 degrees either way.
    val tilt = remember { (2f + Random.nextFloat() * 5f) * if (Random.nextBoolean()) 1 else -1 }
    val presses = remember { MutableInteractionSource() }
    val pressed by presses.collectIsPressedAsState()
    val dip by animateFloatAsState(if (pressed && !stamped) 0.95f else 1f, tween(80), label = "dip")
    Column(
        modifier
            .clickable(presses, indication = null, enabled = !stamped, onClickLabel = "Finish reading", role = Role.Button) {
                stamped = true
                scope.launch {
                    if (reduceMotion(context)) {
                        clock.snapTo(STAMP_END)
                        delay(400)
                    } else {
                        launch { clock.animateTo(STAMP_END, tween(STAMP_END.toInt(), easing = LinearEasing)) }
                        delay(LAND.toLong())
                        view.performHapticFeedback(
                            if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY,
                        )
                        // Long enough to see the stamp before the summary takes its place.
                        delay(600 - LAND.toLong())
                    }
                    onClick()
                }
            }
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val shape = RoundedCornerShape(5.dp)
        Box(
            Modifier
                .graphicsLayer {
                    scaleX = dip
                    scaleY = dip
                }
                .drawBehind {
                    val t = clock.value
                    val dash = 1f - ((t - 160f) / 40f).coerceIn(0f, 1f)
                    val radius = CornerRadius(5.dp.toPx())
                    val width = 2.dp.toPx()
                    if (dash > 0f) {
                        drawRoundRect(
                            seal.copy(alpha = 0.7f * dash),
                            topLeft = Offset(width / 2, width / 2),
                            size = Size(size.width - width, size.height - width),
                            cornerRadius = radius,
                            style = Stroke(width, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
                        )
                    }
                    // A faint ring of ink spreading out as it lands.
                    if (t > LAND) {
                        val r = ((t - LAND) / (STAMP_END - LAND)).coerceIn(0f, 1f)
                        withTransform({
                            rotate(tilt)
                            scale(1f + 0.3f * FastOutSlowInEasing.transform(r))
                        }) {
                            drawRoundRect(seal.copy(alpha = 0.4f * (1f - r)), cornerRadius = radius, style = Stroke(width))
                        }
                    }
                },
        ) {
            val box = Modifier
                .graphicsLayer {
                    val t = clock.value
                    val drop = DropEasing.transform((t / LAND).coerceIn(0f, 1f))
                    val s = sealScale(t)
                    alpha = drop
                    scaleX = s
                    scaleY = s
                    translationY = -14.dp.toPx() * (1f - drop)
                    rotationZ = tilt * drop
                }
                .stampGrain()
                .border(3.dp, seal, shape)
            if (vertical) {
                Column(box.padding(horizontal = 12.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    for (c in "読了") {
                        Text(c.toString(), fontFamily = Gothic, fontWeight = FontWeight.Bold, fontSize = 40.sp, lineHeight = 46.sp, color = seal)
                    }
                }
            } else {
                Text(
                    "読了",
                    modifier = box.padding(horizontal = 18.dp, vertical = 6.dp),
                    fontFamily = Gothic,
                    fontWeight = FontWeight.Bold,
                    fontSize = 40.sp,
                    lineHeight = 48.sp,
                    letterSpacing = 4.sp,
                    color = seal,
                )
            }
        }
        // Left clean of grain, so the small English stays easy to read.
        Text("Finish", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = seal)
    }
}

/** When the seal lands, in milliseconds after the tap; the ink ring fades by [STAMP_END]. */
private const val LAND = 180f
private const val STAMP_END = 500f

/** Falls slowly at first, then fast, like a hand bringing a stamp down. */
private val DropEasing = CubicBezierEasing(0.5f, 0f, 0.9f, 0.6f)

/** Comes down from larger, squashes a little on landing and settles by 320ms. */
private fun sealScale(t: Float): Float = when {
    t < LAND -> 1.4f + (0.92f - 1.4f) * DropEasing.transform(t / LAND)
    t < 256f -> 0.92f + (1.03f - 0.92f) * FastOutSlowInEasing.transform((t - LAND) / (256f - LAND))
    t < 320f -> 1.03f + (1f - 1.03f) * ((t - 256f) / 64f)
    else -> 1f
}

/** The phone's "Remove animations" setting: the stamp then appears at once. */
private fun reduceMotion(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

/** Knocks speckles out of whatever it draws, like ink that didn't take on a stamp. */
internal fun Modifier.stampGrain(): Modifier =
    graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen).drawWithContent {
        drawContent()
        drawRect(GrainBrush, blendMode = BlendMode.DstOut)
    }

private val GrainBrush by lazy { ShaderBrush(ImageShader(grainTile(), TileMode.Repeated, TileMode.Repeated)) }

/**
 * A seamless tile, opaque where the ink skips. Coarse noise mixed with fine
 * makes the gaps clump, as the icon's fractal noise does, rather than an
 * even mist. Seeded, so the stamp looks the same every time.
 */
private fun grainTile(): ImageBitmap {
    val n = 96
    val step = 4
    val m = n / step
    val random = Random(9)
    val coarse = Array(m) { FloatArray(m) { random.nextFloat() } }
    fun at(x: Int, y: Int) = coarse[Math.floorMod(y, m)][Math.floorMod(x, m)]
    val pixels = IntArray(n * n) { k ->
        val x = k % n
        val y = k / n
        // Bilinear between coarse points, wrapping so the tile repeats without seams.
        val gx = x / step
        val gy = y / step
        val fx = (x % step) / step.toFloat()
        val fy = (y % step) / step.toFloat()
        val top = at(gx, gy) * (1 - fx) + at(gx + 1, gy) * fx
        val bottom = at(gx, gy + 1) * (1 - fx) + at(gx + 1, gy + 1) * fx
        val v = 0.6f * (top * (1 - fy) + bottom * fy) + 0.4f * random.nextFloat()
        // About a fifth of the ink drops out.
        if (v > 0.68f) 0xFF000000.toInt() else 0
    }
    return Bitmap.createBitmap(pixels, n, n, Bitmap.Config.ARGB_8888).asImageBitmap()
}
