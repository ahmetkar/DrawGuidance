package com.karapp.drawguidance.data.share

import android.content.Context
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import com.karapp.drawguidance.diagnostics.AppLog
import java.util.concurrent.ConcurrentHashMap

/**
 * v51 Android ↔ Android: Google Nearby Connections. Yakındaki cihazlar Bluetooth/BLE ile bulunur; bağlantı kurulunca
 * Nearby bağlantıyı kendiliğinden Wi-Fi Direct'e (ya da Wi-Fi hotspot'una) yükseltir. Strateji P2P_POINT_TO_POINT:
 * iki cihaz arasında tek, en yüksek bant genişlikli bağlantı. Yalnız bu uygulamanın hizmet kimliğiyle görünür.
 */
class NearbyTransport(context: Context) : ShareTransport {
    override val route = ShareRoute.NEARBY
    override var listener: ShareTransport.Listener? = null

    private val client by lazy { Nearby.getConnectionsClient(context.applicationContext) }
    private val names = ConcurrentHashMap<String, String>()
    private val chunkers = ConcurrentHashMap<String, ShareChunker>()
    private val outgoing = ShareChunker(CHUNK)
    private var localName = "Android"
    private var running = false

    private val payloads = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            val bytes = payload.asBytes() ?: return
            val whole = chunkers.getOrPut(endpointId) { ShareChunker(CHUNK) }.accept(bytes) { got, total ->
                if (total > 4) listener?.onProgress(route, endpointId, got.toFloat() / total)
            } ?: return
            listener?.onMessage(route, endpointId, whole)
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) = Unit
    }

    private val lifecycle = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            names[endpointId] = info.endpointName
            if (info.isIncomingConnection) {
                listener?.onRequest(route, endpointId, info.endpointName, "android", info.authenticationDigits)
            } else {
                // Bağlantıyı bu cihaz istedi: kendi tarafı doğrudan kabul eder (karşı taraf onaylar)
                client.acceptConnection(endpointId, payloads)
            }
        }

        override fun onConnectionResult(endpointId: String, resolution: ConnectionResolution) {
            if (resolution.status.statusCode == ConnectionsStatusCodes.STATUS_OK) {
                listener?.onConnected(route, endpointId, names[endpointId] ?: "Android", "android")
            } else {
                listener?.onDisconnected(route, endpointId)
            }
        }

        override fun onDisconnected(endpointId: String) {
            chunkers.remove(endpointId)
            listener?.onDisconnected(route, endpointId)
        }
    }

    private val discovery = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            if (info.serviceId != ShareWire.NEARBY_SERVICE_ID) return
            names[endpointId] = info.endpointName
            listener?.onFound(route, endpointId, info.endpointName, "android")
        }

        override fun onEndpointLost(endpointId: String) {
            listener?.onLost(route, endpointId)
        }
    }

    override fun start(name: String, instanceId: String) {
        if (running) return
        running = true
        localName = name
        val strategy = Strategy.P2P_POINT_TO_POINT
        client.startAdvertising(name, ShareWire.NEARBY_SERVICE_ID, lifecycle, AdvertisingOptions.Builder().setStrategy(strategy).build())
            .addOnFailureListener { fail("Görünür olunamadı", it) }
        client.startDiscovery(ShareWire.NEARBY_SERVICE_ID, discovery, DiscoveryOptions.Builder().setStrategy(strategy).build())
            .addOnFailureListener { fail("Cihaz aranamadı", it) }
    }

    override fun stop() {
        if (!running) return
        running = false
        runCatching { client.stopAdvertising() }
        runCatching { client.stopDiscovery() }
    }

    override fun connect(endpoint: String) {
        client.requestConnection(localName, endpoint, lifecycle).addOnFailureListener {
            fail("Bağlanılamadı", it)
            listener?.onDisconnected(route, endpoint)
        }
    }

    override fun accept(endpoint: String) {
        client.acceptConnection(endpoint, payloads).addOnFailureListener { fail("Bağlantı kabul edilemedi", it) }
    }

    override fun reject(endpoint: String) {
        runCatching { client.rejectConnection(endpoint) }
    }

    override fun disconnect(endpoint: String) {
        runCatching { client.disconnectFromEndpoint(endpoint) }
        chunkers.remove(endpoint)
        listener?.onDisconnected(route, endpoint)
    }

    override fun send(endpoint: String, message: ByteArray) {
        outgoing.split(message).forEach { part ->
            client.sendPayload(endpoint, Payload.fromBytes(part)).addOnFailureListener { fail("Gönderilemedi", it) }
        }
    }

    override fun shutdown() {
        stop()
        runCatching { client.stopAllEndpoints() }
        chunkers.clear()
    }

    private fun fail(what: String, e: Exception) {
        AppLog.w("Share", "Nearby: $what", e)
        listener?.onError(route, what + ": " + (e.message ?: e.javaClass.simpleName))
    }

    private companion object {
        /** Nearby BYTES yükü en çok 32 KB; 12 bayt parça başlığı için pay bırakılır. */
        const val CHUNK = 30_000
    }
}
