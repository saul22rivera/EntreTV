package com.entretv.app.data.net

import android.os.SystemClock
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import okhttp3.Dns

/**
 * Guarda cada dirección resuelta 10 minutos y, si la búsqueda falla, usa la última que
 * funcionó: con internet intermitente lo que se cuelga suele ser el DNS, no el servidor.
 */
object CachingDns : Dns {
    private const val TTL_MS = 10 * 60_000L

    private class Entry(val addresses: List<InetAddress>, val at: Long)

    private val cache = ConcurrentHashMap<String, Entry>()

    override fun lookup(hostname: String): List<InetAddress> {
        val now = SystemClock.elapsedRealtime()
        val known = cache[hostname]
        if (known != null && now - known.at < TTL_MS) return known.addresses
        return try {
            Dns.SYSTEM.lookup(hostname).also { if (it.isNotEmpty()) cache[hostname] = Entry(it, now) }
        } catch (e: UnknownHostException) {
            known?.addresses ?: throw e
        }
    }
}
