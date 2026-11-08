package com.karapp.drawguidance.ui.premium

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karapp.drawguidance.R
import com.karapp.drawguidance.data.billing.Entitlements
import com.karapp.drawguidance.data.billing.PremiumFeature
import com.karapp.drawguidance.data.billing.SubscriptionRepository
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.ui.components.NeuButton
import com.karapp.drawguidance.ui.theme.NeuTheme
import org.koin.compose.koinInject

/**
 * v39 düzenleyicideki Premium kilitleri: [locked] özelliğin bu tuval türünde kilitli olup olmadığını söyler (anahtarı
 * olmayan tür, ör. çocuk tuvali, hiç kilitlenmez); [request] kilitliyse "Premium gerekli" penceresini açar ve false döner.
 */
@Stable
class PremiumAccess(
    val entitlements: Entitlements,
    val kind: CanvasKind?,
    private val onLocked: (PremiumFeature) -> Unit,
) {
    fun locked(feature: PremiumFeature?): Boolean {
        val key = feature?.key(kind) ?: return false
        return !entitlements.has(key)
    }

    /** Kullanılabiliyorsa true; kilitliyse pencere açılır. */
    fun request(feature: PremiumFeature?): Boolean {
        if (!locked(feature)) return true
        onLocked(feature!!)
        return false
    }

    companion object {
        /** Sağlayıcı yokken (önizleme): kilit yok. */
        val Unlocked = PremiumAccess(Entitlements.premium(), null) {}
    }
}

val LocalPremium = staticCompositionLocalOf { PremiumAccess.Unlocked }

/** v39 düzenleyiciyi saran kapsam: haklar ([SubscriptionRepository.entitlements]) ve "Premium gerekli" penceresi. */
@Composable
fun PremiumScope(kind: CanvasKind?, onOpenSubscription: () -> Unit, content: @Composable () -> Unit) {
    val repo = koinInject<SubscriptionRepository>()
    val ent by repo.entitlements.collectAsState()
    var asked by remember { mutableStateOf<PremiumFeature?>(null) }
    val access = remember(ent, kind) { PremiumAccess(ent, kind) { asked = it } }
    CompositionLocalProvider(LocalPremium provides access) {
        content()
        asked?.let { PremiumRequiredSheet(onDismiss = { asked = null }, onUpgrade = { asked = null; onOpenSubscription() }) }
    }
}

/** v39 kilitli ögenin köşesindeki taç. */
@Composable
fun PremiumLockBadge(modifier: Modifier = Modifier, size: Dp = 16.dp) {
    Box(
        modifier.size(size + 4.dp).clip(CircleShape).background(Color(0xCC1E1B4B)).testTag("premium_badge"),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Default.WorkspacePremium, stringResource(R.string.v39_premium_badge), tint = Gold, modifier = Modifier.size(size))
    }
}

val Gold = Color(0xFFF5B301)

/** v39 "Premium gerekli" penceresi: abonelik sayfasına götürür. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumRequiredSheet(onDismiss: () -> Unit, onUpgrade: () -> Unit) {
    val c = NeuTheme.colors
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.background) {
        Column(
            Modifier.fillMaxWidth().padding(24.dp).navigationBarsPadding().testTag("premium_required_sheet"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Default.WorkspacePremium, null, tint = Gold, modifier = Modifier.size(48.dp))
            Text(stringResource(R.string.v39_premium_required), color = c.content, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text(stringResource(R.string.v39_premium_required_body), color = c.contentMuted, fontSize = 14.sp, textAlign = TextAlign.Center)
            NeuButton(stringResource(R.string.v39_premium_see_plans), onUpgrade, Modifier.fillMaxWidth().testTag("premium_go_subscription"), primary = true)
            NeuButton(stringResource(R.string.v39_premium_not_now), onDismiss, Modifier.fillMaxWidth())
        }
    }
}
