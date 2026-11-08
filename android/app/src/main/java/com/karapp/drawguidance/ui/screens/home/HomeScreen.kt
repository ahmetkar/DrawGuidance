package com.karapp.drawguidance.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.selected
import kotlinx.coroutines.launch
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import com.karapp.drawguidance.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.karapp.drawguidance.R
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.data.model.CanvasUi
import com.karapp.drawguidance.ui.components.AppLogo
import com.karapp.drawguidance.ui.components.LoadingOverlay
import com.karapp.drawguidance.ui.components.MainTab
import com.karapp.drawguidance.ui.components.NeuSurface
import com.karapp.drawguidance.ui.screens.canvaslist.CanvasListViewModel
import com.karapp.drawguidance.ui.screens.canvaslist.components.CanvasFormInitial
import com.karapp.drawguidance.ui.screens.canvaslist.components.CanvasFormModal
import com.karapp.drawguidance.data.model.CanvasSizeTemplate
import com.karapp.drawguidance.ui.screens.canvaslist.components.CanvasThumbnail
import com.karapp.drawguidance.ui.theme.NeuTheme
import org.koin.androidx.compose.koinViewModel
import java.util.Calendar

/**
 * v30 Ana sayfa: karşılama yazısı (günün saatine göre), kaldığın yerden devam kartı (en son bırakılan tuval), yeni
 * oluşturma kısayolları (resim, not, çizgi roman, 3B), bırakıp kapattıkların ve son çizimlerin. Geniş ekranda içerik
 * ortalanır.
 */
