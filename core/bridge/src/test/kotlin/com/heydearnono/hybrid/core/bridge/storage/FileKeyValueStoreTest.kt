package com.heydearnono.hybrid.core.bridge.storage

import com.heydearnono.hybrid.core.bridge.TestDispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class FileKeyValueStoreTest {
    private lateinit var root: File

    private fun store() = FileKeyValueStore(root, TestDispatchers(UnconfinedTestDispatcher()))

    @BeforeTest
    fun setUp() {
        root = Files.createTempDirectory("bridge-kv").toFile()
    }

    @AfterTest
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun `读写删往返`() =
        runTest {
            val subject = store()

            assertNull(subject.get("k"))
            subject.set("k", "v")
            assertEquals("v", subject.get("k"))
            subject.remove("k")
            assertNull(subject.get("k"))
        }

    @Test
    fun `换一个实例仍然读到写过的值`() =
        runTest {
            // 这条才真正验证了「落盘了」：上一个实例的内存缓存对新实例不可见。
            store().set("token", "abc")

            assertEquals("abc", store().get("token"))
        }

    @Test
    fun `删除也会落盘`() =
        runTest {
            store().set("token", "abc")
            store().remove("token")

            assertNull(store().get("token"))
        }

    @Test
    fun `空白 key 直接抛`() =
        runTest {
            // handler 已经拦了一道，这里是第二道：这个类是 public 的，将来会有别的调用方。
            val subject = store()

            assertFailsWith<IllegalArgumentException> { subject.set("", "v") }
            assertFailsWith<IllegalArgumentException> { subject.set("  ", "v") }
            assertFailsWith<IllegalArgumentException> { subject.get("") }
            assertFailsWith<IllegalArgumentException> { subject.remove("") }
        }

    @Test
    fun `落盘后目录里只有 store_json 没有临时文件残留`() =
        runTest {
            store().set("k", "v")

            assertEquals(listOf("store.json"), root.list()?.sorted())
        }

    @Test
    fun `路径分隔符只是普通的 key 不会写出别的文件`() =
        runTest {
            // 单文件方案的整个意义就在这条：key 永远只是 JSON 里的字符串，不会变成路径。
            val subject = store()

            subject.set("../../escaped", "v")

            assertEquals("v", subject.get("../../escaped"))
            assertEquals(listOf("store.json"), root.list()?.sorted())
        }

    @Test
    fun `store_json 损坏时当空的开局而不是让 bridge 不可用`() =
        runTest {
            File(root, "store.json").writeText("{ this is not json")

            val subject = store()

            assertNull(subject.get("k"))
            // 还能继续写。
            subject.set("k", "v")
            assertEquals("v", store().get("k"))
        }

    @Test
    fun `并发写不会互相抹掉`() =
        runTest {
            // 没有锁的话，两个 set 各自读到旧 map 再各自落盘，后写的会把前一个的 key 抹掉。
            val subject = store()

            coroutineScope {
                repeat(20) { i -> launch { subject.set("k$i", "v$i") } }
            }

            val reloaded = store()
            repeat(20) { i -> assertEquals("v$i", reloaded.get("k$i")) }
        }
}
