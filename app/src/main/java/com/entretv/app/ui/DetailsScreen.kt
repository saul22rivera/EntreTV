package com.entretv.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.entretv.app.R
import com.entretv.app.data.local.DownloadEntity
import com.entretv.app.data.local.DownloadStatus
import com.entretv.app.data.local.EpisodeEntity
import com.entretv.app.data.local.PlaybackProgressEntity
import com.entretv.app.data.remote.CastMember
import com.entretv.app.data.remote.ContentDetails
import com.entretv.app.data.repo.SeriesResumeRule
import com.entretv.app.ui.theme.EntreBrand
import java.text.NumberFormat
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun DetailsScreen(
    onBack: () -> Unit,
    onPlayMovie: (String) -> Unit,
    onPlayEpisode: (String) -> Unit,
    viewModel: DetailsViewModel = hiltViewModel()
) {
    val profile = rememberDeviceProfile()
    val context = LocalContext.current
    val movie by viewModel.movie.collectAsState()
    val series by viewModel.series.collectAsState()
    val details by viewModel.details.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val isFavorite by viewModel.isFavorite.collectAsState()
    val movieProgress by viewModel.movieProgress.collectAsState()
    val episodes by viewModel.episodes.collectAsState()
    val episodeProgress by viewModel.episodeProgress.collectAsState()
    val resumeTarget by viewModel.resumeTarget.collectAsState()
    val selectedSeason by viewModel.selectedSeason.collectAsState()
    val notice by viewModel.notice.collectAsState()
    val movieDownload by viewModel.movieDownload.collectAsState()
    val episodeDownloads by viewModel.episodeDownloads.collectAsState()
    val pendingDownloads by viewModel.pendingSeriesDownloads.collectAsState()
    var confirmDelete by remember { mutableStateOf<DownloadEntity?>(null) }
    var confirmStop by remember { mutableStateOf(false) }

    val snackbar = remember { SnackbarHostState() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var plotExpanded by rememberSaveable { mutableStateOf(false) }
    val primaryFocus = remember { FocusRequester() }

    LaunchedEffect(notice) {
        notice?.let {
            snackbar.showSnackbar(it.resolve(context))
            viewModel.consumeNotice()
        }
    }

    val title = movie?.name ?: series?.name.orEmpty()
    val poster = details?.posterUrl?.ifEmpty { null } ?: movie?.icon ?: series?.cover.orEmpty()
    val backdrop = details?.backdropUrl?.ifEmpty { null } ?: poster
    val category = movie?.categoryName ?: series?.categoryName.orEmpty()
    val seasons = remember(episodes) { episodes.map { it.season }.distinct().sorted() }
    val season = selectedSeason ?: resumeTarget?.episode?.season ?: seasons.firstOrNull()
    val seasonEpisodes = remember(episodes, season) { episodes.filter { it.season == season } }
    val wide = !profile.compactRail
    val heroHeight: Dp = when {
        profile.isTv -> 440.dp
        wide -> 380.dp
        else -> 300.dp
    }

    // En TV el foco arranca en el botón principal para usar el control de inmediato.
    LaunchedEffect(title.isNotEmpty()) {
        if (profile.isTv && title.isNotEmpty()) runCatching { primaryFocus.requestFocus() }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.cancelPrewarm()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        containerColor = EntreBrand.Ink,
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            val listState = rememberLazyListState()
            val heroPx = with(LocalDensity.current) { heroHeight.toPx() }
            // TV: la portada queda fija de fondo; al bajar a la sinopsis solo se atenúa.
            if (profile.isTv) {
                val scrolled by remember {
                    derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 120 }
                }
                val scrim by animateFloatAsState(if (scrolled) 0.55f else 0f, label = "detailsScrim")
                TvBackdrop(backdrop, scrim)
            }
            // En TV Compose lleva lo enfocado a un tercio de la pantalla y la portada se iba hacia
            // arriba; aquí solo se desplaza lo necesario para que se vea lo enfocado.
            CompositionLocalProvider(
                LocalBringIntoViewSpec provides if (profile.isTv) MinimalBringIntoView else LocalBringIntoViewSpec.current
            ) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + 32.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item(key = "hero") {
                    DetailsHero(
                        title = title,
                        backdrop = if (profile.isTv) "" else backdrop,
                        details = details,
                        category = category,
                        height = heroHeight,
                        wide = wide,
                        edge = profile.edge,
                        isTv = profile.isTv
                    )
                }

                item(key = "actions") {
                    Column(
                        Modifier
                            .padding(horizontal = profile.edge)
                            .widthIn(max = 820.dp)
                    ) {
                        FlowRow(
                            // Volver a los botones regresa al inicio: la portada se ve completa.
                            modifier = Modifier.onFocusChanged {
                                if (profile.isTv && it.hasFocus && (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0)) {
                                    scope.launch { listState.scrollToTop(heroPx) }
                                }
                            },
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (viewModel.isSeries) {
                                val target = resumeTarget
                                EntreButton(
                                    text = target?.label?.asString() ?: stringResource(R.string.details_loading_episodes),
                                    icon = Icons.Default.PlayArrow,
                                    primary = true,
                                    onClick = { target?.let { onPlayEpisode(it.episode.id) } },
                                    modifier = Modifier
                                        .focusRequester(primaryFocus)
                                        .then(if (wide) Modifier else Modifier.fillMaxWidth())
                                )
                            } else {
                                val resumable = movieProgress.isResumable()
                                EntreButton(
                                    text = stringResource(if (resumable) R.string.details_resume else R.string.details_play),
                                    icon = Icons.Default.PlayArrow,
                                    primary = true,
                                    onClick = { movie?.let { onPlayMovie(it.id) } },
                                    modifier = Modifier
                                        .focusRequester(primaryFocus)
                                        .then(if (wide) Modifier else Modifier.fillMaxWidth())
                                )
                                if (resumable) {
                                    EntreButton(
                                        text = stringResource(R.string.details_play_from_start),
                                        icon = Icons.Default.Replay,
                                        onClick = {
                                            movie?.let { m -> viewModel.playFromStart(m.id) { onPlayMovie(m.id) } }
                                        }
                                    )
                                }
                            }
                            EntreButton(
                                text = stringResource(if (isFavorite) R.string.details_in_my_list else R.string.details_my_list),
                                icon = if (isFavorite) Icons.Default.Check else Icons.Default.Add,
                                onClick = viewModel::toggleFavorite
                            )
                            if (!viewModel.isSeries && movie != null) {
                                MovieDownloadButtons(
                                    download = movieDownload,
                                    onDownload = viewModel::downloadMovie,
                                    onPause = viewModel::pauseDownload,
                                    onResume = viewModel::resumeDownload,
                                    onDelete = { confirmDelete = it }
                                )
                            }
                            details?.trailerUrl?.takeIf { it.isNotEmpty() }?.let { url ->
                                val noTrailerApp = stringResource(R.string.details_trailer_no_app)
                                EntreButton(
                                    text = stringResource(R.string.details_trailer),
                                    icon = Icons.Default.Slideshow,
                                    onClick = {
                                        runCatching {
                                            context.startActivity(
                                                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            )
                                        }.onFailure {
                                            scope.launch { snackbar.showSnackbar(noTrailerApp) }
                                        }
                                    }
                                )
                            }
                        }
                        movieDownload?.takeIf { it.status == DownloadStatus.FAILED }?.let {
                            Spacer(Modifier.height(8.dp))
                            Text(it.statusLabel(), color = Color(0xFFFF8A80), style = MaterialTheme.typography.labelMedium)
                        }

                        val progressFraction: Float?
                        val remainingLabel: String?
                        if (viewModel.isSeries) {
                            progressFraction = resumeTarget?.takeIf { it.isResume }?.progress
                            remainingLabel = null
                        } else {
                            val p = movieProgress
                            progressFraction = if (p.isResumable()) p!!.positionMs.toFloat() / p.durationMs else null
                            remainingLabel = if (p.isResumable()) {
                                stringResource(R.string.details_time_left, ((p!!.durationMs - p.positionMs) / 60_000).toInt())
                            } else null
                        }
                        if (progressFraction != null) {
                            Spacer(Modifier.height(12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                LinearProgressIndicator(
                                    progress = { progressFraction.coerceIn(0f, 1f) },
                                    color = EntreBrand.Accent,
                                    trackColor = Color.White.copy(alpha = 0.2f),
                                    modifier = Modifier.width(220.dp).height(4.dp).clip(RoundedCornerShape(2.dp))
                                )
                                remainingLabel?.let {
                                    Spacer(Modifier.width(10.dp))
                                    Text(it, style = MaterialTheme.typography.labelMedium, color = EntreBrand.TextDim)
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                        details?.tagline?.takeIf { it.isNotEmpty() }?.let {
                            Text(
                                "“$it”",
                                style = if (profile.isTv) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall,
                                fontStyle = FontStyle.Italic,
                                color = EntreBrand.TextDim,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                        val plot = details?.plot.orEmpty()
                        val plotStyle = if (profile.isTv) MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp, fontWeight = FontWeight.Normal, lineHeight = 27.sp)
                        else MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, lineHeight = 25.sp)
                        when {
                            plot.isNotEmpty() -> Text(
                                plot,
                                style = plotStyle,
                                color = EntreBrand.Text,
                                maxLines = if (plotExpanded) Int.MAX_VALUE else 6,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.clickable { plotExpanded = !plotExpanded }
                            )
                            loading -> Text(stringResource(R.string.details_loading_plot), color = EntreBrand.TextDim, style = plotStyle)
                            else -> Text(stringResource(R.string.details_no_plot), color = EntreBrand.TextDim, style = plotStyle)
                        }
                        val creditStyle = if (profile.isTv) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge
                        if (details?.castMembers.isNullOrEmpty()) {
                            details?.cast?.takeIf { it.isNotEmpty() }?.let {
                                Spacer(Modifier.height(12.dp))
                                CreditLine(stringResource(R.string.details_cast), it, creditStyle)
                            }
                        }
                        details?.director?.takeIf { it.isNotEmpty() }?.let {
                            Spacer(Modifier.height(6.dp))
                            CreditLine(stringResource(R.string.details_director), it, creditStyle)
                        }
                        details?.creators?.takeIf { it.isNotEmpty() }?.let {
                            Spacer(Modifier.height(6.dp))
                            CreditLine(stringResource(R.string.details_created_by), it, creditStyle)
                        }
                        // Series: las descargas van entre la sinopsis y la lista de episodios.
                        if (viewModel.isSeries && season != null && seasonEpisodes.isNotEmpty()) {
                            Spacer(Modifier.height(16.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                EntreButton(
                                    text = stringResource(R.string.details_download_season, season),
                                    icon = Icons.Default.Download,
                                    compact = true,
                                    onClick = { viewModel.downloadSeason(season) }
                                )
                                if (pendingDownloads > 0) {
                                    EntreButton(
                                        text = stringResource(R.string.details_stop_downloads_count, pendingDownloads),
                                        icon = Icons.Default.StopCircle,
                                        compact = true,
                                        onClick = { confirmStop = true }
                                    )
                                }
                            }
                        }
                    }
                }

                details?.let { d ->
                    if (d.hasExtraInfo()) {
                        item(key = "info") {
                            InfoCard(d, viewModel.isSeries, profile, Modifier.padding(horizontal = profile.edge, vertical = 16.dp))
                        }
                    }
                    if (d.castMembers.isNotEmpty()) {
                        item(key = "cast") {
                            CastRow(d.castMembers, profile)
                        }
                    }
                }

                if (viewModel.isSeries) {
                    item(key = "seasons") {
                        Column(Modifier.padding(top = 24.dp)) {
                            Text(
                                stringResource(R.string.details_episodes),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = profile.edge)
                            )
                            if (seasons.size > 1) {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = profile.edge, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(seasons, key = { it }) { s ->
                                        EntreButton(
                                            text = stringResource(R.string.details_season, s),
                                            icon = null,
                                            primary = s == season,
                                            compact = true,
                                            onClick = { viewModel.selectSeason(s) }
                                        )
                                    }
                                }
                            } else {
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                    if (episodes.isEmpty()) {
                        item(key = "episodes-loading") {
                            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                if (loading) CircularProgressIndicator(color = EntreBrand.Accent)
                                else Text(stringResource(R.string.details_no_episodes), color = EntreBrand.TextDim)
                            }
                        }
                    }
                    items(seasonEpisodes, key = { it.id }) { ep ->
                        EpisodeRow(
                            episode = ep,
                            fallbackImage = poster,
                            progress = episodeProgress[ep.id],
                            wide = wide,
                            edge = profile.edge,
                            onClick = { onPlayEpisode(ep.id) },
                            trailing = {
                                EpisodeDownloadIcon(
                                    download = episodeDownloads[ep.id],
                                    onDownload = { viewModel.downloadEpisode(ep) },
                                    onPause = viewModel::pauseDownload,
                                    onResume = viewModel::resumeDownload,
                                    onDelete = { confirmDelete = it }
                                )
                            },
                            downloadLabel = episodeDownloads[ep.id]?.statusLabel()
                        )
                    }
                    if (episodes.isNotEmpty()) {
                        item(key = "reset") {
                            TextButton(
                                onClick = viewModel::resetSeries,
                                modifier = Modifier.padding(horizontal = profile.edge - 8.dp, vertical = 8.dp)
                            ) {
                                Text(stringResource(R.string.details_reset_series), color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
            }

            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(8.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.details_back), tint = Color.White)
            }
        }
    }

    confirmDelete?.let { download ->
        ConfirmDeleteDownloadDialog(
            download = download,
            onConfirm = {
                viewModel.deleteDownload(download.contentKey)
                confirmDelete = null
            },
            onDismiss = { confirmDelete = null }
        )
    }

    if (confirmStop) {
        ConfirmActionDialog(
            title = stringResource(R.string.details_stop_downloads_title),
            text = pluralStringResource(R.plurals.details_stop_downloads_text, pendingDownloads, pendingDownloads, title),
            confirmLabel = stringResource(R.string.details_stop_downloads),
            dismissLabel = stringResource(R.string.download_keep_downloading),
            onConfirm = {
                confirmStop = false
                viewModel.stopSeriesDownloads()
            },
            onDismiss = { confirmStop = false }
        )
    }
}

private fun ContentDetails.hasExtraInfo(): Boolean =
    releaseDate.isNotEmpty() || originalTitle.isNotEmpty() || originalLanguage.isNotEmpty() ||
        countries.isNotEmpty() || companies.isNotEmpty() || certification.isNotEmpty() || voteCount > 0 ||
        budget > 0 || revenue > 0 || seasons > 0 || networks.isNotEmpty() || status.isNotEmpty() || genre.isNotEmpty()

private fun money(value: Long): String =
    "US$ " + NumberFormat.getIntegerInstance(Locale("es", "MX")).format(value)

@Composable
private fun releaseLabel(raw: String): String {
    val parts = raw.take(10).split('-')
    if (parts.size != 3) return raw
    val months = stringArrayResource(R.array.details_months_short)
    val month = parts[1].toIntOrNull()?.let { months.getOrNull(it - 1) } ?: return raw
    return stringResource(R.string.details_date, parts[2].trimStart('0'), month, parts[0])
}

/** El estado se guarda en la ficha ya traducido al español (MetadataRepository.status). */
private val statusLabels = mapOf(
    "En emisión" to R.string.meta_status_returning,
    "Finalizada" to R.string.meta_status_ended,
    "Cancelada" to R.string.meta_status_canceled,
    "En producción" to R.string.meta_status_in_production,
    "Anunciada" to R.string.meta_status_planned,
    "Piloto" to R.string.meta_status_pilot,
    "Estrenada" to R.string.meta_status_released,
    "Posproducción" to R.string.meta_status_post_production,
    "Rumor" to R.string.meta_status_rumored
)

@Composable
private fun contentStatusLabel(status: String): String = statusLabels[status]?.let { stringResource(it) } ?: status

/** El idioma original se guarda con su nombre en español (MetadataRepository.languageName). */
@Composable
private fun originalLanguageLabel(stored: String): String {
    val locale = LocalConfiguration.current.locales[0]
    return remember(stored, locale) {
        if (locale.language == "es") return@remember stored
        val spanish = Locale("es", "MX")
        Locale.getISOLanguages()
            .firstOrNull { Locale(it).getDisplayLanguage(spanish).equals(stored, ignoreCase = true) }
            ?.let { code -> Locale(code).getDisplayLanguage(locale).replaceFirstChar { it.titlecase(locale) } }
            ?: stored
    }
}

/** Ficha técnica (TMDB y servidor). Enfocable para que en TV el D-pad pueda pasar por ella. */
@Composable
private fun InfoCard(d: ContentDetails, isSeries: Boolean, profile: DeviceProfile, modifier: Modifier) {
    val rows = buildList {
        if (d.releaseDate.isNotEmpty()) {
            add(stringResource(if (isSeries) R.string.details_premiere else R.string.details_release_date) to releaseLabel(d.releaseDate))
        } else if (d.year.isNotEmpty()) add(stringResource(R.string.details_year) to d.year)
        if (d.durationLabel.isNotEmpty()) {
            add(stringResource(if (isSeries) R.string.details_episode_duration else R.string.details_duration) to d.durationLabel)
        }
        if (d.genre.isNotEmpty()) add(stringResource(R.string.details_genres) to d.genre)
        if (d.rating.isNotEmpty()) {
            val votes = NumberFormat.getIntegerInstance(Locale("es", "MX")).format(d.voteCount)
            add(
                stringResource(R.string.details_rating) to
                    if (d.voteCount > 0) pluralStringResource(R.plurals.details_rating_votes, d.voteCount, d.rating, votes)
                    else "★ ${d.rating}"
            )
        }
        if (d.certification.isNotEmpty()) add(stringResource(R.string.details_certification) to d.certification)
        if (isSeries) {
            if (d.seasons > 0) add(stringResource(R.string.details_seasons) to d.seasons.toString())
            if (d.episodes > 0) add(stringResource(R.string.details_episodes) to d.episodes.toString())
            if (d.networks.isNotEmpty()) add(stringResource(R.string.details_network) to d.networks)
            if (d.status.isNotEmpty()) add(stringResource(R.string.details_status) to contentStatusLabel(d.status))
        } else if (d.status.isNotEmpty() && d.status != "Estrenada") {
            add(stringResource(R.string.details_status) to contentStatusLabel(d.status))
        }
        if (d.originalTitle.isNotEmpty()) add(stringResource(R.string.details_original_title) to d.originalTitle)
        if (d.originalLanguage.isNotEmpty()) {
            add(stringResource(R.string.details_original_language) to originalLanguageLabel(d.originalLanguage))
        }
        if (d.countries.isNotEmpty()) add(stringResource(R.string.details_country) to d.countries)
        if (d.companies.isNotEmpty()) add(stringResource(R.string.details_production) to d.companies)
        if (d.budget > 0) add(stringResource(R.string.details_budget) to money(d.budget))
        if (d.revenue > 0) add(stringResource(R.string.details_revenue) to money(d.revenue))
    }
    if (rows.isEmpty()) return
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val shape = RoundedCornerShape(16.dp)
    val labelStyle = if (profile.isTv) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium
    val valueStyle = if (profile.isTv) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge
    Column(
        modifier
            .widthIn(max = 820.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(EntreBrand.Panel)
            .then(if (focused) Modifier.border(2.dp, EntreBrand.Accent, shape) else Modifier)
            .focusable(interactionSource = source)
            .padding(16.dp)
    ) {
        Text(stringResource(R.string.details_info_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
        rows.forEach { (label, value) ->
            Row(Modifier.padding(vertical = 3.dp)) {
                Text(label, style = labelStyle, color = EntreBrand.TextDim, modifier = Modifier.width(if (profile.isTv) 220.dp else 140.dp))
                Text(value, style = valueStyle, color = EntreBrand.Text, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun CastRow(cast: List<CastMember>, profile: DeviceProfile) {
    Column(Modifier.padding(top = 8.dp)) {
        Text(
            stringResource(R.string.details_cast),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = profile.edge)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = profile.edge, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(cast, key = { it.name + it.character }) { member ->
                CastCard(member, size = if (profile.isTv) 104.dp else 84.dp)
            }
        }
    }
}

@Composable
private fun CastCard(member: CastMember, size: Dp) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(size + 20.dp)
            .focusable(interactionSource = source)
    ) {
        Box(
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(EntreBrand.PanelHigh)
                .then(if (focused) Modifier.border(3.dp, EntreBrand.Accent, CircleShape) else Modifier),
            contentAlignment = Alignment.Center
        ) {
            if (member.photoUrl.isNotEmpty()) {
                AsyncImage(
                    model = member.photoUrl,
                    contentDescription = member.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    member.name.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1) },
                    style = MaterialTheme.typography.titleLarge,
                    color = EntreBrand.TextDim
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            member.name,
            style = MaterialTheme.typography.labelLarge,
            color = if (focused) Color.White else EntreBrand.Text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        if (member.character.isNotEmpty()) {
            Text(
                member.character,
                style = MaterialTheme.typography.labelSmall,
                color = EntreBrand.TextDim,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun PlaybackProgressEntity?.isResumable(): Boolean =
    this != null && positionMs > 30_000 && durationMs > 0 && !isCompleted

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private object MinimalBringIntoView : BringIntoViewSpec

/**
 * Regresa al inicio (portada completa) con prioridad sobre el desplazamiento que el foco lanza
 * para mostrar el botón; si no, ese desplazamiento cancela este y la vista queda a medias.
 * Solo el hero (alto fijo [heroPx]) puede quedar arriba de los botones.
 */
private suspend fun LazyListState.scrollToTop(heroPx: Float) {
    val distance = firstVisibleItemScrollOffset + if (firstVisibleItemIndex > 0) heroPx else 0f
    scroll(MutatePriority.PreventUserInput) {
        var done = 0f
        animate(0f, distance) { value, _ ->
            scrollBy(done - value)
            done = value
        }
        if (firstVisibleItemIndex == 0) scrollBy(-firstVisibleItemScrollOffset.toFloat())
    }
}

/** Portada de TV a pantalla completa detrás del contenido; [scrim] la oscurece al bajar. */
@Composable
private fun TvBackdrop(backdrop: String, scrim: Float) {
    Box(Modifier.fillMaxSize()) {
        if (backdrop.isNotEmpty()) {
            AsyncImage(
                model = backdrop,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.35f),
                        0.4f to Color.Transparent,
                        0.75f to EntreBrand.Ink.copy(alpha = 0.85f),
                        1f to EntreBrand.Ink
                    )
                )
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to EntreBrand.Ink.copy(alpha = 0.9f),
                        0.6f to Color.Transparent
                    )
                )
        )
        if (scrim > 0f) Box(Modifier.fillMaxSize().background(EntreBrand.Ink.copy(alpha = scrim)))
    }
}

@Composable
private fun DetailsHero(
    title: String,
    backdrop: String,
    details: ContentDetails?,
    category: String,
    height: Dp,
    wide: Boolean,
    edge: Dp,
    isTv: Boolean
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .then(if (isTv) Modifier else Modifier.background(EntreBrand.Panel))
    ) {
        if (backdrop.isNotEmpty()) {
            AsyncImage(
                model = backdrop,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        if (!isTv) Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.35f),
                        0.45f to Color.Transparent,
                        1f to EntreBrand.Ink
                    )
                )
        )
        if (wide && !isTv) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            0f to EntreBrand.Ink.copy(alpha = 0.9f),
                            0.55f to Color.Transparent
                        )
                    )
            )
        }
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = edge, vertical = 16.dp)
                .widthIn(max = 720.dp)
        ) {
            Text(
                title,
                style = when {
                    isTv -> MaterialTheme.typography.displaySmall
                    wide -> MaterialTheme.typography.headlineLarge
                    else -> MaterialTheme.typography.headlineMedium
                },
                fontWeight = FontWeight.Black,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(8.dp))
            MetaLine(details, category)
        }
    }
}

@Composable
private fun MetaLine(details: ContentDetails?, category: String) {
    val parts = buildList {
        details?.year?.takeIf { it.isNotEmpty() }?.let { add(it) }
        details?.durationLabel?.takeIf { it.isNotEmpty() }?.let { add(it) }
        (details?.genre?.takeIf { it.isNotEmpty() } ?: category.takeIf { it.isNotEmpty() })?.let { add(it) }
    }
    val rating = details?.rating.orEmpty()
    if (parts.isEmpty() && rating.isEmpty()) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (rating.isNotEmpty()) {
            Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFC107), modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(rating, color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            if (parts.isNotEmpty()) Text("  ·  ", color = EntreBrand.TextDim)
        }
        Text(
            parts.joinToString("  ·  "),
            color = EntreBrand.TextDim,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CreditLine(label: String, value: String, style: TextStyle) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = EntreBrand.TextDim)) { append("$label: ") }
            withStyle(SpanStyle(color = EntreBrand.Text)) { append(value) }
        },
        style = style,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis
    )
}