@Composable
fun HomeScreen(
    onOpenCanvas: (String) -> Unit,
    onOpenTab: (MainTab) -> Unit,
    /** v33 3B tuval: adıyla 3B düzenleyici açılır. */
    onNewModel: (String) -> Unit,
    bottomBar: @Composable () -> Unit = {},
    viewModel: HomeViewModel = koinViewModel(),
    listViewModel: CanvasListViewModel = koinViewModel(),
    /** v33 giriş yapılmışsa (ve internet varsa) üst çubukta Google adı, soyadı ve fotoğrafı. */
    accountViewModel: com.karapp.drawguidance.ui.screens.account.AccountViewModel = koinViewModel(),
    onOpenAccount: () -> Unit = { onOpenTab(MainTab.SETTINGS) },
    /** v39 abonelik sayfası (Premium kartı, giriş yapılmışsa). */
    onOpenSubscription: () -> Unit = {},
) {
    val profile by accountViewModel.active.collectAsState()
    val online by accountViewModel.online.collectAsState()
    val colors = NeuTheme.colors
    val state by viewModel.state.collectAsState()
    var createKind by remember { mutableStateOf<CanvasKind?>(null) }
    // v34 yeni çizim: basit / gelişmiş düğmesinin altında açılan liste (ayrı pencere yok); 3B şekil oluşturma adıyla
    // 3B düzenleyiciyi açar. Bir anda tek grup açık; düğmeye yeniden dokununca kapanır.
    var expanded by rememberSaveable { mutableStateOf<com.karapp.drawguidance.ui.components.DrawingGroup?>(null) }
    var new3dOpen by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // v34 internet varken ve giriş yapılmamışken en üstte giriş önerisi
    val showSignIn = profile == null && online && accountViewModel.available
    // v37 giriş yapmış 12 yaşından küçüklere çocuk modu önerisi (kapatılınca bir daha gösterilmez)
    val kidsPrefs = org.koin.compose.koinInject<com.karapp.drawguidance.data.settings.KidsPrefs>()
    val kidsDismissed by kidsPrefs.homeCardDismissed.collectAsState()
    // Çocuk modu önerisi internet olmasa da (cihazdaki girişli kullanıcının doğum tarihiyle) gösterilir
    val deviceUser by accountViewModel.user.collectAsState()
    // v44 çocuk kilidi: yalnız çocuk çizimi (kart kapatılamaz), çizim görevi ve çocuk tuvalleri
    val kidsLocked = com.karapp.drawguidance.ui.kids.LocalKidsLocked.current
    val showKids = kidsLocked || (!kidsDismissed && com.karapp.drawguidance.data.settings.KidsPrefs.isChild(deviceUser?.takeIf { it.signedIn }?.birthDate))
    val toggleGroup: (com.karapp.drawguidance.ui.components.DrawingGroup) -> Unit = { g -> expanded = if (expanded == g) null else g }
    val now = remember(state) { System.currentTimeMillis() }
    // v39 görev kartı (13 yaşından küçük girişli) ve Premium kartı (Premium değilse)
    val subscriptions = org.koin.compose.koinInject<com.karapp.drawguidance.data.billing.SubscriptionRepository>()
    val entitlements by subscriptions.entitlements.collectAsState()
    val challenge = org.koin.compose.koinInject<com.karapp.drawguidance.data.challenge.ChallengeProgressRepository>()
    val challengeRows by challenge.rows.collectAsState()
    val signedInNow = deviceUser?.signedIn == true
    val showChallenge = kidsLocked || HomeV39.showChallengeCard(signedInNow, deviceUser?.birthDate)
    val kidsOnly: (com.karapp.drawguidance.data.model.CanvasUi) -> Boolean = { !kidsLocked || it.kind == CanvasKind.KIDS }
    val leftOff = remember(state.leftOff, kidsLocked) { state.leftOff.filter { kidsOnly(it.canvas) } }
    val mostTime = remember(state.mostTime, kidsLocked) { state.mostTime.filter { kidsOnly(it.canvas) } }

    Scaffold(containerColor = colors.background, bottomBar = bottomBar) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                Modifier.fillMaxSize().widthIn(max = 920.dp).testTag("home_screen"),
                state = listState,
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                if (showSignIn) item(key = "signin") {
                    com.karapp.drawguidance.ui.screens.account.SignInSuggestionCard(accountViewModel, Modifier.statusBarsPadding().padding(top = 8.dp))
                }
                item(key = "header") { Header(state, profile, onOpenAccount, statusBar = !showSignIn) }
                // v44 çocuk kilidi açık: bilgi ve (yaşla açıldıysa) ebeveyn şifresi
                if (kidsLocked) item(key = "kids_lock") { com.karapp.drawguidance.ui.kids.KidsLockHomeCard() }
                if (showKids) item(key = "kids") {
                    val kidsName = stringResource(R.string.kids_canvas_name)
                    KidsHomeCard(onStart = {
                        val size = CanvasKind.KIDS.defaultSize
                        listViewModel.createCanvas(kidsName, size.width, size.height, "#FFFFFF", aiEnabled = true, onCreated = onOpenCanvas, kind = CanvasKind.KIDS)
                    }, onDismiss = if (kidsLocked) null else kidsPrefs::dismissHomeCard)
                }
                if (showChallenge) item(key = "challenge") {
                    val kidsName = stringResource(R.string.kids_canvas_name)
                    val best = challengeRows.mapValues { it.value.bestScore }
                    val pass = com.karapp.drawguidance.ui.screens.canvasedit.challenge.ChallengeScore.PASS
                    val levelCount = com.karapp.drawguidance.ui.screens.canvasedit.challenge.ChallengeLevels.all.size
                    // v40 50 seviye ve Premium kilidi: sıradaki seviye Premium'sa Premium ya da yeniden oynama önerilir
                    val plan = ChallengeHomePlan.of(
                        best, levelCount, com.karapp.drawguidance.ui.screens.canvasedit.challenge.ChallengeLevels.FREE_LEVELS, pass,
                        premium = entitlements.has(com.karapp.drawguidance.data.billing.FeatureKeys.KIDS_CHALLENGE_ADVANCED),
                        stars = com.karapp.drawguidance.ui.screens.canvasedit.challenge.ChallengeScore::stars,
                    )
                    ChallengeHomeCard(
                        plan,
                        onPremium = {
                            when (HomeV39.premiumTarget(signedInNow)) {
                                HomeV39.PremiumTarget.SIGN_IN -> onOpenAccount()
                                HomeV39.PremiumTarget.SUBSCRIPTION -> onOpenSubscription()
                            }
                        },
                        onOpen = { next ->
                            val open: (String) -> Unit = { id ->
                                com.karapp.drawguidance.ui.screens.canvasedit.challenge.ChallengeLaunch.request(id, next)
                                onOpenCanvas(id)
                            }
                            val existing = state.kidsCanvasId
                            if (existing != null) open(existing) else {
                                val size = CanvasKind.KIDS.defaultSize
                                listViewModel.createCanvas(kidsName, size.width, size.height, "#FFFFFF", aiEnabled = true, onCreated = open, kind = CanvasKind.KIDS)
                            }
                        },
                    )
                }
                // v43 sunucudan gelen duyurular (en üstte; kapatılana kadar)
                item(key = "announcements") { HomeAnnouncements() }
                if (!kidsLocked) item(key = "hero") {
                    val resume = state.resume
                    if (resume != null) ResumeCard(resume, now) { onOpenCanvas(resume.canvas.localId) }
                    else StartCard(state.canvasCount == 0) {
                        // Çizmeye başla: gelişmiş çizimin listesi açılır ve görünür
                        expanded = com.karapp.drawguidance.ui.components.DrawingGroup.ADVANCED
                        scope.launch { listState.animateScrollToItem((if (showSignIn) 3 else 2) + (if (showKids) 1 else 0) + (if (showChallenge) 1 else 0)) }
                    }
                }
                if (!kidsLocked) item(key = "create") {
                    SectionTitle(stringResource(R.string.home_create_title))
                    // v33 iki oluşturma düğmesi: basit çizim ve gelişmiş çizim; v34 alt modlar düğmelerin altında açılır
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        GroupTile(
                            stringResource(R.string.home_new_simple), stringResource(R.string.v40_home_group_simple), Icons.Filled.Gesture,
                            Color(0xFF8B5CF6), Modifier.weight(1f).testTag("home_new_simple"),
                            selected = expanded == com.karapp.drawguidance.ui.components.DrawingGroup.SIMPLE,
                        ) { toggleGroup(com.karapp.drawguidance.ui.components.DrawingGroup.SIMPLE) }
                        GroupTile(
                            stringResource(R.string.home_new_advanced), stringResource(R.string.v40_home_group_advanced), Icons.Filled.Brush,
                            Color(0xFF6366F1), Modifier.weight(1f).testTag("home_new_advanced"),
                            selected = expanded == com.karapp.drawguidance.ui.components.DrawingGroup.ADVANCED,
                        ) { toggleGroup(com.karapp.drawguidance.ui.components.DrawingGroup.ADVANCED) }
                    }
                    com.karapp.drawguidance.ui.components.DrawingGroupList(
                        expanded,
                        // Seçilince liste kapanır (form ya da 3B adı penceresi açılır)
                        onPick = { k -> createKind = k; expanded = null },
                        onNew3d = { new3dOpen = true; expanded = null },
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                if (leftOff.isNotEmpty()) item {
                    SectionTitle(stringResource(R.string.home_left_title))
                    LazyRow(Modifier.padding(top = 10.dp).testTag("home_left_row"), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(leftOff, key = { "l" + it.canvas.localId }) { l ->
                            MiniCanvasCard(l.canvas, stringResource(R.string.home_left_ago, relative(now, l.closedAt))) { onOpenCanvas(l.canvas.localId) }
                        }
                    }
                }
                // v40 son çizimler yerine en çok vakit geçirdiklerin (süre veritabanında; sayılar Tuvallerim'de)
                if (mostTime.isNotEmpty()) item(key = "most_time") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionTitle(stringResource(R.string.v40_home_most_time_title), Modifier.weight(1f))
                        Row(
                            Modifier.clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button) { onOpenTab(MainTab.CANVASES) }.padding(8.dp)
                                .testTag("home_see_all"),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(stringResource(R.string.home_see_all), style = MaterialTheme.typography.labelLarge, color = colors.accent)
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = colors.accent, modifier = Modifier.size(16.dp))
                        }
                    }
                    LazyRow(Modifier.padding(top = 6.dp).testTag("home_most_time_row"), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(mostTime, key = { "t" + it.canvas.localId }) { t ->
                            MiniCanvasCard(t.canvas, com.karapp.drawguidance.ui.components.timeSpentText(t.timeSpentMs)) { onOpenCanvas(t.canvas.localId) }
                        }
                    }
                }
                if (!kidsLocked && HomeV39.showPremiumCard(entitlements.isPremium)) item(key = "premium") {
                    PremiumHomeCard(signedInNow, onClick = {
                        when (HomeV39.premiumTarget(signedInNow)) {
                            HomeV39.PremiumTarget.SIGN_IN -> onOpenAccount()
                            HomeV39.PremiumTarget.SUBSCRIPTION -> onOpenSubscription()
                        }
                    })
                }
            }
            LoadingOverlay(visible = !state.loaded, message = stringResource(R.string.loading_generic))
        }
    }

    if (new3dOpen) {
        com.karapp.drawguidance.ui.components.New3dSheet(
            onCreate = { name -> new3dOpen = false; onNewModel(name) },
            onDismiss = { new3dOpen = false },
        )
    }
    createKind?.let { kind ->
        val t = kind.defaultSize
        CanvasFormModal(
            isEditMode = false,
            initial = CanvasFormInitial(width = t.width, height = t.height, kind = kind),
            onDismiss = { createKind = null },
            // v33 formda yalnız seçilen grubun türleri
            kindChoices = if (kind.simple) CanvasKind.Simple else CanvasKind.Advanced,
            onSubmit = { result ->
                createKind = null
                listViewModel.createCanvas(
                    name = result.name, width = result.width, height = result.height,
                    backgroundColorHex = result.backgroundColorHex, aiEnabled = result.aiEnabled,
                    onCreated = onOpenCanvas, kind = result.kind, pageCount = result.pageCount,
                    background = result.background, galleryUri = result.galleryUri,
                    templateId = result.templateId,
                )
            },
        )
    }
}

