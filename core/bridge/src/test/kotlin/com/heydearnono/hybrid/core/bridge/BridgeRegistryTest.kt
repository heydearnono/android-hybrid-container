package com.heydearnono.hybrid.core.bridge

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame

class BridgeRegistryTest {
    @Test
    fun `按 method 找到 handler`() {
        val handler = FakeHandler("storage.get")
        val registry = BridgeRegistry(listOf(handler, FakeHandler("ui.toast")))

        assertSame(handler, registry.find("storage.get"))
        assertEquals(setOf("storage.get", "ui.toast"), registry.methods)
    }

    @Test
    fun `没注册的 method 返回 null`() {
        assertNull(BridgeRegistry(emptyList()).find("storage.get"))
    }

    @Test
    fun `重复注册同一个 method 直接抛`() {
        // 「哪个赢」在这种情况下是随机的，所以构造期就要炸，不留到运行期。
        val error =
            assertFailsWith<IllegalArgumentException> {
                BridgeRegistry(listOf(FakeHandler("storage.get"), FakeHandler("storage.get")))
            }

        assertEquals("能力 storage.get 被注册了 2 次", error.message)
    }
}
