package com.shadowvault.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Первый запуск: установка секретного жеста (рисуем дважды для подтверждения).
 * После успеха иконка скрывается и открывается хранилище.
 */
class SetupActivity : AppCompatActivity() {

    private var candidate: FloatArray? = null
    private lateinit var hint: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        hint = TextView(this).apply {
            text = "Нарисуйте ваш секретный жест"
            setTextColor(Color.GRAY)
            textSize = 16f
        }

        val gestureView = SecretGestureView(this).apply {
            setBackgroundColor(Color.BLACK)
            mode = SecretGestureView.Mode.SETUP
            onGestureDrawn = { g -> onGesture(g) }
        }

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(gestureView, FrameLayout.LayoutParams(-1, -1))
            addView(hint, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
                setMargins(0, 0, 0, 120)
            })
        }
        setContentView(root)
    }

    private fun onGesture(g: FloatArray) {
        val c = candidate
        if (c == null) {
            candidate = g
            hint.text = "Повторите жест для подтверждения"
            return
        }
        if (GestureMath.distance(c, g) <= GestureMath.MATCH_THRESHOLD) {
            GestureStorage.save(this, g)
            CoverActivity.hideIcon(this) // хранилище невидимо: у приложения нет иконки
            startActivity(Intent(this, VaultActivity::class.java))
            finish()
        } else {
            candidate = null
            hint.text = "Жесты не совпали. Нарисуйте заново"
        }
    }
}
