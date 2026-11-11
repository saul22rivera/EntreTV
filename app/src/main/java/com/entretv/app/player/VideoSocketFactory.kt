package com.entretv.app.player

import java.net.InetAddress
import java.net.Socket
import javax.net.SocketFactory

/**
 * Sockets del video marcados con DSCP AF41 (video interactivo). Los routers con WMM o QoS
 * automático los ponen en la cola de video del wifi; los que no lo usan lo ignoran.
 * Si el sistema no deja marcar, el socket sale igual sin marca.
 */
object VideoSocketFactory : SocketFactory() {
    /** DSCP 34 (AF41) en el byte de tipo de servicio. */
    private const val TRAFFIC_CLASS_VIDEO = 0x88

    private val base: SocketFactory = getDefault()

    private fun Socket.marked(): Socket = apply { runCatching { trafficClass = TRAFFIC_CLASS_VIDEO } }

    override fun createSocket(): Socket = base.createSocket().marked()

    override fun createSocket(host: String?, port: Int): Socket = base.createSocket(host, port).marked()

    override fun createSocket(host: String?, port: Int, localHost: InetAddress?, localPort: Int): Socket =
        base.createSocket(host, port, localHost, localPort).marked()

    override fun createSocket(host: InetAddress?, port: Int): Socket = base.createSocket(host, port).marked()

    override fun createSocket(address: InetAddress?, port: Int, localAddress: InetAddress?, localPort: Int): Socket =
        base.createSocket(address, port, localAddress, localPort).marked()
}
