package com.entretv.app

import android.Manifest
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.entretv.app.data.profile.ProfileSession
import com.entretv.app.player.PipController
import com.entretv.app.tv.TvDeepLink
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
import com.entretv.app.ui.guide.GuideScreen
import com.entretv.app.ui.navigation.DetailsRoute
import com.entretv.app.ui.navigation.DownloadsRoute
import com.entretv.app.ui.navigation.GuideRoute
import com.entretv.app.ui.navigation.HomeRoute
import com.entretv.app.ui.navigation.PlayerRoute
import com.entretv.app.ui.profile.ProfileActions
import com.entretv.app.ui.profile.ProfileMenu
import com.entretv.app.ui.profile.ProfileViewModel
import com.entretv.app.ui.profile.ProfilesHost
import com.entretv.app.ui.theme.EntreBrand
import com.entretv.app.ui.theme.EntreTVTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var profileSession: ProfileSession

    /** True mientras el usuario está en la pantalla de reproducción. */
    private var inPlayer by mutableStateOf(false)

    /** Tarjeta elegida en el inicio de la TV; se abre apenas hay sesión. */
    private var pendingLink by mutableStateOf<TvDeepLink?>(null)

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
        // Con estado guardado es la misma apertura (rotación, cambio de idioma): no se vuelve a preguntar.
        if (savedInstanceState == null) {
            profileSession.newLaunch()
            pendingLink = TvDeepLink.parse(intent)
        }
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
                        // Sin sesión la tarjeta del inicio solo abre la app.
                        LaunchedEffect(pendingLink) {
                            pendingLink = null
                            AppStartup.markReady()
                        }
                        LoginScreen()
                    }
                    Session.LOGGED_IN -> {
                        val profiles: ProfileViewModel = hiltViewModel()
                        val profileUi by profiles.ui.collectAsStateWithLifecycle()
                        // Al abrir, Inicio espera a que se elija el perfil: no carga nada ni abre
                        // la tarjeta del inicio de la TV antes de tiempo (se abre después de elegir).
                        if (profileUi.startup) {
                            LaunchedEffect(Unit) { AppStartup.markReady() }
                            Box(Modifier.fillMaxSize().background(EntreBrand.Ink))
                        } else {
                            AppNavHost(profiles)
                        }
                        ProfilesHost(profiles, onExitApp = ::finish)
                    }
                }
                UpdateHost(suppressed = inPlayer)
            }
        }
    }

    @Composable
    private fun AppNavHost(profiles: ProfileViewModel) {
        val navController = rememberNavController()
        val link = pendingLink
        LaunchedEffect(link) {
            if (link == null) return@LaunchedEffect
            pendingLink = null
            AppStartup.markReady()
            if (!profiles.allowLink(link)) return@LaunchedEffect
            val route: Any = if (link.play) PlayerRoute(link.id, link.kind) else DetailsRoute(link.id, link.kind)
            // Inicio queda debajo: Atrás vuelve ahí y no a lo que estaba abierto antes.
            navController.navigate(route) {
                popUpTo<HomeRoute>()
                launchSingleTop = true
            }
        }
        val profileName by profiles.activeName.collectAsStateWithLifecycle()
        val profileAvatar by profiles.activeAvatar.collectAsStateWithLifecycle()
        val profileMenu = ProfileMenu(profileName, profileAvatar)
        val profileActions = remember(profiles) {
            ProfileActions(
                onSwitch = profiles::openPicker,
                onManage = profiles::openManage,
                guardAdult = profiles::guardAdult
            )
        }
        NavHost(navController = navController, startDestination = HomeRoute) {
            composable<HomeRoute> {
                inPlayer = false
                HomeScreen(
                    profileMenu = profileMenu,
                    profileActions = profileActions,
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
                    onOpenDownloads = { navController.navigate(DownloadsRoute) },
                    onPlayLiveFullscreen = { channelId ->
                        navController.navigate(PlayerRoute(channelId, "LIVE", keepPlaying = true))
                    },
                    onOpenGuide = { playlistId, category ->
                        navController.navigate(GuideRoute(playlistId, category)) { launchSingleTop = true }
                    }
                )
            }
            composable<GuideRoute> { entry ->
                inPlayer = false
                GuideScreen(
                    onBack = {
                        // Un Atrás repetido durante la transición no debe sacar también el inicio.
                        if (entry.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) navController.popBackStack()
                    },
                    onPlayLive = { channel ->
                        navController.navigate(PlayerRoute(channel.id, "LIVE"))
                    },
                    onPlayCatchUp = { channel, program ->
                        navController.navigate(
                            PlayerRoute(channel.id, "CATCHUP", startSec = program.start, endSec = program.end, title = program.title)
                        )
                    }
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        TvDeepLink.parse(intent)?.let { pendingLink = it }
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
