package com.karapp.drawguidance.data.share

/** v51 bağlantı yolu: Nearby (Wi-Fi Direct / Bluetooth, Android ↔ Android) ya da aynı Wi-Fi ağı (Android ↔ iOS). */
enum class ShareRoute { NEARBY, LAN }

/**
 * v51 bir bağlantı yolunun ortak arayüzü. Uç noktalar yol içinde benzersiz bir anahtarla tanınır; mesajlar
 * [ShareWire] mesajlarıdır (yol, parçalama ya da çerçevelemeyi kendisi yapar).
 */
interface ShareTransport {
    val route: ShareRoute

    interface Listener {
        fun onFound(route: ShareRoute, endpoint: String, name: String, platform: String)
        fun onLost(route: ShareRoute, endpoint: String)
        /** Karşı cihaz bağlanmak istiyor: [ShareTransport.accept] ya da [ShareTransport.reject] çağrılır. [code]: doğrulama kodu. */
        fun onRequest(route: ShareRoute, endpoint: String, name: String, platform: String, code: String?)
        fun onConnected(route: ShareRoute, endpoint: String, name: String, platform: String)
        fun onDisconnected(route: ShareRoute, endpoint: String)
        fun onMessage(route: ShareRoute, endpoint: String, bytes: ByteArray)
        /** Büyük mesaj alınırken ilerleme (0..1). */
        fun onProgress(route: ShareRoute, endpoint: String, fraction: Float) {}
        fun onError(route: ShareRoute, message: String) {}
    }

    var listener: Listener?

    /** Görünür ol ve yakındaki cihazları ara. */
    fun start(name: String, instanceId: String)
    fun stop()
    fun connect(endpoint: String)
    fun accept(endpoint: String)
    fun reject(endpoint: String)
    fun disconnect(endpoint: String)
    fun send(endpoint: String, message: ByteArray)
    /** Her şeyi kapatır (bağlantılar dahil). */
    fun shutdown()
}
