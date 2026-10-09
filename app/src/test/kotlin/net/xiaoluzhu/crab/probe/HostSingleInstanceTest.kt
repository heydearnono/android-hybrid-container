package net.xiaoluzhu.crab.probe

import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 宿主单实例（pro M4 的落点表：Android 取 `singleTask`，再次启动走 `onNewIntent`）。
 *
 * 这一格只能扫文件：`launchMode` 由系统服务端读，JVM 里没有任何东西会因为它取错而失败；而它取错在设备上
 * 也要换一个入口启动、回桌面再点图标才看得出来。所以判据是「manifest 里显式写着 singleTask」——pro 要的
 * 是显式取值、不靠默认值，默认的 `standard` 恰好就是多开的那一种。
 *
 * 读的是源 manifest，不是合并后的那份：后者是构建产物、不是 Gradle 登记给这个测试的输入。合并时被库的
 * manifest 改掉 `launchMode` 要写 `tools:replace`，本仓没有。
 */
class HostSingleInstanceTest {
    private val manifest = RepoFiles.file("app/src/main/AndroidManifest.xml")

    private fun hostActivity(): Element {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val document = factory.newDocumentBuilder().parse(manifest)
        val activities = document.getElementsByTagName("activity")
        val host =
            (0 until activities.length)
                .map { activities.item(it) as Element }
                .firstOrNull { it.getAttributeNS(ANDROID_NS, "name") == ".MainActivity" }
        return assertNotNull(host, "manifest 里找不到 .MainActivity")
    }

    @Test
    fun `宿主 Activity 显式取 singleTask`() {
        assertEquals(
            "singleTask",
            hostActivity().getAttributeNS(ANDROID_NS, "launchMode"),
            "宿主必须显式写 android:launchMode=\"singleTask\"：不写就是 standard，从别的入口进来过一次再点图标会多开一个容器",
        )
    }

    @Test
    fun `宿主就是桌面图标那个入口`() {
        val filters = hostActivity().getElementsByTagName("intent-filter")
        val launcher =
            (0 until filters.length).map { filters.item(it) as Element }.any { filter ->
                filter.namesOf("action").contains("android.intent.action.MAIN") &&
                    filter.namesOf("category").contains("android.intent.category.LAUNCHER")
            }
        assertTrue(launcher, "singleTask 挂在了不是桌面入口的 Activity 上，等于没挂")
    }

    @Test
    fun `再次启动接上了 onNewIntent`() {
        val source = RepoFiles.text("app/src/main/kotlin/net/xiaoluzhu/crab/MainActivity.kt")
        assertTrue(
            source.contains("override fun onNewIntent(intent: Intent)"),
            "pro 要新请求交给已有的容器，Android 上的落点是 onNewIntent",
        )
    }

    private fun Element.namesOf(tag: String): List<String> {
        val nodes = getElementsByTagName(tag)
        return (0 until nodes.length).map { (nodes.item(it) as Element).getAttributeNS(ANDROID_NS, "name") }
    }

    private companion object {
        const val ANDROID_NS: String = "http://schemas.android.com/apk/res/android"
    }
}
