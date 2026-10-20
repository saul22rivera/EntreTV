package com.entretv.app.ui

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

/**
 * Idioma de la app (español o inglés), independiente del idioma del equipo: muchas TV
 * vienen en inglés y la app arranca en español hasta que se cambie en Configuración.
 */
object AppLanguage {
    const val DEFAULT = "es"
    private val SUPPORTED = setOf("es", "en")
    private const val PREFS = "entre_locale"
    private const val KEY = "app_language"

    fun current(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            ?.takeIf { it in SUPPORTED } ?: DEFAULT

    private fun save(context: Context, language: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, language).apply()
    }

    /** Al abrir la app. Desde Android 13 el sistema guarda el idioma y se puede cambiar en sus ajustes. */
    fun init(context: Context) {
        if (Build.VERSION.SDK_INT >= 33) {
            val manager = context.getSystemService(LocaleManager::class.java) ?: return
            val system = manager.applicationLocales
            if (system.isEmpty) manager.applicationLocales = LocaleList.forLanguageTags(current(context))
            else system[0].language.takeIf { it in SUPPORTED }?.let { save(context, it) }
        } else {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(current(context)))
        }
    }

    /** Cambia el idioma; las pantallas abiertas se vuelven a crear con los textos nuevos. */
    fun set(context: Context, language: String) {
        if (language !in SUPPORTED || language == current(context)) return
        save(context, language)
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language))
    }

    /**
     * Contexto con el idioma de la app para textos fuera de una pantalla (notificaciones,
     * servicios): antes de Android 13 el contexto de la aplicación sigue en el idioma del equipo.
     */
    fun wrap(context: Context): Context {
        val config = Configuration(context.resources.configuration)
        config.setLocale(Locale.forLanguageTag(current(context)))
        return context.createConfigurationContext(config)
    }
}
