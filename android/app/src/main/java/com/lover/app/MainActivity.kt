package com.lover.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lover.app.core.design.LoverTheme
import com.lover.app.feature.auth.AuthScreen
import com.lover.app.feature.auth.AuthViewModel
import com.lover.app.feature.couple.CoupleScreen
import com.lover.app.feature.couple.CoupleViewModel
import com.lover.app.feature.main.MainScreen
import com.lover.app.feature.main.MainViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LoverTheme {
                Surface(Modifier.fillMaxSize()) {
                    val mainViewModel: MainViewModel = hiltViewModel()
                    val state by mainViewModel.data.collectAsStateWithLifecycle()
                    val restoreComplete by mainViewModel.restoreComplete.collectAsStateWithLifecycle()
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
                        state.couple == null -> {
                            val coupleViewModel: CoupleViewModel = hiltViewModel()
                            CoupleScreen(coupleViewModel)
                        }
                        else -> MainScreen(mainViewModel)
                    }
                }
            }
        }
    }
}
