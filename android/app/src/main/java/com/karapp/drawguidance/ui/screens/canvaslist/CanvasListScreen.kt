package com.karapp.drawguidance.ui.screens.canvaslist

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.ui.platform.testTag
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import com.karapp.drawguidance.ui.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.karapp.drawguidance.R
import com.karapp.drawguidance.data.model.CanvasUi
import com.karapp.drawguidance.ui.components.BrushIcons
import com.karapp.drawguidance.ui.components.NeuButton
import com.karapp.drawguidance.ui.components.NeuIconButton
import com.karapp.drawguidance.ui.components.NeuTopBar
import com.karapp.drawguidance.ui.screens.canvaslist.components.CanvasCard
import com.karapp.drawguidance.ui.screens.canvaslist.components.CanvasFormInitial
import com.karapp.drawguidance.ui.screens.canvaslist.components.CanvasFormModal
import com.karapp.drawguidance.ui.theme.NeuTheme
import com.karapp.drawguidance.ui.theme.neumorphic
import org.koin.androidx.compose.koinViewModel

@Composable
fun CanvasListScreen(
    onOpenCanvas: (localId: String) -> Unit,
    bottomBar: @Composable () -> Unit = {},
    viewModel: CanvasListViewModel = koinViewModel(),
) {
    val colors = NeuTheme.colors
    val allCanvases by viewModel.canvases.collectAsState()
    // v44 çocuk kilidinde yalnız çocuk tuvalleri
    val kidsLocked = com.karapp.drawguidance.ui.kids.LocalKidsLocked.current
    val canvases = remember(allCanvases, kidsLocked) {
        if (kidsLocked) allCanvases.filter { it.kind == com.karapp.drawguidance.data.model.CanvasKind.KIDS } else allCanvases
    }
    val savingImageIds by viewModel.savingImageIds.collectAsState()
    // v40 üstteki özet kutuları (toplam, türler, bu hafta, çizim süresi)
    val times by viewModel.times.collectAsState()
    val stats = remember(canvases, times) { com.karapp.drawguidance.ui.screens.canvaslist.components.CanvasStats.of(canvases, times, System.currentTimeMillis()) }
    val snackbarHostState = remember { SnackbarHostState() }

    var showCreateModal by remember { mutableStateOf(false) }
    var canvasPendingSettings by remember { mutableStateOf<CanvasUi?>(null) }
    var canvasPendingDelete by remember { mutableStateOf<CanvasUi?>(null) }
    // Çoklu seçim: tuvaller ve klasörler ("i:" / "f:" anahtarları)
    var selection by remember { mutableStateOf<Set<String>>(emptySet()) }
    var overflowMenuOpen by remember { mutableStateOf(false) }
    var gridView by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(true) }
    var sort by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(CanvasSort.RECENT) }
    var sortMenu by remember { mutableStateOf(false) }
    var newFolderOpen by remember { mutableStateOf(false) }
    var renameFolder by remember { mutableStateOf<com.karapp.drawguidance.data.local.FolderEntity?>(null) }
    var moveOpen by remember { mutableStateOf(false) }
    var deleteSelectionOpen by remember { mutableStateOf(false) }
    val folders by viewModel.folders.collectAsState()
    val currentFolderId by viewModel.currentFolder.collectAsState()
    val currentFolder = folders.firstOrNull { it.id == currentFolderId }
    val selectedCanvasIds = com.karapp.drawguidance.ui.components.ListSelection.items(selection)
    val selectedFolderIds = com.karapp.drawguidance.ui.components.ListSelection.folders(selection)
    // Tek tuval seçiliyse eski tek seçim işlemleri (kaydet, ayarlar, sil) gösterilir
    val selectedCanvasId = selectedCanvasIds.singleOrNull()?.takeIf { selectedFolderIds.isEmpty() }
    // v33 başlıkta anlık arama: klasörde o klasörün tuvalleri, ana listede bütün tuvaller ve klasör adları aranır
    var query by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    val searching = query.isNotBlank()
    val shown = remember(canvases, currentFolderId, query) {
        val scope = if (searching && currentFolderId == null) canvases else canvases.filter { it.folderId == currentFolderId }
        com.karapp.drawguidance.ui.components.TitleSearch.filter(scope, query) { it.name }
    }
    // Klasör değişince arama temizlenir
    LaunchedEffect(currentFolderId) { query = "" }
    val folderCounts = remember(canvases) { canvases.groupingBy { it.folderId }.eachCount() }
    // Silinen öğeler seçimden düşer
    LaunchedEffect(canvases, folders) {
        val existing = canvases.mapTo(HashSet()) { com.karapp.drawguidance.ui.components.ListSelection.item(it.localId) } +
            folders.map { com.karapp.drawguidance.ui.components.ListSelection.folder(it.id) }
        if (!existing.containsAll(selection)) selection = selection.filterTo(HashSet()) { it in existing }
    }

    // Geri tuşu önce seçimi kaldırır, sonra aramayı temizler, sonra klasörden ana listeye döner
    BackHandler(enabled = selection.isNotEmpty()) { selection = emptySet() }
    BackHandler(enabled = selection.isEmpty() && searching) { query = "" }
    BackHandler(enabled = selection.isEmpty() && !searching && currentFolderId != null) { viewModel.openFolder(null) }

    var savedImage by remember { mutableStateOf<List<com.karapp.drawguidance.data.images.SavedImageEntity>?>(null) }
    val saveDialog by viewModel.saveDialog.collectAsState()
    val imageSaveFailed = stringResource(R.string.image_save_failed)
    val printSavedText = stringResource(R.string.v40_cmyk_saved)
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is CanvasListEvent.ImageSaved -> savedImage = event.images
                CanvasListEvent.ImageSaveFailed -> snackbarHostState.showSnackbar(imageSaveFailed)
                is CanvasListEvent.DocumentSaved -> snackbarHostState.showSnackbar(
                    printSavedText.format(event.file.location, com.karapp.drawguidance.ui.screens.canvasedit.formatBytes(event.file.sizeBytes)),
                )
            }
        }
    }

    Scaffold(
        containerColor = colors.background,
        bottomBar = bottomBar,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            val selectedCanvas = canvases.firstOrNull { it.localId == selectedCanvasId }
            if (selectedCanvas != null) {
                NeuTopBar(
                    title = selectedCanvas.name,
                    subtitle = stringResource(R.string.selected_canvas_subtitle),
                    navigation = {
                        NeuIconButton(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), { selection = emptySet() })
                    },
                    actions = {
                        if (selectedCanvas.localId in savingImageIds) {
                            Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = colors.accent)
                            }
                        } else {
                            NeuIconButton(
                                Icons.Filled.SaveAlt,
                                stringResource(R.string.save_as_image),
                                { viewModel.openSaveDialog(selectedCanvas.localId) },
                            )
                        }
                        NeuIconButton(Icons.AutoMirrored.Filled.DriveFileMove, stringResource(R.string.move_to_folder), { moveOpen = true })
                        Box {
                            NeuIconButton(Icons.Filled.MoreVert, stringResource(R.string.more_options), { overflowMenuOpen = true })
                            DropdownMenu(
                                expanded = overflowMenuOpen,
                                onDismissRequest = { overflowMenuOpen = false },
                                containerColor = colors.background,
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_canvas_settings)) },
                                    leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                                    onClick = {
                                        overflowMenuOpen = false
                                        canvasPendingSettings = selectedCanvas
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_delete_canvas), color = colors.danger) },
                                    leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = colors.danger) },
                                    onClick = {
                                        overflowMenuOpen = false
                                        canvasPendingDelete = selectedCanvas
                                    },
                                )
                            }
                        }
                    },
                )
            } else if (selection.isNotEmpty()) {
                // Çoklu seçim (ya da klasör seçimi): üst menüden toplu silme ve taşıma
                NeuTopBar(
                    title = stringResource(R.string.selected_items, selection.size),
                    subtitle = stringResource(R.string.selected_items_subtitle),
                    navigation = {
                        NeuIconButton(Icons.Filled.Close, stringResource(R.string.clear_selection), { selection = emptySet() })
                    },
                    actions = {
                        val singleFolder = selectedFolderIds.singleOrNull()?.takeIf { selectedCanvasIds.isEmpty() }
                        if (singleFolder != null) {
                            NeuIconButton(Icons.Filled.Edit, stringResource(R.string.folder_rename), { renameFolder = folders.firstOrNull { it.id == singleFolder } })
                        }
                        if (selectedFolderIds.isEmpty()) {
                            NeuIconButton(Icons.AutoMirrored.Filled.DriveFileMove, stringResource(R.string.move_to_folder), { moveOpen = true })
                        }
                        NeuIconButton(Icons.Filled.Delete, stringResource(R.string.delete_selected), { deleteSelectionOpen = true }, tint = colors.danger)
                    },
                )
            } else if (currentFolder != null) {
                NeuTopBar(
                    title = currentFolder.name,
                    subtitle = stringResource(R.string.canvas_count, canvases.count { it.folderId == currentFolderId }),
                    navigation = {
                        NeuIconButton(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.folder_up), { viewModel.openFolder(null) })
                    },
                    actions = {
                        NeuIconButton(
                            if (gridView) Icons.AutoMirrored.Filled.ViewList else Icons.Filled.GridView,
                            stringResource(if (gridView) R.string.images_view_list else R.string.images_view_grid),
                            { gridView = !gridView },
                        )
                        NeuIconButton(Icons.Filled.Add, stringResource(R.string.new_canvas), { showCreateModal = true }, tint = colors.accent)
                    },
                )
            } else {
                NeuTopBar(
                    title = stringResource(R.string.canvas_list_title),
                    subtitle = if (canvases.isNotEmpty()) stringResource(R.string.canvas_count, canvases.size) else null,
                    navigation = {
                        Box(Modifier.size(42.dp).neumorphic(colors, CircleShape, elevation = 5.dp), contentAlignment = Alignment.Center) {
                            Icon(BrushIcons.Pastel, null, tint = colors.accent, modifier = Modifier.size(22.dp))
                        }
                    },
                    actions = {
                        if (canvases.isNotEmpty()) {
                            Box {
                                NeuIconButton(Icons.AutoMirrored.Filled.Sort, stringResource(R.string.images_sort), { sortMenu = true })
                                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }, containerColor = colors.background) {
                                    CanvasSort.entries.forEach { option ->
                                        DropdownMenuItem(
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
                            )
                            NeuIconButton(Icons.Filled.CreateNewFolder, stringResource(R.string.folder_new), { newFolderOpen = true })
                        }
                        NeuIconButton(Icons.Filled.Add, stringResource(R.string.new_canvas), { showCreateModal = true }, tint = colors.accent)
                    },
                )
            }
        },
    ) { paddingValues ->
        val rootFolders = if (currentFolderId == null) com.karapp.drawguidance.ui.components.TitleSearch.filter(folders, query) { it.name } else emptyList()
        // Arama sonucunda görünmeyen öğeler seçimden düşer
        LaunchedEffect(query, shown, rootFolders) {
            if (searching && selection.isNotEmpty()) {
                val visible = shown.mapTo(HashSet()) { com.karapp.drawguidance.ui.components.ListSelection.item(it.localId) } +
                    rootFolders.map { com.karapp.drawguidance.ui.components.ListSelection.folder(it.id) }
                if (!visible.containsAll(selection)) selection = selection.filterTo(HashSet()) { it in visible }
            }
        }
        if (canvases.isEmpty() && folders.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(paddingValues).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    Modifier.size(120.dp).neumorphic(colors, CircleShape, elevation = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(BrushIcons.Pastel, null, tint = colors.accent, modifier = Modifier.size(52.dp))
                }
                Spacer(Modifier.height(28.dp))
                Text(
                    text = stringResource(R.string.empty_canvas_list),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.contentMuted,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))
                NeuButton(stringResource(R.string.new_canvas), { showCreateModal = true }, primary = true, icon = Icons.Filled.Add)
            }
        } else Column(Modifier.fillMaxSize().padding(paddingValues)) {
            com.karapp.drawguidance.ui.components.TitleSearchField(
                query = query,
                onQueryChange = { query = it },
                placeholder = stringResource(if (currentFolder != null) R.string.search_in_folder_hint else R.string.search_canvases_hint),
                clearDescription = stringResource(R.string.search_clear),
                modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 2.dp, bottom = 10.dp),
                tag = "canvas_search",
            )
            val sorted = remember(shown, sort) { sort.apply(shown) }
            // v40 özet yalnız ana listede (klasörde ve aramada değil)
            val showStats = !searching && currentFolderId == null && canvases.isNotEmpty()
            val itemKey = { canvas: CanvasUi -> com.karapp.drawguidance.ui.components.ListSelection.item(canvas.localId) }
            val onCardClick: (CanvasUi) -> Unit = { canvas ->
                if (selection.isNotEmpty()) selection = com.karapp.drawguidance.ui.components.ListSelection.toggle(selection, itemKey(canvas))
                else onOpenCanvas(canvas.localId)
            }
            val onCardLongClick: (CanvasUi) -> Unit = { canvas -> selection = com.karapp.drawguidance.ui.components.ListSelection.toggle(selection, itemKey(canvas)) }
            val onFolderClick: (com.karapp.drawguidance.data.local.FolderEntity) -> Unit = { f ->
                val key = com.karapp.drawguidance.ui.components.ListSelection.folder(f.id)
                if (selection.isNotEmpty()) selection = com.karapp.drawguidance.ui.components.ListSelection.toggle(selection, key) else viewModel.openFolder(f.id)
            }
            val onFolderLongClick: (com.karapp.drawguidance.data.local.FolderEntity) -> Unit = { f ->
                selection = com.karapp.drawguidance.ui.components.ListSelection.toggle(selection, com.karapp.drawguidance.ui.components.ListSelection.folder(f.id))
            }
            if (searching && sorted.isEmpty() && rootFolders.isEmpty()) {
                Text(
                    stringResource(R.string.search_no_result, query.trim()),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.contentMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxSize().padding(32.dp).testTag("canvas_search_empty"),
                )
            } else if (gridView) {
                // Galeri: klasörler üstte; her tuval kendi oranında, tarihe göre bölümlerde (Resimlerim ile aynı düzen)
                val sections = sort != CanvasSort.NAME
                androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid(
                    columns = androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells.Adaptive(minSize = 156.dp),
                    modifier = Modifier.fillMaxSize().testTag("canvas_gallery"),
                    contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 24.dp),
                    verticalItemSpacing = 18.dp,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (showStats) item(key = "stats", span = androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan.FullLine) {
                        com.karapp.drawguidance.ui.screens.canvaslist.components.CanvasStatsRow(stats)
                    }
                    if (rootFolders.isNotEmpty()) {
                        item(key = "folders-header", span = androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan.FullLine) {
                            com.karapp.drawguidance.ui.components.GallerySectionHeader(stringResource(R.string.folders_title))
                        }
                        rootFolders.forEach { f ->
                            item(key = "folder-" + f.id) {
                                com.karapp.drawguidance.ui.components.FolderTile(
                                    f, folderCounts[f.id] ?: 0,
                                    selected = com.karapp.drawguidance.ui.components.ListSelection.folder(f.id) in selection,
                                    onClick = { onFolderClick(f) }, onLongClick = { onFolderLongClick(f) },
                                )
                            }
                        }
                    }
                    var lastSection = -1
                    sorted.forEach { canvas ->
                        if (sections) {
                            val section = com.karapp.drawguidance.ui.components.gallerySection(sort.timeOf(canvas))
                            if (section != lastSection) {
                                lastSection = section
                                item(key = "section-$section", span = androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan.FullLine) {
                                    com.karapp.drawguidance.ui.components.GallerySectionHeader(stringResource(section))
                                }
                            }
                        }
                        item(key = canvas.localId) {
                            CanvasCard(
                                canvas = canvas,
                                isSelected = itemKey(canvas) in selection,
                                onClick = { onCardClick(canvas) },
                                onLongClick = { onCardLongClick(canvas) },
                            )
                        }
                    }
                }
            } else {
                val dateFormat = remember { java.text.SimpleDateFormat("d MMM yyyy, HH:mm", java.util.Locale.forLanguageTag("tr")) }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 340.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (showStats) item(key = "stats", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                        com.karapp.drawguidance.ui.screens.canvaslist.components.CanvasStatsRow(stats, Modifier.padding(bottom = 8.dp))
                    }
                    items(rootFolders, key = { "folder-" + it.id }) { f ->
                        com.karapp.drawguidance.ui.components.FolderRow(
                            f, folderCounts[f.id] ?: 0,
                            selected = com.karapp.drawguidance.ui.components.ListSelection.folder(f.id) in selection,
                            onClick = { onFolderClick(f) }, onLongClick = { onFolderLongClick(f) },
                        )
                    }
                    items(sorted, key = { it.localId }) { canvas ->
                        com.karapp.drawguidance.ui.screens.canvaslist.components.CanvasRow(
                            canvas = canvas,
                            isSelected = itemKey(canvas) in selection,
                            dateText = dateFormat.format(java.util.Date(canvas.updatedAt)),
                            onClick = { onCardClick(canvas) },
                            onLongClick = { onCardLongClick(canvas) },
                        )
                    }
                }
            }
        }
    }

    if (newFolderOpen) {
        com.karapp.drawguidance.ui.components.FolderNameDialog(
            stringResource(R.string.folder_new), "",
            onConfirm = { name -> newFolderOpen = false; viewModel.createFolder(name) },
            onDismiss = { newFolderOpen = false },
        )
    }
    renameFolder?.let { folder ->
        com.karapp.drawguidance.ui.components.FolderNameDialog(
            stringResource(R.string.folder_rename), folder.name,
            onConfirm = { name -> renameFolder = null; viewModel.renameFolder(folder.id, name); selection = emptySet() },
            onDismiss = { renameFolder = null },
        )
    }
    if (moveOpen) {
        com.karapp.drawguidance.ui.components.MoveToFolderDialog(
            folders = folders,
            current = currentFolderId,
            onMove = { target -> moveOpen = false; viewModel.moveCanvases(selectedCanvasIds, target); selection = emptySet() },
            onNewFolder = { name -> moveOpen = false; viewModel.createFolder(name, selectedCanvasIds); selection = emptySet() },
            onDismiss = { moveOpen = false },
        )
    }
    if (deleteSelectionOpen) {
        val inFolders = selectedFolderIds.sumOf { folderCounts[it] ?: 0 }
        AlertDialog(
            onDismissRequest = { deleteSelectionOpen = false },
            containerColor = colors.background,
            title = { Text(stringResource(R.string.delete_selected_title, selection.size)) },
            text = {
                Text(
                    stringResource(R.string.delete_selected_canvases_text, selectedCanvasIds.size, selectedFolderIds.size, inFolders),
                    color = colors.contentMuted,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteSelection(selectedCanvasIds, selectedFolderIds)
                    selection = emptySet()
                    deleteSelectionOpen = false
                }) { Text(stringResource(R.string.delete_selected), color = colors.danger) }
            },
            dismissButton = { TextButton(onClick = { deleteSelectionOpen = false }) { Text(stringResource(R.string.cancel_button)) } },
        )
    }

    // v44 çocuk kilidinde yeni tuval yalnız çocuk çizimi (form açılmaz)
    val kidsCanvasName = stringResource(R.string.kids_canvas_name)
    if (showCreateModal && kidsLocked) {
        LaunchedEffect(Unit) {
            showCreateModal = false
            val size = com.karapp.drawguidance.data.model.CanvasKind.KIDS.defaultSize
            viewModel.createCanvas(kidsCanvasName, size.width, size.height, "#FFFFFF", aiEnabled = true, onCreated = { onOpenCanvas(it) }, kind = com.karapp.drawguidance.data.model.CanvasKind.KIDS)
        }
    } else if (showCreateModal) {
        CanvasFormModal(
            isEditMode = false,
            initial = CanvasFormInitial(),
            onDismiss = { showCreateModal = false },
            onSubmit = { result ->
                showCreateModal = false
                viewModel.createCanvas(
                    name = result.name,
                    width = result.width,
                    height = result.height,
                    backgroundColorHex = result.backgroundColorHex,
                    aiEnabled = result.aiEnabled,
                    onCreated = { localId -> onOpenCanvas(localId) },
                    kind = result.kind,
                    pageCount = result.pageCount,
                    background = result.background,
                    galleryUri = result.galleryUri,
                    templateId = result.templateId,
                )
            },
        )
    }

    canvasPendingSettings?.let { canvas ->
        CanvasFormModal(
            isEditMode = true,
            initial = CanvasFormInitial(
                name = canvas.name,
                width = canvas.width,
                height = canvas.height,
                backgroundColorHex = canvas.backgroundColorHex,
                aiEnabled = canvas.aiEnabled,
                kind = canvas.kind,
                background = canvas.background,
            ),
            onDismiss = { canvasPendingSettings = null },
            onSubmit = { result ->
                viewModel.updateSettings(
                    localId = canvas.localId,
                    name = result.name,
                    width = result.width,
                    height = result.height,
                    backgroundColorHex = result.backgroundColorHex,
                    aiEnabled = result.aiEnabled,
                    background = result.background,
                    galleryUri = result.galleryUri,
                )
                canvasPendingSettings = null
            },
        )
    }

    savedImage?.let { images ->
        com.karapp.drawguidance.ui.components.ImageSavedDialog(images.first(), onDismiss = { savedImage = null }, totalCount = images.size)
    }

    // Seçili tuvali kaydet: kaydetme ayarlarını soran pencere
    saveDialog?.let { dialog ->
        com.karapp.drawguidance.ui.components.ImageSaveOptionsDialog(
            initial = dialog.options,
            pageCount = dialog.canvas.pageCount,
            hasGuides = !dialog.canvas.guide.isEmpty,
            locationLabel = viewModel.imageSaveLocation,
            outputSize = { scale -> viewModel.imageOutputSize(dialog.canvas, scale) },
            onSave = { options -> viewModel.saveAsImage(dialog.canvas.localId, options) },
            documentLocationLabel = viewModel.pdfLocationLabel,
            onDismiss = viewModel::closeSaveDialog,
        )
    }

    canvasPendingDelete?.let { canvas ->
        AlertDialog(
            onDismissRequest = { canvasPendingDelete = null },
            containerColor = colors.background,
            title = { Text(stringResource(R.string.delete_confirm_title)) },
            text = { Text(stringResource(R.string.delete_confirm_message), color = colors.contentMuted) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteCanvas(canvas.localId)
                    selection = selection - com.karapp.drawguidance.ui.components.ListSelection.item(canvas.localId)
                    canvasPendingDelete = null
                }) { Text(stringResource(R.string.action_delete_canvas), color = colors.danger) }
            },
            dismissButton = {
                TextButton(onClick = { canvasPendingDelete = null }) { Text(stringResource(R.string.cancel_button)) }
            },
        )
    }
}

/** Tuval sıralaması: son düzenlenen, en yeni oluşturulan, ada göre. */
enum class CanvasSort(val label: Int) {
    RECENT(R.string.canvas_sort_recent), NEWEST(R.string.canvas_sort_newest), NAME(R.string.images_sort_name);

    fun apply(list: List<CanvasUi>): List<CanvasUi> = when (this) {
        RECENT -> list.sortedByDescending { it.updatedAt }
        NEWEST -> list.sortedByDescending { it.createdAt }
        NAME -> list.sortedBy { it.name.lowercase(java.util.Locale.forLanguageTag("tr")) }
    }

    /** Bölümlemede kullanılan tarih. */
    fun timeOf(canvas: CanvasUi): Long = if (this == NEWEST) canvas.createdAt else canvas.updatedAt
}
