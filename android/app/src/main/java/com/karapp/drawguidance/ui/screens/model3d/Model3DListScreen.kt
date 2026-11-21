package com.karapp.drawguidance.ui.screens.model3d

import androidx.compose.ui.semantics.selected
import androidx.compose.foundation.border
import android.graphics.BitmapFactory
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import com.karapp.drawguidance.content.model3d.Catalog3D
import com.karapp.drawguidance.ui.components.NeuChip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import com.karapp.drawguidance.ui.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karapp.drawguidance.R
import com.karapp.drawguidance.data.library.Model3DRepository
import com.karapp.drawguidance.data.local.Model3DInfo
import com.karapp.drawguidance.ui.components.NeuButton
import com.karapp.drawguidance.ui.components.NeuIconButton
import com.karapp.drawguidance.ui.components.NeuSurface
import com.karapp.drawguidance.ui.components.NeuTopBar
import com.karapp.drawguidance.ui.theme.NeuTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

class Model3DListViewModel(private val repository: Model3DRepository) : ViewModel() {
    val models: StateFlow<List<Model3DInfo>> = repository.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun delete(id: String) { viewModelScope.launch { repository.delete(listOf(id)) } }
}

/**
 * Küçük resmin çözülmüş hali (kimlik ve güncelleme zamanına göre bir kez). v27: PNG ana iş parçacığında değil arka
 * planda çözülür ve ortak önbellekte tutulur (pencereler açılırken takılma olmasın; yeniden açınca hemen görünür).
 */
@Composable
fun rememberThumb(info: Model3DInfo): ImageBitmap? {
    val key = "${info.id}@${info.updatedAt}"
    return com.karapp.drawguidance.ui.components.rememberKeyedThumb(key, Model3DThumbs::get, dispatcher = kotlinx.coroutines.Dispatchers.IO) { k ->
        val bytes = info.thumb ?: return@rememberKeyedThumb null
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()?.also { Model3DThumbs.put(k, it) }
    }
}

/** v27 3B şekil küçük resimleri önbelleği (bayt sınırlı). */
private object Model3DThumbs {
    private val cache = object : android.util.LruCache<String, ImageBitmap>(8 shl 20) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }
    init { com.karapp.drawguidance.platform.DeviceCaps.cache(editor = false) { synchronized(cache) { cache.evictAll() } } }

    fun get(key: String): ImageBitmap? = cache.get(key)

    fun put(key: String, img: ImageBitmap) { cache.put(key, img) }
}

/** 3B şeklin kartı: küçük resim ve ad; [onEdit] verilirse kalem düğmesi. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Model3DCard(
    info: Model3DInfo, onClick: () -> Unit, modifier: Modifier = Modifier, onLongClick: (() -> Unit)? = null, onEdit: (() -> Unit)? = null,
    selected: Boolean = false,
) {
    val colors = NeuTheme.colors
    val thumb = rememberThumb(info)
    NeuSurface(modifier.semantics { contentDescription = info.name; if (selected) this.selected = true }, elevation = 5.dp) {
        Column(
            Modifier.clip(RoundedCornerShape(18.dp))
                // v25 seçili kart vurgulanır (tuvalde "3B şekil ekle" penceresi)
                .then(if (selected) Modifier.background(colors.accent.copy(alpha = 0.14f)).border(2.dp, colors.accent, RoundedCornerShape(18.dp)) else Modifier)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick).padding(8.dp),
        ) {
            Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(colors.background), contentAlignment = Alignment.Center) {
                if (thumb != null) Image(thumb, null, Modifier.fillMaxSize())
                else Icon(Icons.Filled.ViewInAr, null, tint = colors.contentMuted, modifier = Modifier.size(36.dp))
                if (onEdit != null) {
                    NeuIconButton(Icons.Filled.Edit, "${info.name}: " + stringResource(R.string.model3d_edit), onEdit, Modifier.align(Alignment.TopEnd).padding(2.dp), size = 32.dp)
                }
            }
            Text(info.name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp, start = 2.dp))
        }
    }
}

/** İçe aktarılacak dosya (ayrıntı seçilene kadar). */

