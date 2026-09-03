package com.heydearnono.hybrid.core.bridge.handler

import com.heydearnono.hybrid.core.bridge.InMemoryKeyValueStore
import com.heydearnono.hybrid.core.bridge.json
import com.heydearnono.hybrid.core.bridge.rejectedCode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private fun obj(raw: String): JsonObject = Json.parseToJsonElement(raw) as JsonObject

class StorageHandlersTest {
    private val store = InMemoryKeyValueStore()

    @Test
    fun `set 之后 get 拿到同一个值`() =
        runTest {
            StorageSetHandler(store).handle(obj("""{"key":"token","value":"abc"}"""))

            assertEquals("""{"value":"abc"}""", StorageGetHandler(store).handle(obj("""{"key":"token"}""")).json())
        }

    @Test
    fun `没写过的 key 回 value null 而不是错误`() =
        runTest {
            // localStorage 也是返回 null 而不是抛异常，JS 侧不该为一次正常的 miss 写 try/catch。
            assertEquals("""{"value":null}""", StorageGetHandler(store).handle(obj("""{"key":"missing"}""")).json())
        }

    @Test
    fun `remove 之后读不到`() =
        runTest {
            StorageSetHandler(store).handle(obj("""{"key":"k","value":"v"}"""))
            StorageRemoveHandler(store).handle(obj("""{"key":"k"}"""))

            assertNull(store.get("k"))
        }

    @Test
    fun `remove 不存在的 key 不算错误`() =
        runTest {
            assertEquals("null", StorageRemoveHandler(store).handle(obj("""{"key":"ghost"}""")).json())
        }

    @Test
    fun `set 允许空串 value 但不允许缺失`() =
        runTest {
            StorageSetHandler(store).handle(obj("""{"key":"k","value":""}"""))
            assertEquals("", store.get("k"))

            // 缺 value 更可能是调用方写错了字段名，静默存个空串会让这种错误很难查。
            assertEquals("INVALID_PARAMS", StorageSetHandler(store).handle(obj("""{"key":"k2"}""")).rejectedCode())
            assertNull(store.get("k2"))
        }

    @Test
    fun `三个能力都拒绝空白 key`() =
        runTest {
            assertEquals("INVALID_PARAMS", StorageGetHandler(store).handle(obj("""{"key":" "}""")).rejectedCode())
            assertEquals(
                "INVALID_PARAMS",
                StorageSetHandler(store).handle(obj("""{"key":"","value":"v"}""")).rejectedCode(),
            )
            assertEquals("INVALID_PARAMS", StorageRemoveHandler(store).handle(null).rejectedCode())
        }
}
