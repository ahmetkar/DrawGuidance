package com.karapp.drawguidance.ui.screens.subscription

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Restore
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karapp.drawguidance.R
import com.karapp.drawguidance.data.billing.Entitlements
import com.karapp.drawguidance.data.billing.PlanIds
import com.karapp.drawguidance.data.billing.PlanUi
import com.karapp.drawguidance.data.billing.SubscriptionStatuses
import com.karapp.drawguidance.ui.components.NeuButton
import com.karapp.drawguidance.ui.components.NeuIconButton
import com.karapp.drawguidance.ui.components.NeuSurface
import com.karapp.drawguidance.ui.components.NeuTopBar
import com.karapp.drawguidance.ui.theme.NeuTheme
import java.text.DateFormat
import java.util.Date

/**
 * v39 Abonelik sayfası; v41 SaaS düzeni: üstte başlık ve kısa açıklama, yan yana (dar ekranda alt alta) plan kartları
 * (fiyat, özellik listesi, kartın içinde eylem düğmesi; Premium "Önerilen"), altında başlıklar altında bölümler:
 * "Aboneliğin" (plan, durum, ayrıntılar ve Google Play yönetim düğmeleri), "Faturalandırma" (geri yükleme, Play'de
 * yönetme) ve "Sık sorulanlar".
 */
@Composable
fun SubscriptionScreen(onBack: () -> Unit, vm: SubscriptionViewModel) {
    val state by vm.state.collectAsState()
    val plans by vm.plans.collectAsState()
    val ent by vm.entitlements.collectAsState()
    val signedIn by vm.signedIn.collectAsState()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val msg = state.message?.let { stringResource(it.textRes()) }
    LaunchedEffect(state.message) { if (msg != null) { snackbar.showSnackbar(msg); vm.consumeMessage() } }
    val storeOk = state.storeAvailable
    val premiumPrice = state.product?.basePlanOffer(com.karapp.drawguidance.data.billing.SubscriptionRepository.PREMIUM_BASE_PLAN_ID)?.recurringPhase?.formattedPrice
    val c = NeuTheme.colors
    Scaffold(
        containerColor = c.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            NeuTopBar(
                title = stringResource(R.string.v39_sub_title),
                navigation = { NeuIconButton(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), onBack) },
                actions = {
                    NeuIconButton(Icons.Default.Refresh, stringResource(R.string.v39_sub_refresh), { vm.refresh() }, modifier = Modifier.testTag("sub_refresh"))
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp)
                .wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = 880.dp).testTag("subscription_screen"),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Başlık
            Column(Modifier.fillMaxWidth().testTag("sub_hero"), verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.v41_sub_hero), color = c.content, fontWeight = FontWeight.Bold, fontSize = 22.sp, textAlign = TextAlign.Center)
                Text(stringResource(R.string.v41_sub_hero_text), color = c.contentMuted, fontSize = 14.sp, textAlign = TextAlign.Center)
            }
            val reason = when {
                !signedIn -> stringResource(R.string.v39_sub_need_sign_in)
                !storeOk -> stringResource(R.string.v39_sub_store_unavailable)
                else -> null
            }
            // Plan kartları: geniş ekranda yan yana
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val wide = maxWidth >= 600.dp
                val cards: @Composable (Modifier) -> Unit = { m ->
                    plans.forEach { p ->
                        val premium = p.plan.id == PlanIds.PREMIUM
                        val current = ent.planId == p.plan.id || (p.plan.id == PlanIds.FREE && !ent.isPremium)
                        PlanCard(
                            p, current = current, playPrice = if (premium) premiumPrice else null, modifier = m,
                            action = {
                                when {
                                    current -> NeuButton(stringResource(R.string.v39_sub_current), {}, Modifier.fillMaxWidth().testTag("plan_current_${p.plan.id}"), enabled = false)
                                    premium -> {
                                        NeuButton(
                                            stringResource(R.string.v39_sub_upgrade), { context.findActivity()?.let { vm.purchase(it) } },
                                            Modifier.fillMaxWidth().testTag("sub_upgrade"), primary = true, enabled = reason == null && !state.busy,
                                            icon = Icons.Default.WorkspacePremium,
                                        )
                                        if (reason != null) Text(reason, color = c.contentMuted, fontSize = 12.sp, modifier = Modifier.testTag("sub_upgrade_reason"))
                                    }
                                    // Premium'dan Free'ye dönmek Google Play'de aboneliği iptal etmektir
                                    else -> NeuButton(stringResource(R.string.v41_sub_downgrade), { vm.manage(context) }, Modifier.fillMaxWidth().testTag("plan_downgrade"))
                                }
                            },
                        )
                    }
                }
                if (wide) Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) { cards(Modifier.weight(1f)) }
                else Column(verticalArrangement = Arrangement.spacedBy(14.dp)) { cards(Modifier.fillMaxWidth()) }
            }

            // Aboneliğin: plan, durum ve (abonelik varsa) ayrıntılar ile Play yönetim düğmeleri
            SectionHeader(stringResource(R.string.v41_sub_section_yours), "sub_section_yours")
            StatusCard(ent, signedIn)
            val current by vm.current.collectAsState()
            SubscriptionDetailsCard(current)

            // Faturalandırma: geri yükleme ve Play'de yönetme
            SectionHeader(stringResource(R.string.v41_sub_section_billing), "sub_section_billing")
            NeuSurface(Modifier.fillMaxWidth().testTag("sub_billing")) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.v41_sub_billing_text), color = c.contentMuted, fontSize = 13.sp)
                    NeuButton(stringResource(R.string.v39_sub_restore), { vm.restore() }, Modifier.fillMaxWidth().testTag("sub_restore"), enabled = signedIn && storeOk && !state.busy, icon = Icons.Default.Restore)
                    NeuButton(stringResource(R.string.v39_sub_manage), { vm.manage(context) }, Modifier.fillMaxWidth().testTag("sub_manage"), icon = Icons.AutoMirrored.Filled.OpenInNew)
                }
            }

            // Sık sorulanlar
            SectionHeader(stringResource(R.string.v41_sub_section_faq), "sub_section_faq")
            NeuSurface(Modifier.fillMaxWidth().testTag("sub_faq")) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Faq(stringResource(R.string.v41_sub_faq_cancel_q), stringResource(R.string.v41_sub_faq_cancel_a))
                    Faq(stringResource(R.string.v41_sub_faq_devices_q), stringResource(R.string.v41_sub_faq_devices_a))
                    Faq(stringResource(R.string.v41_sub_faq_free_q), stringResource(R.string.v41_sub_faq_free_a))
                }
            }
        }
    }
}

