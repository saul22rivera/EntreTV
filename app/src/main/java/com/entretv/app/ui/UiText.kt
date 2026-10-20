package com.entretv.app.ui

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Texto para mostrar que sale de un ViewModel, repositorio o del reproductor. Se guarda el
 * id del recurso y no el texto: así sigue el idioma de la app aunque cambie con la pantalla
 * abierta (y el contexto de la aplicación no siempre está en ese idioma antes de Android 13).
 * Los argumentos pueden ser otros [UiText].
 */
sealed interface UiText {
    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText
    data class Plural(@PluralsRes val id: Int, val count: Int, val args: List<Any> = emptyList()) : UiText
    /** Texto que viene tal cual (nombre de una categoría, mensaje del servidor). */
    data class Raw(val text: String) : UiText

    fun resolve(context: Context): String = when (this) {
        is Res -> context.getString(id, *resolveArgs(context, args))
        is Plural -> context.resources.getQuantityString(id, count, *resolveArgs(context, args))
        is Raw -> text
    }

    private fun resolveArgs(context: Context, args: List<Any>): Array<Any> =
        args.map { if (it is UiText) it.resolve(context) else it }.toTypedArray()
}

fun uiText(@StringRes id: Int, vararg args: Any): UiText = UiText.Res(id, args.toList())

fun uiPlural(@PluralsRes id: Int, count: Int, vararg args: Any): UiText = UiText.Plural(id, count, args.toList())

@Composable
fun UiText.asString(): String = resolve(LocalContext.current)
