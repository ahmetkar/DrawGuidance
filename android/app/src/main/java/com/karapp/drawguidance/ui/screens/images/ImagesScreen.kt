package com.karapp.drawguidance.ui.screens.images

import com.karapp.drawguidance.ui.theme.neuOutline

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.karapp.drawguidance.data.images.ImageSharer
import com.karapp.drawguidance.ui.components.formatFileSize
import com.karapp.drawguidance.data.images.ShareResult
import com.karapp.drawguidance.data.images.ShareTarget
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Folder
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.karapp.drawguidance.R
import com.karapp.drawguidance.data.images.SavedImageEntity
import com.karapp.drawguidance.ui.components.NeuIconButton
import com.karapp.drawguidance.ui.components.NeuSurface
import com.karapp.drawguidance.ui.components.NeuTopBar
import com.karapp.drawguidance.ui.theme.NeuTheme
import com.karapp.drawguidance.ui.theme.neumorphic
import org.koin.androidx.compose.koinViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Kaydedilenler (v42; eski adı Resimlerim): tuvallerden kaydedilen resimler (PNG, JPEG, WebP) ve belgeler (PDF, CBZ). */
@Composable
fun ImagesScreen(
    bottomBar: @Composable () -> Unit = {},
    viewModel: ImagesViewModel = koinViewModel(),
) {
    val colors = NeuTheme.colors
    val images by viewModel.images.collectAsState()
    var preview by remember { mutableStateOf<SavedImageEntity?>(null) }
    var pendingDelete by remember { mutableStateOf<SavedImageEntity?>(null) }
    val dateFormat = remember { SimpleDateFormat("d MMM yyyy, HH:mm", Locale.forLanguageTag("tr")) }

    var gridView by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(true) }
    // v42 Kaydedilenler: varsayılan kayıt klasöründekiler ve öteki konumlar ayrı sekmede
    var otherTab by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var sort by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(ImageSort.NEWEST) }
    var sortMenu by remember { mutableStateOf(false) }
    // Klasörler ve çoklu seçim (resimler ve klasörler üst menüden toplu silinir)
    val folders by viewModel.folders.collectAsState()
    val currentFolderId by viewModel.currentFolder.collectAsState()
    val currentFolder = folders.firstOrNull { it.id == currentFolderId }
    var selection by remember { mutableStateOf<Set<String>>(emptySet()) }
    var deleteSelectionOpen by remember { mutableStateOf(false) }
    var moveOpen by remember { mutableStateOf(false) }
    var renameFolder by remember { mutableStateOf<com.karapp.drawguidance.data.local.FolderEntity?>(null) }
    val selectedImageIds = com.karapp.drawguidance.ui.components.ListSelection.items(selection).mapNotNull { it.toLongOrNull() }
    val selectedFolderIds = com.karapp.drawguidance.ui.components.ListSelection.folders(selection)
    val folderCounts = remember(images) { images.orEmpty().groupingBy { it.folderId }.eachCount() }
    androidx.activity.compose.BackHandler(enabled = selection.isNotEmpty()) { selection = emptySet() }
    androidx.activity.compose.BackHandler(enabled = selection.isEmpty() && currentFolderId != null) { viewModel.openFolder(null) }
    androidx.compose.runtime.LaunchedEffect(images, folders) {
        val existing = images.orEmpty().mapTo(HashSet()) { com.karapp.drawguidance.ui.components.ListSelection.item(it.id.toString()) } +
            folders.map { com.karapp.drawguidance.ui.components.ListSelection.folder(it.id) }
        if (!existing.containsAll(selection)) selection = selection.filterTo(HashSet()) { it in existing }
    }
    val selectionHandler = remember { mutableStateOf<(String) -> Unit>({}) }
    selectionHandler.value = { key -> selection = com.karapp.drawguidance.ui.components.ListSelection.toggle(selection, key) }
    Scaffold(
        containerColor = colors.background,
        bottomBar = bottomBar,
        topBar = {
            if (selection.isNotEmpty()) {
                NeuTopBar(
                    title = stringResource(R.string.selected_items, selection.size),
                    subtitle = stringResource(R.string.selected_items_subtitle),
                    navigation = { NeuIconButton(Icons.Filled.Close, stringResource(R.string.clear_selection), { selection = emptySet() }) },
                    actions = {
                        val singleFolder = selectedFolderIds.singleOrNull()?.takeIf { selectedImageIds.isEmpty() }
                        if (singleFolder != null) {
                            NeuIconButton(Icons.Filled.Edit, stringResource(R.string.folder_rename), { renameFolder = folders.firstOrNull { it.id == singleFolder } })
                        }
                        if (selectedFolderIds.isEmpty()) {
                            NeuIconButton(Icons.AutoMirrored.Filled.DriveFileMove, stringResource(R.string.move_to_folder), { moveOpen = true })
                        }
                        NeuIconButton(Icons.Filled.Delete, stringResource(R.string.delete_selected), { deleteSelectionOpen = true }, tint = colors.danger)
                    },
                )
                return@Scaffold
            }
            if (currentFolder != null) {
                NeuTopBar(
                    title = currentFolder.name,
                    subtitle = stringResource(R.string.images_count, folderCounts[currentFolder.id] ?: 0),
                    navigation = { NeuIconButton(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.folder_up), { viewModel.openFolder(null) }) },
                    actions = {
                        NeuIconButton(
                            if (gridView) Icons.AutoMirrored.Filled.ViewList else Icons.Filled.GridView,
                            stringResource(if (gridView) R.string.images_view_list else R.string.images_view_grid),
                            { gridView = !gridView },
                            size = 42.dp,
                        )
                    },
                )
                return@Scaffold
            }
            NeuTopBar(
                title = stringResource(R.string.images_title),
                subtitle = images?.takeIf { it.isNotEmpty() }?.let { list ->
                    // v42 resimler ve belgeler (PDF, CBZ) birlikte
                    stringResource(R.string.images_count, list.size) + " · " + formatFileSize(list.sumOf { it.sizeBytes })
                },
                actions = {
                    if (!images.isNullOrEmpty()) {
                        Box {
                            NeuIconButton(Icons.AutoMirrored.Filled.Sort, stringResource(R.string.images_sort), { sortMenu = true }, size = 42.dp)
                            androidx.compose.material3.DropdownMenu(
                                expanded = sortMenu,
                                onDismissRequest = { sortMenu = false },
                                modifier = Modifier.background(colors.background),
                            ) {
                                ImageSort.entries.forEach { option ->
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = { Text(stringResource(option.label), color = if (option == sort) colors.accent else colors.content) },
                                        onClick = { sort = option; sortMenu = false },
                                    )
                                }
                            }
                        }
                        NeuIconButton(
                            if (gridView) Icons.AutoMirrored.Filled.ViewList else Icons.Filled.GridView,
                            stringResource(if (gridView) R.string.images_view_list else R.string.images_view_grid),
                            { gridView = !gridView },
                            size = 42.dp,
                        )
                    }
                },
            )
        },
    ) { padding ->
        val everything = images ?: return@Scaffold
        val defaultCount = everything.count { it.defaultLocation }
        val otherCount = everything.size - defaultCount
        // Öteki konumlar sekmesinde klasör yok (klasörler varsayılan kayıt yerindedir)
        val all = remember(everything, otherTab) { everything.filter { it.defaultLocation != otherTab } }
        val list = remember(all, currentFolderId, otherTab) { if (otherTab) all else all.filter { it.folderId == currentFolderId } }
        val rootFolders = if (currentFolderId == null && !otherTab) folders else emptyList()
        Column(Modifier.fillMaxSize().padding(padding)) {
        if (currentFolderId == null && selection.isEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).testTag("saved_tabs"),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                com.karapp.drawguidance.ui.components.NeuChip(
                    stringResource(R.string.v42_saved_tab_default) + " ($defaultCount)", !otherTab, { otherTab = false },
                    Modifier.weight(1f).testTag("saved_tab_default"), icon = Icons.Filled.Folder,
                )
                com.karapp.drawguidance.ui.components.NeuChip(
                    stringResource(R.string.v42_saved_tab_other) + " ($otherCount)", otherTab, { otherTab = true },
                    Modifier.weight(1f).testTag("saved_tab_other"), icon = Icons.Filled.FolderOpen,
                )
            }
        }
        val inner = PaddingValues(0.dp)
        if (all.isEmpty() && rootFolders.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(32.dp).testTag("saved_empty"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(Modifier.size(110.dp).neumorphic(colors, CircleShape, elevation = 12.dp), contentAlignment = Alignment.Center) {
                    Icon(if (otherTab) Icons.Filled.FolderOpen else Icons.Filled.Bookmark, null, tint = colors.accent, modifier = Modifier.size(48.dp))
                }
                Spacer(Modifier.height(24.dp))
                Text(
                    stringResource(if (otherTab) R.string.v42_saved_empty_other else R.string.images_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.contentMuted,
                    textAlign = TextAlign.Center,
                )
            }
            return@Column
        }
        val sorted = remember(list, sort) { sort.apply(list) }
        val state = GalleryState(
            folders = rootFolders,
            folderCounts = folderCounts,
            selection = selection,
            onOpen = { image ->
                val key = com.karapp.drawguidance.ui.components.ListSelection.item(image.id.toString())
                if (selection.isNotEmpty()) selectionHandler.value(key) else preview = image
            },
            onLongClick = { image -> selectionHandler.value(com.karapp.drawguidance.ui.components.ListSelection.item(image.id.toString())) },
            onFolder = { f ->
                val key = com.karapp.drawguidance.ui.components.ListSelection.folder(f.id)
                if (selection.isNotEmpty()) selectionHandler.value(key) else viewModel.openFolder(f.id)
            },
            onFolderLongClick = { f -> selectionHandler.value(com.karapp.drawguidance.ui.components.ListSelection.folder(f.id)) },
            onDelete = { pendingDelete = it },
        )
        if (gridView) {
            ImageGallery(sorted, sort, inner, state)
        } else {
            ImageList(sorted, dateFormat, inner, state)
        }
        }
    }

    preview?.let { image ->
        val context = LocalContext.current
        var shareProblem by remember(image.id) { mutableStateOf<ShareResult?>(null) }
        val chooserTitle = stringResource(R.string.share_chooser_title)
        Dialog(onDismissRequest = { preview = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            val maxImageHeight = (LocalConfiguration.current.screenHeightDp * 0.5f).dp
            NeuSurface(modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth().padding(20.dp), elevation = 10.dp) {
                Column(
                    Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(image.title, translate = false, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        NeuIconButton(Icons.Filled.Close, stringResource(R.string.close), { preview = null }, size = 38.dp)
                    }
                    // v42 belge (PDF, CBZ, TIFF): türü, konumu ve "Aç" düğmesi
                    if (!image.isImage) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            DocumentThumb(image, Modifier.size(width = 84.dp, height = 108.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(stringResource(R.string.v42_saved_document, image.typeLabel, formatFileSize(image.sizeBytes)), style = MaterialTheme.typography.bodyMedium)
                                Text(image.location, translate = false, style = MaterialTheme.typography.labelSmall, color = colors.contentMuted)
                                com.karapp.drawguidance.ui.components.NeuButton(
                                    stringResource(R.string.v42_saved_open),
                                    { shareProblem = ImageSharer.open(context, image.uri, image.mime).takeIf { it !is ShareResult.Started } },
                                    Modifier.testTag("saved_open"), primary = true, icon = Icons.AutoMirrored.Filled.OpenInNew,
                                )
                            }
                        }
                    } else
                    // Yatay ekranda resim yüksekliği sınırlanır, oran korunur
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        AsyncImage(
                            model = image.uri,
                            contentDescription = image.title,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .heightIn(max = maxImageHeight)
                                .aspectRatio(image.width.toFloat() / image.height.coerceAtLeast(1))
                                .clip(RoundedCornerShape(12.dp)),
                        )
                    }
                    Text(stringResource(R.string.share_title), style = MaterialTheme.typography.labelLarge, color = colors.contentMuted)
                    // Uygulamalara paylaşım (Android paylaşma niyeti; API anahtarı gerekmez)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf(
                            Triple(ShareTarget.WHATSAPP, R.string.share_whatsapp, Icons.AutoMirrored.Filled.Chat),
                            Triple(ShareTarget.INSTAGRAM, R.string.share_instagram, Icons.Filled.PhotoCamera),
                            Triple(ShareTarget.PINTEREST, R.string.share_pinterest, Icons.Filled.PushPin),
                            Triple(ShareTarget.OTHER, R.string.share_other, Icons.Filled.Share),
                        ).forEach { (target, label, icon) ->
                            ShareButton(
                                label = stringResource(label),
                                icon = icon,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    val result = ImageSharer.share(context, image.uri, target, chooserTitle, image.mime)
                                    shareProblem = result.takeIf { it !is ShareResult.Started }
                                },
                            )
                        }
                    }
                    when (val problem = shareProblem) {
                        is ShareResult.NotInstalled -> Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.share_not_installed, problem.target.displayName),
                                style = MaterialTheme.typography.bodyMedium, color = colors.danger, modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { ImageSharer.openStore(context, problem.target) }) {
                                Text(stringResource(R.string.share_open_store), color = colors.accent)
                            }
                        }
                        ShareResult.FileMissing -> Text(stringResource(R.string.share_file_missing), style = MaterialTheme.typography.bodyMedium, color = colors.danger)
                        ShareResult.NoViewer -> Text(stringResource(R.string.v42_saved_no_viewer), style = MaterialTheme.typography.bodyMedium, color = colors.danger, modifier = Modifier.testTag("saved_no_viewer"))
                        else -> Unit
                    }
                }
            }
        }
    }

    pendingDelete?.let { image ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = colors.background,
            title = { Text(stringResource(R.string.delete_image)) },
            text = { Text(stringResource(R.string.delete_image_confirm), color = colors.contentMuted) },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(image.id); pendingDelete = null }) {
                    Text(stringResource(R.string.delete_image), color = colors.danger)
                }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.cancel_button)) } },
        )
    }

    if (deleteSelectionOpen) {
        val inFolders = selectedFolderIds.sumOf { folderCounts[it] ?: 0 }
        AlertDialog(
            onDismissRequest = { deleteSelectionOpen = false },
            containerColor = colors.background,
            title = { Text(stringResource(R.string.delete_selected_title, selection.size)) },
            text = { Text(stringResource(R.string.delete_selected_images_text, selectedImageIds.size, selectedFolderIds.size, inFolders), color = colors.contentMuted) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteSelection(selectedImageIds, selectedFolderIds)
                    selection = emptySet()
                    deleteSelectionOpen = false
                }) { Text(stringResource(R.string.delete_selected), color = colors.danger) }
            },
            dismissButton = { TextButton(onClick = { deleteSelectionOpen = false }) { Text(stringResource(R.string.cancel_button)) } },
        )
    }
    if (moveOpen) {
        com.karapp.drawguidance.ui.components.MoveToFolderDialog(
            folders = folders,
            current = currentFolderId,
            onMove = { target -> moveOpen = false; viewModel.moveImages(selectedImageIds, target); selection = emptySet() },
            onNewFolder = { name -> moveOpen = false; viewModel.createFolder(name, selectedImageIds); selection = emptySet() },
            onDismiss = { moveOpen = false },
        )
    }
    renameFolder?.let { folder ->
        com.karapp.drawguidance.ui.components.FolderNameDialog(
            stringResource(R.string.folder_rename), folder.name,
            onConfirm = { name -> renameFolder = null; viewModel.renameFolder(folder.id, name); selection = emptySet() },
            onDismiss = { renameFolder = null },
        )
    }
}