@Composable
private fun Header(state: HomeState, profile: com.karapp.drawguidance.data.local.LocalUserEntity?, onProfile: () -> Unit, statusBar: Boolean = true) {
    val colors = NeuTheme.colors
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val greeting = stringResource(
        when (hour) {
            in 5..10 -> R.string.home_greeting_morning
            in 11..16 -> R.string.home_greeting_day
            in 17..21 -> R.string.home_greeting_evening
            else -> R.string.home_greeting_night
        },
    )
    val welcome = if (state.canvasCount == 0) stringResource(R.string.home_welcome_first) else stringResource(R.string.home_welcome_back)
    Row(
        Modifier.fillMaxWidth().then(if (statusBar) Modifier.statusBarsPadding().padding(top = 8.dp) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppLogo(52.dp)
        Spacer(Modifier.width(14.dp))
        if (profile == null) {
            Column(Modifier.weight(1f)) {
                Text(greeting, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("home_greeting"))
                Text(welcome, style = MaterialTheme.typography.bodyMedium, color = colors.contentMuted)
            }
        } else {
            // v34 girişliyken: selamlama adla (ör. "İyi akşamlar, Ahmet"), sağ üstte gradyan halkalı fotoğraf
            val name = com.karapp.drawguidance.ui.screens.account.AccountViewModel.nameOf(profile)
            val first = profile.givenName?.takeIf { it.isNotBlank() } ?: name.substringBefore(' ')
            val cd = stringResource(R.string.home_profile_cd, name)
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.home_greeting_named, greeting, first), style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("home_greeting"),
                )
                Text(welcome, style = MaterialTheme.typography.bodyMedium, color = colors.contentMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier.clip(CircleShape).clickable(role = Role.Button, onClick = onProfile).androidSemantics(cd).testTag("home_profile"),
                contentAlignment = Alignment.Center,
            ) {
                com.karapp.drawguidance.ui.screens.account.RingAvatar(profile, 54.dp)
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = modifier)
}

private val HeroShape = RoundedCornerShape(28.dp)

/** Kaldığın yerden devam: gradyanlı büyük kart, solda bilgiler, sağda tuvalin önizlemesi. */
@Composable
private fun ResumeCard(left: LeftCanvas, now: Long, onOpen: () -> Unit) {
    val c = left.canvas
    val neu = com.karapp.drawguidance.ui.theme.NeuTheme.colors
    Box(
        Modifier.fillMaxWidth().clip(HeroShape)
            .background(Brush.linearGradient(listOf(neu.gradientStart, neu.gradientEnd)))
            .clickable(role = Role.Button, onClick = onOpen)
            .testTag("home_resume"),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.home_resume_title), style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.8f))
                Text(c.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    kindLabel(c.kind) + " · " + stringResource(R.string.home_left_ago, relative(now, left.closedAt)),
                    style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f),
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.clip(CircleShape).background(Color.White).padding(horizontal = 16.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.PlayArrow, null, tint = neu.gradientStart, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.home_resume_button), style = MaterialTheme.typography.labelLarge, color = neu.gradientStart, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.width(14.dp))
            Box(Modifier.size(118.dp).clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.18f)).padding(8.dp)) {
                Box(Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)).background(Color.White)) {
                    Thumb(c)
                }
            }
        }
    }
}

