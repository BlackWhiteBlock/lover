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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lover.app.core.design.LoverNoticeHost
import com.lover.app.core.design.LoverTheme
import com.lover.app.core.design.ThemeTransitionOverlay
import com.lover.app.core.notice.NoticeStore
import com.lover.app.feature.auth.AuthScreen
import com.lover.app.feature.auth.AuthViewModel
import com.lover.app.feature.main.MainScreen
import com.lover.app.feature.main.MainViewModel
import com.lover.app.feature.onboarding.OnboardingScreen
import com.lover.app.feature.onboarding.OnboardingViewModel
import com.lover.app.feature.splash.SplashScreen
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var noticeStore: NoticeStore

    override fun onCreate(savedInstanceState: Bundle?) {
        val systemSplash = installSplashScreen()
        systemSplash.setKeepOnScreenCondition { false }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val mainViewModel: MainViewModel = hiltViewModel()
            val state by mainViewModel.data.collectAsStateWithLifecycle()
            val restoreComplete by mainViewModel.restoreComplete.collectAsStateWithLifecycle()

            var revealVisible by remember { mutableStateOf(false) }
            var revealLinkId by remember { mutableStateOf<String?>(null) }

            LaunchedEffect(mainViewModel) {
                mainViewModel.coupleRevealEvent.collect { linkId ->
                    revealLinkId = linkId
                    revealVisible = true
                }
            }

            // During reveal, Lover (pink) sits under the rose curtain.
            val soloMode = when {
                state.accessToken == null -> false
                revealVisible -> false
                !state.linked -> true
                else -> false
            }

            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        mainViewModel.refreshSessionQuietly()
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            LoverTheme(soloMode = soloMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var splashDone by rememberSaveable { mutableStateOf(false) }

                    LaunchedEffect(state.accessToken) {
                        if (state.accessToken == null) {
                            noticeStore.clear()
                            revealVisible = false
                            revealLinkId = null
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        when {
                            !state.sessionLoaded ||
                                (state.accessToken != null && (!restoreComplete || !state.sessionReady)) -> {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            }
                            state.accessToken == null -> {
                                val authViewModel: AuthViewModel = hiltViewModel()
                                AuthScreen(authViewModel)
                            }
                            !state.profileCompleted -> {
                                val onboardingViewModel: OnboardingViewModel = hiltViewModel()
                                OnboardingScreen(onboardingViewModel)
                            }
                            else -> MainScreen(
                                viewModel = mainViewModel,
                                deferTogetherDatePrompt = revealVisible,
                            )
                        }

                        if (splashDone) {
                            LoverNoticeHost(
                                noticeStore = noticeStore,
                                modifier = Modifier.align(Alignment.TopCenter),
                            )
                        }

                        ThemeTransitionOverlay(
                            visible = revealVisible,
                            onComplete = {
                                val id = revealLinkId
                                if (!id.isNullOrBlank()) {
                                    mainViewModel.markCoupleThemeRevealShown(id)
                                }
                                revealVisible = false
                                revealLinkId = null
                            },
                        )

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
    }
}
