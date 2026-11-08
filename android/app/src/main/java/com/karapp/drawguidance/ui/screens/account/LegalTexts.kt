package com.karapp.drawguidance.ui.screens.account

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import com.karapp.drawguidance.R
import com.karapp.drawguidance.i18n.I18n
import com.karapp.drawguidance.ui.components.NeuButton
import com.karapp.drawguidance.ui.i18n.Text
import com.karapp.drawguidance.ui.theme.NeuTheme
import com.karapp.drawguidance.ui.components.NeuSheet

/**
 * v36 hesap açarken onaylanan metinler: Kullanım Şartları ve KVKK Aydınlatma Metni. Metinler `assets/legal/` içinde
 * dile göre (`<ad>_<dil>.txt`; yoksa İngilizce) durur. Basit biçim: `# ` başlık, `## ` ara başlık, `- ` madde.
 */
object LegalTexts {
    /** Onaylanan metinlerin sürümü (kullanıcı kaydında ve sunucuda saklanır; metinler değişince artırılır). */
    const val VERSION = "2026-10-25"

    enum class Doc(val file: String) { TERMS("terms"), KVKK("kvkk") }

    fun load(context: Context, doc: Doc, language: String = I18n.language): String {
        val assets = context.assets
        return listOf(language, "en", "tr").firstNotNullOfOrNull { lang ->
            runCatching { assets.open("legal/${doc.file}_$lang.txt").bufferedReader(Charsets.UTF_8).use { it.readText() } }.getOrNull()
        }.orEmpty()
    }
}

/** v36 yasal metin penceresi: başlıklar, paragraflar ve maddeler; uzun metin kaydırılır. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegalTextSheet(doc: LegalTexts.Doc, onDismiss: () -> Unit) {
    val colors = NeuTheme.colors
    val context = LocalContext.current
    val text = remember(doc) { LegalTexts.load(context, doc) }
    NeuSheet(onDismiss) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().heightIn(max = 640.dp).verticalScroll(rememberScrollState())
                .padding(start = 22.dp, end = 22.dp, bottom = 24.dp).testTag("legal_${doc.file}"),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            text.lines().forEach { raw ->
                val line = raw.trimEnd()
                when {
                    line.isBlank() -> Unit
                    line.startsWith("# ") -> Text(line.removePrefix("# "), translate = false, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    line.startsWith("## ") -> Text(
                        line.removePrefix("## "), translate = false, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                        color = colors.accent, modifier = Modifier.padding(top = 8.dp),
                    )
                    line.startsWith("- ") -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.padding(top = 8.dp).size(5.dp).clip(CircleShape).background(colors.accent))
                        Text(line.removePrefix("- "), translate = false, style = MaterialTheme.typography.bodyMedium)
                    }
                    else -> Text(line, translate = false, style = MaterialTheme.typography.bodyMedium, color = colors.content)
                }
            }
            NeuButton(stringResource(R.string.legal_close), onDismiss, Modifier.fillMaxWidth().padding(top = 8.dp).testTag("legal_close"), primary = true)
        }
    }
}

/**
 * v36 onay satırı: onay kutusu ve içinde bağlantı olan cümle ([template] içindeki `%1$s` yerine [link]); bağlantıya
 * dokununca metin açılır, cümlenin geri kalanına dokununca kutu işaretlenir.
 */
@Composable
fun ConsentRow(checked: Boolean, onChecked: (Boolean) -> Unit, template: String, link: String, onOpen: () -> Unit, tag: String) {
    val colors = NeuTheme.colors
    val parts = template.split("%1\$s", limit = 2)
    val sentence = buildAnnotatedString {
        append(parts.getOrElse(0) { "" })
        withLink(
            LinkAnnotation.Clickable(
                tag = tag,
                styles = TextLinkStyles(SpanStyle(color = colors.accent, fontWeight = FontWeight.SemiBold, textDecoration = TextDecoration.Underline)),
                linkInteractionListener = { onOpen() },
            ),
        ) { append(link) }
        append(parts.getOrElse(1) { "" })
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = checked, onCheckedChange = onChecked, modifier = Modifier.testTag(tag),
            colors = CheckboxDefaults.colors(checkedColor = colors.accent, uncheckedColor = colors.contentMuted, checkmarkColor = colors.onAccent),
        )
        Text(sentence, style = MaterialTheme.typography.bodyMedium, color = colors.content, modifier = Modifier.weight(1f))
    }
}
