package net.xiaoluzhu.crab.container

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 路径归一化是「判错了就等于把 assets 目录漏出去」的那类逻辑，所以每种穿越写法都单独一条。
 *
 * `PathHandler.handle` 拿到的路径已经被 `Uri.getPath()` 解过一轮码（javap 核过 androidx.webkit 的实现），
 * 所以下面既写解码前的形状也写解码后的形状——两种都必须判成越界。
 */
class AssetRoutingTest {
    @Test
    fun `入口页命中，返回 assets 相对路径与 html MIME`() {
        assertEquals(AssetRoute.Hit("probe/index.html", "text/html"), AssetRouting.resolve("/probe/index.html"))
    }

    @Test
    fun `不带前导斜杠也命中`() {
        assertEquals(AssetRoute.Hit("probe/probe.js", "text/javascript"), AssetRouting.resolve("probe/probe.js"))
    }

    @Test
    fun `点段与重复斜杠只是噪音，不影响命中`() {
        assertEquals(AssetRoute.Hit("probe/index.html", "text/html"), AssetRouting.resolve("//probe/./index.html"))
    }

    @Test
    fun `正常字符的百分号编码要还原`() {
        assertEquals(AssetRoute.Hit("probe/index.html", "text/html"), AssetRouting.resolve("/probe/%69ndex.html"))
    }

    @Test
    fun `承载目录之外一律 NotFound——escape 断言靠的就是这条`() {
        assertEquals(AssetRoute.NotFound, AssetRouting.resolve("/outside/out-of-bounds.txt"))
    }

    @Test
    fun `名字以承载目录开头的兄弟目录不算命中`() {
        assertEquals(AssetRoute.NotFound, AssetRouting.resolve("/probeX/index.html"))
    }

    @Test
    fun `只指到目录或根路径都是 NotFound`() {
        assertEquals(AssetRoute.NotFound, AssetRouting.resolve(""))
        assertEquals(AssetRoute.NotFound, AssetRouting.resolve("/"))
        assertEquals(AssetRoute.NotFound, AssetRouting.resolve("/probe"))
        assertEquals(AssetRoute.NotFound, AssetRouting.resolve("/probe/"))
    }

    @Test
    fun `明文穿越判越界`() {
        assertEquals(AssetRoute.OutOfBounds, AssetRouting.resolve("/probe/../outside/out-of-bounds.txt"))
    }

    @Test
    fun `编码穿越判越界`() {
        assertEquals(AssetRoute.OutOfBounds, AssetRouting.resolve("/probe/%2e%2e%2foutside/out-of-bounds.txt"))
        assertEquals(AssetRoute.OutOfBounds, AssetRouting.resolve("/probe/%2E%2E/outside/out-of-bounds.txt"))
        assertEquals(AssetRoute.OutOfBounds, AssetRouting.resolve("/probe/..%2Foutside/out-of-bounds.txt"))
    }

    @Test
    fun `双重编码穿越判越界——解一轮看着不像 dotdot，就是它想混过去`() {
        assertEquals(AssetRoute.OutOfBounds, AssetRouting.resolve("/probe/%252e%252e%252foutside/out-of-bounds.txt"))
    }

    @Test
    fun `穿越出去再走回来也是越界，不给它抵消`() {
        assertEquals(AssetRoute.OutOfBounds, AssetRouting.resolve("/probe/../probe/index.html"))
    }

    @Test
    fun `反斜杠与 NUL 判越界`() {
        assertEquals(AssetRoute.OutOfBounds, AssetRouting.resolve("/probe/..\\outside\\out-of-bounds.txt"))
        assertEquals(AssetRoute.OutOfBounds, AssetRouting.resolve("/probe/index.html%00.png"))
    }

    @Test
    fun `坏掉的百分号编码当坏请求处理`() {
        assertEquals(AssetRoute.NotFound, AssetRouting.resolve("/probe/%zz.html"))
        assertEquals(AssetRoute.NotFound, AssetRouting.resolve("/probe/index.html%2"))
    }

    @Test
    fun `MIME 按扩展名推，认不出的当二进制`() {
        assertEquals("text/html", AssetRouting.mimeOf("probe/index.html"))
        assertEquals("text/javascript", AssetRouting.mimeOf("probe/probe.js"))
        assertEquals("image/png", AssetRouting.mimeOf("probe/probe.PNG"))
        assertEquals("text/plain", AssetRouting.mimeOf("outside/out-of-bounds.txt"))
        assertEquals("application/octet-stream", AssetRouting.mimeOf("probe/data.bin"))
        assertEquals("application/octet-stream", AssetRouting.mimeOf("probe/LICENSE"))
    }
}
