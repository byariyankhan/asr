package io.joinasr.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The icons that have to be drawn rather than typed.
 *
 * The tab bar keeps its glyphs: ⌂ ▥ ◎ ○ are what the frames specify, they
 * read correctly next to their labels, and they are fine on a phone. These
 * are not. Figma draws the notification bell as a vector, and there is
 * no bell character in a system font that is not the full-colour emoji —
 * which arrives at whatever size the emoji font decides and looks like a
 * sticker glued to the header. The padlock has the same problem in an even
 * smaller space, and the earn chooser's three activities need a matched
 * set, which three arrows from a system font were never going to be.
 *
 * All are defined on a 24-unit grid and scaled, so one drawn at 22dp and
 * another at 12dp keep the same stroke weight relative to themselves.
 */
object AsrIcons {

    /** The proportional stroke, so every icon looks like it was drawn by the same hand. */
    private const val STROKE = 1.8f

    /** The notification bell from the dashboard header. */
    @Composable
    fun Bell(colour: Color, size: Dp = 20.dp) = Icon(size) { scale ->
        val stroke = strokeOf(scale)
        drawPath(
            path = path(scale) {
                // The body: two shoulders, a dome, and the flare at the
                // mouth, closed. One path rather than several, so the
                // corners join instead of meeting.
                moveTo(5.0f, 17.2f)
                lineTo(6.7f, 14.6f)
                lineTo(6.7f, 10.6f)
                cubicTo(6.7f, 7.0f, 9.1f, 4.4f, 12f, 4.4f)
                cubicTo(14.9f, 4.4f, 17.3f, 7.0f, 17.3f, 10.6f)
                lineTo(17.3f, 14.6f)
                lineTo(19.0f, 17.2f)
                close()
            },
            color = colour,
            style = stroke,
        )
        drawPath(
            path = path(scale) {
                // The clapper, swinging just under the mouth.
                moveTo(10.2f, 17.8f)
                cubicTo(10.5f, 19.8f, 13.5f, 19.8f, 13.8f, 17.8f)
            },
            color = colour,
            style = stroke,
        )
    }

    /**
     * A padlock, for the corner of an app whose limit is spent.
     *
     * Its own drawing rather than a font's: the only lock in a system font
     * is the emoji, which arrives full colour and at whatever size the emoji
     * font decides, and this has to sit inside eighteen density-independent
     * pixels beside a real app icon without looking like a sticker.
     */
    @Composable
    fun Lock(colour: Color, size: Dp = 12.dp) = Icon(size) { scale ->
        val centreX = 12f * scale
        val shackleRadius = 3.4f * scale
        val shoulder = 11.6f * scale
        drawArc(
            color = colour,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(centreX - shackleRadius, shoulder - shackleRadius),
            size = Size(shackleRadius * 2, shackleRadius * 2),
            style = Stroke(width = 2f * scale, cap = StrokeCap.Round),
        )
        val bodyWidth = 11f * scale
        val bodyHeight = 8f * scale
        drawRoundRect(
            color = colour,
            topLeft = Offset(centreX - bodyWidth / 2, shoulder),
            size = Size(bodyWidth, bodyHeight),
            cornerRadius = CornerRadius(2f * scale, 2f * scale),
        )
    }

    /**
     * The three earn activities, each a small figure doing the thing. Each
     * takes a [phase] from 0 to 1 so the chooser can move them (a step, a
     * phone set down, a push-up) and the sheet can hold them still; the
     * drawing is a function of the phase, nothing is stored.
     */

