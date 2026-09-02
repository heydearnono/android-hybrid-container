import com.example.base.buildlogic.androidExtension
import com.example.base.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * Compose 支持。必须在 `base.android.application` / `base.android.library` 之后 apply。
 *
 * 内置 Kotlin **不带** Compose 编译器插件：`buildFeatures.compose = true` 时 AGP 会直接报
 * "the Compose Compiler Gradle plugin is required"（已实测）。插件版本必须与 AGP 内置的
 * Kotlin 版本一致，由版本目录的 `kotlin` 统一管。
 */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
            androidExtension().buildFeatures.compose = true

            dependencies {
                val bom = libs.findLibrary("compose-bom").get()
                add("implementation", platform(bom))
                add("androidTestImplementation", platform(bom))
                add("implementation", libs.findLibrary("compose-ui").get())
                add("implementation", libs.findLibrary("compose-foundation").get())
                add("implementation", libs.findLibrary("compose-ui-graphics").get())
                add("implementation", libs.findLibrary("compose-ui-tooling-preview").get())
                add("implementation", libs.findLibrary("compose-material3").get())
                add("debugImplementation", libs.findLibrary("compose-ui-tooling").get())
            }
        }
    }
}
