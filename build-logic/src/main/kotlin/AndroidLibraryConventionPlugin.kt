import com.android.build.api.dsl.LibraryExtension
import com.heydearnono.hybrid.buildlogic.configureAndroidCommon
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.getByType

/**
 * Android library 模块。
 *
 * 铁律：**不要** apply `org.jetbrains.kotlin.android`——AGP 9 内置 Kotlin 并默认开启，再 apply 会冲突。
 */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.library")
            configureAndroidCommon(extensions.getByType<LibraryExtension>())
        }
    }
}