private val WatchedRed = Color(0xFFE53935)

@Composable
private fun EpisodeRow(
    episode: EpisodeEntity,
    fallbackImage: String,
    progress: PlaybackProgressEntity?,
    wide: Boolean,
    edge: Dp,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit,
    downloadLabel: String?
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) 1.02f else 1f, label = "episodeScale")
    val shape = RoundedCornerShape(12.dp)
    val fraction = progress?.let { if (it.isCompleted) 1f else if (it.durationMs > 0) it.positionMs.toFloat() / it.durationMs else 0f }
    val watched = progress != null && (progress.isCompleted || progress.positionMs >= SeriesResumeRule.MIN_WATCH_MS)
    val tint = when {
        progress?.isCompleted == true -> WatchedRed.copy(alpha = 0.30f)
        watched -> WatchedRed.copy(alpha = 0.16f)
        else -> Color.Transparent
    }

    // La fila y el botón de descarga son dos focos separados: en TV, derecha pasa de uno al otro.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = edge - 6.dp, vertical = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(shape)
                .background(tint)
                .background(if (focused) Color.White.copy(alpha = 0.10f) else Color.Transparent)
                .then(if (focused) Modifier.border(2.dp, EntreBrand.Accent, shape) else Modifier)
                .clickable(interactionSource = source, indication = LocalIndication.current, onClick = onClick)
                .padding(6.dp)
        ) {
            Box(
                Modifier
                    .width(if (wide) 220.dp else 136.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(EntreBrand.PanelHigh),
                contentAlignment = Alignment.Center
            ) {
                val image = episode.imageUrl.ifEmpty { fallbackImage }
                if (image.isNotEmpty()) {
                    AsyncImage(
                        model = image,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                }
                if (fraction != null && fraction > 0f) {
                    Text(
                        if (progress?.isCompleted == true) stringResource(R.string.details_watched)
                        else stringResource(R.string.download_percent, (fraction * 100).toInt().coerceIn(1, 99)),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (progress?.isCompleted == true) WatchedRed.copy(alpha = 0.85f) else Color.Black.copy(alpha = 0.7f))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                    LinearProgressIndicator(
                        progress = { fraction.coerceIn(0f, 1f) },
                        color = EntreBrand.Accent,
                        trackColor = Color.White.copy(alpha = 0.25f),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(4.dp)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "${episode.episodeNum}. ${episode.title}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (episode.durationSecs > 0) {
                    Text(
                        stringResource(R.string.details_minutes, episode.durationSecs / 60),
                        style = MaterialTheme.typography.labelSmall,
                        color = EntreBrand.TextDim
                    )
                }
                downloadLabel?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = EntreBrand.Accent, maxLines = 1)
                }
                if (episode.plot.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        episode.plot,
                        style = MaterialTheme.typography.bodySmall,
                        color = EntreBrand.TextDim,
                        maxLines = if (wide) 3 else 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        Spacer(Modifier.width(6.dp))
        trailing()
    }
}
