package com.entretv.app.data.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.entretv.app.BuildConfig
import com.entretv.app.R
import com.entretv.app.data.repo.PlaylistRepository
import com.entretv.app.data.settings.SettingsRepository
import com.entretv.app.ui.UiText
import com.entretv.app.ui.uiText
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Contenido de `update.json` publicado junto al APK en GitHub Releases.
 * Los campos nuevos tienen valor por defecto: las versiones viejas los ignoran y
 * un update.json viejo sigue funcionando en esta versión.
 */
@Serializable
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val notes: String = "",
    /** Las instalaciones con versionCode menor quedan bloqueadas hasta actualizar. */
    val minVersionCode: Int = 0,
    /** Esta versión es obligatoria para todos los que tengan una anterior. */
    val mandatory: Boolean = false,
    /** Servidor IPTV nuevo; vacío = no cambia. */
    val serverUrl: String = "",
    /** Clave de TMDB para fichas y Top 10; vacío = no cambia. */
    val tmdbApiKey: String = ""
) {
    val isRequired: Boolean
        get() = versionCode > BuildConfig.VERSION_CODE &&
            (mandatory || minVersionCode > BuildConfig.VERSION_CODE)
}

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val progress: Float?) : UpdateState
    data class ReadyToInstall(val info: UpdateInfo, val needsPermission: Boolean) : UpdateState
    data class Failed(val message: UiText, val info: UpdateInfo? = null) : UpdateState
}

val UpdateState.isRequired: Boolean
    get() = when (this) {
        is UpdateState.Available -> info.isRequired
        is UpdateState.Downloading -> info.isRequired
        is UpdateState.ReadyToInstall -> info.isRequired
        is UpdateState.Failed -> info?.isRequired == true
        else -> false
    }

