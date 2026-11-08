package com.karapp.drawguidance.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * v33 internet bağlantısı durumu. Uygulama bağlantı olmadan da tam çalışır; sunucuyla ilgili işler bağlantı gelince
 * yapılır ([SyncEngine]), hesapla ilgili ayarlar yalnız bağlantı varken görünür.
 */
interface Connectivity {
    val online: StateFlow<Boolean>

    /** v37 etkin ağ ücretli mi (mobil veri, ölçülü Wi-Fi); "yalnız Wi-Fi" eşitlemesi buna bakar. */
    val metered: StateFlow<Boolean> get() = UNMETERED

    companion object {
        internal val UNMETERED: StateFlow<Boolean> = MutableStateFlow(false)
    }
}

/** Android'in ağ geri çağrılarıyla bağlantı izleme (API 21+; API 23+ doğrulanmış internet aranır). */
class AndroidConnectivity(context: Context) : Connectivity {
    private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private val networks = java.util.concurrent.ConcurrentHashMap<Network, Boolean>()
    private val _online = MutableStateFlow(current())
    override val online: StateFlow<Boolean> = _online.asStateFlow()
    private val _metered = MutableStateFlow(isMetered())
    override val metered: StateFlow<Boolean> = _metered.asStateFlow()

    init {
        runCatching {
            val request = NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build()
            cm?.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) { networks[network] = true; refresh() }
                override fun onLost(network: Network) { networks.remove(network); refresh() }
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    networks[network] = usable(caps); refresh()
                }
            })
        }
    }

    private fun refresh() {
        _online.value = networks.values.any { it } || current()
        _metered.value = isMetered()
    }

    private fun isMetered(): Boolean = runCatching { cm?.isActiveNetworkMetered == true }.getOrDefault(false)

    private fun usable(caps: NetworkCapabilities): Boolean {
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return false
        return Build.VERSION.SDK_INT < 23 || caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) ||
            // Yerel ağdaki sunucu (internetsiz Wi-Fi) için doğrulanmamış Wi-Fi/Ethernet de bağlantı sayılır
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    @Suppress("DEPRECATION")
    private fun current(): Boolean = runCatching {
        val manager = cm ?: return@runCatching false
        if (Build.VERSION.SDK_INT >= 23) {
            val caps = manager.getNetworkCapabilities(manager.activeNetwork ?: return@runCatching false) ?: return@runCatching false
            usable(caps)
        } else manager.activeNetworkInfo?.isConnected == true
    }.getOrDefault(false)
}

/** Testlerde elle değiştirilen bağlantı. */
class FixedConnectivity(initial: Boolean = true) : Connectivity {
    private val _online = MutableStateFlow(initial)
    override val online: StateFlow<Boolean> = _online.asStateFlow()
    private val _metered = MutableStateFlow(false)
    override val metered: StateFlow<Boolean> = _metered.asStateFlow()
    fun set(value: Boolean) { _online.value = value }
    fun setMetered(value: Boolean) { _metered.value = value }
}
