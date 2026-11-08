package com.karapp.drawguidance.ui.screens.home

import com.karapp.drawguidance.ui.theme.neuOutline

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.karapp.drawguidance.R
import com.karapp.drawguidance.ui.components.MascotFace
import com.karapp.drawguidance.ui.components.MascotFigure
import com.karapp.drawguidance.ui.components.MascotGesture
import com.karapp.drawguidance.ui.components.MascotLook
import com.karapp.drawguidance.ui.components.rememberTyping
import com.karapp.drawguidance.ui.i18n.Text
import com.karapp.drawguidance.ui.theme.NeuTheme

/**
 * v37 Ana sayfada 12 yaşından küçüklere çocuk modu önerisi: el sallayan maskot Boyacık yanındaki balonda "İstersen en
 * eğlenceli çizimleri yapmaya başla" der (harf harf yazılırken ağzı oynar). Hadi başla bir çocuk tuvali açar, Şimdi
 * değil öneriyi kalıcı olarak kapatır.
 */
@Composable
fun KidsHomeCard(onStart: () -> Unit, onDismiss: (() -> Unit)?, modifier: Modifier = Modifier) {
    val colors = NeuTheme.colors
    val say = stringResource(R.string.kids_home_say)
    val typing = rememberTyping(say)
    val card = RoundedCornerShape(28.dp)
    val bubble = remember { SideBubbleShape() }
    val bubbleFill = if (colors.isDark) Color(0xFF2B2F45) else Color.White
    val ink = if (colors.isDark) Color(0xFFF5F3FF) else Color(0xFF2B2140)
    Box(
        modifier
            .fillMaxWidth()
            .clip(card)
            .background(
                Brush.linearGradient(
                    if (colors.isDark) listOf(Color(0xFF3B2E5A), Color(0xFF4A3B2A))
                    else listOf(Color(0xFFFFF1C9), Color(0xFFFFD6E8), Color(0xFFE3DAFF)),
                ),
            )
            .border(2.dp, Brush.linearGradient(listOf(Color(0xFFFFB938), Color(0xFFA78BFA))), card)
            .testTag("home_kids_card"),
    ) {
        Column(Modifier.padding(start = 10.dp, end = 16.dp, top = 14.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MascotFigure(
                    MascotLook(MascotFace.EXCITED, MascotGesture.WAVE),
                    Modifier.size(112.dp),
                    mouthOpen = typing.mouthOpen,
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        stringResource(R.string.kids_home_title), style = MaterialTheme.typography.labelLarge,
                        color = Color(0xFF8B5CF6), fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 14.dp),
                    )
                    Box(
                        Modifier
                            .neuOutline(colors, bubble)
                            .background(bubbleFill, bubble)
                            .clickable(role = Role.Button, onClick = typing::skip)
                            .padding(start = 22.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
                    ) {
                        // Yazının yeri baştan ayrılır (satırlar kaymaz); yazılmamış kısım saydam
                        val shown = typing.typed.coerceAtMost(say.length)
                        Text(
                            buildAnnotatedString {
                                append(say.substring(0, shown))
                                withStyle(SpanStyle(color = Color.Transparent)) { append(say.substring(shown)) }
                            },
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = ink,
                            modifier = Modifier.semantics { contentDescription = say }.testTag("home_kids_text"),
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Spacer(Modifier.weight(1f))
                // v44 çocuk kilidinde kapatılamaz
                if (onDismiss != null) Text(
                    stringResource(R.string.kids_home_dismiss), style = MaterialTheme.typography.labelLarge, color = ink.copy(alpha = 0.7f),
                    modifier = Modifier.clip(CircleShape).clickable(role = Role.Button, onClick = onDismiss)
                        .padding(horizontal = 14.dp, vertical = 12.dp).testTag("home_kids_dismiss"),
                )
                Row(
                    Modifier
                        .clip(CircleShape)
                        .background(Brush.verticalGradient(listOf(Color(0xFF4ADE80), Color(0xFF16A34A))))
                        .border(2.dp, Color.White.copy(alpha = 0.85f), CircleShape)
                        .clickable(role = Role.Button, onClick = onStart)
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .testTag("home_kids_start"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Filled.FormatColorFill, null, tint = Color.White, modifier = Modifier.size(22.dp))
                    Text(stringResource(R.string.kids_home_start), style = MaterialTheme.typography.titleSmall, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** Yanındaki maskota doğru (soldan; sağdan sola dillerde sağdan) kuyruklu yuvarlak balon. */
private class SideBubbleShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = with(density) { 18.dp.toPx() }.coerceAtMost(size.minDimension / 2f)
        val tail = with(density) { 10.dp.toPx() }
        val ty = size.height * 0.5f
        val left = tail
        val w = size.width
        // Sağdan sola dillerde ayna (kuyruk sağda)
        val rtl = layoutDirection == LayoutDirection.Rtl
        fun x(v: Float) = if (rtl) w - v else v
        val path = Path().apply {
            moveTo(x(left + r), 0f)
            lineTo(x(w - r), 0f)
            quadraticTo(x(w), 0f, x(w), r)
            lineTo(x(w), size.height - r)
            quadraticTo(x(w), size.height, x(w - r), size.height)
            lineTo(x(left + r), size.height)
            quadraticTo(x(left), size.height, x(left), size.height - r)
            lineTo(x(left), ty + tail * 0.8f)
            lineTo(x(0f), ty)
            lineTo(x(left), ty - tail * 0.8f)
            lineTo(x(left), r)
            quadraticTo(x(left), 0f, x(left + r), 0f)
            close()
        }
        return Outline.Generic(path)
    }
}
