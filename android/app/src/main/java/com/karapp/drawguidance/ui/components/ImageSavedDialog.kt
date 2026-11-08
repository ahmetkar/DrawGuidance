package com.karapp.drawguidance.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import com.karapp.drawguidance.ui.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.karapp.drawguidance.R
import com.karapp.drawguidance.data.images.SavedImageEntity
import com.karapp.drawguidance.ui.theme.NeuTheme

/** Dosya boyutunu okunur biçimde yazar (ör. 245,3 KB). */
fun formatFileSize(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> String.format(java.util.Locale.forLanguageTag("tr"), "%.2f MB", bytes / (1024f * 1024f))
    bytes >= 1024L -> String.format(java.util.Locale.forLanguageTag("tr"), "%.1f KB", bytes / 1024f)
    else -> "$bytes B"
}

/** Resim kaydedildikten sonra boyutunu (piksel ve dosya) ve kaydedildiği konumu gösteren pencere. */
@Composable
fun ImageSavedDialog(image: SavedImageEntity, onDismiss: () -> Unit, totalCount: Int = 1) {
    val colors = NeuTheme.colors
    // Yatay telefonda ekran kısa: küçük önizleme, bilgiler kaydırılabilir
    val thumbMax = if (LocalConfiguration.current.screenHeightDp < 500) 72.dp else 140.dp
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.background,
        modifier = Modifier.testTag("image_saved_dialog"),
        title = { Text(stringResource(R.string.image_saved_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    AsyncImage(
                        model = image.uri,
                        contentDescription = image.title,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.heightIn(max = thumbMax).clip(RoundedCornerShape(10.dp)).background(Color.White),
                    )
                }
                InfoRow(stringResource(R.string.image_saved_dimensions_label), stringResource(R.string.image_saved_dimensions, image.width, image.height))
                InfoRow(stringResource(R.string.image_saved_file_size_label), formatFileSize(image.sizeBytes))
                InfoRow(stringResource(R.string.image_saved_location_label), image.location)
                if (totalCount > 1) {
                    Text(stringResource(R.string.image_saved_more, totalCount), style = MaterialTheme.typography.bodyMedium, color = colors.accent)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok_button), color = colors.accent) } },
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    val colors = NeuTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = colors.contentMuted)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = colors.content)
    }
}
