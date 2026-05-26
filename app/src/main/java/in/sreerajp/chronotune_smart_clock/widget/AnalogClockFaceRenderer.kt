package `in`.sreerajp.chronotune_smart_clock.widget

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Native-Canvas port of the app's hero analog watch face
 * (see MainActivity.kt WorldClockScreen Canvas, lines ~387-546).
 *
 * Renders to a Bitmap so it can be set on a RemoteViews ImageView,
 * which is the only way to deliver custom Compose-quality art to a widget.
 */
object AnalogClockFaceRenderer {

    fun render(
        sizePx: Int,
        timestampMs: Long,
        dark: Boolean,
        faceAlpha: Float = 1f
    ): Bitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        draw(canvas, sizePx.toFloat(), sizePx.toFloat(), timestampMs, dark, faceAlpha)
        return bmp
    }

    private fun draw(
        canvas: Canvas,
        width: Float,
        height: Float,
        timestampMs: Long,
        dark: Boolean,
        faceAlpha: Float
    ) {
        val cx = width / 2f
        val cy = height / 2f
        val radius = min(width, height) / 2f - dp(2f, width)

        // Palette mirrors ui/theme/Color.kt (Vermillion + Gold + Cosmic Velvet)
        val clockPrimary = if (dark) Color.parseColor("#FF6B47") else Color.parseColor("#D2552B")
        val clockSecondary = if (dark) Color.parseColor("#F2C14E") else Color.parseColor("#DAA520")
        val clockOutline = if (dark) Color.parseColor("#94A3B8") else Color.parseColor("#6B5946")
        val accentRed = if (dark) Color.parseColor("#FF1744") else Color.parseColor("#C62828")
        val hubInset = if (dark) Color.parseColor("#181D2A") else Color.parseColor("#FFFEFA")

        // ===== Face: radial gradient =====
        // faceAlpha controls the visibility of the dial backdrop so the
        // wallpaper can show through; ticks, hands, and badges stay opaque.
        val clampedFaceAlpha = faceAlpha.coerceIn(0f, 1f)
        val faceGradient = if (dark) {
            RadialGradient(
                cx, cy, radius,
                intArrayOf(
                    withAlpha(Color.parseColor("#252B3D"), clampedFaceAlpha),
                    withAlpha(Color.parseColor("#181D2A"), clampedFaceAlpha),
                    withAlpha(Color.parseColor("#0A0C12"), clampedFaceAlpha)
                ),
                floatArrayOf(0f, 0.6f, 1f),
                Shader.TileMode.CLAMP
            )
        } else {
            RadialGradient(
                cx, cy, radius,
                intArrayOf(
                    withAlpha(Color.parseColor("#FFFEFA"), clampedFaceAlpha),
                    withAlpha(Color.parseColor("#FAF1DD"), clampedFaceAlpha),
                    withAlpha(Color.parseColor("#E8D9C0"), clampedFaceAlpha)
                ),
                floatArrayOf(0f, 0.6f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        val facePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = faceGradient }
        canvas.drawCircle(cx, cy, radius, facePaint)

        // ===== Bezel: 3 concentric rings =====
        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        ringPaint.color = clockPrimary
        ringPaint.strokeWidth = dp(2.5f, width)
        canvas.drawCircle(cx, cy, radius, ringPaint)

        ringPaint.color = withAlpha(clockPrimary, 0.20f)
        ringPaint.strokeWidth = dp(1f, width)
        canvas.drawCircle(cx, cy, radius - dp(5f, width), ringPaint)

        ringPaint.color = withAlpha(clockOutline, 0.18f)
        ringPaint.strokeWidth = dp(0.7f, width)
        canvas.drawCircle(cx, cy, radius - dp(32f, width), ringPaint)

        // ===== 60 ticks (quarters big, hours medium, minutes thin) =====
        val tickOuterR = radius - dp(9f, width)
        val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            strokeCap = Paint.Cap.ROUND
        }
        for (i in 0 until 60) {
            val angle = Math.toRadians(i * 6.0 - 90.0)
            val isQuarter = i % 15 == 0
            val isHour = i % 5 == 0
            val tickLen = when {
                isQuarter -> dp(18f, width)
                isHour -> dp(11f, width)
                else -> dp(4.5f, width)
            }
            val strokeW = when {
                isQuarter -> dp(3.5f, width)
                isHour -> dp(2f, width)
                else -> dp(1f, width)
            }
            val tickColor = when {
                isQuarter -> clockPrimary
                isHour -> withAlpha(clockPrimary, 0.70f)
                else -> withAlpha(clockOutline, 0.40f)
            }
            tickPaint.color = tickColor
            tickPaint.strokeWidth = strokeW
            val innerR = tickOuterR - tickLen
            val cosA = cos(angle).toFloat()
            val sinA = sin(angle).toFloat()
            canvas.drawLine(
                cx + innerR * cosA, cy + innerR * sinA,
                cx + tickOuterR * cosA, cy + tickOuterR * sinA,
                tickPaint
            )
        }

        // ===== Cardinal hour numerals (12 / 3 / 6 / 9) =====
        // Placed just inside the quarter ticks. Drawn with a dark halo so they
        // stay legible even when the face is fully transparent.
        val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (dark) Color.parseColor("#FFF9EE") else Color.parseColor("#1A1A1A")
            textAlign = Paint.Align.CENTER
            textSize = dp(16f, width)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setShadowLayer(
                dp(2.5f, width),
                0f,
                dp(0.5f, width),
                if (dark) Color.parseColor("#CC000000") else Color.parseColor("#66000000")
            )
        }
        val numberR = radius - dp(38f, width)
        val numberFm = numberPaint.fontMetrics
        val numberBaselineOffset = -(numberFm.ascent + numberFm.descent) / 2f
        val cardinals = listOf(
            "12" to -90.0,
            "3" to 0.0,
            "6" to 90.0,
            "9" to 180.0
        )
        for ((label, deg) in cardinals) {
            val rad = Math.toRadians(deg)
            val nx = cx + numberR * cos(rad).toFloat()
            val ny = cy + numberR * sin(rad).toFloat() + numberBaselineOffset
            canvas.drawText(label, nx, ny, numberPaint)
        }

        // ===== Smooth time =====
        val cal = Calendar.getInstance().apply { timeInMillis = timestampMs }
        val ms = cal.get(Calendar.MILLISECOND)
        val secSmooth = cal.get(Calendar.SECOND) + ms / 1000.0
        val minSmooth = cal.get(Calendar.MINUTE) + secSmooth / 60.0
        val hrSmooth = (cal.get(Calendar.HOUR) % 12) + minSmooth / 60.0

        // ===== Complications: AM/PM (above hub) and date strip (below hub) =====
        // Drawn before the hands so the hands sweep over them, like a real watch.
        // Badge background stays fully opaque even when the face is transparent,
        // so the date / AM-PM remain readable against arbitrary wallpapers.
        val windowBgColor = if (dark) Color.parseColor("#0A0C12") else Color.parseColor("#FFFEFA")
        val windowBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = windowBgColor
            style = Paint.Style.FILL
            setShadowLayer(dp(2.5f, width), 0f, dp(0.5f, width), Color.parseColor("#80000000"))
        }
        val windowBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = withAlpha(clockPrimary, 0.85f)
            style = Paint.Style.STROKE
            strokeWidth = dp(1.2f, width)
        }
        val cornerR = dp(5f, width)

        fun drawTextBadge(
            text: String,
            centerX: Float,
            centerY: Float,
            textColor: Int,
            textSizeDp: Float,
            padH: Float,
            padV: Float,
            letterSpace: Float
        ) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = textColor
                textAlign = Paint.Align.CENTER
                textSize = dp(textSizeDp, width)
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                letterSpacing = letterSpace
            }
            val textW = paint.measureText(text)
            val metrics = paint.fontMetrics
            val halfH = (metrics.descent - metrics.ascent) / 2f + padV
            val rect = RectF(
                centerX - textW / 2f - padH,
                centerY - halfH,
                centerX + textW / 2f + padH,
                centerY + halfH
            )
            canvas.drawRoundRect(rect, cornerR, cornerR, windowBgPaint)
            canvas.drawRoundRect(rect, cornerR, cornerR, windowBorderPaint)
            val baseline = centerY - (metrics.ascent + metrics.descent) / 2f
            canvas.drawText(text, centerX, baseline, paint)
        }

        // --- AM/PM badge above center hub ---
        val amPmText = if (cal.get(Calendar.AM_PM) == Calendar.AM) "AM" else "PM"
        drawTextBadge(
            text = amPmText,
            centerX = cx,
            centerY = cy - radius * 0.34f,
            textColor = clockPrimary,
            textSizeDp = 18f,
            padH = dp(9f, width),
            padV = dp(5f, width),
            letterSpace = 0.18f
        )

        // --- Date strip below center hub: "SUN  23 MAY" ---
        val weekdayText = SimpleDateFormat("EEE", Locale.getDefault())
            .format(Date(timestampMs))
            .uppercase(Locale.getDefault())
        val dayText = cal.get(Calendar.DAY_OF_MONTH).toString()
        val monthText = SimpleDateFormat("MMM", Locale.getDefault())
            .format(Date(timestampMs))
            .uppercase(Locale.getDefault())
        val dateText = "$weekdayText  $dayText $monthText"
        drawTextBadge(
            text = dateText,
            centerX = cx,
            centerY = cy + radius * 0.34f,
            textColor = if (dark) Color.parseColor("#F5F5F5") else Color.parseColor("#1A1A1A"),
            textSizeDp = 16f,
            padH = dp(10f, width),
            padV = dp(5f, width),
            letterSpace = 0.10f
        )

        // ===== Hour hand (tapered + glow) =====
        val hrPath = taperedHand(
            cx, cy,
            angleDeg = hrSmooth * 30.0,
            length = radius * 0.50f,
            tail = radius * 0.13f,
            baseWidth = dp(9f, width)
        )
        // Glow (mimics the stroked alpha layer in the Compose original)
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
            maskFilter = BlurMaskFilter(dp(3f, width), BlurMaskFilter.Blur.NORMAL)
        }
        glowPaint.color = withAlpha(clockPrimary, 0.28f)
        glowPaint.strokeWidth = dp(10f, width)
        canvas.drawPath(hrPath, glowPaint)

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        fillPaint.color = clockPrimary
        canvas.drawPath(hrPath, fillPaint)

        // ===== Minute hand (tapered + glow) =====
        val minPath = taperedHand(
            cx, cy,
            angleDeg = minSmooth * 6.0,
            length = radius * 0.74f,
            tail = radius * 0.15f,
            baseWidth = dp(7f, width)
        )
        glowPaint.color = withAlpha(clockSecondary, 0.24f)
        glowPaint.strokeWidth = dp(8f, width)
        canvas.drawPath(minPath, glowPaint)

        fillPaint.color = clockSecondary
        canvas.drawPath(minPath, fillPaint)

        // ===== Second hand (watch-style with lollipop) =====
        val secA = Math.toRadians(secSmooth * 6.0 - 90.0)
        val secCos = cos(secA).toFloat()
        val secSin = sin(secA).toFloat()
        val secLen = radius * 0.86f
        val secTail = radius * 0.22f
        val secTailEndX = cx - secTail * secCos
        val secTailEndY = cy - secTail * secSin
        val secTipEndX = cx + secLen * secCos
        val secTipEndY = cy + secLen * secSin

        val secPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentRed
            strokeCap = Paint.Cap.ROUND
            strokeWidth = dp(1.8f, width)
        }
        canvas.drawLine(secTailEndX, secTailEndY, secTipEndX, secTipEndY, secPaint)

        // Counterbalance circle at tail
        fillPaint.color = accentRed
        canvas.drawCircle(secTailEndX, secTailEndY, dp(5f, width), fillPaint)
        fillPaint.color = hubInset
        canvas.drawCircle(secTailEndX, secTailEndY, dp(2f, width), fillPaint)

        // Lollipop dot near tip (outlined ring)
        val lolliCx = cx + (secLen * 0.78f) * secCos
        val lolliCy = cy + (secLen * 0.78f) * secSin
        val lolliPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentRed
            style = Paint.Style.STROKE
            strokeWidth = dp(1.5f, width)
        }
        canvas.drawCircle(lolliCx, lolliCy, dp(4f, width), lolliPaint)

        // ===== Center hub: 4 layers =====
        fillPaint.color = withAlpha(clockPrimary, 0.30f)
        canvas.drawCircle(cx, cy, dp(13f, width), fillPaint)
        fillPaint.color = clockPrimary
        canvas.drawCircle(cx, cy, dp(8f, width), fillPaint)
        fillPaint.color = hubInset
        canvas.drawCircle(cx, cy, dp(4.5f, width), fillPaint)
        fillPaint.color = accentRed
        canvas.drawCircle(cx, cy, dp(2.2f, width), fillPaint)
    }

    private fun taperedHand(
        cx: Float, cy: Float,
        angleDeg: Double,
        length: Float,
        tail: Float,
        baseWidth: Float
    ): Path {
        val a = Math.toRadians(angleDeg - 90.0)
        val cosA = cos(a).toFloat()
        val sinA = sin(a).toFloat()
        val pcos = -sinA
        val psin = cosA
        val baseCx = cx - tail * cosA
        val baseCy = cy - tail * sinA
        val tipX = cx + length * cosA
        val tipY = cy + length * sinA
        val half = baseWidth / 2f
        return Path().apply {
            moveTo(baseCx + pcos * half, baseCy + psin * half)
            lineTo(tipX, tipY)
            lineTo(baseCx - pcos * half, baseCy - psin * half)
            close()
        }
    }

    /**
     * The Compose original draws on a 280dp dial; all `*.dp.toPx()` values
     * were authored against that frame. The widget bitmap is rendered at
     * its actual pixel size, so we map dp -> px by treating the bitmap
     * width as the reference 280dp dial.
     */
    private const val REFERENCE_DIAL_DP = 280f
    private fun dp(value: Float, currentWidthPx: Float): Float =
        value / REFERENCE_DIAL_DP * currentWidthPx

    private fun withAlpha(color: Int, alpha: Float): Int {
        val a = (alpha.coerceIn(0f, 1f) * 255).toInt()
        return (a shl 24) or (color and 0x00FFFFFF)
    }
}