@Composable
private fun ShareButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    val colors = NeuTheme.colors
    val description = stringResource(R.string.share_cd, label)
    NeuSurface(modifier = modifier.semantics(mergeDescendants = true) { contentDescription = description }, elevation = 5.dp, onClick = onClick) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(icon, null, tint = colors.accent, modifier = Modifier.size(22.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Resim sıralaması. */
enum class ImageSort(val label: Int) {
    NEWEST(R.string.images_sort_newest), OLDEST(R.string.images_sort_oldest), NAME(R.string.images_sort_name), SIZE(R.string.images_sort_size);

    fun apply(list: List<SavedImageEntity>): List<SavedImageEntity> = when (this) {
        NEWEST -> list.sortedByDescending { it.createdAt }
        OLDEST -> list.sortedBy { it.createdAt }
        NAME -> list.sortedBy { it.title.lowercase(Locale.forLanguageTag("tr")) }
        SIZE -> list.sortedByDescending { it.sizeBytes }
    }
}

/**
 * Galeri görünümü (çizim uygulamalarının galerileri gibi): resimler kendi en/boy oranlarıyla basamaklı ızgarada,
 * gölgeli yuvarlak köşeli önizlemelerle; altlarında adı, ölçüsü ve dosya boyutu. Tarihe göre sıralıyken bölüm başlıkları var.
 */
/** Galeri ve liste görünümlerinin ortak durumu: klasörler, seçim ve dokunma işleri. */
private class GalleryState(
    val folders: List<com.karapp.drawguidance.data.local.FolderEntity>,
    val folderCounts: Map<String?, Int>,
    val selection: Set<String>,
    val onOpen: (SavedImageEntity) -> Unit,
    val onLongClick: (SavedImageEntity) -> Unit,
    val onFolder: (com.karapp.drawguidance.data.local.FolderEntity) -> Unit,
    val onFolderLongClick: (com.karapp.drawguidance.data.local.FolderEntity) -> Unit,
    val onDelete: (SavedImageEntity) -> Unit,
) {
    fun selected(image: SavedImageEntity) = com.karapp.drawguidance.ui.components.ListSelection.item(image.id.toString()) in selection
    fun selected(folder: com.karapp.drawguidance.data.local.FolderEntity) = com.karapp.drawguidance.ui.components.ListSelection.folder(folder.id) in selection
}

@Composable
private fun ImageGallery(
    list: List<SavedImageEntity>,
    sort: ImageSort,
    padding: PaddingValues,
    state: GalleryState,
) {
    val sections = sort == ImageSort.NEWEST || sort == ImageSort.OLDEST
    androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid(
        columns = androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells.Adaptive(minSize = 156.dp),
        modifier = Modifier.fillMaxSize().padding(padding).testTag("image_gallery"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalItemSpacing = 16.dp,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (state.folders.isNotEmpty()) {
            item(key = "folders-header", span = androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan.FullLine) {
                com.karapp.drawguidance.ui.components.GallerySectionHeader(stringResource(R.string.folders_title))
            }
            state.folders.forEach { f ->
                item(key = "folder-" + f.id) {
                    com.karapp.drawguidance.ui.components.FolderTile(
                        f, state.folderCounts[f.id] ?: 0, state.selected(f), { state.onFolder(f) }, { state.onFolderLongClick(f) },
                    )
                }
            }
        }
        var lastSection = -1
        list.forEach { image ->
            if (sections) {
                val section = com.karapp.drawguidance.ui.components.gallerySection(image.createdAt)
                if (section != lastSection) {
                    lastSection = section
                    item(key = "section-$section", span = androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan.FullLine) {
                        com.karapp.drawguidance.ui.components.GallerySectionHeader(stringResource(section))
                    }
                }
            }
            item(key = image.id) { GalleryCard(image, state) }
        }
    }
}

@Composable
private fun GalleryCard(
    image: SavedImageEntity,
    state: GalleryState,
) {
    val onDelete = state.onDelete
    val selected = state.selected(image)
    com.karapp.drawguidance.ui.components.GalleryTile(
        title = image.title,
        subtitle = if (image.isImage) stringResource(R.string.image_size_line, image.width, image.height, formatFileSize(image.sizeBytes))
        else stringResource(R.string.v42_saved_document, image.typeLabel, formatFileSize(image.sizeBytes)),
        ratio = if (image.isImage) image.width.toFloat() / image.height.coerceAtLeast(1) else 0.78f,
        onClick = { state.onOpen(image) },
        onLongClick = { state.onLongClick(image) },
        selected = selected,
    ) {
        if (image.isImage) AsyncImage(
            model = image.uri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        ) else DocumentThumb(image, Modifier.fillMaxSize())
        TypeBadge(image, Modifier.align(Alignment.BottomStart).padding(6.dp))
        // Sağ üstte küçük silme düğmesi
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                .size(28.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.32f))
                .androidx_clickable { onDelete(image) }
                .semantics(mergeDescendants = true) {},
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Delete, stringResource(R.string.delete_image), tint = Color.White, modifier = Modifier.size(16.dp))
        }
        if (selected) com.karapp.drawguidance.ui.components.SelectedCheck(Modifier.align(Alignment.TopStart).padding(8.dp))
    }
}

