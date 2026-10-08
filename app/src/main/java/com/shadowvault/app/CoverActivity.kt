package com.shadowvault.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Vibrator
import android.view.View
import androidx.appcompat.app.AppCompatActivity

/**
 * Экран-прикрытие: чёрный, ничего не показывает. Внешне — «пустое» или
 * зависшее приложение. Жест открывает хранилище. 10 неверных жестов подряд —
 * 30 секунд блокировки ввода.
 */
class CoverActivity : AppCompatActivity() {

    companion object {
        private const val PREFS = "sys_cfg"
        private const val KEY_FAILS = "fails"
        private const val KEY_LOCK_UNTIL = "lock_until"

        fun hideIcon(ctx: Context) {
            ctx.packageManager.setComponentEnabledSetting(
                ComponentName(ctx, CoverActivity::class.java),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
        }

        fun showIcon(ctx: Context) {
            ctx.packageManager.setComponentEnabledSetting(
                ComponentName(ctx, CoverActivity::class.java),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
        }
    }

    private lateinit var gestureView: SecretGestureView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!GestureStorage.hasTemplate(this)) {
            startActivity(Intent(this, SetupActivity::class.java))
            finish()
            return
        }

        gestureView = SecretGestureView(this).apply {
            setBackgroundColor(Color.BLACK)
            mode = SecretGestureView.Mode.VERIFY
            template = GestureStorage.load(this@CoverActivity)
            onVerified = {
                resetFails()
                startActivity(Intent(this@CoverActivity, VaultActivity::class.java))
            }
            onFailed = { onWrongGesture() }
        }
        setContentView(gestureView)
    }

    override fun onResume() {
        super.onResume()
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (System.currentTimeMillis() < prefs.getLong(KEY_LOCK_UNTIL, 0)) {
            finish() // блокировка: экран «завис» и закрывается
        }
        gestureView.template = GestureStorage.load(this)
    }

    private fun onWrongGesture() {
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (System.currentTimeMillis() < prefs.getLong(KEY_LOCK_UNTIL, 0)) return

        val fails = prefs.getInt(KEY_FAILS, 0) + 1
        val ed = prefs.edit().putInt(KEY_FAILS, fails)
        if (fails >= 10) {
            ed.putLong(KEY_LOCK_UNTIL, System.currentTimeMillis() + 30_000)
                .putInt(KEY_FAILS, 0)
        }
        ed.apply()
        (getSystemService(VIBRATOR_SERVICE) as? Vibrator)?.vibrate(60)
    }

    private fun resetFails() {
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY_FAILS, 0).apply()
    }
}
