package io.github.terlingua.books.data.content

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.driver.bundled.SQLITE_OPEN_READONLY
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Read-only access to the bundled content database. The bundled SQLite driver is used because the
 * platform SQLite on older devices is not guaranteed to ship FTS5.
 */
class ContentDatabase(
    private val context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val mutex = Mutex()
    private var connection: SQLiteConnection? = null

    suspend fun <T> read(block: (SQLiteConnection) -> T): T = withContext(dispatcher) {
        mutex.withLock { block(connection ?: open().also { connection = it }) }
    }

    private fun open(): SQLiteConnection {
        val dir = File(context.noBackupFilesDir, "content").apply { mkdirs() }
        val db = File(dir, "terlingua-books.db")
        val marker = File(dir, "installed.stamp")
        val stamp = installStamp()
        if (!db.isFile || !marker.isFile || marker.readText() != stamp) {
            val tmp = File(dir, "terlingua-books.db.tmp")
            context.assets.open(ContentSql.ASSET_PATH).use { input -> tmp.outputStream().use { input.copyTo(it) } }
            db.delete()
            check(tmp.renameTo(db)) { "Could not install content database" }
            marker.writeText(stamp)
        }
        return BundledSQLiteDriver().open(db.absolutePath, SQLITE_OPEN_READONLY)
    }

    private fun installStamp(): String {
        val pm = context.packageManager
        val info = if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, 0)
        }
        return "${info.lastUpdateTime}:${PackageInfoCompat.getLongVersionCode(info)}"
    }
}

inline fun <T> SQLiteConnection.query(
    sql: String,
    bind: SQLiteStatement.() -> Unit = {},
    row: (SQLiteStatement) -> T,
): List<T> = prepare(sql).use { st ->
    st.bind()
    buildList { while (st.step()) add(row(st)) }
}

fun SQLiteStatement.textOrNull(index: Int): String? = if (isNull(index)) null else getText(index)
