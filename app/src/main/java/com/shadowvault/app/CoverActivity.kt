package com.shadowvault.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Vibrator
import androidx.appcompat.app.AppCompatActivity

/**
 * Экран-прикрытие.
 *
 * Внешне выглядит как пустой/зависший экран.
 *
 * После настройки правильный жест открывает VaultActivity.
 *
 * 10 неправильных жестов подряд:
 * 30 секунд блокировки.
 */
class CoverActivity : AppCompatActivity() {

    companion object {

        private const val PREFS = "sys_cfg"

        private const val KEY_FAILS = "fails"

        private const val KEY_LOCK_UNTIL = "lock_until"

        fun hideIcon(ctx: Context) {

            ctx.packageManager.setComponentEnabledSetting(
                ComponentName(
                    ctx,
                    CoverActivity::class.java
                ),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
        }

        fun showIcon(ctx: Context) {

            ctx.packageManager.setComponentEnabledSetting(
                ComponentName(
                    ctx,
                    CoverActivity::class.java
                ),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
        }
    }

    private lateinit var gestureView: SecretGestureView

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        /*
         * Если жест ещё не настроен,
         * запускаем первичную настройку.
         */
        if (!GestureStorage.hasTemplate(this)) {

            startActivity(
                Intent(
                    this,
                    SetupActivity::class.java
                )
            )

            finish()

            return
        }

        gestureView = SecretGestureView(this).apply {

            setBackgroundColor(Color.BLACK)

            mode = SecretGestureView.Mode.VERIFY

            template = GestureStorage.load(
                this@CoverActivity
            )

            /*
             * Правильный жест.
             */
            onVerified = {

                resetFails()

                startActivity(
                    Intent(
                        this@CoverActivity,
                        VaultActivity::class.java
                    )
                )
            }

            /*
             * Неправильный жест.
             *
             * Теперь этот callback действительно вызывается
             * SecretGestureView.
             */
            onFailed = {

                onWrongGesture()
            }
        }

        setContentView(gestureView)
    }

    override fun onResume() {

        super.onResume()

        val prefs = getSharedPreferences(
            PREFS,
            Context.MODE_PRIVATE
        )

        val lockUntil = prefs.getLong(
            KEY_LOCK_UNTIL,
            0L
        )

        /*
         * Если активна блокировка,
         * закрываем экран.
         */
        if (System.currentTimeMillis() < lockUntil) {

            finish()

            return
        }

        /*
         * Обновляем шаблон жеста.
         */
        if (::gestureView.isInitialized) {

            gestureView.template =
                GestureStorage.load(this)
        }
    }

    private fun onWrongGesture() {

        val prefs = getSharedPreferences(
            PREFS,
            Context.MODE_PRIVATE
        )

        /*
         * Если блокировка уже действует,
         * ничего не делаем.
         */
        if (
            System.currentTimeMillis() <
            prefs.getLong(KEY_LOCK_UNTIL, 0L)
        ) {
            return
        }

        val fails =
            prefs.getInt(KEY_FAILS, 0) + 1

        val editor = prefs.edit()
            .putInt(KEY_FAILS, fails)

        /*
         * 10 неправильных жестов.
         */
        if (fails >= 10) {

            editor
                .putLong(
                    KEY_LOCK_UNTIL,
                    System.currentTimeMillis() + 30_000L
                )
                .putInt(KEY_FAILS, 0)
        }

        editor.apply()

        /*
         * Короткая вибрация при неправильном жесте.
         */
        (getSystemService(VIBRATOR_SERVICE) as? Vibrator)
            ?.vibrate(60)
    }

    private fun resetFails() {

        getSharedPreferences(
            PREFS,
            Context.MODE_PRIVATE
        )
            .edit()
            .putInt(KEY_FAILS, 0)
            .apply()
    }
}