/**
 * v56 aşama 10 ana sekmedeki **3B Çizimlerim**: kullanıcının 3B çizimleri (eski 3B Şekillerim kayıtları dahil).
 * GLB içe/dışa aktarma ve hazır şekiller sekmesi kalktı (kütüphane yalnız tuvale eklemede). Uzun basınca silinir.
 */
@Composable
fun Model3DListScreen(
    onOpen: (String?) -> Unit,
    bottomBar: @Composable () -> Unit,
    vm: Model3DListViewModel = koinViewModel(),
) {
    val colors = NeuTheme.colors
    val models by vm.models.collectAsState()
    var deleting by remember { mutableStateOf<Model3DInfo?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    val shownModels = remember(models, query) { com.karapp.drawguidance.ui.components.TitleSearch.filter(models, query) { it.name } }

    Scaffold(containerColor = colors.background, bottomBar = bottomBar) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).statusBarsPadding().testTag("models3d_screen")) {
            NeuTopBar(
                title = stringResource(R.string.v56_m3_my_drawings),
                subtitle = stringResource(R.string.v56_m3_list_hint),
                actions = {
                    NeuButton(stringResource(R.string.v56_m3_new_drawing), { onOpen(null) }, Modifier.testTag("models3d_new"), primary = true, icon = Icons.Filled.Add)
                },
            )
            com.karapp.drawguidance.ui.components.TitleSearchField(
                query = query,
                onQueryChange = { query = it },
                placeholder = stringResource(R.string.search_models_hint),
                clearDescription = stringResource(R.string.search_clear),
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 2.dp),
                tag = "models3d_search",
            )
            if (models.isEmpty()) {
                Text(stringResource(R.string.v56_m3_list_empty), style = MaterialTheme.typography.bodyMedium, color = colors.contentMuted, modifier = Modifier.padding(24.dp))
            } else if (shownModels.isEmpty()) {
                Text(
                    stringResource(R.string.search_no_result, query.trim()), style = MaterialTheme.typography.bodyMedium, color = colors.contentMuted,
                    modifier = Modifier.padding(24.dp).testTag("models3d_search_empty"),
                )
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(150.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(shownModels, key = { it.id }) { info ->
                    Model3DCard(info, onClick = { onOpen(info.id) }, onLongClick = { deleting = info })
                }
            }
        }
    }
    deleting?.let { info ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            containerColor = colors.background,
            title = { Text(stringResource(R.string.model3d_delete_title, info.name)) },
            text = { Text(stringResource(R.string.model3d_delete_text)) },
            confirmButton = { TextButton(onClick = { vm.delete(info.id); deleting = null }) { Text(stringResource(R.string.delete_button), color = colors.danger) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel_button)) } },
        )
    }
}

/** v27 seçilen GLB dosyası (ad ve içerik). */
class PendingGlb(val name: String, val bytes: ByteArray)

/** v27 GLB/glTF seçicisini açan, dosyayı arka planda okuyup [onRead]'e veren yardımcı (okunamazsa [onFail]). */
@Composable
fun rememberGlbPicker(onRead: (PendingGlb) -> Unit, onFail: (String) -> Unit): () -> Unit {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val r = runCatching {
                withContext(Dispatchers.IO) {
                    val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                        if (c.moveToFirst()) c.getString(0) else null
                    } ?: "model"
                    // Çok büyük dosyalar belleği aşmasın (giriş düzeyi cihazlar)
                    val size = context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: -1L
                    if (size > MAX_GLB_BYTES) error(com.karapp.drawguidance.i18n.I18n.trf("dosya çok büyük ({0} MB)", size / 1_048_576))
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("dosya okunamadı")
                    PendingGlb(name.substringBeforeLast('.'), bytes)
                }
            }
            r.onSuccess(onRead).onFailure { onFail(it.message ?: it.javaClass.simpleName) }
        }
    }
    return { runCatching { launcher.launch(arrayOf("model/gltf-binary", "model/gltf+json", "application/octet-stream", "*/*")) }.onFailure { onFail(it.message ?: "seçici açılamadı") } }
}

/** v27 içe aktarılan GLB dosyasının en büyük boyutu. */
private const val MAX_GLB_BYTES = 96L shl 20

