package io.max.paperplay.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.max.paperplay.ui.res.Res
import io.max.paperplay.ui.res.app_plane
import org.jetbrains.compose.resources.imageResource
import io.max.paperplay.ui.theme.LocalReduceMotion
import org.jetbrains.compose.ui.tooling.preview.Preview
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

// The launcher icon, themed: a flat primary-colour square with the onPrimary paper plane. The plane is the launcher's own image (res/drawable-xxxhdpi/ic_launcher_foreground.png).

// One loop of the story. The beats are fractions of the loop (LOOP_MS long); the order is bob, fly out, note appears,
// plane returns and hits the note, note flies off, plane settles.
private const val LOOP_MS = 7000
private const val OUT_START = 0.30f
private const val NOTE_IN = 0.45f
private const val RETURN_START = 0.55f
private const val HIT = 0.765f
private const val ARRIVE = 0.78f
private const val SETTLE_END = 0.90f
private const val KNOCK_LENGTH = 0.09f
private const val SQUASH_LENGTH = 0.085f

// Where the note floats, as fractions of the icon: just in front of the plane's tip, so the returning plane meets it.
private const val NOTE_X = 0.66f
private const val NOTE_Y = 0.5f
private const val NOTE_SIZE = 0.2f

/**
 * The launcher icon, alive. The scalloped badge turns slowly and the plane bobs in the air. Then it swoops off to the right, a music
 * note pops up where it was, the plane glides back in from the left and hits the note, which spins away in a burst of sparkles while
 * the icon squashes and springs back. With Reduce motion on, the icon is drawn still.
 */
@Composable
internal fun AnimatedLogo(modifier: Modifier = Modifier, size: Dp = 96.dp) {
    val reduced = LocalReduceMotion.current
    val pop = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) { if (!reduced) pop.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessLow)) }
    if (reduced) {
        LogoCanvas(0f, modifier, size)
        return
    }
    val t = rememberInfiniteTransition(label = "logo")
    val cycle by t.animateFloat(0f, 1f, infiniteRepeatable(tween(LOOP_MS, easing = LinearEasing)), label = "cycle")
    LogoCanvas(cycle, modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value }, size)
}

/** The icon standing still, for places that only show it (the About card on Profile). */
@Composable
internal fun StaticLogo(modifier: Modifier = Modifier, size: Dp = 56.dp) = LogoCanvas(0f, modifier, size)

private class Flight(val dx: Float, val lift: Float, val tilt: Float, val trail: Float)

private fun easeOutBack(x: Float): Float {
    val c1 = 1.70158f
    val c3 = c1 + 1f
    return 1f + c3 * (x - 1f).pow(3) + c1 * (x - 1f).pow(2)
}

/** The plane's place at [cycle], as fractions of the icon size. */
private fun flight(cycle: Float): Flight = when {
    cycle < OUT_START -> {
        val w = sin(cycle / OUT_START * 2 * PI).toFloat()
        Flight(0f, w * -0.025f, w * 4f, 0f)
    }
    cycle < NOTE_IN -> {
        val p = (cycle - OUT_START) / (NOTE_IN - OUT_START)
        val e = p * p * p
        Flight(e * 1.1f, -e * 0.12f, -e * 14f, p)
    }
    cycle < RETURN_START -> Flight(1.1f, -0.12f, -14f, 1f) // out of sight, waiting for the note to appear
    cycle < ARRIVE -> {
        val p = (cycle - RETURN_START) / (ARRIVE - RETURN_START)
        val e = p * p // speeds up on the way in
        Flight(-1.1f * (1f - e), 0.1f * (1f - e), 10f * (1f - e), 1f - e)
    }
    cycle < SETTLE_END -> {
        val p = (cycle - ARRIVE) / (SETTLE_END - ARRIVE)
        Flight(0.03f * sin(p * PI).toFloat() * (1f - p), 0f, 0f, 0f) // a little overshoot after the hit
    }
    else -> Flight(0f, 0f, 0f, 0f)
}

