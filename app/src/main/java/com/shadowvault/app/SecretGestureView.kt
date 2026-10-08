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
 * Экран жеста. Рисует линию только как обратную связь при настройке.
 * В режиме проверки штрих почти невидим (alpha = 12) — посторонний не поймёт,
 * что экран реагирует на касания.
 */
class SecretGestureView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    enum class Mode { SETUP, VERIFY }

    var mode = Mode.VERIFY
    var template: FloatArray? = null // для VERIFY
    var onVerified: (() -> Unit)? = null
    var onFailed: (() -> Unit)? = null // осмысленный жест, но не совпал
    var onGestureDrawn: ((normalized: FloatArray) -> Unit)? = null // для SETUP

    private val points = mutableListOf<FloatArray>()
    private val path = Path()
    private val strokeAlpha = if (mode == Mode.SETUP) 200 else 12

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        alpha = strokeAlpha
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

    private fun handleGesture() {
        val pts = points.toList()
        points.clear()
        postDelayed({ path.reset(); invalidate() }, 350)

        if (!GestureMath.isMeaningful(pts)) return
        val normalized = GestureMath.normalize(GestureMath.resample(pts))

        when (mode) {
            Mode.SETUP -> onGestureDrawn?.invoke(normalized)
            Mode.VERIFY -> {
                val t = template ?: return
                if (GestureMath.distance(normalized, t) <= GestureMath.MATCH_THRESHOLD) {
                    onVerified?.invoke()
                }
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawPath(path, paint)
    }
}