/** v41 bölüm başlığı (başlıklar altında yönetim düğmeleri). */
@Composable
private fun SectionHeader(text: String, tag: String) {
    Text(
        text, color = NeuTheme.colors.content, fontWeight = FontWeight.SemiBold, fontSize = 17.sp,
        modifier = Modifier.padding(top = 8.dp).testTag(tag).semantics { heading() },
    )
}

@Composable
private fun Faq(q: String, a: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(q, color = NeuTheme.colors.content, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Text(a, color = NeuTheme.colors.contentMuted, fontSize = 13.sp)
    }
}

@Composable
private fun StatusCard(e: Entitlements, signedIn: Boolean) {
    val c = NeuTheme.colors
    NeuSurface(Modifier.fillMaxWidth().testTag("sub_status")) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(
                if (e.isPremium) Icons.Default.WorkspacePremium else Icons.Default.Person, null,
                tint = if (e.isPremium) Color39.Gold else c.accent, modifier = Modifier.size(28.dp),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(if (e.isPremium) R.string.v39_sub_current_premium else R.string.v39_sub_current_free),
                    color = c.content, fontWeight = FontWeight.Bold, fontSize = 17.sp,
                )
                if (!signedIn) Text(stringResource(R.string.v39_sub_need_sign_in), color = c.contentMuted, fontSize = 13.sp)
                if (e.status != SubscriptionStatuses.NONE) Text(stringResource(R.string.v39_sub_status, statusText(e.status)), color = c.contentMuted, fontSize = 13.sp)
                e.expiryTime?.let { Text(stringResource(R.string.v39_sub_expiry, DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it))), color = c.contentMuted, fontSize = 13.sp) }
                if (e.isPremium) Text(stringResource(if (e.autoRenewing) R.string.v39_sub_renew_on else R.string.v39_sub_renew_off), color = c.contentMuted, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun statusText(s: String): String = stringResource(
    when (s) {
        SubscriptionStatuses.ACTIVE -> R.string.v39_sub_st_active
        SubscriptionStatuses.IN_GRACE_PERIOD -> R.string.v39_sub_st_grace
        SubscriptionStatuses.CANCELED -> R.string.v39_sub_st_canceled
        SubscriptionStatuses.ON_HOLD -> R.string.v39_sub_st_hold
        SubscriptionStatuses.PAUSED -> R.string.v39_sub_st_paused
        SubscriptionStatuses.PENDING -> R.string.v39_sub_st_pending
        SubscriptionStatuses.EXPIRED -> R.string.v39_sub_st_expired
        else -> R.string.v39_sub_st_none
    },
)

@Composable
private fun PlanCard(p: PlanUi, current: Boolean, playPrice: String?, modifier: Modifier, action: @Composable () -> Unit) {
    val c = NeuTheme.colors
    val premium = p.plan.id == PlanIds.PREMIUM
    val price = when {
        !premium -> stringResource(R.string.v39_sub_free_price)
        else -> stringResource(R.string.v39_sub_per_month, playPrice ?: SubscriptionViewModel.formatPrice(p.plan.priceMicros, p.plan.currency) ?: "100 TL")
    }
    val features = if (premium) {
        listOf(R.string.v41_sub_f_everything_free, R.string.v41_sub_f_bg_removal, R.string.v41_sub_f_ai, R.string.v41_sub_f_comic, R.string.v41_sub_f_kids, R.string.v41_sub_f_no_ads)
    } else {
        listOf(R.string.v41_sub_f_modes, R.string.v41_sub_f_shapes, R.string.v41_sub_f_basic_comic, R.string.v41_sub_f_sync, R.string.v41_sub_f_ads)
    }
    NeuSurface(modifier.testTag("plan_${p.plan.id}"), selected = current || premium) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (premium) Icon(Icons.Default.WorkspacePremium, null, tint = Color39.Gold, modifier = Modifier.size(22.dp).padding(end = 4.dp))
                Text(stringResource(if (premium) R.string.v39_plan_premium else R.string.v39_plan_free), color = c.content, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                when {
                    current -> Text(stringResource(R.string.v39_sub_current), color = c.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    premium -> Text(
                        stringResource(R.string.v41_sub_recommended), color = c.background, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.background(c.accent, androidx.compose.foundation.shape.RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 2.dp)
                            .testTag("plan_recommended"),
                    )
                }
            }
            Text(price, color = c.content, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("plan_price_${p.plan.id}"))
            Text(stringResource(if (premium) R.string.v39_plan_premium_desc else R.string.v39_plan_free_desc), color = c.contentMuted, fontSize = 13.sp)
            Column(Modifier.padding(vertical = 4.dp).testTag("plan_features_${p.plan.id}"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                features.forEach { f ->
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Check, null, tint = if (premium) Color39.Gold else c.accent, modifier = Modifier.size(18.dp))
                        Text(stringResource(f), color = c.content, fontSize = 14.sp)
                    }
                }
            }
            action()
        }
    }
}

/** v39 taç rengi. */
object Color39 { val Gold = androidx.compose.ui.graphics.Color(0xFFF5B301) }

internal fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) { if (c is Activity) return c; c = c.baseContext }
    return null
}

/** v39 Ayarlar'daki tek "Abonelik" satırı: geçerli plan ve abonelik sayfası. */
@Composable
fun SubscriptionSettingsRow(onClick: () -> Unit) {
    val repo = org.koin.compose.koinInject<com.karapp.drawguidance.data.billing.SubscriptionRepository>()
    val ent by repo.entitlements.collectAsState()
    val c = NeuTheme.colors
    Row(
        Modifier.fillMaxWidth().clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp).testTag("settings_subscription"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.WorkspacePremium, null, tint = Color39.Gold, modifier = Modifier.size(24.dp))
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(stringResource(R.string.v39_settings_subscription), color = c.content, fontWeight = FontWeight.SemiBold)
            Text(stringResource(if (ent.isPremium) R.string.v39_plan_premium else R.string.v39_plan_free), color = c.contentMuted, fontSize = 13.sp)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = c.contentMuted)
    }
}