    /**
     * A person walking, seen from the side: head, torso, and the legs and
     * arms swung against each other over the ground. Phase 0 is one leg
     * forward, 1 the other; halfway the legs pass and the body dips.
     */
    @Composable
    fun Walk(colour: Color, phase: Float, size: Dp = 24.dp) = Icon(size) { scale ->
        val stroke = strokeOf(scale)
        val swing = phase * 2f - 1f                    // -1..1
        val bob = 0.5f * (1f - kotlin.math.abs(swing)) // dips as the legs pass
        val hipY = 12.6f + bob
        drawCircle(
            color = colour,
            radius = 2.1f * scale,
            center = Offset(12f * scale, (4.4f + bob) * scale),
        )
        drawPath(
            path = path(scale) {
                // Torso, leaning a little into the walk.
                moveTo(12.3f, 6.9f + bob)
                lineTo(11.8f, hipY)
                // Legs: the forward one reaches, the back one trails.
                moveTo(11.8f, hipY)
                lineTo(11.8f + 4.6f * swing, 19.4f)
                moveTo(11.8f, hipY)
                lineTo(11.8f - 4.0f * swing, 19.4f)
                // Arms, swung the other way, and never quite together, so
                // the figure does not fold into its own torso mid-stride.
                moveTo(12.2f, 8.2f + bob)
                lineTo(11.0f - 3.6f * swing, 12.8f)
                moveTo(12.2f, 8.2f + bob)
                lineTo(13.4f + 3.6f * swing, 12.8f)
                // The ground.
                moveTo(3.5f, 20.4f)
                lineTo(20.5f, 20.4f)
            },
            color = colour,
            style = stroke,
        )
    }

    /**
     * A phone being put down on a surface, seen from the front: standing
     * at phase 0, tipping away and foreshortening until it lies flat at 1.
     * The bottom edge stays on the surface throughout; the height is what
     * changes, so nothing swings through the line.
     */
    @Composable
    fun Focus(colour: Color, phase: Float, size: Dp = 24.dp) = Icon(size) { scale ->
        val stroke = strokeOf(scale)
        val tilt = Math.toRadians(80.0 * phase)
        val height = 13f * kotlin.math.cos(tilt).toFloat().coerceAtLeast(0.2f)
        val top = 20f - height
        val corner = (1.6f).coerceAtMost(height / 2f)
        drawPath(
            path = path(scale) {
                moveTo(3.5f, 20f)
                lineTo(20.5f, 20f)
            },
            color = colour,
            style = stroke,
        )
        drawRoundRect(
            color = colour,
            topLeft = Offset(8f * scale, top * scale),
            size = Size(8f * scale, height * scale),
            cornerRadius = CornerRadius(corner * scale),
            style = stroke,
        )
        // The earpiece, so the rectangle reads as a phone; it foreshortens
        // with the rest and is gone once the phone is flat.
        if (height > 4f) {
            val ear = top + 2.2f * (height / 13f)
            drawPath(
                path = path(scale) {
                    moveTo(10.6f, ear)
                    lineTo(13.4f, ear)
                },
                color = colour,
                style = stroke,
            )
        }
    }

    /**
     * A push-up from the side: feet on the floor, the body straight from
     * them to the shoulder, one arm to the floor, the head ahead. Phase 0
     * is the top, 1 the bottom; the arm bends as the body comes down.
     */
    @Composable
    fun PushUps(colour: Color, phase: Float, size: Dp = 24.dp) = Icon(size) { scale ->
        val stroke = strokeOf(scale)
        val feet = Offset(20.2f, 19.2f)
        val shoulder = Offset(7.6f, 13.0f + 3.4f * phase)
        val hand = Offset(9.8f, 19.6f)
        // The head sits a little past the shoulder along the body's line.
        val dx = shoulder.x - feet.x
        val dy = shoulder.y - feet.y
        val len = kotlin.math.sqrt(dx * dx + dy * dy)
        val head = Offset(shoulder.x + dx / len * 3.4f, shoulder.y + dy / len * 3.4f)
        val elbow = Offset(
            (shoulder.x + hand.x) / 2f + 3.4f * phase,
            (shoulder.y + hand.y) / 2f + 0.6f * phase,
        )
        drawCircle(color = colour, radius = 2.1f * scale, center = Offset(head.x * scale, head.y * scale))
        drawPath(
            path = path(scale) {
                moveTo(shoulder.x, shoulder.y)
                lineTo(feet.x, feet.y)
                moveTo(shoulder.x, shoulder.y)
                lineTo(elbow.x, elbow.y)
                lineTo(hand.x, hand.y)
                moveTo(3.5f, 20f)
                lineTo(20.5f, 20f)
            },
            color = colour,
            style = stroke,
        )
    }

