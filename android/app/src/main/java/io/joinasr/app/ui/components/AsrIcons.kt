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
 * The two icons that have to be drawn rather than typed.
 *
 * The tab bar keeps its glyphs: ⌂ ▥ ◎ ○ are what the frames specify, they
 * read correctly next to their labels, and they are fine on a phone. These
 * two are not. Figma draws the notification bell as a vector, and there is
 * no bell character in a system font that is not the full-colour emoji —
 * which arrives at whatever size the emoji font decides and looks like a
 * sticker glued to the header. The padlock has the same problem in an even
 * smaller space.
 *
 * Both are defined on a 24-unit grid and scaled, so one drawn at 22dp and
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
