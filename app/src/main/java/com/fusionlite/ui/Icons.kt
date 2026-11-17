package com.fusionlite.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * One vector icon family, drawn on a 24x24 grid with a constant 2px stroke.
 *
 * Hand-drawn on purpose: the project has no icon-font dependency, and every
 * glyph inherits the active tint so light/dark theming is automatic. Emoji are
 * never used as structural icons.
 */
enum class FusionIcon {
    SUN,
    BOLT,
    BATTERY,
    PANEL,
    REFRESH,
    CHEVRON_LEFT,
    CHEVRON_RIGHT,
    CHEVRON_DOWN,
    CHEVRON_UP,
    EXPAND,
    CLOSE,
    CHECK,
    ALERT,
    INFO,
    HOME,
    CHART,
    COIN,
    USER,
    EYE,
    EYE_OFF,
    SERVER,
    ARROW_UP_RIGHT,
    ARROW_DOWN_LEFT,
    LOCK,
    GRID,
    SPARK,
    GAUGE,
    COPY,
}

@Composable
fun FusionIcon(
    icon: FusionIcon,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    size: Dp = 24.dp,
    contentDescription: String? = null,
) {
    val semantics = if (contentDescription == null) {
        // Decorative: hide from the accessibility tree so TalkBack does not
        // announce an unlabeled image next to visible text.
        Modifier.clearAndSetSemantics { }
    } else {
        Modifier.semantics { this.contentDescription = contentDescription }
    }
    Canvas(modifier.size(size).then(semantics)) {
        drawFusionIcon(icon, tint)
    }
}

/** Icon plus a text label, with the icon hidden from screen readers. */
@Composable
fun FusionLabeledIcon(
    icon: FusionIcon,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
) = FusionIcon(icon = icon, modifier = modifier, tint = tint, size = size, contentDescription = null)

