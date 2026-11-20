package com.karapp.drawguidance.data.share

/** v51 bağlantı yolu: aynı Wi-Fi ağı (Android ve iOS; ileride başka yollar eklenebilir). */
enum class ShareRoute { LAN }

/**
 * v51 bir bağlantı yolunun ortak arayüzü. Uç noktalar yol içinde benzersiz bir anahtarla tanınır; mesajlar
 * [ShareWire] mesajlarıdır (yol, parçalama ya da çerçevelemeyi kendisi yapar).
 */
interface ShareTransport {
    val route: ShareRoute

    interface Listener {
        fun onFound(route: ShareRoute, endpoint: String, name: String, platform: String)
        fun onLost(route: ShareRoute, endpoint: String)
        /**
         * Karşı cihaz bağlanmak istiyor: [ShareTransport.accept] ya da [ShareTransport.reject] çağrılır. v56: eşleşme
         * şifresi artık burada verilmez; kabul edilince ve gerekiyorsa [onPairCode] ile gelir. [intent] = "live" ise
         * bağlantı canlı yayın içindir ([title] yayınlanacak tuvalin adı) ve kabulden önce kullanıcıya sorulur.
         */
        fun onRequest(route: ShareRoute, endpoint: String, name: String, platform: String, needsCode: Boolean, intent: String?, title: String?)
        /** v56 kabul edilen eşleşmemiş cihaz için şimdi üretilen şifre: ekranda gösterilir, karşı cihazda girilir. */
        fun onPairCode(route: ShareRoute, endpoint: String, name: String, platform: String, code: String) {}
        fun onConnected(route: ShareRoute, endpoint: String, name: String, platform: String)
        fun onDisconnected(route: ShareRoute, endpoint: String)
        fun onMessage(route: ShareRoute, endpoint: String, bytes: ByteArray)
        /** v54 bağlanan cihazda: karşı cihazın ekranındaki eşleşme şifresi girilmeli ([ShareTransport.submitCode]). */
        fun onCodeNeeded(route: ShareRoute, endpoint: String, name: String) {}
        /** Büyük mesaj alınırken ilerleme (0..1). */
        fun onProgress(route: ShareRoute, endpoint: String, fraction: Float) {}
        fun onError(route: ShareRoute, message: String) {}
    }

    var listener: Listener?

    /** Görünür ol ve yakındaki cihazları ara. */
    fun start(name: String, instanceId: String)
    fun stop()
    /** v56 [intent]/[title]: bağlantının amacı karşı cihaza tanışmada bildirilir (canlı yayın onayı için). */
    fun connect(endpoint: String, intent: String? = null, title: String? = null)
    fun accept(endpoint: String)
    /** v56 [reason]: "rejected", "timeout" ya da "blocked" (bağlanan cihazda uygun yazı gösterilir). */
    fun reject(endpoint: String, reason: String = REJECT_REASON)
    fun disconnect(endpoint: String)
    /** v54 eşleşme şifresi girildi / vazgeçildi. */
    fun submitCode(endpoint: String, code: String) {}
    fun cancelCode(endpoint: String) {}
    fun send(endpoint: String, message: ByteArray)
    /** Her şeyi kapatır (bağlantılar dahil). */
    fun shutdown()
}

/** v56 bağlantı amacı: canlı yayın. */
const val CONNECT_INTENT_LIVE = "live"
const val REJECT_REASON = "rejected"
const val REJECT_TIMEOUT = "timeout"
const val REJECT_BLOCKED = "blocked"