/** İlk kullanım ya da bırakılan tuval yokken: çizmeye başla kartı. */
@Composable
private fun StartCard(first: Boolean, onCreate: () -> Unit) {
    val neu = com.karapp.drawguidance.ui.theme.NeuTheme.colors
    Box(
        Modifier.fillMaxWidth().clip(HeroShape)
            .background(Brush.linearGradient(listOf(neu.gradientDeep, neu.gradientStart, neu.gradientEnd)))
            .clickable(role = Role.Button, onClick = onCreate)
            .testTag("home_start"),
    ) {
        Row(Modifier.padding(22.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    stringResource(if (first) R.string.home_start_title_first else R.string.home_start_title),
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White,
                )
                Text(stringResource(R.string.home_start_text), style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f))
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.clip(CircleShape).background(Color.White).padding(horizontal = 16.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.home_start_button), style = MaterialTheme.typography.labelLarge, color = neu.gradientStart, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.width(12.dp))
            AppLogo(92.dp, rounded = false)
        }
    }
}

/** v33 oluşturma grubu düğmesi (basit / gelişmiş): simge, ad ve alt modların kısa listesi. */
@Composable
private fun GroupTile(label: String, hint: String, icon: ImageVector, tint: Color, modifier: Modifier, selected: Boolean = false, onClick: () -> Unit) {
    val colors = NeuTheme.colors
    // v34 açıkken içe gömük görünür, ok aşağı döner
    val turn by androidx.compose.animation.core.animateFloatAsState(if (selected) 180f else 0f, label = "group_chevron")
    NeuSurface(
        modifier = modifier.semantics { this.selected = selected }, elevation = 6.dp, selected = selected, onClick = onClick,
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(tint.copy(alpha = if (selected) 0.24f else 0.15f)), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.weight(1f))
                Icon(
                    Icons.Filled.ExpandMore, null, tint = if (selected) tint else colors.contentMuted,
                    modifier = Modifier.size(22.dp).graphicsLayer { rotationZ = turn },
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(hint, style = MaterialTheme.typography.labelSmall, color = colors.contentMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun Thumb(c: CanvasUi) {
    CanvasThumbnail(
        canvasWidth = c.width, canvasHeight = c.height, backgroundColorHex = c.pagePaperHex,
        strokes = remember(c.strokes, c.layers) { com.karapp.drawguidance.data.model.Layers.visible(c.strokes, c.layers) },
        modifier = Modifier.fillMaxSize(), background = c.pageBackground,
    )
}

@Composable
private fun MiniCanvasCard(c: CanvasUi, subtitle: String, onClick: () -> Unit) {
    val colors = NeuTheme.colors
    Column(Modifier.width(136.dp).clip(RoundedCornerShape(18.dp)).clickable(role = Role.Button, onClick = onClick).testTag("home_card_${c.localId}")) {
        NeuSurface(Modifier.fillMaxWidth().aspectRatio(1f), elevation = 5.dp) {
            Box(Modifier.fillMaxSize().padding(8.dp).clip(RoundedCornerShape(12.dp))) { Thumb(c) }
        }
        Text(c.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp, start = 4.dp))
        Text(subtitle, style = MaterialTheme.typography.labelSmall, color = colors.contentMuted, maxLines = 1, modifier = Modifier.padding(start = 4.dp))
    }
}

@Composable
private fun kindLabel(kind: CanvasKind): String = stringResource(
    when (kind) {
        CanvasKind.ART -> R.string.home_kind_art
        CanvasKind.NOTES -> R.string.home_kind_notes
        CanvasKind.COMIC -> R.string.home_kind_comic
        CanvasKind.SIMPLE_ART -> R.string.home_kind_simple_art
        CanvasKind.SIMPLE_NOTES -> R.string.home_kind_simple_notes
        CanvasKind.KIDS -> R.string.home_kind_kids
    },
)

/** "az önce", "5 dk", "3 sa", "dün", "4 gün" … */
@Composable
private fun relative(now: Long, t: Long): String {
    val m = ((now - t) / 60_000L).coerceAtLeast(0)
    return when {
        m < 1 -> stringResource(R.string.time_just_now)
        m < 60 -> stringResource(R.string.time_minutes, m.toInt())
        m < 60 * 24 -> stringResource(R.string.time_hours, (m / 60).toInt())
        m < 60 * 48 -> stringResource(R.string.time_yesterday)
        else -> stringResource(R.string.time_days, (m / (60 * 24)).toInt())
    }
}

/** Erişilebilirlik açıklaması (alt öğeler birleşir). */
private fun Modifier.androidSemantics(description: String): Modifier =
    this.then(Modifier.semantics(mergeDescendants = true) { contentDescription = description })
