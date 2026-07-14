package com.lover.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
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
                    when {
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
