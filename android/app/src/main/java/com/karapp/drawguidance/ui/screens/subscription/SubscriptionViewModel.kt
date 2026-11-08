package com.karapp.drawguidance.ui.screens.subscription

import android.app.Activity
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karapp.drawguidance.data.billing.BillingMessage
import com.karapp.drawguidance.data.billing.SubscriptionRepository
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/** v39 abonelik sayfası: planlar, kullanıcının aboneliği ve Play işlemleri ([SubscriptionRepository]). */
class SubscriptionViewModel(private val repo: SubscriptionRepository) : ViewModel() {
    val state = repo.state
    val plans = repo.plans
    val entitlements = repo.entitlements
    val signedIn = repo.signedIn
    /** v40 yerelde saklanan abonelik satırı (ayrıntı kartı). */
    val current = repo.current
    val storeAvailable: Boolean get() = repo.billing.storeAvailable

    init {
        viewModelScope.launch { runCatching { repo.loadProduct() } }
    }

    fun purchase(activity: Activity) = viewModelScope.launch { repo.purchase(activity) }
    fun restore() = viewModelScope.launch { repo.restore() }
    fun refresh() = viewModelScope.launch {
        repo.refreshPlans()
        runCatching { repo.loadProduct() }
        repo.refresh()
    }
    fun manage(context: Context) = repo.manage(context)
    fun consumeMessage() = repo.consumeMessage()

    companion object {
        /**
         * v39 yerel fiyat yazımı: Play'in biçimlenmiş fiyatı yoksa tutar ve para birimi dile göre yazılır
         * (Türkçede TRY "100 TL"). Tutar yoksa null.
         */
        fun formatPrice(micros: Long, currency: String, locale: Locale = Locale.getDefault()): String? {
            if (micros <= 0) return null
            val amount = micros / 1_000_000.0
            if (currency == "TRY" && locale.language == "tr") {
                val n = NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 2; minimumFractionDigits = 0 }
                return "${n.format(amount)} TL"
            }
            return runCatching {
                NumberFormat.getCurrencyInstance(locale).apply {
                    this.currency = Currency.getInstance(currency)
                    minimumFractionDigits = if (amount % 1.0 == 0.0) 0 else 2
                }.format(amount)
            }.getOrNull()
        }
    }
}

/** Sonuç iletisinin metni (yoksa null). */
fun BillingMessage.textRes(): Int = when (this) {
    BillingMessage.NOT_SIGNED_IN -> com.karapp.drawguidance.R.string.v39_sub_msg_sign_in
    BillingMessage.STORE_UNAVAILABLE -> com.karapp.drawguidance.R.string.v39_sub_msg_store
    BillingMessage.PRODUCT_UNAVAILABLE -> com.karapp.drawguidance.R.string.v39_sub_msg_product
    BillingMessage.CANCELED -> com.karapp.drawguidance.R.string.v39_sub_msg_canceled
    BillingMessage.PENDING -> com.karapp.drawguidance.R.string.v39_sub_msg_pending
    BillingMessage.VERIFIED -> com.karapp.drawguidance.R.string.v39_sub_msg_verified
    BillingMessage.SERVER_NOT_CONFIGURED -> com.karapp.drawguidance.R.string.v39_sub_msg_server
    BillingMessage.VERIFY_FAILED -> com.karapp.drawguidance.R.string.v39_sub_msg_verify_failed
    BillingMessage.OFFLINE -> com.karapp.drawguidance.R.string.v39_sub_msg_offline
    BillingMessage.NOTHING_TO_RESTORE -> com.karapp.drawguidance.R.string.v39_sub_msg_nothing
    BillingMessage.RESTORED -> com.karapp.drawguidance.R.string.v39_sub_msg_restored
    BillingMessage.REFRESHED -> com.karapp.drawguidance.R.string.v39_sub_msg_refreshed
    BillingMessage.ALREADY_OWNED -> com.karapp.drawguidance.R.string.v39_sub_msg_owned
    BillingMessage.ERROR -> com.karapp.drawguidance.R.string.v39_sub_msg_error
}
