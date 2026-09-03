package com.heydearnono.hybrid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.heydearnono.hybrid.core.designsystem.theme.BaseTheme
import com.heydearnono.hybrid.navigation.BaseNavHost

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BaseTheme {
                BaseNavHost()
            }
        }
    }
}