@Singleton
class UpdateRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    client: OkHttpClient,
    private val json: Json,
    private val playlists: PlaylistRepository,
    private val settings: SettingsRepository
) {
    companion object {
        private const val PREFS = "entre_update"
        private const val KEY_LAST_CHECK = "last_check"
    }

    private val http = client.newBuilder()
        .cache(null)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs by lazy { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    private val apkFile get() = File(File(context.cacheDir, "updates").apply { mkdirs() }, "EntreTV-update.apk")

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** El diálogo solo se muestra solo si la búsqueda fue manual o hay una versión nueva. */
    private val _manual = MutableStateFlow(false)
    val manual: StateFlow<Boolean> = _manual.asStateFlow()

    private var downloadJob: Job? = null

    val isConfigured: Boolean get() = BuildConfig.UPDATE_URL.isNotBlank()
    val currentVersionName: String get() = BuildConfig.VERSION_NAME

    /** Sin [manual] se consulta en silencio en cada inicio: solo aparece algo si hay versión nueva. */
    fun check(manual: Boolean) {
        val current = _state.value
        if (current is UpdateState.Checking || current is UpdateState.Downloading) return
        if (!manual && current.isRequired) return
        if (!manual && !isConfigured) return
        _manual.value = manual
        if (!isConfigured) {
            _state.value = UpdateState.Failed(uiText(R.string.update_error_not_configured))
            return
        }
        _state.value = if (manual) UpdateState.Checking else UpdateState.Idle
        scope.launch {
            val result = runCatching { fetchInfo() }
            prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
            val info = result.getOrNull()
            info?.let { applyRemoteConfig(it) }
            _state.value = when {
                info == null -> if (manual) UpdateState.Failed(uiText(R.string.update_error_check)) else UpdateState.Idle
                info.versionCode > BuildConfig.VERSION_CODE -> UpdateState.Available(info)
                manual -> UpdateState.UpToDate
                else -> UpdateState.Idle
            }
            // Obligatoria: se baja sola; el diálogo bloquea la app mientras tanto.
            if (info != null && info.isRequired) download(info)
        }
    }

    /** Servidor y clave de TMDB remotos: se aplican aunque no haya versión nueva. */
    private suspend fun applyRemoteConfig(info: UpdateInfo) {
        runCatching {
            val server = info.serverUrl.trim()
            if (server.startsWith("http") && server != settings.serverUrl.first()) {
                playlists.applyServerOverride(server)
            }
            val key = info.tmdbApiKey.trim()
            if (key.isNotEmpty()) settings.setRemoteTmdbKey(key)
        }
    }

    private fun fetchInfo(): UpdateInfo {
        val request = Request.Builder().url(BuildConfig.UPDATE_URL).header("Cache-Control", "no-cache").build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            return json.decodeFromString(UpdateInfo.serializer(), response.body!!.string())
        }
    }

    fun download(info: UpdateInfo) {
        if (downloadJob?.isActive == true) return
        _manual.value = true
        _state.value = UpdateState.Downloading(info, null)
        downloadJob = scope.launch {
            val ok = runCatching { downloadApk(info) }.getOrElse { false }
            _state.value = if (ok) {
                UpdateState.ReadyToInstall(info, needsPermission = !canInstall())
            } else {
                UpdateState.Failed(uiText(R.string.update_error_download), info)
            }
            // En una actualización obligatoria el instalador se abre solo.
            if (ok && info.isRequired) withContext(Dispatchers.Main) { install() }
        }
    }

    private suspend fun downloadApk(info: UpdateInfo): Boolean = withContext(Dispatchers.IO) {
        val target = apkFile
        val partial = File(target.parentFile, target.name + ".part")
        http.newCall(Request.Builder().url(info.apkUrl).build()).execute().use { response ->
            if (!response.isSuccessful) return@withContext false
            val body = response.body ?: return@withContext false
            val total = body.contentLength().takeIf { it > 0 }
            var read = 0L
            body.byteStream().use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var lastEmit = 0L
                    while (isActive) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        read += n
                        if (total != null && read - lastEmit > 256 * 1024) {
                            lastEmit = read
                            _state.value = UpdateState.Downloading(info, read.toFloat() / total)
                        }
                    }
                }
            }
        }
        if (!isValidUpdate(partial)) {
            partial.delete()
            return@withContext false
        }
        target.delete()
        partial.renameTo(target)
    }

    /** Evita instalar un APK ajeno o más viejo que el instalado. */
    @Suppress("DEPRECATION")
    private fun isValidUpdate(file: File): Boolean {
        val info = context.packageManager.getPackageArchiveInfo(file.path, 0) ?: return false
        val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        return info.packageName == context.packageName && code > BuildConfig.VERSION_CODE
    }

    private fun canInstall(): Boolean =
        Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()

    /**
     * Abre el instalador de Android. Si falta el permiso de "fuentes desconocidas"
     * abre esa pantalla primero; devuelve false si el equipo no la tiene.
     */
    fun install(): Boolean {
        val current = _state.value as? UpdateState.ReadyToInstall ?: return false
        if (!canInstall()) {
            // Muchas TV no tienen la pantalla por app: se intenta la de seguridad y luego Ajustes.
            val opened = listOf(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")),
                Intent(Settings.ACTION_SECURITY_SETTINGS),
                Intent(Settings.ACTION_SETTINGS)
            ).any { intent ->
                runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
            }
            _state.value = current.copy(needsPermission = true)
            return opened
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apkFile)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            _state.value = current.copy(needsPermission = false)
            true
        } catch (_: ActivityNotFoundException) {
            _state.value = UpdateState.Failed(uiText(R.string.update_error_no_installer), current.info)
            false
        }
    }

    /** Al volver de la pantalla de permisos, refresca si ya se puede instalar. */
    fun refreshPermission() {
        val current = _state.value as? UpdateState.ReadyToInstall ?: return
        _state.value = current.copy(needsPermission = !canInstall())
    }

    fun dismiss() {
        if (_state.value.isRequired) return
        downloadJob?.cancel()
        _manual.value = false
        _state.value = UpdateState.Idle
    }
}
