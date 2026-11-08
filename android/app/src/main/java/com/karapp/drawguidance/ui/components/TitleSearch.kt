package com.karapp.drawguidance.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.karapp.drawguidance.ui.i18n.Text
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.karapp.drawguidance.ui.theme.NeuFieldShape
import com.karapp.drawguidance.ui.theme.NeuTheme
import com.karapp.drawguidance.ui.theme.neumorphic
import java.util.Locale

/**
 * v33 başlıkta anlık arama: büyük/küçük harf (Türkçe kurallarıyla) ve Türkçe harf farkları (ı/i, ş/s, ğ/g, ü/u, ö/o,
 * ç/c, şapkalı harfler) gözetilmez; aranan sözcüklerin hepsi başlıkta geçmelidir.
 */
object TitleSearch {
    private val tr = Locale.forLanguageTag("tr")

    /** Karşılaştırma anahtarı: küçük harf ve sade harfler. */
    fun key(text: String): String {
        val lower = text.lowercase(tr)
        val out = StringBuilder(lower.length)
        lower.forEach { c ->
            out.append(
                when (c) {
                    'ı', 'î', 'ï', 'í', 'ì' -> 'i'
                    'ş' -> 's'
                    'ğ' -> 'g'
                    'ü', 'û', 'ú', 'ù' -> 'u'
                    'ö', 'ó', 'ò', 'ô' -> 'o'
                    'ç' -> 'c'
                    'â', 'á', 'à', 'ä' -> 'a'
                    'é', 'è', 'ê', 'ë' -> 'e'
                    else -> c
                },
            )
        }
        // "İ".lowercase(tr) tek harf "i" verir; birleşik nokta işareti (U+0307) kalırsa atılır
        return out.toString().replace("̇", "")
    }

    fun matches(title: String, query: String): Boolean {
        val words = key(query).split(' ').filter { it.isNotBlank() }
        if (words.isEmpty()) return true
        val t = key(title)
        return words.all { it in t }
    }

    fun <T> filter(items: List<T>, query: String, title: (T) -> String): List<T> =
        if (query.isBlank()) items else items.filter { matches(title(it), query) }
}

/**
 * Liste sayfalarının üstündeki arama alanı (yazdıkça süzülür). Dokunulana dek yalnız arama kutusu görünümündeki bir
 * düğmedir (sayfadaki öteki yazı alanlarıyla karışmaz); dokununca yazı alanı açılır ve klavye gelir. × aramayı temizler
 * ve kutuyu kapatır.
 */
@Composable
fun TitleSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    clearDescription: String,
    modifier: Modifier = Modifier,
    tag: String = "title_search",
) {
    val colors = NeuTheme.colors
    var active by rememberSaveable { mutableStateOf(query.isNotEmpty()) }
    if (query.isNotEmpty()) active = true
    val focus = remember { FocusRequester() }
    Row(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .neumorphic(colors, NeuFieldShape, elevation = 4.dp, inset = true)
            .clip(NeuFieldShape)
            .then(
                if (active) Modifier
                else Modifier
                    .clickable(role = Role.Button) { active = true }
                    .semantics { contentDescription = placeholder }
                    .testTag(tag),
            )
            .padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Search, null, tint = colors.contentMuted, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (active) {
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.content),
                    cursorBrush = SolidColor(colors.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag(tag).semantics { contentDescription = placeholder },
                    decorationBox = { inner ->
                        if (query.isEmpty()) {
                            Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = colors.contentMuted.copy(alpha = 0.7f), maxLines = 1)
                        }
                        inner()
                    },
                )
                LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
            } else {
                Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = colors.contentMuted.copy(alpha = 0.7f), maxLines = 1)
            }
        }
        if (active) {
            NeuIconButton(Icons.Filled.Close, clearDescription, { onQueryChange(""); active = false }, size = 36.dp, modifier = Modifier.testTag("${tag}_clear"))
        } else {
            Spacer(Modifier.width(10.dp))
        }
    }
}
