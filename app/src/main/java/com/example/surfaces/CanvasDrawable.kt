package com.example.surfaces

import android.graphics.*
import android.graphics.drawable.Drawable
import com.example.surfaces.utils.TimeUtils

class CanvasDrawable : Drawable() {
    private var prevTimestamp = TimeUtils.now()
    private var step = POS_STEP
    private var posX = 0f
    private var posY = 0f

    private val backgroundPaint = Paint().apply {
        color = Color.GREEN
    }

    private val circlePaint = Paint().apply {
        isAntiAlias = true
        color = Color.RED
    }

    override fun draw(canvas: Canvas) {
        val currentTimestamp = TimeUtils.now()
        canvas.drawRect(bounds, backgroundPaint)
        val width = bounds.width()
        val height = bounds.height()

        if (currentTimestamp - prevTimestamp > FRAME_DURATION) {
            val ration = width.toFloat() / height.toFloat()

            when {
                posX > width -> {
                    step = -POS_STEP
                }

                posX < 0 -> {
                    step = POS_STEP
                }
            }

            posX += step.toFloat()
            posY = (posX / ration)
        }

        canvas.drawCircle(posX, posY, 0.1f * width, circlePaint)
    }

    override fun setAlpha(alpha: Int) {
        backgroundPaint.alpha = alpha
    }

    override fun getOpacity(): Int {
        return PixelFormat.TRANSLUCENT
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        backgroundPaint.colorFilter = colorFilter
    }

    private companion object {
        const val FRAME_DURATION = 60
        const val POS_STEP = 4
    }
}