package com.woorieodiga.app

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import kotlin.math.max

/** 비트맵은 한 번만 읽어 둔다(RAM 400MB 제한, 다시 그릴 때 디코딩하지 않기). */
object Bitmaps {
    private val cache = HashMap<Int, Bitmap>()

    fun get(context: Context, res: Int): Bitmap = cache.getOrPut(res) {
        BitmapFactory.decodeResource(context.resources, res, BitmapFactory.Options().apply { inScaled = false })
    }
}

private fun Context.isNight() =
    (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

/** 구간 배경 위에 탈것을 얹는다. 움직임 없이 상태가 바뀔 때만 다시 그린다. */
class SceneView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33000000 }
    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val dst = RectF()

    private var bg = 0
    private var ride = 0
    private var badge = 0
    private var spotX = 0.32f
    private var spotY = 0.45f

    init {
        if (context.isNight()) {
            // 밤에는 배경을 어둡게(같은 그림, 밝기만 낮춤)
            paint.colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setScale(0.72f, 0.74f, 0.82f, 1f) })
        }
    }

    fun set(bgRes: Int, rideRes: Int, badgeRes: Int = 0) {
        if (bgRes == bg && rideRes == ride && badgeRes == badge) return
        bg = bgRes
        ride = rideRes
        badge = badgeRes
        val spot = SPOTS[bgRes] ?: (0.30f to 0.47f)
        spotX = spot.first
        spotY = spot.second
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (bg == 0) return
        val b = Bitmaps.get(context, bg)
        // 섬이 가운데 60% 안에 있으니 1.3배 확대해 빈 여백을 줄인다
        val scale = max(width.toFloat() / b.width, height.toFloat() / b.height) * ZOOM
        val w = b.width * scale
        val h = b.height * scale
        val left = (width - w) / 2f
        val top = (height - h) / 2f
        dst.set(left, top, left + w, top + h)
        canvas.drawBitmap(b, null, dst, paint)

        if (ride != 0) {
            val r = Bitmaps.get(context, ride)
            val rw = w * RIDE_SIZE
            val footX = left + w * spotX
            val footY = top + h * spotY
            canvas.drawOval(footX - rw * 0.3f, footY - rw * 0.06f, footX + rw * 0.3f, footY + rw * 0.06f, shadow)
            // 탈것 그림은 발끝이 캔버스 아래 10% 위에 있다
            dst.set(footX - rw / 2f, footY - rw * 0.9f, footX + rw / 2f, footY + rw * 0.1f)
            canvas.drawBitmap(r, null, dst, null)
        }

        if (badge != 0) {
            val s = Bitmaps.get(context, badge)
            val size = minOf(width, height) * 0.26f
            val m = size * 0.15f
            dst.set(width - size - m, m, width - m, m + size)
            canvas.drawBitmap(s, null, dst, badgePaint)
        }
    }

    companion object {
        private const val ZOOM = 1.3f
        private const val RIDE_SIZE = 0.15f

        /** 배경마다 도로의 빈 자리(탈것 발끝 위치, 그림 기준 비율). */
        private val SPOTS = mapOf(
            R.drawable.bg_01_neighborhood to (0.36f to 0.44f),
            R.drawable.bg_02_city to (0.30f to 0.44f),
            R.drawable.bg_03_field_river to (0.36f to 0.41f),
            R.drawable.bg_04_mountain to (0.30f to 0.47f),
        )
    }
}

/** 진행 띠: 구간 아이콘 5개 위로 작은 탈것이 지나간다. */
class StripView(context: Context) : View(context) {
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.ivi_progress_track)
        strokeCap = Paint.Cap.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.ivi_progress_fill)
        strokeCap = Paint.Cap.ROUND
    }
    private val bmp = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val dim = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { alpha = 110 }
    private val dst = RectF()

    private var stops = intArrayOf()
    private var progress = 0f
    private var mini = 0

    fun set(stopIcons: IntArray, progress: Float, miniRes: Int) {
        if (stopIcons.contentEquals(stops) && progress == this.progress && miniRes == mini) return
        stops = stopIcons
        this.progress = progress
        mini = miniRes
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (stops.isEmpty()) return
        val icon = minOf(height * 0.45f, width / (stops.size * 1.5f))
        val pad = icon / 2f + 8f
        val y = height - icon / 2f - 4f
        val x0 = pad
        val x1 = width - pad
        track.strokeWidth = icon * 0.16f
        fill.strokeWidth = icon * 0.16f
        canvas.drawLine(x0, y, x1, y, track)
        val px = x0 + (x1 - x0) * progress
        canvas.drawLine(x0, y, px, y, fill)

        stops.forEachIndexed { i, res ->
            val cx = x0 + (x1 - x0) * i / (stops.size - 1)
            val passed = (i.toFloat() / (stops.size - 1)) <= progress + 0.001f
            dst.set(cx - icon / 2f, y - icon / 2f, cx + icon / 2f, y + icon / 2f)
            canvas.drawBitmap(Bitmaps.get(context, res), null, dst, if (passed) bmp else dim)
        }
        if (mini != 0) {
            val m = icon * 0.78f
            dst.set(px - m / 2f, y - icon / 2f - m - 2f, px + m / 2f, y - icon / 2f - 2f)
            canvas.drawBitmap(Bitmaps.get(context, mini), null, dst, bmp)
        }
    }
}
