package com.shadowvault.app

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/**
 * Экран жеста.
 *
 * SETUP:
 *   жест хорошо виден пользователю во время настройки.
 *
 * VERIFY:
 *   линия почти невидима, чтобы посторонний не видел форму жеста.
 */
class SecretGestureView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    enum class Mode {
        SETUP,
        VERIFY
    }

    var mode = Mode.VERIFY

    var template: FloatArray? = null

    var onVerified: (() -> Unit)? = null

    /**
     * Вызывается, если жест достаточно длинный/осмысленный,
     * но не совпал с сохранённым шаблоном.
     */
    var onFailed: (() -> Unit)? = null

    /**
     * Используется при настройке нового жеста.
     */
    var onGestureDrawn: ((normalized: FloatArray) -> Unit)? = null

    private val points = mutableListOf<FloatArray>()

    private val path = Path()

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 8f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {

        when (event.actionMasked) {

            MotionEvent.ACTION_DOWN -> {
                points.clear()
                path.reset()

                path.moveTo(event.x, event.y)
                points.add(floatArrayOf(event.x, event.y))

                updatePaintAlpha()
                invalidate()

                return true
            }

            MotionEvent.ACTION_MOVE -> {
                path.lineTo(event.x, event.y)
                points.add(floatArrayOf(event.x, event.y))

                invalidate()

                return true
            }

            MotionEvent.ACTION_UP -> {
                path.lineTo(event.x, event.y)
                points.add(floatArrayOf(event.x, event.y))

                invalidate()

                handleGesture()

                return true
            }
        }

        return false
    }

    /**
     * Важно:
     * mode может быть изменён ПОСЛЕ создания View.
     *
     * Поэтому alpha нельзя вычислять только один раз
     * при создании объекта.
     */
    private fun updatePaintAlpha() {
        paint.alpha = when (mode) {
            Mode.SETUP -> 200
            Mode.VERIFY -> 12
        }
    }

    private fun handleGesture() {

        val pts = points.toList()

        points.clear()

        postDelayed(
            {
                path.reset()
                invalidate()
            },
            350
        )

        if (!GestureMath.isMeaningful(pts)) {
            return
        }

        val normalized = GestureMath.normalize(
            GestureMath.resample(pts)
        )

        when (mode) {

            Mode.SETUP -> {
                onGestureDrawn?.invoke(normalized)
            }

            Mode.VERIFY -> {

                val savedTemplate = template ?: return

                val distance = GestureMath.distance(
                    normalized,
                    savedTemplate
                )

                if (distance <= GestureMath.MATCH_THRESHOLD) {

                    onVerified?.invoke()

                } else {

                    // РАНЬШЕ ЭТОГО НЕ БЫЛО:
                    // CoverActivity никогда не получал сигнал
                    // о неправильном жесте.
                    onFailed?.invoke()
                }
            }
        }
    }

    override fun onDraw(canvas: Canvas) {

        updatePaintAlpha()

        canvas.drawPath(path, paint)
    }
}
