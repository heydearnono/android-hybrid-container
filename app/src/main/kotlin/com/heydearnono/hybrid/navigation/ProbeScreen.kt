package com.heydearnono.hybrid.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * `router.open({route: "probe"})` 的落点。
 *
 * 内容不属于三端契约（PROTOCOL §3）：能被打开、能返回即可，一行标题文字就够。
 * 存在的唯一理由是让白名单里有一个三端都必须有的路由名，这样一致性验收页才能测到
 * `router.open` 成功的样子，不只是 `NOT_FOUND` 那个失败分支。
 */
@Composable
internal fun ProbeScreen(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = "probe")
    }
}
