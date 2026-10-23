package com.entretv.app.data.profile

import androidx.room.withTransaction
import com.entretv.app.data.local.AppDatabase
import com.entretv.app.data.local.DEFAULT_PROFILE_ID
import com.entretv.app.data.local.FavoriteDao
import com.entretv.app.data.local.ProfileDao
import com.entretv.app.data.local.ProfileEntity
import com.entretv.app.data.local.ProgressDao
import com.entretv.app.data.settings.ParentalControl
import com.entretv.app.data.settings.SettingsRepository
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.experimental.xor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext

/** Resultado de comprobar un PIN de perfil. */
sealed interface PinCheck {
    data object Ok : PinCheck
    data class Wrong(val attemptsLeft: Int) : PinCheck
    data class Locked(val seconds: Int) : PinCheck
}

/** Qué hacer con el PIN al guardar un perfil. */
sealed interface PinChange {
    data object Keep : PinChange
    data object Remove : PinChange
    data class Set(val pin: String) : PinChange
}

@Singleton
class ProfileRepository @Inject constructor(
    private val database: AppDatabase,
    private val dao: ProfileDao,
    private val progressDao: ProgressDao,
    private val favoriteDao: FavoriteDao,
    private val session: ProfileSession,
    private val settings: SettingsRepository,
    private val parental: ParentalControl
) {
    /** Los perfiles en su orden; de paso mantiene al día lo que el arranque lee sin abrir la base. */
    val profiles: Flow<List<ProfileEntity>> = dao.observeAll().onEach { list ->
        session.setProfileCount(list.size)
        val active = list.firstOrNull { it.id == session.activeId.value } ?: list.firstOrNull()
        if (active != null && !sameAsSession(active)) session.activate(active)
    }

    private fun sameAsSession(p: ProfileEntity): Boolean =
        p.id == session.activeId.value && p.isKids == session.kids.value && p.hasPin == session.activeHasPin &&
            p.name == session.activeName.value && p.avatar == session.activeAvatar.value

    suspend fun get(id: String): ProfileEntity? = withContext(Dispatchers.IO) { dao.get(id) }

    /** Entra al perfil. El desbloqueo de adultos no pasa de un perfil a otro. */
    suspend fun select(id: String): ProfileEntity? {
        val profile = get(id) ?: return null
        if (profile.id != session.activeId.value) parental.lock()
        session.activate(profile)
        return profile
    }

    suspend fun create(name: String, avatar: Int, kids: Boolean, pin: String?): ProfileEntity? =
        withContext(Dispatchers.IO) {
            val list = dao.getAll()
            if (list.size >= MAX_PROFILES) return@withContext null
            val salt = pin?.let { newSalt() }
            val profile = ProfileEntity(
                id = UUID.randomUUID().toString(),
                name = name.trim().take(NAME_MAX),
                avatar = avatar,
                isKids = kids,
                pinHash = if (pin != null && salt != null) hash(pin, salt) else null,
                pinSalt = salt?.toHex(),
                sortOrder = (list.maxOfOrNull { it.sortOrder } ?: 0) + 1
            )
            dao.upsert(profile)
            session.setProfileCount(list.size + 1)
            profile
        }

    suspend fun update(id: String, name: String, avatar: Int, kids: Boolean, pin: PinChange): ProfileEntity? =
        withContext(Dispatchers.IO) {
            val current = dao.get(id) ?: return@withContext null
            var updated = current.copy(name = name.trim().take(NAME_MAX), avatar = avatar, isKids = kids)
            when (pin) {
                PinChange.Keep -> Unit
                PinChange.Remove -> updated = updated.copy(pinHash = null, pinSalt = null)
                is PinChange.Set -> {
                    val salt = newSalt()
                    updated = updated.copy(pinHash = hash(pin.pin, salt), pinSalt = salt.toHex())
                }
            }
            if (pin != PinChange.Keep) session.clearPinAttempts(id)
            dao.upsert(updated)
            if (id == session.activeId.value) session.activate(updated)
            updated
        }

    /**
     * El perfil creado al actualizar (o con la instalación) toma el nombre escrito al iniciar
     * sesión. Un perfil que ya tiene nombre no se toca.
     */
    suspend fun nameDefaultProfile(name: String) = withContext(Dispatchers.IO) {
        val clean = name.trim().take(NAME_MAX)
        if (clean.isEmpty()) return@withContext
        val current = dao.get(DEFAULT_PROFILE_ID) ?: return@withContext
        if (current.name.isNotBlank()) return@withContext
        val updated = current.copy(name = clean)
        dao.upsert(updated)
        if (updated.id == session.activeId.value) session.activate(updated)
    }

    /** Borra el perfil con su progreso y Mi lista. El último no se puede borrar. */
    suspend fun delete(id: String): Boolean = withContext(Dispatchers.IO) {
        val list = dao.getAll()
        if (list.size <= 1 || list.none { it.id == id }) return@withContext false
        database.withTransaction {
            progressDao.deleteByProfile(id)
            favoriteDao.deleteByProfile(id)
            dao.delete(id)
        }
        settings.clearProfileData(id)
        session.clearPinAttempts(id)
        session.setProfileCount(list.size - 1)
        if (session.activeId.value == id) {
            parental.lock()
            session.activate(list.first { it.id != id })
        }
        true
    }

    suspend fun verifyPin(id: String, pin: String): PinCheck = check(id) {
        dao.get(id)?.let { matches(it, pin) } ?: false
    }

    /** Hay con qué proteger Configuración del perfil de niños: el PIN de un perfil de adultos o el de adultos. */
    suspend fun adultPinExists(): Boolean = withContext(Dispatchers.IO) {
        dao.getAll().any { !it.isKids && it.hasPin } || parental.pinSet.first()
    }

    suspend fun verifyAdultPin(pin: String): PinCheck = check(ADULT_KEY) {
        dao.getAll().any { !it.isKids && matches(it, pin) } || (parental.pinSet.first() && parental.checkPin(pin))
    }

    /** Cinco intentos fallidos bloquean el PIN 30 segundos. */
    private suspend fun check(key: String, test: suspend () -> Boolean): PinCheck {
        val now = System.currentTimeMillis()
        val until = session.lockedUntil(key).coerceAtMost(now + LOCK_MS)
        if (until > now) return PinCheck.Locked(secondsLeft(until - now))
        val ok = withContext(Dispatchers.Default) { test() }
        if (ok) {
            session.clearPinAttempts(key)
            return PinCheck.Ok
        }
        val fails = (if (until != 0L) 0 else session.failedAttempts(key)) + 1
        return if (fails >= MAX_ATTEMPTS) {
            session.setPinAttempts(key, 0, now + LOCK_MS)
            PinCheck.Locked(secondsLeft(LOCK_MS))
        } else {
            session.setPinAttempts(key, fails, 0L)
            PinCheck.Wrong(MAX_ATTEMPTS - fails)
        }
    }

    private fun secondsLeft(ms: Long): Int = ((ms + 999) / 1000).toInt()

    private fun matches(profile: ProfileEntity, pin: String): Boolean {
        val stored = profile.pinHash ?: return false
        val salt = profile.pinSalt?.fromHex() ?: return false
        return MessageDigest.isEqual(stored.toByteArray(), hash(pin, salt).toByteArray())
    }

    companion object {
        const val MAX_PROFILES = 5
        const val NAME_MAX = 20
        const val MAX_ATTEMPTS = 5
        private const val LOCK_MS = 30_000L
        private const val ADULT_KEY = "adult"
        private const val ITERATIONS = 10_000

        private val random = SecureRandom()

        private fun newSalt(): ByteArray = ByteArray(16).also(random::nextBytes)

        /** PBKDF2-HMAC-SHA256 (un bloque de 32 bytes), igual en todas las versiones de Android. */
        private fun hash(pin: String, salt: ByteArray): String {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(pin.toByteArray(), "HmacSHA256"))
            mac.update(salt)
            var block = mac.doFinal(byteArrayOf(0, 0, 0, 1))
            val out = block.copyOf()
            repeat(ITERATIONS - 1) {
                block = mac.doFinal(block)
                for (i in out.indices) out[i] = out[i] xor block[i]
            }
            return out.toHex()
        }

        private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

        private fun String.fromHex(): ByteArray? =
            if (length % 2 != 0) null
            else runCatching { ByteArray(length / 2) { substring(it * 2, it * 2 + 2).toInt(16).toByte() } }.getOrNull()
    }
}
