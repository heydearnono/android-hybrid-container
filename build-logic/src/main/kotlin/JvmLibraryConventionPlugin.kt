import com.heydearnono.hybrid.buildlogic.configureKotlinJvm
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * 纯 JVM Kotlin 模块。
 *
 * AGP 9 的内置 Kotlin 只对 Android 模块生效，纯 JVM 模块必须自己 apply KGP。
 *
 * 额外 apply `com.android.lint`：不加的话 `:app:lint` 会把这些模块当外部依赖跳过
 * （构建日志里会明说 "Lint will treat :core:xxx as an external dependency"）。
 * 大部分业务逻辑就在这几个模块里，漏掉等于静态检查基本没覆盖。
 */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("java-library")
            pluginManager.apply("org.jetbrains.kotlin.jvm")
            pluginManager.apply("com.android.lint")
            configureKotlinJvm()
        }
    }
}
