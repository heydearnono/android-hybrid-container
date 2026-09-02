package com.example.base

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.base.core.designsystem.theme.BaseTheme
import com.example.base.navigation.BaseNavHost

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
