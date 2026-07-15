package com.lover.app.feature.couple

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.lover.app.core.design.Blush
import com.lover.app.core.design.Stone
import com.lover.app.core.design.WarmBackground

/**
 * Legacy full-screen couple gate. Invite / bind now live inside「我们」in MainScreen.
 */
@Composable
fun CoupleScreen(@Suppress("UNUSED_PARAMETER") viewModel: CoupleViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Blush.copy(alpha = 0.55f), WarmBackground)))
            .widthIn(max = 448.dp)
            .padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("建立我们的小宇宙", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        Text(
            "请从底部「我们」进入，邀请或绑定另一半",
            color = Stone,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
