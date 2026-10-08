package com.shadowvault.app

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class VaultActivity : AppCompatActivity() {

    private val entries = mutableListOf<VaultStore.Entry>()
    private lateinit var adapter: Adapter

    private val pickImage = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri ?: return@registerForActivityResult
        Toast.makeText(this, "Импорт…", Toast.LENGTH_SHORT).show()
        Thread {
            try {
                VaultStore.import(this, uri)
                runOnUiThread { reload() }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "Ошибка импорта", Toast.LENGTH_SHORT).show() }
            }
        }.start()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val list = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@VaultActivity)
            setBackgroundColor(Color.BLACK)
        }
        adapter = Adapter()
        list.adapter = adapter

        val addBtn = makeBtn("+ Добавить изображение") {
            pickImage.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
        val changeGesture = makeBtn("Изменить жест") {
            // Удаляем шаблон — при следующем входе запустится SetupActivity
            deleteFile("gesture.bin")
            finish()
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
            addView(addBtn)
            addView(changeGesture)
        }
        setContentView(root)
    }

    private fun makeBtn(label: String, onClick: () -> Unit): TextView =
        TextView(this).apply {
            text = label
            setTextColor(Color.WHITE)
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, 40, 0, 40)
            setOnClickListener { onClick() }
        }

    private fun reload() {
        entries.clear()
        entries.addAll(VaultStore.load(this))
        adapter.notifyDataSetChanged()
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    // ---------- список ----------

    inner class Adapter : RecyclerView.Adapter<Adapter.H>() {

        inner class H(val tv: TextView) : RecyclerView.ViewHolder(tv)

        private val fmt = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): H {
            val tv = TextView(parent.context).apply {
                setTextColor(Color.LTGRAY)
                textSize = 15f
                setPadding(40, 40, 40, 40)
            }
            tv.layoutParams = RecyclerView.LayoutParams(-1, -2)
            return H(tv)
        }

        override fun getItemCount() = entries.size

        override fun onBindViewHolder(h: H, pos: Int) {
            val e = entries[pos]
            h.tv.text = "IMG-${e.id.takeLast(6)}  •  ${fmt.format(Date(e.addedAt))}  •  ${formatSize(e.size)}"
            h.tv.setOnClickListener { ViewerActivity.start(this@VaultActivity, e) }
            h.tv.setOnLongClickListener {
                AlertDialog.Builder(this@VaultActivity)
                    .setMessage("Удалить это изображение безвозвратно?")
                    .setPositiveButton("Удалить") { _, _ ->
                        Thread { VaultStore.delete(this@VaultActivity, e); runOnUiThread { reload() } }.start()
                    }
                    .setNegativeButton("Отмена", null)
                    .show()
                true
            }
        }

        private fun formatSize(bytes: Long): String = when {
            bytes >= 1 shl 20 -> "%.1f МБ".format(bytes / 1048576.0)
            bytes >= 1 shl 10 -> "%.0f КБ".format(bytes / 1024.0)
            else -> "$bytes Б"
        }
    }
}
