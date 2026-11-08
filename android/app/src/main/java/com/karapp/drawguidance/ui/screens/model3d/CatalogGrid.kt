package com.karapp.drawguidance.ui.screens.model3d

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.karapp.drawguidance.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.karapp.drawguidance.content.model3d.Catalog3D
import com.karapp.drawguidance.content.model3d.CatalogItem
import com.karapp.drawguidance.content.model3d.RenderOptions
import com.karapp.drawguidance.content.model3d.Renderer
import com.karapp.drawguidance.content.model3d.TextureCatalog
import com.karapp.drawguidance.content.model3d.View3
import com.karapp.drawguidance.ui.components.NeuChip
import com.karapp.drawguidance.ui.theme.NeuTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/** v24 hazır şekillerin küçük resimleri (bir kez çizilir, bellekte tutulur; aynı anda en çok iki çizim). */
object CatalogThumbs {
    private val cache = HashMap<String, ImageBitmap>()
    // v25 giriş düzeyi cihazlarda aynı anda tek çizim (bellek ve ısınma)
    private val gate = Semaphore(if (Budget3D.tier == com.karapp.drawguidance.platform.DeviceCaps.Tier.LOW) 1 else 2)
    private val textures = HashMap<String, com.karapp.drawguidance.content.model3d.Texture?>()

    private fun texture(id: String) = synchronized(textures) { textures.getOrPut(id) { TextureCatalog.byId(id)?.generate(64) } }
    private const val SIZE = 144

    /** v27 disk önbelleği klasörü (uygulama açılışında verilir; null: yalnız bellekte). */
    @Volatile var dir: java.io.File? = null

    /** Küçük resim çizimi değişirse artırılır (eski dosyalar kullanılmaz). */
    private const val DISK_VERSION = 1

    fun cached(id: String): ImageBitmap? = synchronized(cache) { cache[id] }

    private fun file(id: String): java.io.File? = dir?.let { java.io.File(it, "${id.replace(Regex("[^A-Za-z0-9_.-]"), "_")}-$SIZE-v$DISK_VERSION.png") }

    suspend fun get(item: CatalogItem): ImageBitmap {
        cached(item.id)?.let { return it }
        // v27 diskteki küçük resim (önceki oturumdan): çizim yerine hızlı çözme
        withContext(Dispatchers.IO) {
            file(item.id)?.takeIf { it.isFile }?.let { f -> runCatching { android.graphics.BitmapFactory.decodeFile(f.path)?.asImageBitmap() }.getOrNull() }
        }?.let { img -> synchronized(cache) { cache[item.id] = img }; return img }
        return gate.withPermit {
            cached(item.id) ?: withContext(Dispatchers.Default) {
                val m = item.build()
                val f = Renderer.render(
                    m, View3.fit(m, m.camera, SIZE, SIZE, 0.08f),
                    RenderOptions(textures = ::texture, supersample = 2, ground = false, background = 0),
                )
                val bmp = Bitmap.createBitmap(f.color, SIZE, SIZE, Bitmap.Config.ARGB_8888)
                file(item.id)?.let { out ->
                    runCatching {
                        out.parentFile?.mkdirs()
                        val tmp = java.io.File(out.path + ".tmp")
                        tmp.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                        tmp.renameTo(out)
                    }
                }
                bmp.asImageBitmap().also { synchronized(cache) { cache[item.id] = it } }
            }
        }
    }
}

/**
 * v24 hazır 3B şekiller ızgarası: kategori seçimi (Geometri, Fizik, Kimya, Biyoloji, Astronomi, Çizim) ve o
 * kategorinin şekilleri; dokununca [onPick].
 */
@Composable
fun CatalogGrid(
    onPick: (CatalogItem) -> Unit, modifier: Modifier = Modifier, columns: Int = 4, tile: Dp = 64.dp, selected: String? = null,
    /** v33 başlıkta arama: doluyken bütün kategorilerde ada göre süzülür. */
    query: String = "",
    /** v33 basit çizimde yalnız bu hazır şekiller (sırasıyla, kategorisiz). */
    only: List<String>? = null,
) {
    val colors = NeuTheme.colors
    var category by rememberSaveable { mutableStateOf(Catalog3D.categories.first()) }
    val searching = query.isNotBlank()
    val items = when {
        only != null -> only.mapNotNull { id -> Catalog3D.all.firstOrNull { it.id == id } }
        searching -> com.karapp.drawguidance.ui.components.TitleSearch.filter(Catalog3D.all, query) { it.name }
        else -> Catalog3D.all.filter { it.category == category }
    }
    Column(modifier.testTag("model3d_catalog"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (!searching && only == null) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Catalog3D.categories.forEach { c -> NeuChip(c, c == category, onClick = { category = c }) }
        }
        if (searching && items.isEmpty()) {
            Text(
                androidx.compose.ui.res.stringResource(com.karapp.drawguidance.R.string.search_no_result, query.trim()),
                style = MaterialTheme.typography.bodyMedium, color = colors.contentMuted,
                modifier = Modifier.padding(8.dp).testTag("model3d_catalog_empty"),
            )
        }
        items.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { item ->
                    // v27 pencere açılırken (kayma animasyonu) çizim başlamaz: birkaç kare sonra
                    val thumb = com.karapp.drawguidance.ui.components.rememberKeyedThumb(
                        item.id, CatalogThumbs::cached, settleFrames = SETTLE_FRAMES, dispatcher = kotlinx.coroutines.Dispatchers.Main.immediate,
                    ) { CatalogThumbs.get(item) }
                    val chosen = item.id == selected
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                            // v25 seçili şekil vurgulanır (tuvalde "3B şekil ekle" penceresi)
                            .then(if (chosen) Modifier.background(colors.accent.copy(alpha = 0.16f)).border(2.dp, colors.accent, RoundedCornerShape(12.dp)) else Modifier)
                            .clickable { onPick(item) }
                            .semantics { contentDescription = item.name; if (chosen) this.selected = true }.padding(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(Modifier.size(tile).aspectRatio(1f).clip(RoundedCornerShape(10.dp)).background(colors.background), contentAlignment = Alignment.Center) {
                            val t = thumb
                            if (t != null) Image(t, null, Modifier.fillMaxSize()) else Icon(Icons.Filled.ViewInAr, null, tint = colors.contentMuted.copy(alpha = 0.4f))
                        }
                        Text(item.name, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** v27 pencere açılış animasyonu süresince (kare) küçük resim üretimi bekler. */
internal const val SETTLE_FRAMES = 14
