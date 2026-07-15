package com.lover.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lover.app.core.data.InviteSession
import com.lover.app.core.design.LoverNoticeHost
import com.lover.app.core.design.LoverTheme
import com.lover.app.core.notice.NoticeStore
import com.lover.app.core.util.InviteLinks
import com.lover.app.feature.auth.AuthScreen
import com.lover.app.feature.auth.AuthViewModel
import com.lover.app.feature.main.MainScreen
import com.lover.app.feature.main.MainViewModel
import com.lover.app.feature.splash.SplashScreen
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var noticeStore: NoticeStore
    @Inject lateinit var inviteSession: InviteSession

    override fun onCreate(savedInstanceState: Bundle?) {
        // 立即移除系统启动窗（仅保留纯色底），避免先闪一下桌面图标
        val systemSplash = installSplashScreen()
        systemSplash.setKeepOnScreenCondition { false }
        super.onCreate(savedInstanceState)
        captureInviteIntent(intent)
        enableEdgeToEdge()
        setContent {
            LoverTheme {
                Surface(modifier.fillMaxSize()) {
                    val mainViewModel: MainViewModel = hiltViewModel()
                    val state by mainViewModel.data.collectAsStateWithLifecycle()
                    val restoreComplete by mainViewModel.restoreComplete.collectAsStateWithLifecycle()
                    var splashDone by rememberSaveable { mutableStateOf(false) }

                    LaunchedEffect(state.accessToken) {
                        if (state.accessToken == null) {
                            noticeStore.clear()
                        }
                    }

                    Box(Modifier.fillMaxSize()) {
                        when {
                            !state.sessionLoaded || (state.accessToken != null && !restoreComplete) -> {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            }
                            state.accessToken == null -> {
                                val authViewModel: AuthViewModel = hiltViewModel()
                                AuthScreen(authViewModel)
                            }
                            else -> MainScreen(mainViewModel)
                        }

                        if (splashDone) {
                            LoverNoticeHost(
                                noticeStore = noticeStore,
                                modifier = Modifier.align(Alignment.TopCenter),
                            )
                        }

                        if (!splashDone) {
                            SplashScreen(onComplete = { splashDone = true })
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        captureInviteIntent(intent)
    }

    private fun captureInviteIntent(intent: Intent?) {
        InviteLinks.parseCode(intent?.data)?.let(inviteSession::offer)
    }
}
