package com.karapp.drawguidance.ui.screens.subscription

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karapp.drawguidance.R
import com.karapp.drawguidance.data.billing.BillingGateway
import com.karapp.drawguidance.data.billing.SubscriptionRepository
import com.karapp.drawguidance.data.billing.SubscriptionStatuses
import com.karapp.drawguidance.data.local.SubscriptionEntity
import com.karapp.drawguidance.ui.components.NeuButton
import com.karapp.drawguidance.ui.components.NeuSurface
import com.karapp.drawguidance.ui.theme.NeuTheme
import java.text.DateFormat
import java.util.Date

/**
 * v40 abonelik ayrıntıları (yerel `subscriptions` satırından; saf, birim testli): başlangıç, dönem sonu ya da sonraki
 * yenileme, otomatik yenileme, ek süre / askıda / duraklatıldı durumları ve iptal tarihi.
 */
data class SubscriptionDetails(
    val status: String,
    val startTime: Long?,
    /** Dönemin sonu: otomatik yenileniyorsa "sonraki yenileme", değilse "bitiş" olarak gösterilir. */
    val periodEnd: Long?,
    val renews: Boolean,
    val autoRenewing: Boolean,
    val inGracePeriod: Boolean,
    val onHold: Boolean,
    val paused: Boolean,
    val autoResumeTime: Long?,
    val canceled: Boolean,
    val cancelTime: Long?,
    val cancelReason: String?,
) {
    companion object {
        /** Abonelik yoksa (Free ya da hiç satır yok) null. */
        fun from(e: SubscriptionEntity?): SubscriptionDetails? {
            if (e == null || e.status == SubscriptionStatuses.NONE || e.productId == null) return null
            val canceled = e.status == SubscriptionStatuses.CANCELED || e.cancelTime != null || e.cancelReason != null
            val renews = e.autoRenewing && e.status in setOf(SubscriptionStatuses.ACTIVE, SubscriptionStatuses.IN_GRACE_PERIOD)
            return SubscriptionDetails(
                status = e.status, startTime = e.startTime, periodEnd = e.expiryTime, renews = renews, autoRenewing = e.autoRenewing,
                inGracePeriod = e.status == SubscriptionStatuses.IN_GRACE_PERIOD, onHold = e.status == SubscriptionStatuses.ON_HOLD,
                paused = e.status == SubscriptionStatuses.PAUSED, autoResumeTime = e.autoResumeTime,
                canceled = canceled, cancelTime = e.cancelTime, cancelReason = e.cancelReason,
            )
        }

        /**
         * Google Play'in bu ürünün abonelik sayfası (uygulamalar otomatik yenilemeyi kendileri açıp kapatamaz; iptal ve
         * yeniden başlatma da orada yapılır).
         */
        fun manageUri(packageName: String, productId: String?): Uri = BillingGateway.manageSubscriptionsUri(packageName, productId)

        fun openManage(context: Context, productId: String?): Boolean = runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, manageUri(context.packageName, productId)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
    }
}

/** v40 abonelik sayfasındaki ayrıntı kartı ve Google Play yönetim düğmeleri (abonelik yoksa gösterilmez). */
@Composable
fun SubscriptionDetailsCard(entity: SubscriptionEntity?) {
    val d = SubscriptionDetails.from(entity) ?: return
    val c = NeuTheme.colors
    val context = LocalContext.current
    val date = { t: Long -> DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(t)) }
    val product = entity?.productId ?: SubscriptionRepository.PREMIUM_PRODUCT_ID
    NeuSurface(Modifier.fillMaxWidth().testTag("sub_details")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Autorenew, null, tint = c.accent)
                Text(stringResource(R.string.v40_sub_details_title), color = c.content, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            d.startTime?.let { DetailLine(stringResource(R.string.v40_sub_start, date(it)), "sub_start") }
            d.periodEnd?.let {
                DetailLine(stringResource(if (d.renews) R.string.v40_sub_next_renewal else R.string.v40_sub_period_end, date(it)), "sub_period_end")
            }
            DetailLine(stringResource(if (d.autoRenewing) R.string.v40_sub_auto_renew_on else R.string.v40_sub_auto_renew_off), "sub_auto_renew")
            if (d.inGracePeriod) DetailLine(stringResource(R.string.v40_sub_grace), "sub_grace", warn = true)
            if (d.onHold) DetailLine(stringResource(R.string.v40_sub_on_hold), "sub_on_hold", warn = true)
            if (d.paused) {
                DetailLine(d.autoResumeTime?.let { stringResource(R.string.v40_sub_paused_until, date(it)) } ?: stringResource(R.string.v40_sub_paused), "sub_paused", warn = true)
            }
            if (d.canceled) {
                DetailLine(d.cancelTime?.let { stringResource(R.string.v40_sub_canceled_on, date(it)) } ?: stringResource(R.string.v40_sub_canceled), "sub_canceled", warn = true)
            }
            // v41 başlığın altında yönetim düğmeleri
            androidx.compose.material3.HorizontalDivider(Modifier.padding(vertical = 4.dp), color = c.contentMuted.copy(alpha = 0.25f))
            Text(stringResource(R.string.v41_sub_manage_title), color = c.content, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.testTag("sub_manage_title"))
            Text(stringResource(R.string.v40_sub_play_hint), color = c.contentMuted, fontSize = 12.sp)
            NeuButton(
                stringResource(R.string.v40_sub_manage_auto_renew), { SubscriptionDetails.openManage(context, product) },
                Modifier.fillMaxWidth().testTag("sub_manage_auto_renew"), icon = Icons.AutoMirrored.Filled.OpenInNew,
            )
            NeuButton(
                stringResource(R.string.v40_sub_cancel_or_resume), { SubscriptionDetails.openManage(context, product) },
                Modifier.fillMaxWidth().testTag("sub_cancel_resume"), icon = Icons.AutoMirrored.Filled.OpenInNew,
            )
        }
    }
}

@Composable
private fun DetailLine(text: String, tag: String, warn: Boolean = false) {
    val c = NeuTheme.colors
    Text(text, color = if (warn) c.danger else c.contentMuted, fontSize = 13.sp, modifier = Modifier.testTag(tag))
}
