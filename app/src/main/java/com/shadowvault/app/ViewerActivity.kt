package com.shadowvault.app

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * Просмотр: куски собираются и расшифровываются строго в памяти.
 * Расшифрованный файл на диске не создаётся никогда.
 * Касание — закрыть.
 */
class ViewerActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_ENTRY = "entry"
        fun start(ctx: Context, e: VaultStore.Entry) {
            ctx.startActivity(Intent(ctx, ViewerActivity::class.java).putExtra(EXTRA_ENTRY, e.id))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getStringExtra(EXTRA_ENTRY) ?: return finish()
        val entry = VaultStore.load(this).find { it.id == id } ?: return finish()

        val placeholder = TextView(this).apply {
            text = "…"
            setTextColor(Color.DKGRAY)
            textSize = 40f
            gravity = Gravity.CENTER
        }
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(placeholder, FrameLayout.LayoutParams(-1, -1))
        }
        setContentView(root)

        Thread {
            try {
                val data = VaultStore.readPlain(this, entry)
                val bmp = BitmapFactory.decodeByteArray(data, 0, data.size)
                runOnUiThread {
                    if (bmp == null) {
                        Toast.makeText(this, "Не удалось декодировать", Toast.LENGTH_SHORT).show()
                        finish(); return@runOnUiThread
                    }
                    val iv = ImageView(this).apply {
                        setImageBitmap(bmp)
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        setOnClickListener { finish() }
                    }
                    setContentView(iv)
                }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "Ошибка дешифрования", Toast.LENGTH_SHORT).show(); finish() }
            }
        }.start()
    }
}
