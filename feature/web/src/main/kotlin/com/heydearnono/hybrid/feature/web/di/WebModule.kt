package com.heydearnono.hybrid.feature.web.di

import com.heydearnono.hybrid.feature.web.WebPageViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

fun webModule() =
    module {
        viewModelOf(::WebPageViewModel)
    }
