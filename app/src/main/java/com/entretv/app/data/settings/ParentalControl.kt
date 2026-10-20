package com.entretv.app.data.settings

import com.entretv.app.data.local.CategoryPrefEntity
import java.security.MessageDigest
import java.text.Normalizer
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * PIN parental. Con PIN puesto, las categorías de adultos se ocultan hasta que se
 * desbloquean; el desbloqueo dura hasta cerrar la app.
 */
@Singleton
class ParentalControl @Inject constructor(
    private val settings: SettingsRepository
) {
    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked

    val pinSet: Flow<Boolean> = settings.pinHash.map { it.isNotEmpty() }

    /** True si el contenido adulto debe quedar oculto. */
    val locked: Flow<Boolean> = combine(pinSet, _unlocked) { set, open -> set && !open }

    suspend fun unlock(pin: String): Boolean {
        val ok = checkPin(pin)
        if (ok) _unlocked.value = true
        return ok
    }

    suspend fun checkPin(pin: String): Boolean {
        val stored = settings.pinHash.first()
        return stored.isEmpty() || stored == hash(pin)
    }

    fun lock() {
        _unlocked.value = false
    }

    suspend fun setPin(pin: String) {
        settings.setPinHash(hash(pin))
        _unlocked.value = false
    }

    suspend fun clearPin() {
        settings.setPinHash("")
        _unlocked.value = false
    }

    companion object {
        const val PIN_LENGTH = 4

        private val accents = Regex("\\p{Mn}+")
        private val adultWords = Regex(
            "\\b(adult|adults|adulto|adultos|xxx|porn\\w*|erotic\\w*|erotico\\w*|hentai|playboy|hot club)\\b|\\+\\s?18|\\b18\\s?\\+"
        )

        fun looksAdult(name: String): Boolean {
            val normalized = accents.replace(Normalizer.normalize(name.lowercase(), Normalizer.Form.NFD), "")
            return adultWords.containsMatchIn(normalized)
        }

        /** Lo marcado a mano manda; si no, se decide por el nombre. */
        fun isAdult(name: String, pref: CategoryPrefEntity?): Boolean = pref?.adult ?: looksAdult(name)

        private fun hash(pin: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest("entretv:$pin".toByteArray())
                .joinToString("") { "%02x".format(it) }
    }
}
