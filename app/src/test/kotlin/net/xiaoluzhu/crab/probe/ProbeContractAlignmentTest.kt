package net.xiaoluzhu.crab.probe

import net.xiaoluzhu.crab.container.AssetRoute
import net.xiaoluzhu.crab.container.AssetRouting
import net.xiaoluzhu.crab.container.DocumentStartScript
import net.xiaoluzhu.crab.container.HostingOrigin
import net.xiaoluzhu.crab.container.ProbeContract
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * 探针页、`scripts/probe.sh`、Kotlin 侧 [ProbeContract] 三份东西必须对齐，而**只有这一条测试盯着**：
 * `.html` / `.js` / `.sh` 不在编译器、Spotless、lint 的覆盖范围里，改错一个 slug 不会有任何编译期反馈，
 * 表现只是探针少一行结果——而少一行会被读成 FAIL。
 *
 * M3 覆盖到页面自己能判的八条；导航/对话框/权限（M4）落地时在这里加。
 */
class ProbeContractAlignmentTest {
    private val indexHtml = RepoFiles.text(INDEX_HTML)
    private val probeJs = RepoFiles.text(PROBE_JS)
    private val probeSh = RepoFiles.text(PROBE_SH)

    @Test
    fun `入口地址指向的素材真的在 assets 里`() {
        val route = AssetRouting.resolve(HostingOrigin.ENTRY_URL.removePrefix(HostingOrigin.ORIGIN))
        val hit = assertIs<AssetRoute.Hit>(route, "入口地址 ${HostingOrigin.ENTRY_URL} 被路由判成了 $route")
        assertTrue(
            File(RepoFiles.probeAssetsDir, hit.assetPath).isFile,
            "路由说入口页是 ${hit.assetPath}，但 assets 里没有这个文件",
        )
        assertEquals("text/html", hit.mimeType)
    }

    @Test
    fun `入口页留着注入标记，且它在页面自己的第一段脚本之前`() {
        // 标记的字面量取自 DocumentStartScript：兜底注入按它找插入点，两边写歪一个字就静默不注入。
        val marker = indexHtml.indexOf(DocumentStartScript.MARKER)
        assertTrue(marker >= 0, "$INDEX_HTML 里没有 ${DocumentStartScript.MARKER}；兜底注入要往这里插")
        val firstScript = indexHtml.indexOf("<script")
        assertTrue(
            firstScript < 0 || marker < firstScript,
            "${DocumentStartScript.MARKER} 必须排在页面自己的第一段脚本之前，否则 inject-order 判的就不是「开口之前」",
        )
    }

    @Test
    fun `快照脚本夹在注入标记与 probe_js 之间——它是 inject-order 的取证点`() {
        val marker = indexHtml.indexOf(DocumentStartScript.MARKER)
        val snapshot = indexHtml.indexOf(SNAPSHOT_GLOBAL)
        // 找 src 属性而不是裸文件名：注释里也会提到 probe.js，裸文件名会匹配到注释上去（踩过）。
        val probeJsTag = indexHtml.indexOf("src=\"probe.js\"")

        assertTrue(snapshot > marker, "$SNAPSHOT_GLOBAL 必须在注入之后取，否则取到的是空快照")
        assertTrue(
            probeJsTag > snapshot,
            "$SNAPSHOT_GLOBAL 必须在 probe.js 之前取；probe.js 只读快照，不自己看当下的 __CRAB__",
        )
        assertTrue(probeJs.contains(SNAPSHOT_GLOBAL), "$PROBE_JS 要读 $SNAPSHOT_GLOBAL 来判 inject-order")
    }

