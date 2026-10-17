package ca.danielpower.jstudy.ui

import android.graphics.Bitmap
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.random.Random

/** Room kept for [FinishStamp] under the last line of horizontal text. */
internal val STAMP_ACROSS_HEIGHT = 76.dp

/** Room kept for [FinishStamp] beside the last column of vertical text. */
internal val STAMP_DOWN_WIDTH = 56.dp

/**
 * The end of a story's text: a 読了 ("finished reading") seal in vermilion,
 * grained like the app icon, with "Finish" under it. Tapping it marks the
 * story read. In [vertical] text the seal stands upright, one column wide.
 */
@Composable
internal fun FinishStamp(vertical: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val seal = MaterialTheme.colorScheme.primary
    Column(
        modifier
            .clip(WashiShape)
            .clickable(onClickLabel = "Finish reading", role = Role.Button, onClick = onClick)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val box = Modifier.stampGrain().border(2.dp, seal, RoundedCornerShape(4.dp))
        if (vertical) {
            Column(box.padding(horizontal = 4.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                for (c in "読了") {
                    Text(c.toString(), fontFamily = Gothic, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 24.sp, color = seal)
                }
            }
        } else {
            Text(
                "読了",
                modifier = box.padding(horizontal = 10.dp, vertical = 2.dp),
                fontFamily = Gothic,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                letterSpacing = 2.sp,
                color = seal,
            )
        }
        // Left clean of grain, so the small English stays easy to read.
        Text("Finish", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = seal)
    }
}

/** Knocks speckles out of whatever it draws, like ink that didn't take on a stamp. */
private fun Modifier.stampGrain(): Modifier =
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