internal fun DrawScope.drawFusionIcon(icon: FusionIcon, tint: Color) {
    val u = size.minDimension / 24f
    val stroke = Stroke(
        width = 2f * u,
        cap = StrokeCap.Round,
        join = StrokeJoin.Round,
    )
    fun p(x: Float, y: Float) = Offset(x * u, y * u)
    fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
        drawLine(tint, p(x1, y1), p(x2, y2), strokeWidth = stroke.width, cap = StrokeCap.Round)

    fun polyline(vararg points: Pair<Float, Float>, close: Boolean = false) {
        val path = Path()
        points.forEachIndexed { index, (x, y) ->
            if (index == 0) path.moveTo(x * u, y * u) else path.lineTo(x * u, y * u)
        }
        if (close) path.close()
        drawPath(path, tint, style = stroke)
    }

    fun arc(
        left: Float, top: Float, right: Float, bottom: Float,
        start: Float, sweep: Float,
    ) = drawArc(
        color = tint,
        startAngle = start,
        sweepAngle = sweep,
        useCenter = false,
        topLeft = p(left, top),
        size = Size((right - left) * u, (bottom - top) * u),
        style = stroke,
    )

    when (icon) {
        FusionIcon.SUN -> {
            drawCircle(tint, radius = 4.2f * u, center = p(12f, 12f), style = stroke)
            repeat(8) { index ->
                val angle = index * PI / 4.0
                val inner = 7f
                val outer = 10.2f
                line(
                    (12 + cos(angle) * inner).toFloat(),
                    (12 + sin(angle) * inner).toFloat(),
                    (12 + cos(angle) * outer).toFloat(),
                    (12 + sin(angle) * outer).toFloat(),
                )
            }
        }

        FusionIcon.BOLT -> polyline(
            13.2f to 2.5f,
            5.4f to 13.6f,
            11.0f to 13.6f,
            10.4f to 21.5f,
            18.6f to 10.4f,
            12.9f to 10.4f,
            close = true,
        )

        FusionIcon.BATTERY -> {
            drawRoundRect(
                tint,
                topLeft = p(2.5f, 7f),
                size = Size(16f * u, 10f * u),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.2f * u),
                style = stroke,
            )
            line(20.5f, 10f, 22f, 10f)
            line(20.5f, 14f, 22f, 14f)
            drawRoundRect(
                tint.copy(alpha = 0.55f),
                topLeft = p(5f, 9.5f),
                size = Size(6f * u, 5f * u),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(1f * u),
            )
        }

        FusionIcon.PANEL -> {
            drawRoundRect(
                tint,
                topLeft = p(2.5f, 4f),
                size = Size(19f * u, 11f * u),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.8f * u),
                style = stroke,
            )
            line(9f, 4.4f, 9f, 14.6f)
            line(15f, 4.4f, 15f, 14.6f)
            line(3f, 9.5f, 21f, 9.5f)
            line(12f, 15f, 12f, 19f)
            line(8f, 21f, 16f, 21f)
        }

        FusionIcon.REFRESH -> {
            arc(3.5f, 3.5f, 20.5f, 20.5f, -55f, 250f)
            polyline(15.4f to 2.6f, 20.9f to 4.3f, 19.2f to 9.6f)
        }

        FusionIcon.CHEVRON_LEFT -> polyline(14.5f to 5.5f, 8f to 12f, 14.5f to 18.5f)
        FusionIcon.CHEVRON_RIGHT -> polyline(9.5f to 5.5f, 16f to 12f, 9.5f to 18.5f)
        FusionIcon.CHEVRON_DOWN -> polyline(5.5f to 9.5f, 12f to 16f, 18.5f to 9.5f)
        FusionIcon.CHEVRON_UP -> polyline(5.5f to 14.5f, 12f to 8f, 18.5f to 14.5f)

        FusionIcon.EXPAND -> {
            line(3.5f, 9f, 3.5f, 3.5f); line(3.5f, 3.5f, 9f, 3.5f)
            line(15f, 3.5f, 20.5f, 3.5f); line(20.5f, 3.5f, 20.5f, 9f)
            line(3.5f, 15f, 3.5f, 20.5f); line(3.5f, 20.5f, 9f, 20.5f)
            line(15f, 20.5f, 20.5f, 20.5f); line(20.5f, 20.5f, 20.5f, 15f)
        }

        FusionIcon.CLOSE -> {
            line(6f, 6f, 18f, 18f)
            line(18f, 6f, 6f, 18f)
        }

        FusionIcon.CHECK -> polyline(4.5f to 12.5f, 9.8f to 17.8f, 19.5f to 6.5f)

        FusionIcon.ALERT -> {
            polyline(12f to 3f, 21.5f to 20f, 2.5f to 20f, close = true)
            line(12f, 9f, 12f, 14f)
            drawCircle(tint, radius = 1.15f * u, center = p(12f, 17f))
        }

        FusionIcon.INFO -> {
            drawCircle(tint, radius = 9f * u, center = p(12f, 12f), style = stroke)
            line(12f, 11f, 12f, 17f)
            drawCircle(tint, radius = 1.15f * u, center = p(12f, 7.8f))
        }

        FusionIcon.HOME -> {
            polyline(3f to 11f, 12f to 3.8f, 21f to 11f)
            polyline(5.6f to 9.8f, 5.6f to 20.2f, 18.4f to 20.2f, 18.4f to 9.8f)
            polyline(10f to 20f, 10f to 14.5f, 14f to 14.5f, 14f to 20f)
        }

        FusionIcon.CHART -> {
            line(3f, 20.5f, 21f, 20.5f)
            drawRoundRect(
                tint,
                topLeft = p(4.5f, 12f),
                size = Size(3.4f * u, 8.5f * u),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.4f * u),
            )
            drawRoundRect(
                tint,
                topLeft = p(10.3f, 7f),
                size = Size(3.4f * u, 13.5f * u),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.4f * u),
            )
            drawRoundRect(
                tint.copy(alpha = 0.65f),
                topLeft = p(16.1f, 15f),
                size = Size(3.4f * u, 5.5f * u),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.4f * u),
            )
        }

        FusionIcon.COIN -> {
            drawCircle(tint, radius = 8.6f * u, center = p(12f, 12f), style = stroke)
            line(12f, 6.5f, 12f, 17.5f)
            drawCircle(tint, radius = 2.6f * u, center = p(12f, 12f), style = stroke)
        }

        FusionIcon.USER -> {
            drawCircle(tint, radius = 3.8f * u, center = p(12f, 7.8f), style = stroke)
            val shoulders = Path().apply {
                moveTo(4.2f * u, 20.4f * u)
                cubicTo(4.6f * u, 13.6f * u, 19.4f * u, 13.6f * u, 19.8f * u, 20.4f * u)
            }
            drawPath(shoulders, tint, style = stroke)
        }

        FusionIcon.EYE, FusionIcon.EYE_OFF -> {
            val eye = Path().apply {
                moveTo(2.2f * u, 12f * u)
                quadraticTo(7f * u, 5.2f * u, 12f * u, 5.2f * u)
                quadraticTo(17f * u, 5.2f * u, 21.8f * u, 12f * u)
                quadraticTo(17f * u, 18.8f * u, 12f * u, 18.8f * u)
                quadraticTo(7f * u, 18.8f * u, 2.2f * u, 12f * u)
                close()
            }
            drawPath(eye, tint, style = stroke)
            drawCircle(tint, radius = 2.8f * u, center = p(12f, 12f))
            if (icon == FusionIcon.EYE_OFF) {
                drawLine(
                    tint,
                    p(3.5f, 3.5f),
                    p(20.5f, 20.5f),
                    strokeWidth = 2.4f * u,
                    cap = StrokeCap.Round,
                )
            }
        }

        FusionIcon.SERVER -> {
            drawRoundRect(
                tint,
                topLeft = p(2.5f, 3.5f),
                size = Size(19f * u, 7f * u),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f * u),
                style = stroke,
            )
            drawRoundRect(
                tint,
                topLeft = p(2.5f, 13.5f),
                size = Size(19f * u, 7f * u),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f * u),
                style = stroke,
            )
            drawCircle(tint, radius = 1.1f * u, center = p(7f, 7f))
            drawCircle(tint, radius = 1.1f * u, center = p(7f, 17f))
            line(11f, 7f, 17.5f, 7f)
            line(11f, 17f, 17.5f, 17f)
        }

        FusionIcon.ARROW_UP_RIGHT -> {
            line(6.5f, 17.5f, 17.5f, 6.5f)
            polyline(8.5f to 6.5f, 17.5f to 6.5f, 17.5f to 15.5f)
        }

        FusionIcon.ARROW_DOWN_LEFT -> {
            line(17.5f, 6.5f, 6.5f, 17.5f)
            polyline(15.5f to 17.5f, 6.5f to 17.5f, 6.5f to 8.5f)
        }

        FusionIcon.LOCK -> {
            drawRoundRect(
                tint,
                topLeft = p(4.5f, 10.5f),
                size = Size(15f * u, 10.5f * u),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.4f * u),
                style = stroke,
            )
            val shackle = Path().apply {
                moveTo(8f * u, 10.5f * u)
                lineTo(8f * u, 7.5f * u)
                cubicTo(8f * u, 3.4f * u, 16f * u, 3.4f * u, 16f * u, 7.5f * u)
                lineTo(16f * u, 10.5f * u)
            }
            drawPath(shackle, tint, style = stroke)
            drawCircle(tint, radius = 1.2f * u, center = p(12f, 15.5f))
        }

        FusionIcon.GRID -> {
            drawRoundRect(
                tint,
                topLeft = p(3f, 3f),
                size = Size(18f * u, 18f * u),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.2f * u),
                style = stroke,
            )
            line(9f, 3.4f, 9f, 20.6f)
            line(15f, 3.4f, 15f, 20.6f)
            line(3.4f, 9f, 20.6f, 9f)
            line(3.4f, 15f, 20.6f, 15f)
        }

        FusionIcon.SPARK -> {
            val star = Path().apply {
                moveTo(12f * u, 2.5f * u)
                quadraticTo(13.4f * u, 9.2f * u, 21.5f * u, 12f * u)
                quadraticTo(13.4f * u, 14.8f * u, 12f * u, 21.5f * u)
                quadraticTo(10.6f * u, 14.8f * u, 2.5f * u, 12f * u)
                quadraticTo(10.6f * u, 9.2f * u, 12f * u, 2.5f * u)
                close()
            }
            drawPath(star, tint, style = stroke)
        }

        FusionIcon.GAUGE -> {
            arc(3f, 4f, 21f, 22f, 150f, 240f)
            line(12f, 13f, 16.5f, 8.5f)
            drawCircle(tint, radius = 1.6f * u, center = p(12f, 13f))
        }

        FusionIcon.COPY -> {
            drawRoundRect(
                tint,
                topLeft = p(8f, 8f),
                size = Size(12f * u, 12f * u),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.2f * u),
                style = stroke,
            )
            val back = Path().apply {
                moveTo(16f * u, 8f * u)
                lineTo(16f * u, 5.5f * u)
                cubicTo(16f * u, 4.4f * u, 15.1f * u, 3.5f * u, 14f * u, 3.5f * u)
                lineTo(6.5f * u, 3.5f * u)
                cubicTo(5.4f * u, 3.5f * u, 4.5f * u, 4.4f * u, 4.5f * u, 5.5f * u)
                lineTo(4.5f * u, 14f * u)
                cubicTo(4.5f * u, 15.1f * u, 5.4f * u, 16f * u, 6.5f * u, 16f * u)
                lineTo(8f * u, 16f * u)
            }
            drawPath(back, tint, style = stroke)
        }
    }
}

/**
 * Tiny helper used by tests/visual audits: composite one icon over a
 * background, mirroring the PathOperation import so the file keeps a single
 * drawing vocabulary.
 */
internal fun DrawScope.drawIconBadge(icon: FusionIcon, tint: Color, background: Color) {
    val circle = Path().apply { addOval(androidx.compose.ui.geometry.Rect(Offset.Zero, size)) }
    val cut = Path().apply {
        addOval(
            androidx.compose.ui.geometry.Rect(
                center = Offset(size.width / 2f, size.height / 2f),
                radius = size.minDimension * 0.42f,
            )
        )
    }
    val ring = Path().apply { op(circle, cut, PathOperation.Difference) }
    drawPath(ring, background)
    drawFusionIcon(icon, tint)
}
