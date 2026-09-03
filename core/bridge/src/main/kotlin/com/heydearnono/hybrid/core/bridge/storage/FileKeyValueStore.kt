package com.heydearnono.hybrid.core.bridge.storage

import com.heydearnono.hybrid.core.bridge.port.KeyValueStore
import com.heydearnono.hybrid.core.common.DispatcherProvider
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

private const val STORE_FILE_NAME = "store.json"
private const val TEMP_FILE_NAME = "store.json.tmp"

/**
 * 落盘的键值存储。[root] 是 `java.io.File`，所以这个类是纯 JVM 的，测试给个临时目录就能跑；
 * `:app` 传 `File(context.filesDir, "bridge-kv")`。
 *
 * **存成一个 store.json，不做 file-per-key。** 理由是安全而不是性能：file-per-key
 * 必须处理 `../../foo` 这类 key 的路径逃逸，而 key 是 JS 侧随便给的。单文件方案里
 * key 永远只是 JSON 里的一个字符串，从构造上就没有这类漏洞。
 */
class FileKeyValueStore(
    private val root: File,
    private val dispatchers: DispatcherProvider,
) : KeyValueStore {
    private val json = Json
    private val mutex = Mutex()
    private var cache: MutableMap<String, String>? = null

    override suspend fun get(key: String): String? = locked { it[key.validated()] }

    override suspend fun set(
        key: String,
        value: String,
    ) = locked { entries ->
        entries[key.validated()] = value
        persist(entries)
    }

    override suspend fun remove(key: String) =
        locked { entries ->
            // 没这个 key 就不用写盘。删不存在的 key 是正常调用，不是错误。
            if (entries.remove(key.validated()) != null) persist(entries)
        }

    /**
     * 全部读写都串行化，并统一切到 io。
     *
     * 加锁是因为内存缓存和磁盘内容必须一致：两个并发的 set 各自读到旧 map 再各自落盘，
     * 后写的那个会把前一个的 key 抹掉。
     */
    private suspend fun <T> locked(block: (MutableMap<String, String>) -> T): T =
        withContext(dispatchers.io) {
            mutex.withLock {
                val entries = cache ?: load().also { cache = it }
                block(entries)
            }
        }

    private fun load(): MutableMap<String, String> {
        val file = File(root, STORE_FILE_NAME)
        if (!file.isFile) return mutableMapOf()
        return try {
            json.decodeFromString<Map<String, String>>(file.readText()).toMutableMap()
        } catch (_: IOException) {
            // 读不动就当空的开局。这是给页面用的本地缓存，不是账本——
            // 为了它让整个 bridge 不可用不值得。
            mutableMapOf()
        } catch (_: IllegalArgumentException) {
            // 文件被截断或不是合法 JSON，同上。SerializationException 落在这一条里。
            mutableMapOf()
        }
    }

    /**
     * 写临时文件再原子替换。进程在写一半时被杀，旧文件仍然完整，
     * 不会留下一个解不开的 store.json 把所有 key 一起弄丢。
     */
    private fun persist(entries: Map<String, String>) {
        root.mkdirs()
        val temp = File(root, TEMP_FILE_NAME)
        temp.writeText(json.encodeToString(entries))
        Files.move(
            temp.toPath(),
            File(root, STORE_FILE_NAME).toPath(),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE,
        )
    }

    /**
     * key 的合法性在 handler 里已经校验过一次，这里再拦一道：这个类是 public 的，
     * 将来会有别的调用方，而「空 key」在单文件方案里会变成一个永远取不回来的洞。
     */
    private fun String.validated(): String {
        require(isNotBlank()) { "key 不能为空" }
        return this
    }
}
