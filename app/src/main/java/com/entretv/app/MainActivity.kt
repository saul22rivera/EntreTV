package com.entretv.app

import android.Manifest
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.entretv.app.player.PipController
import com.entretv.app.ui.AppStartup
import com.entretv.app.ui.BrowseItem
import com.entretv.app.ui.DetailsScreen
import com.entretv.app.ui.DownloadsScreen
import com.entretv.app.ui.HomeScreen
import com.entretv.app.ui.LoginScreen
import com.entretv.app.ui.PlayerScreen
import com.entretv.app.ui.Session
import com.entretv.app.ui.SessionViewModel
import com.entretv.app.ui.UpdateHost
import com.entretv.app.ui.navigation.DetailsRoute
import com.entretv.app.ui.navigation.DownloadsRoute
import com.entretv.app.ui.navigation.HomeRoute
import com.entretv.app.ui.navigation.PlayerRoute
import com.entretv.app.ui.theme.EntreTVTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /** True mientras el usuario está en la pantalla de reproducción. */
    private var inPlayer by mutableStateOf(false)

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        val start = SystemClock.uptimeMillis()
        splash.setKeepOnScreenCondition {
            !AppStartup.ready.value && SystemClock.uptimeMillis() - start < AppStartup.MAX_WAIT_MS
        }
        splash.setOnExitAnimationListener { provider ->
            provider.view.animate().alpha(0f).setDuration(250L).withEndAction { provider.remove() }.start()
        }
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        enableEdgeToEdge()
        setContent {
            EntreTVTheme {
                val sessionViewModel: SessionViewModel = hiltViewModel()
                val session by sessionViewModel.session.collectAsStateWithLifecycle()
                when (session) {
                    Session.LOADING -> Unit
                    Session.LOGGED_OUT -> {
                        inPlayer = false
                        LaunchedEffect(Unit) { AppStartup.markReady() }
                        LoginScreen()
                    }
                    Session.LOGGED_IN -> AppNavHost()
                }
                UpdateHost(suppressed = inPlayer)
            }
        }
    }

    @Composable
    private fun AppNavHost() {
        val navController = rememberNavController()
        NavHost(navController = navController, startDestination = HomeRoute) {
            composable<HomeRoute> {
                inPlayer = false
                HomeScreen(
                    onOpen = { item ->
                        when (item) {
                            is BrowseItem.Live ->
                                navController.navigate(PlayerRoute(item.channel.id, "LIVE"))
                            is BrowseItem.Movie ->
                                navController.navigate(DetailsRoute(item.movie.id, "MOVIE"))
                            is BrowseItem.Show ->
                                navController.navigate(DetailsRoute(item.series.id, "SERIES"))
                        }
                    },
                    onPlayMovie = { movieId ->
                        navController.navigate(PlayerRoute(movieId, "MOVIE"))
                    },
                    onPlayEpisode = { episodeId ->
                        navController.navigate(PlayerRoute(episodeId, "EPISODE"))
                    },
                    onOpenDownloads = { navController.navigate(DownloadsRoute) }
                )
            }
            composable<DownloadsRoute> {
                inPlayer = false
                DownloadsScreen(
                    onBack = { navController.popBackStack() },
                    onPlay = { download ->
                        navController.navigate(PlayerRoute(download.contentKey, download.kind))
                    }
                )
            }
            composable<DetailsRoute> {
                inPlayer = false
                DetailsScreen(
                    onBack = { navController.popBackStack() },
                    onPlayMovie = { movieId ->
                        navController.navigate(PlayerRoute(movieId, "MOVIE"))
                    },
                    onPlayEpisode = { episodeId ->
                        navController.navigate(PlayerRoute(episodeId, "EPISODE"))
                    }
                )
            }
            composable<PlayerRoute> {
                inPlayer = true
                PlayerScreen(onBack = { navController.popBackStack() })
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (inPlayer) PipController.enter(this)
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        PipController.onModeChanged(isInPictureInPictureMode)
    }
}
