package net.xiaoluzhu.crab.probe

import net.xiaoluzhu.crab.container.HostingOrigin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * M2 要求的那条**可执行检查**：承载 origin 的字面量在源码树里只许出现在定义处，加上探针页那份期望值。
 *
 * 走查不算数。origin 写成多份的后果是改的时候漏掉一处，而漏掉的表现是脚本静默不注入、或者导航被误拦——
 * 两者都不报错，只能靠这条测试在提交前拦下来。
 */
class HostingOriginSingleDefinitionTest {
    @Test
    fun `承载域名只出现在定义处与探针页期望值里`() {
        val hits =
            RepoFiles
                .scannableSources()
                .filter { it.readText().contains(HostingOrigin.DOMAIN) }
                .map { RepoFiles.relativePathOf(it) }
                .sorted()

        assertEquals(
            ALLOWED_FILES.sorted(),
            hits,
            "承载域名 ${HostingOrigin.DOMAIN} 出现的位置不对。它只允许写在 ${ALLOWED_FILES.first()}（定义处）" +
                "与 ${ALLOWED_FILES.last()}（探针页拿它比 location.origin）。别处要从 HostingOrigin 派生。",
        )
    }

    @Test
    fun `探针页那份期望值与定义处逐字相等`() {
        val probeJs = RepoFiles.text(PROBE_JS)
        val expected = Regex("""var EXPECTED_ORIGIN = '([^']+)';""").find(probeJs)?.groupValues?.get(1)
        assertEquals(HostingOrigin.ORIGIN, expected, "$PROBE_JS 里的 EXPECTED_ORIGIN 与 HostingOrigin.ORIGIN 不一致")
    }

    @Test
    fun `派生出来的地址不含另写的字面量`() {
        // 拼错的话 isHostingOrigin 会把自己家的地址判成越界，导航直接被拦——这里先把形状钉住。
        assertTrue(HostingOrigin.ORIGIN.endsWith("://${HostingOrigin.DOMAIN}"))
        assertTrue(HostingOrigin.ENTRY_URL.startsWith("${HostingOrigin.ORIGIN}/"))
    }

    private companion object {
        const val DEFINITION = "core/container/src/main/kotlin/net/xiaoluzhu/crab/container/HostingOrigin.kt"
        const val PROBE_JS = "app/src/main/assets/probe/probe.js"
        val ALLOWED_FILES = listOf(DEFINITION, PROBE_JS)
    }
}