private fun Modifier.androidx_clickable(onClick: () -> Unit) = this.then(Modifier.clickable(onClick = onClick))

/** Liste görünümü: sade satırlar (kutu yok) — küçük resim, ad, boyut ve tarih, silme. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ImageList(
    list: List<SavedImageEntity>,
    dateFormat: SimpleDateFormat,
    padding: PaddingValues,
    state: GalleryState,
) {
    val onDelete = state.onDelete
    val colors = NeuTheme.colors
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 340.dp),
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        items(state.folders, key = { "folder-" + it.id }) { f ->
            com.karapp.drawguidance.ui.components.FolderRow(
                f, state.folderCounts[f.id] ?: 0, state.selected(f), { state.onFolder(f) }, { state.onFolderLongClick(f) },
            )
        }
        items(list, key = { it.id }) { image ->
            val selected = state.selected(image)
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .then(if (selected) Modifier.background(colors.accent.copy(alpha = 0.10f)) else Modifier)
                    .combinedClickable(onClick = { state.onOpen(image) }, onLongClick = { state.onLongClick(image) })
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(64.dp)
                        .neuOutline(com.karapp.drawguidance.ui.theme.NeuTheme.colors, RoundedCornerShape(12.dp))
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White),
                ) {
                    if (image.isImage) AsyncImage(model = image.uri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    else DocumentThumb(image, Modifier.fillMaxSize())
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(image.title, translate = false, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        (if (image.isImage) stringResource(R.string.image_size_line, image.width, image.height, formatFileSize(image.sizeBytes)) + " · " + image.typeLabel
                        else stringResource(R.string.v42_saved_document, image.typeLabel, formatFileSize(image.sizeBytes))) +
                            " · " + dateFormat.format(Date(image.createdAt)),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.contentMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                androidx.compose.material3.IconButton(onClick = { onDelete(image) }) {
                    Icon(Icons.Filled.Delete, stringResource(R.string.delete_image), tint = colors.danger.copy(alpha = 0.8f))
                }
            }
        }
    }
}


/** v42 belgenin (PDF, CBZ, TIFF) küçük resmi: türünün simgesi ve adı. */
@Composable
private fun DocumentThumb(image: SavedImageEntity, modifier: Modifier) {
    val colors = NeuTheme.colors
    val icon = when (image.typeLabel) {
        "PDF" -> Icons.Filled.PictureAsPdf
        "CBZ" -> Icons.AutoMirrored.Filled.MenuBook
        else -> Icons.Filled.Description
    }
    Box(modifier.background(colors.accent.copy(alpha = 0.10f)).testTag("doc_thumb_${image.id}"), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, null, tint = colors.accent, modifier = Modifier.size(36.dp))
            Text(image.typeLabel, translate = false, style = MaterialTheme.typography.labelLarge, color = colors.accent)
        }
    }
}

/** v42 dosya türü rozeti (PNG, JPEG, PDF, CBZ). */
@Composable
private fun TypeBadge(image: SavedImageEntity, modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.45f)).padding(horizontal = 6.dp, vertical = 2.dp)) {
        Text(image.typeLabel, translate = false, style = MaterialTheme.typography.labelSmall, color = Color.White)
    }
}
