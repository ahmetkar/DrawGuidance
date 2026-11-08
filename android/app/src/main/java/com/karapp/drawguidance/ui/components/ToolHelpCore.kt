package com.karapp.drawguidance.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics

/**
 * v33 araç yardımı: çizim alanının üstündeki menülerde bir düğmeye dokununca adı, [ToolHelpHoldMillis] basılı tutunca
 * ne işe yaradığı çizim alanının köşesindeki kutuda görünür. Menüler [LocalToolHelp] ile bu durumu alır; yerel değer
 * yalnız araç çubuklarının çevresinde verilir (pencerelerde ve panellerde düğmeler eskisi gibi çalışır).
 */
@Immutable
data class ToolHint(val name: String, val description: String?, val id: Long)

@Stable
class ToolHelpState {
    /** Kutuda gösterilen ad (ve basılı tutulduysa açıklama); null: kutu kapalı. */
    var hint by mutableStateOf<ToolHint?>(null)
        private set
    private var seq = 0L

    /** Düğme adından açıklama (eşleşme yoksa null); ekran açıklama tablosunu bağlar. */
    var describer: (String) -> String? = { null }

    /** Eşleşmeyen düğmeler için genel açıklama. */
    var fallback: (String) -> String = { it }

    /** [keys] sırayla denenir; hiçbiri eşleşmezse genel açıklama. */
    fun describe(vararg keys: String): String = keys.firstNotNullOfOrNull { describer(it) } ?: fallback(keys.firstOrNull().orEmpty())

    fun showName(name: String) {
        hint = ToolHint(name, null, ++seq)
    }

    fun showDescription(name: String, description: String = describe(name)) {
        hint = ToolHint(name, description, ++seq)
    }

    fun dismiss() {
        hint = null
    }
}

/** Araç çubuklarında verilir; null iken düğmeler yardımsız (eski davranış). */
val LocalToolHelp = staticCompositionLocalOf<ToolHelpState?> { null }

/** Açıklamanın açılması için basılı tutma süresi. */
const val ToolHelpHoldMillis = 2000L

/** Uzun basma süresi [ToolHelpHoldMillis] olan görünüm ayarı (öteki değerler olduğu gibi). */
private class HoldViewConfiguration(private val base: ViewConfiguration) : ViewConfiguration by base {
    override val longPressTimeoutMillis: Long get() = ToolHelpHoldMillis
}

/** İçindeki düğmelerde uzun basma [ToolHelpHoldMillis] sürer. */
@Composable
fun ToolHold(content: @Composable () -> Unit) {
    val base = LocalViewConfiguration.current
    val config = remember(base) { if (base is HoldViewConfiguration) base else HoldViewConfiguration(base) }
    CompositionLocalProvider(LocalViewConfiguration provides config, content = content)
}

/**
 * Yardımlı dokunma: dokununca ([showName] ise) ad görünür ve düğme çalışır; basılı tutunca açıklama görünür, düğme
 * çalışmaz. Pasif düğmede de ad/açıklama görünür ama işlem yapılmaz (erişilebilirlikte pasif kalır). [ToolHold]
 * içinde kullanılmalı (uzun basma süresi oradan gelir).
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.toolHelpClickable(
    help: ToolHelpState,
    label: String,
    enabled: Boolean,
    role: Role?,
    interaction: MutableInteractionSource?,
    showName: Boolean = true,
    description: () -> String = { help.describe(label) },
    onClick: () -> Unit,
): Modifier = this
    .combinedClickable(
        interactionSource = if (enabled) interaction else null,
        indication = null,
        enabled = true,
        role = role,
        onLongClick = { help.showDescription(label, description()) },
        onClick = {
            if (showName) help.showName(label)
            if (enabled) onClick()
        },
    )
    .then(if (enabled) Modifier else Modifier.semantics { disabled() })
