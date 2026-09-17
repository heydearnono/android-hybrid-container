package net.xiaoluzhu.crab.container

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 脚本源码这里只能钉「文本形状」——它在 WebView 里跑出来什么样，只能在模拟器上由 `inject-order` /
 * `inject-scope` 两条断言看。所以钉的是那几条讲究有没有写在源码里。
 */
class DocumentStartScriptTest {
    @Test
    fun `先判 origin 再动 window`() {
        val source = DocumentStartScript.source
        val originGuard = source.indexOf("location.origin")
        val assignment = source.indexOf("window.__CRAB__ = crab")
        assertTrue(originGuard >= 0, "脚本必须先判 origin：兜底那条路没有 origin 概念，全靠这一句")
        assertTrue(assignment > originGuard, "赋值必须在 origin 判断之后")
        assertTrue(source.contains("return"), "origin 不符要提前 return")
    }

    @Test
    fun `origin 字面量从 HostingOrigin 派生`() {
        assertTrue(DocumentStartScript.source.contains(HostingOrigin.ORIGIN))
    }

    @Test
    fun `已存在就不重置——重复注入的证据不能被抹平`() {
        val source = DocumentStartScript.source
        assertTrue(
            source.contains("window.__CRAB__ ||"),
            "必须用 `||` 初始化；直接赋新对象会把 injected 抹回 1，重复注入就看不出来了",
        )
        assertTrue(source.contains("crab.injected = crab.injected + 1"), "injected 只增不减")
    }

    @Test
    fun `有标记时插在标记处，标记本身消失`() {
        val html = "<html><head>${DocumentStartScript.MARKER}<script>page()</script></head></html>"
        val result = DocumentStartScript.inlineInto(html)

        assertTrue(result.contains("<script>${DocumentStartScript.source}</script>"))
        assertTrue(!result.contains(DocumentStartScript.MARKER), "标记应被替换掉，不留残余")
        assertTrue(
            result.indexOf(DocumentStartScript.source) < result.indexOf("page()"),
            "注入必须落在页面自己的第一段脚本之前，否则 inject-order 判不出「页面开口前」",
        )
    }

    @Test
    fun `没有标记时退到 head 之后`() {
        val result = DocumentStartScript.inlineInto("<html><head lang=\"zh\"><title>t</title></head></html>")

        assertTrue(result.startsWith("<html><head lang=\"zh\"><script>"))
        assertTrue(
            result.indexOf(DocumentStartScript.source) < result.indexOf("<title>"),
            "插在 head 开标签之后、head 内首个元素之前",
        )
    }

    @Test
    fun `连 head 都没有时插到最前面——不许一个字都不插`() {
        val result = DocumentStartScript.inlineInto("<p>裸片段</p>")

        assertTrue(result.startsWith("<script>"))
        assertTrue(result.endsWith("<p>裸片段</p>"))
    }

    @Test
    fun `标记优先于 head——两处都在时只插一次`() {
        val html = "<html><head><meta>${DocumentStartScript.MARKER}</head></html>"
        val result = DocumentStartScript.inlineInto(html)

        assertEquals(1, occurrencesOf(result, "<script>"), "只许注入一次；叠加会让 injected 变 2")
        assertTrue(result.indexOf("<meta>") < result.indexOf("<script>"), "标记在 meta 之后，就该插在 meta 之后")
    }

    @Test
    fun `兜底只改 HTML，素材一律不碰`() {
        assertTrue(DocumentStartScript.appliesTo(AssetRouting.mimeOf("probe/index.html")))
        assertTrue(DocumentStartScript.appliesTo(AssetRouting.mimeOf("probe/second.html")))
        assertFalse(DocumentStartScript.appliesTo(AssetRouting.mimeOf("probe/probe.js")))
        assertFalse(DocumentStartScript.appliesTo(AssetRouting.mimeOf("probe/probe.png")))
        assertFalse(DocumentStartScript.appliesTo(AssetRouting.mimeOf("probe/style.css")))
    }

    private fun occurrencesOf(
        text: String,
        token: String,
    ): Int {
        var count = 0
        var index = text.indexOf(token)
        while (index >= 0) {
            count++
            index = text.indexOf(token, index + token.length)
        }
        return count
    }
}
