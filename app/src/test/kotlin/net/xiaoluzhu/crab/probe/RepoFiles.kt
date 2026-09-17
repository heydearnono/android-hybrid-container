package net.xiaoluzhu.crab.probe

import java.io.File

/**
 * 让 JVM 单测能扫源码树。
 *
 * 探针页（`.html` / `.js`）和 `scripts/probe.sh` 不在编译器、Spotless、lint 的覆盖范围里，它们与 Kotlin
 * 侧的对应关系只能靠扫文本兜住，所以这几条测试必须能读到仓库里的真实文件，而不是 classpath 资源。
 */
internal object RepoFiles {
    /** 从测试的工作目录（Gradle 设成模块目录）往上找到含 `settings.gradle.kts` 的那一层。 */
    val root: File by lazy {
        val workingDir = System.getProperty("user.dir") ?: error("拿不到 user.dir")
        var candidate: File? = File(workingDir).absoluteFile
        while (candidate != null) {
            if (File(candidate, "settings.gradle.kts").isFile) return@lazy candidate
            candidate = candidate.parentFile
        }
        error("找不到仓库根目录（从 $workingDir 往上没见到 settings.gradle.kts）")
    }

    val probeAssetsDir: File get() = File(root, "app/src/main/assets")

    fun file(relativePath: String): File =
        File(root, relativePath).also {
            check(it.isFile) { "仓库里没有 $relativePath" }
        }

    fun text(relativePath: String): String = file(relativePath).readText()

    /**
     * 参与「源码里只许出现一处」检查的范围：会被编进产物或被执行的文件。文档里的引用不算，它们不参与运行。
     */
    fun scannableSources(): List<File> {
        val roots =
            listOf(
                "app/src/main",
                "core/container/src/main",
                "core/webview/src/main",
                "build-logic/src",
                "scripts",
            ).map { File(root, it) }
        return roots.flatMap { dir ->
            dir.walkTopDown().filter { it.isFile && it.extension !in BINARY_EXTENSIONS }
        }
    }

    fun relativePathOf(file: File): String = file.relativeTo(root).path

    private val BINARY_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp", "ico", "woff", "woff2", "ttf")
}
