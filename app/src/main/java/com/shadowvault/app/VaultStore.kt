package com.shadowvault.app

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Хранилище «невозможно найти»:
 *  - filesDir недоступен галереям, файловым менеджерам и другим приложениям (без root)
 *  - изображение шифруется целиком (AES-256-GCM), затем режется на куски
 *  - каждый кусок лежит в случайном подкаталоге под случайным именем без расширения
 *  - индекс (какие куски к какой картинке) — отдельный зашифрованный JSON
 *    под случайным именем; само имя индекса лежит в приватных SharedPreferences
 *  - в распакованном виде изображение на диске никогда не существует:
 *    расшифровка только в памяти, в момент показа
 */
object VaultStore {

    private const val PREFS = "sys_cfg"
    private const val KEY_INDEX_NAME = "idx"
    private const val CHUNK_SIZE = 128 * 1024
    private const val SYS_DIR = ".sys"

    data class Entry(
        val id: String,
        val size: Long,
        val addedAt: Long,
        val chunks: List<String> // пути относительно filesDir
    )

    // ---------- индекс ----------

    private fun indexFile(ctx: Context): File {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        var name = prefs.getString(KEY_INDEX_NAME, null)
        if (name == null) {
            name = GestureMath.randomHex(16)
            prefs.edit().putString(KEY_INDEX_NAME, name).apply()
        }
        return File(ctx.filesDir, "$SYS_DIR/$name")
    }

    @Synchronized
    fun load(ctx: Context): MutableList<Entry> {
        val f = indexFile(ctx)
        if (!f.exists()) return mutableListOf()
        return try {
            val plain = CryptoManager.decrypt(f.readBytes())
            val arr = JSONArray(String(plain, Charsets.UTF_8))
            val out = mutableListOf<Entry>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val chunks = mutableListOf<String>()
                val ca = o.getJSONArray("chunks")
                for (j in 0 until ca.length()) chunks.add(ca.getString(j))
                out.add(Entry(o.getString("id"), o.getLong("size"), o.getLong("added"), chunks))
            }
            out
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    @Synchronized
    private fun save(ctx: Context, entries: List<Entry>) {
        val arr = JSONArray()
        entries.forEach { e ->
            arr.put(JSONObject()
                .put("id", e.id)
                .put("size", e.size)
                .put("added", e.addedAt)
                .put("chunks", JSONArray(e.chunks)))
        }
        val blob = CryptoManager.encrypt(arr.toString().toByteArray(Charsets.UTF_8))
        val f = indexFile(ctx)
        f.parentFile?.mkdirs()
        // Пишем через временный файл, чтобы не оставить частичный индекс
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeBytes(blob)
        tmp.renameTo(f)
    }

    // ---------- импорт / чтение / удаление ----------

    @Synchronized
    fun import(ctx: Context, uri: Uri): Entry {
        val plain = ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
        val blob = CryptoManager.encrypt(plain)

        // Случайное «облако» директорий: .sys/ab/12/…
        val shardDir = File(
            ctx.filesDir,
            "$SYS_DIR/${GestureMath.randomHex(2)}/${GestureMath.randomHex(2)}"
        ).apply { mkdirs() }

        val chunks = mutableListOf<String>()
        var off = 0
        while (off < blob.size) {
            val end = minOf(off + CHUNK_SIZE, blob.size)
            val partFile = File(shardDir, GestureMath.randomHex(16))
            // Прямая запись без мусора во временных папках
            java.io.FileOutputStream(partFile).use { it.write(blob, off, end - off) }
            chunks.add(partFile.relativeTo(ctx.filesDir).path)
            off = end
        }

        val entry = Entry(
            id = GestureMath.randomHex(8),
            size = plain.size.toLong(),
            addedAt = System.currentTimeMillis(),
            chunks = chunks
        )
        val all = load(ctx)
        all.add(entry)
        save(ctx, all)
        return entry
    }

    /** Собрать куски и расшифровать В ПАМЯТЬ. На диск расшифрованное не пишется. */
    fun readPlain(ctx: Context, entry: Entry): ByteArray {
        val full = java.io.ByteArrayOutputStream()
        entry.chunks.forEach { rel ->
            val f = File(ctx.filesDir, rel)
            if (!f.exists()) throw java.io.FileNotFoundException(rel)
            full.write(f.readBytes())
        }
        return CryptoManager.decrypt(full.toByteArray())
    }

    @Synchronized
    fun delete(ctx: Context, entry: Entry) {
        entry.chunks.forEach { rel -> File(ctx.filesDir, rel).delete() }
        // Чистим пустые директории-шарды
        entry.chunks.map { File(ctx.filesDir, it).parentFile }.distinct().forEach { dir ->
            dir?.takeIf { it.isDirectory && it.list()?.isEmpty() == true }?.delete()
        }
        val all = load(ctx)
        all.removeAll { it.id == entry.id }
        save(ctx, all)
    }
}
