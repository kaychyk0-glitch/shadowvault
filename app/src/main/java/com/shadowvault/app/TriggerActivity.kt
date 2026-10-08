package com.shadowvault.app

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * Невидимый триггер по секретному коду набора *#*#4263#*#*.
 * Сам по себе ничего не показывает (прозрачная тема) —
 * просто перенаправляет на экран-прикрытие и исчезает.
 */
class TriggerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(this, CoverActivity::class.java))
        finish()
    }
}
