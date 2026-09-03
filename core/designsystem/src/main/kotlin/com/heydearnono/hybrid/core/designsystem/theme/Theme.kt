package com.heydearnono.hybrid.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Brand = Color(0xFF00639B)
private val BrandDark = Color(0xFF95CCFF)

private val LightColors =
    lightColorScheme(
        primary = Brand,
        onPrimary = Color.White,
    )

private val DarkColors =
    darkColorScheme(
        primary = BrandDark,
        onPrimary = Color(0xFF003354),
    )

/**
 * 全 App 唯一的主题入口。
 *
 * 刻意不接 dynamic color：颜色随壁纸变化会让「视觉是否正确」无法靠固定值判断，
 * 而当前没有设备可以肉眼确认。需要时在这里加。
 */
@Composable
fun BaseTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
