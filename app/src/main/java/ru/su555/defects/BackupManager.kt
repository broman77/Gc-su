package ru.su555.defects

import android.content.Context
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object BackupManager {
    private const val DB_FILE = "defects.db"

    fun create(context: Context, store: LocalStore): ByteArray {
        store.writableDatabase.execSQL("PRAGMA wal_checkpoint(FULL)")

        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            fun addFile(entryName: String, file: File) {
                if (!file.exists() || !file.isFile) return
                zip.putNextEntry(ZipEntry(entryName))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }

            addFile("database/$DB_FILE", context.getDatabasePath(DB_FILE))

            val photoDir = File(context.filesDir, "defect_photos")
            photoDir.listFiles()
                ?.filter { it.isFile }
                ?.forEach { addFile("photos/${it.name}", it) }

            zip.putNextEntry(ZipEntry("backup-version.txt"))
            zip.write("1".toByteArray())
            zip.closeEntry()
        }
        return out.toByteArray()
    }

    fun restore(context: Context, store: LocalStore, bytes: ByteArray) {
        val tempDir = File(context.cacheDir, "restore_${System.currentTimeMillis()}").apply { mkdirs() }
        val tempDb = File(tempDir, DB_FILE)
        val tempPhotos = File(tempDir, "photos").apply { mkdirs() }

        try {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        when {
                            entry.name == "database/$DB_FILE" -> {
                                tempDb.outputStream().use { zip.copyTo(it) }
                            }
                            entry.name.startsWith("photos/") -> {
                                val name = entry.name.substringAfter("photos/")
                                if (name.isNotBlank() && !name.contains("/") && !name.contains("\\")) {
                                    File(tempPhotos, name).outputStream().use { zip.copyTo(it) }
                                }
                            }
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }

            require(tempDb.exists() && tempDb.length() > 0) {
                "Резервная копия не содержит базу данных"
            }

            store.close()

            val db = context.getDatabasePath(DB_FILE)
            db.parentFile?.mkdirs()
            File(db.absolutePath + "-wal").delete()
            File(db.absolutePath + "-shm").delete()
            tempDb.copyTo(db, overwrite = true)

            val targetPhotos = File(context.filesDir, "defect_photos")
            targetPhotos.deleteRecursively()
            targetPhotos.mkdirs()
            tempPhotos.listFiles()?.forEach { it.copyTo(File(targetPhotos, it.name), overwrite = true) }

            store.activeDefects()
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