/** Draws the icon for one moment of the loop ([cycle] 0..1). A pure function of its inputs, so each beat can be previewed. */
@Composable
internal fun LogoCanvas(cycle: Float, modifier: Modifier = Modifier, size: Dp = 96.dp) {
    val note = rememberVectorPainter(Icons.Rounded.MusicNote)
    val plane = imageResource(Res.drawable.app_plane)
    val badge = androidx.compose.material3.MaterialTheme.colorScheme.primary
    val ink = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        val c = Offset(s / 2, s / 2)

        // The squash after the hit: a short, damped wobble of the whole icon.
        val squashT = ((cycle - HIT) / SQUASH_LENGTH).coerceIn(0f, 1f)
        val squash = if (cycle < HIT) 0f else sin(squashT * PI).toFloat() * (1f - squashT)
        scale(1f - 0.05f * squash, 1f + 0.04f * squash, c) {
            drawRoundRect(badge, size = Size(s, s), cornerRadius = CornerRadius(s * 0.34f))

            clipPath(Path().apply { addRoundRect(RoundRect(0f, 0f, s, s, CornerRadius(s * 0.34f))) }) {
                drawNote(note, cycle, s, ink)
                val f = flight(cycle)
                translate(left = f.dx * s, top = f.lift * s) {
                    rotate(f.tilt, c) {
                        if (f.trail > 0.02f) for (i in 0..2) {
                            val y = s * (0.43f + 0.07f * i)
                            drawLine(
                                ink.copy(alpha = 0.55f * f.trail), Offset(s * (0.06f + 0.05f * i), y), Offset(s * (0.24f + 0.03f * i), y),
                                strokeWidth = s * 0.028f, cap = StrokeCap.Round,
                            )
                        }
                        // The launcher's adaptive icon shows the middle two thirds of its canvas, with the plane inset 16%: that is 1.02 times the icon.
                        val side = (s * 1.02f).toInt()
                        val edge = ((s - side) / 2f).toInt()
                        drawImage(plane, dstOffset = IntOffset(edge, edge), dstSize = IntSize(side, side), filterQuality = FilterQuality.High, colorFilter = ColorFilter.tint(ink))
                    }
                }
            }
        }
    }
}

/** The music note and the sparkles of the hit: nothing before the note appears, a burst and a spin-away after the hit. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawNote(note: androidx.compose.ui.graphics.painter.Painter, cycle: Float, s: Float, ink: Color) {
    if (cycle < NOTE_IN) return
    val w = s * NOTE_SIZE * 2
    val baseX = s * NOTE_X
    val baseY = s * NOTE_Y
    if (cycle < HIT) {
        // Pops in with a wobble, then floats.
        val q = ((cycle - NOTE_IN) / (RETURN_START - NOTE_IN)).coerceIn(0f, 1f)
        val grow = easeOutBack(q)
        val wobble = sin(q * 3 * PI).toFloat() * 10f * (1f - q)
        val float = sin((cycle - NOTE_IN) * 2 * PI * 4).toFloat() * s * 0.008f
        placeNote(note, baseX, baseY + float, w, grow, wobble, 1f, ink)
        return
    }
    val u = ((cycle - HIT) / KNOCK_LENGTH).coerceIn(0f, 1f)
    if (u < 1f) {
        // Knocked up and to the right, spinning and shrinking.
        placeNote(note, baseX + s * 0.30f * u, baseY - s * 0.42f * u + s * 0.25f * u * u, w, 1f - u, 540f * u, 1f - u, ink)
        // Five sparkles fly outwards from the point of impact and fade.
        for (i in 0 until 5) {
            val a = (i * 72f - 90f) * PI.toFloat() / 180f
            val r = s * 0.22f * u
            drawCircle(ink.copy(alpha = 1f - u), radius = s * 0.018f * (1f - 0.5f * u), center = Offset(baseX - s * 0.04f + r * cos(a), baseY + r * sin(a)))
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.placeNote(
    note: androidx.compose.ui.graphics.painter.Painter, x: Float, y: Float, w: Float, scaleBy: Float, rotateBy: Float, alpha: Float, ink: Color,
) {
    val pivot = Offset(w / 2, w / 2)
    translate(left = x - w / 2, top = y - w / 2) {
        rotate(rotateBy, pivot) {
            scale(scaleBy, pivot) {
                with(note) { draw(Size(w, w), alpha = alpha.coerceIn(0f, 1f), colorFilter = ColorFilter.tint(ink)) }
            }
        }
    }
}

@Preview(name = "Bobbing", widthDp = 120, heightDp = 120)
@Composable
private fun LogoBobbingPreview() = LogoCanvas(0.12f)

@Preview(name = "Note appears", widthDp = 120, heightDp = 120)
@Composable
private fun LogoNotePreview() = LogoCanvas(0.52f)

@Preview(name = "Plane returns", widthDp = 120, heightDp = 120)
@Composable
private fun LogoReturnPreview() = LogoCanvas(0.74f)

@Preview(name = "Hit", widthDp = 120, heightDp = 120)
@Composable
private fun LogoHitPreview() = LogoCanvas(0.80f)
