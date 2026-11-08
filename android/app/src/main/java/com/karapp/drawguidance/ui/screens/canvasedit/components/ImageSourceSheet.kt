package com.karapp.drawguidance.ui.screens.canvasedit.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import com.karapp.drawguidance.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.karapp.drawguidance.R
import com.karapp.drawguidance.data.images.SavedImageEntity
import com.karapp.drawguidance.ui.components.NeuButton
import com.karapp.drawguidance.ui.components.NeuSurface
import com.karapp.drawguidance.ui.theme.NeuTheme
import com.karapp.drawguidance.ui.components.NeuSheet

/**
 * v29 resim ekleme: iki ayrı seçenek — cihazdan (galeri / dosyalar) seçmek ya da uygulamada kaydedilmiş resimlerden
 * (Resimlerim) seçmek. İkincisinde kayıtlı resimler küçük resimlerle listelenir.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageSourceSheet(
    savedImages: List<SavedImageEntity>,
    onDevice: () -> Unit,
    onSaved: (SavedImageEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = NeuTheme.colors
    var showSaved by rememberSaveable { mutableStateOf(false) }
    NeuSheet(onDismiss) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 20.dp).testTag("image_source_sheet"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.image_source_title), style = MaterialTheme.typography.titleLarge)
            NeuButton(
                stringResource(R.string.image_source_device), onDevice,
                Modifier.fillMaxWidth().testTag("image_source_device"), icon = Icons.Filled.PhoneAndroid, primary = !showSaved,
            )
            NeuButton(
                stringResource(R.string.image_source_saved), { showSaved = true },
                Modifier.fillMaxWidth().testTag("image_source_saved"), icon = Icons.Filled.Collections, primary = showSaved,
            )
            if (showSaved) {
                if (savedImages.isEmpty()) {
                    Text(stringResource(R.string.image_source_saved_empty), style = MaterialTheme.typography.bodyMedium, color = colors.contentMuted)
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(104.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp).testTag("saved_image_grid"),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(savedImages, key = { it.id }) { image ->
                            NeuSurface(
                                modifier = Modifier.fillMaxWidth().semantics { contentDescription = image.title }.testTag("saved_image_${image.id}"),
                                elevation = 4.dp,
                                onClick = { onSaved(image) },
                            ) {
                                Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    AsyncImage(
                                        model = image.uri, contentDescription = null, contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(10.dp)),
                                    )
                                    Text(image.title, translate = false, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