    /**
     * A plank from the side: forearms on the floor, the body one straight
     * line from shoulder to heel, low. Phase moves the body a hair, the
     * breathing of somebody holding still.
     */
    @Composable
    fun Plank(colour: Color, phase: Float, size: Dp = 24.dp) = Icon(size) { scale ->
        val stroke = strokeOf(scale)
        val breath = 0.35f * phase
        val shoulder = Offset(7.4f, 12.6f - breath)
        val heel = Offset(20.4f, 16.6f)
        drawCircle(color = colour, radius = 2.0f * scale, center = Offset(4.2f * scale, (11.2f - breath) * scale))
        drawPath(
            path = path(scale) {
                // Shoulder to heel.
                moveTo(shoulder.x, shoulder.y)
                lineTo(heel.x, heel.y)
                // Upper arm down to the elbow, forearm along the floor.
                moveTo(shoulder.x, shoulder.y)
                lineTo(6.6f, 18.4f)
                lineTo(11.4f, 18.4f)
                // The floor.
                moveTo(3.5f, 20f)
                lineTo(20.5f, 20f)
            },
            color = colour,
            style = stroke,
        )
    }

    /**
     * A wall sit from the side: a wall on the left, the back flat against
     * it, thighs level, shins down to the floor. Phase is the shake of a
     * hold that is starting to hurt.
     */
    @Composable
    fun WallSit(colour: Color, phase: Float, size: Dp = 24.dp) = Icon(size) { scale ->
        val stroke = strokeOf(scale)
        val shake = 0.3f * phase
        val hip = Offset(8.6f, 13.2f + shake)
        val knee = Offset(15.4f, 13.2f + shake)
        drawCircle(color = colour, radius = 2.0f * scale, center = Offset(8.6f * scale, 5.2f * scale))
        drawPath(
            path = path(scale) {
                // The wall.
                moveTo(5.6f, 3.5f)
                lineTo(5.6f, 20f)
                // Back, thigh, shin.
                moveTo(8.6f, 7.4f)
                lineTo(hip.x, hip.y)
                lineTo(knee.x, knee.y)
                lineTo(15.4f, 19.6f)
                // The floor.
                moveTo(5.6f, 20f)
                lineTo(20.5f, 20f)
            },
            color = colour,
            style = stroke,
        )
    }

    /**
     * One icon on a 24-unit grid.
     *
     * [content] receives the scale from grid units to pixels, so every path
     * below can be written in the same coordinates whatever size it is drawn
     * at.
     */
    @Composable
    private fun Icon(size: Dp, content: DrawScope.(scale: Float) -> Unit) {
        Canvas(modifier = Modifier.size(size)) {
            content(this.size.minDimension / 24f)
        }
    }

    private fun strokeOf(scale: Float) = Stroke(
        width = STROKE * scale,
        cap = StrokeCap.Round,
        join = StrokeJoin.Round,
    )

    private inline fun path(scale: Float, build: PathBuilder.() -> Unit): Path {
        val path = Path()
        PathBuilder(path, scale).build()
        return path
    }

    /** Writes grid coordinates into a [Path] in pixels. */
    class PathBuilder(private val path: Path, private val scale: Float) {
        fun moveTo(x: Float, y: Float) = path.moveTo(x * scale, y * scale)

        fun lineTo(x: Float, y: Float) = path.lineTo(x * scale, y * scale)

        fun cubicTo(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) =
            path.cubicTo(
                x1 * scale, y1 * scale,
                x2 * scale, y2 * scale,
                x3 * scale, y3 * scale,
            )

        fun close() = path.close()
    }
}
