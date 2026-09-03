import com.android.build.api.dsl.ApplicationExtension
import com.heydearnono.hybrid.buildlogic.configureAndroidCommon
import com.heydearnono.hybrid.buildlogic.intVersionOf
import com.heydearnono.hybrid.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.getByType

/**
 * Android application 模块。同样不 apply KGP，见 [AndroidLibraryConventionPlugin]。
 */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.application")
            val extension = extensions.getByType<ApplicationExtension>()
            configureAndroidCommon(extension)
            extension.defaultConfig.targetSdk = libs.intVersionOf("targetSdk")
        }
    }
}