    @Test
    fun `入口页引到的本地素材都在`() {
        val referenced =
            Regex("""(?:src|href)="([^":]+)"""")
                .findAll(indexHtml)
                .map { it.groupValues[1] }
                .filterNot { it.startsWith("#") }
                .toSet()
        assertTrue(referenced.isNotEmpty(), "$INDEX_HTML 一个本地素材都没引，探针页素材对不上")
        for (path in referenced) {
            val file = File(RepoFiles.probeAssetsDir, "${HostingOrigin.HOSTING_DIR}/$path")
            assertTrue(file.isFile, "$INDEX_HTML 引了 $path，但 assets 里没有 ${RepoFiles.relativePathOf(file)}")
        }
    }

    @Test
    fun `探针页用的固定标记与 ProbeContract 一致`() {
        assertEquals(ProbeContract.MARKER_INTERCEPTED, jsConstant("MARKER_INTERCEPTED"))
        assertEquals(ProbeContract.MARKER_OUT_OF_BOUNDS, jsConstant("MARKER_OUT_OF_BOUNDS"))
        assertTrue(
            probeJs.contains("'${ProbeContract.LOG_PREFIX} '"),
            "探针页打结果用的前缀必须是 ${ProbeContract.LOG_PREFIX}，probe.sh 按它回读",
        )
        assertTrue(
            probeJs.contains("'${ProbeContract.LOG_PREFIX_ENV} '"),
            "探针页打环境自报用的前缀必须是 ${ProbeContract.LOG_PREFIX_ENV}",
        )
    }

    @Test
    fun `越界文件的内容就是那个固定标记`() {
        assertEquals(ProbeContract.MARKER_OUT_OF_BOUNDS, RepoFiles.text(OUT_OF_BOUNDS).trim())
    }

    @Test
    fun `越界文件在承载目录之外，路由不可能把它送出去`() {
        assertTrue(
            File(RepoFiles.probeAssetsDir, OUT_OF_BOUNDS_ASSET).isFile,
            "越界素材必须真实存在，否则 escape 断言测不到东西",
        )
        assertEquals(AssetRoute.NotFound, AssetRouting.resolve("/$OUT_OF_BOUNDS_ASSET"))
    }

    @Test
    fun `探针页自己判的 slug 都在清单里，顺序也一致`() {
        val pageSlugs = jsArray("PAGE_SLUGS")
        assertTrue(pageSlugs.isNotEmpty(), "$PROBE_JS 里读不出 PAGE_SLUGS")
        for (slug in pageSlugs) {
            assertTrue(slug in ProbeContract.SLUGS, "$PROBE_JS 里的 slug `$slug` 不在 ProbeContract.SLUGS 里")
        }
        assertEquals(
            ProbeContract.SLUGS.filter { it in pageSlugs },
            pageSlugs,
            "探针页里的 slug 顺序与 pro 定的顺序不一致",
        )
    }

    @Test
    fun `probe 脚本按固定顺序输出全部十六条`() {
        val emitted =
            Regex("""^emit ([a-z-]+) """, RegexOption.MULTILINE)
                .findAll(probeSh)
                .map { it.groupValues[1] }
                .toList()
        assertEquals(ProbeContract.SLUGS, emitted, "$PROBE_SH 打的十六行与 ProbeContract.SLUGS 不一致")
    }

    private fun jsConstant(name: String): String? =
        Regex("""var $name = '([^']*)';""").find(probeJs)?.groupValues?.get(1)

    private fun jsArray(name: String): List<String> {
        val body = Regex("""var $name = \[([^]]*)]""").find(probeJs)?.groupValues?.get(1) ?: return emptyList()
        return Regex("""'([^']+)'""").findAll(body).map { it.groupValues[1] }.toList()
    }

    private companion object {
        const val INDEX_HTML = "app/src/main/assets/probe/index.html"
        const val PROBE_JS = "app/src/main/assets/probe/probe.js"
        const val PROBE_SH = "scripts/probe.sh"
        const val OUT_OF_BOUNDS_ASSET = "outside/out-of-bounds.txt"
        const val OUT_OF_BOUNDS = "app/src/main/assets/$OUT_OF_BOUNDS_ASSET"
        const val SNAPSHOT_GLOBAL = "window.__CRAB_SNAPSHOT__"
    }
}
