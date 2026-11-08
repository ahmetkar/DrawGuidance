package com.karapp.drawguidance.ui.screens.account

import com.karapp.drawguidance.ui.theme.neuOutline

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.karapp.drawguidance.R
import com.karapp.drawguidance.data.local.LocalUserEntity
import com.karapp.drawguidance.ui.components.BrandColors
import com.karapp.drawguidance.ui.components.NeuButton
import com.karapp.drawguidance.ui.components.NeuReadOnlyField
import com.karapp.drawguidance.ui.components.NeuIconButton
import com.karapp.drawguidance.ui.components.NeuSurface
import com.karapp.drawguidance.ui.components.NeuTextField
import com.karapp.drawguidance.ui.components.NeuTopBar
import com.karapp.drawguidance.ui.theme.NeuTheme
import java.text.DateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale
import com.karapp.drawguidance.ui.components.NeuSheet

internal fun Context.findActivity(): Activity? = generateSequence(this) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull()

/** v33 profil fotoğrafı (Google'dan; yoksa adın baş harfleri). */
@Composable
fun ProfileAvatar(user: LocalUserEntity, size: Dp, modifier: Modifier = Modifier) {
    val colors = NeuTheme.colors
    val initials = AccountViewModel.nameOf(user).split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1).uppercase(Locale.forLanguageTag("tr")) }
    Box(modifier.size(size).clip(CircleShape).background(colors.accent.copy(alpha = 0.18f)).testTag("profile_avatar"), contentAlignment = Alignment.Center) {
        Text(initials.ifEmpty { "?" }, style = MaterialTheme.typography.titleSmall, color = colors.accent, fontWeight = FontWeight.Bold)
        if (!user.photoUrl.isNullOrBlank()) {
            coil.compose.AsyncImage(model = user.photoUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(CircleShape))
        }
    }
}

/**
 * v33 Ayarlar'daki hesap kartı: yalnız internet bağlantısı varken görünür (yoksa uygulama giriş yapılmamış gibi
 * çalışır). Girişsiz: "Google ile giriş yap"; girişli: profil ve "Hesap ayarları".
 *
 * v36 Ayarlar'ın en üstünde, sekmelerin üzerinde ekran genişliğinde başlık olarak durur. Zemini beyazdır (koyu temada
 * yazıyı gizlememek için temanın açık yüzeyi); yazı renkleri zemine göre sabit seçilir, tasarımın nöromorfik gölgesi
 * alt kenarda kalır.
 */
@Composable
fun AccountHeader(vm: AccountViewModel, onOpenAccount: () -> Unit) {
    val colors = NeuTheme.colors
    val online by vm.online.collectAsState()
    val active by vm.active.collectAsState()
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    if (!online) return
    // Açık temada beyaz zemin ve koyu mürekkep; koyu temada temanın açık yüzeyi ve açık yazı
    val surface = if (colors.isDark) colors.lightShadow else Color.White
    val ink = if (colors.isDark) colors.content else Color(0xFF1F2937)
    val muted = if (colors.isDark) colors.contentMuted else Color(0xFF5B6476)
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(bottomStart = 26.dp, bottomEnd = 26.dp)
    Box(
        Modifier.fillMaxWidth()
            .neuOutline(colors, shape)
            .clip(shape)
            .background(surface)
            .testTag("account_card"),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp).wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = 640.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val u = active
            if (u == null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(
                        Modifier.size(52.dp).clip(CircleShape).background(Brush.linearGradient(listOf(colors.gradientStart, colors.gradientEnd))),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.Person, null, tint = Color.White, modifier = Modifier.size(30.dp)) }
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.account_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = ink)
                        Text(stringResource(R.string.account_signin_text), style = MaterialTheme.typography.bodySmall, color = muted)
                    }
                }
                if (!vm.available) Text(stringResource(R.string.account_error_not_configured), style = MaterialTheme.typography.bodySmall, color = colors.danger)
                GoogleSignInButton(
                    { context.findActivity()?.let { vm.signIn(it) } },
                    Modifier.fillMaxWidth().testTag("account_sign_in"), enabled = !state.busy && vm.available,
                )
                // Sonsuz dönen gösterge yerine yazı (ekran boşta kalabilsin)
                if (state.busy) Text(stringResource(R.string.account_busy), style = MaterialTheme.typography.bodySmall, color = colors.accent)
                state.message?.takeIf { it != R.string.birth_date_invalid }?.let {
                    Text(stringResource(it) + (state.detail?.let { d -> "\n" + com.karapp.drawguidance.i18n.I18n.tr(d) } ?: ""), style = MaterialTheme.typography.bodySmall, color = muted, modifier = Modifier.testTag("account_message"))
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    RingAvatar(u, 56.dp, background = surface)
                    Column(Modifier.weight(1f)) {
                        Text(AccountViewModel.nameOf(u), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("account_name"))
                        Text(u.email.orEmpty(), style = MaterialTheme.typography.bodySmall, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Row(
                        Modifier.clip(CircleShape).background(colors.accent.copy(alpha = 0.12f))
                            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onOpenAccount)
                            .testTag("account_settings_open")
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(Icons.Filled.ManageAccounts, null, tint = colors.accent, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.account_settings), style = MaterialTheme.typography.labelLarge, color = colors.accent, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                }
            }
        }
    }
    AccountDialogs(vm)
}

/** Ayarların en altında: giriş yapılmışsa (ve internet varsa) "Çıkış yap". */
@Composable
fun SignOutButton(vm: AccountViewModel) {
    val active by vm.active.collectAsState()
    if (active == null) return
    var ask by remember { mutableStateOf(false) }
    NeuButton(
        stringResource(R.string.account_sign_out), { ask = true }, Modifier.fillMaxWidth().testTag("account_sign_out"),
        icon = Icons.AutoMirrored.Filled.Logout, contentColor = NeuTheme.colors.danger,
    )
    if (ask) SignOutConfirm(vm) { ask = false }
}

/** Çıkış onayı (Ayarlar ve Hesap ayarları aynı pencereyi kullanır). */
@Composable
private fun SignOutConfirm(vm: AccountViewModel, close: () -> Unit) {
    AlertDialog(
        onDismissRequest = close,
        containerColor = NeuTheme.colors.background,
        title = { Text(stringResource(R.string.account_sign_out)) },
        text = { Text(stringResource(R.string.account_sign_out_text)) },
        confirmButton = { TextButton(onClick = { close(); vm.signOut() }, modifier = Modifier.testTag("account_sign_out_confirm")) { Text(stringResource(R.string.account_sign_out)) } },
        dismissButton = { TextButton(onClick = close) { Text(stringResource(R.string.cancel_button)) } },
    )
}

/**
 * v37 Hesap ayarları → Oturum: hesap silmenin üstünde, ondan belirgin biçimde ayrı kart. Çıkış geri alınabilir bir
 * işlem olduğu için nötr (vurgu) renkte; silme kartı ise kırmızı uyarıyla "Tehlikeli bölge" başlığının altında kalır.
 */
@Composable
private fun AccountSessionCard(vm: AccountViewModel, enabled: Boolean) {
    val colors = NeuTheme.colors
    var ask by remember { mutableStateOf(false) }
    NeuSurface(modifier = Modifier.fillMaxWidth().testTag("account_session_card"), elevation = 6.dp) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionHeader(Icons.AutoMirrored.Filled.Logout, stringResource(R.string.account_session_title))
            Text(stringResource(R.string.account_sign_out_hint), style = MaterialTheme.typography.bodySmall, color = colors.contentMuted)
            NeuButton(
                stringResource(R.string.account_sign_out), { ask = true }, Modifier.fillMaxWidth().testTag("account_screen_sign_out"),
                enabled = enabled, icon = Icons.AutoMirrored.Filled.Logout, contentColor = colors.accent,
            )
        }
    }
    if (ask) SignOutConfirm(vm) { ask = false }
}

/** İlk girişte doğum tarihi penceresi. */
@Composable
fun AccountDialogs(vm: AccountViewModel) {
    val state by vm.state.collectAsState()
    // v40 giriş başarılı/başarısız sonuç penceresi
    SignInResultHost(vm, state.signInOutcome)
    state.pendingBirth?.let { pending ->
        BirthDateDialog(
            title = stringResource(R.string.birth_date_title),
            text = stringResource(R.string.birth_date_text, pending.displayName ?: pending.email.orEmpty()),
            initial = null,
            error = state.message == R.string.birth_date_invalid,
            onConfirm = { vm.completeBirth(it, LegalTexts.VERSION) },
            onDismiss = vm::cancelBirth,
            // v36 hesap açılırken kullanım şartları ve KVKK aydınlatma metni onaylanır
            consent = true,
        )
    }
}

/**
 * Gün / ay / yıl alanlarıyla doğum tarihi (geçerli ve bugünden ileri olmayan tarih). Alttan açılan pencere: yazı
 * alanları klavyeyle birlikte rahat kullanılır.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun BirthDateDialog(
    title: String, text: String, initial: LocalDate?, error: Boolean, onConfirm: (LocalDate) -> Unit, onDismiss: () -> Unit,
    /** v36 hesap açılışı: Kullanım Şartları ve KVKK Aydınlatma Metni onaylanmadan kaydedilemez. */
    consent: Boolean = false,
) {
    val colors = NeuTheme.colors
    var day by rememberSaveable { mutableStateOf(initial?.dayOfMonth?.toString().orEmpty()) }
    var month by rememberSaveable { mutableStateOf(initial?.monthValue?.toString().orEmpty()) }
    var year by rememberSaveable { mutableStateOf(initial?.year?.toString().orEmpty()) }
    var termsOk by rememberSaveable { mutableStateOf(false) }
    var kvkkOk by rememberSaveable { mutableStateOf(false) }
    var reading by rememberSaveable { mutableStateOf<LegalTexts.Doc?>(null) }
    val parsed = parseBirthDate(day, month, year)
    val accepted = !consent || (termsOk && kvkkOk)
    NeuSheet(onDismiss) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState())
                .padding(start = 22.dp, end = 22.dp, bottom = 24.dp).testTag("birth_date_dialog"),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Filled.Cake, null, tint = colors.accent)
                Text(title, style = MaterialTheme.typography.headlineSmall)
            }
            Text(text, style = MaterialTheme.typography.bodyMedium, color = colors.contentMuted)
            BirthDateFields(day, month, year, { day = it }, { month = it }, { year = it })
            if (error || (year.length == 4 && parsed == null)) {
                Text(stringResource(R.string.birth_date_invalid), style = MaterialTheme.typography.bodySmall, color = colors.danger)
            }
            if (consent) {
                Column(Modifier.fillMaxWidth().testTag("consent_box"), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    ConsentRow(
                        termsOk, { termsOk = it }, stringResource(R.string.consent_terms_text), stringResource(R.string.consent_terms_link),
                        onOpen = { reading = LegalTexts.Doc.TERMS }, tag = "consent_terms",
                    )
                    ConsentRow(
                        kvkkOk, { kvkkOk = it }, stringResource(R.string.consent_kvkk_text), stringResource(R.string.consent_kvkk_link),
                        onOpen = { reading = LegalTexts.Doc.KVKK }, tag = "consent_kvkk",
                    )
                    if (!accepted) Text(stringResource(R.string.consent_required), style = MaterialTheme.typography.bodySmall, color = colors.contentMuted)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                NeuButton(stringResource(R.string.cancel_button), onDismiss, Modifier.weight(1f).testTag("birth_date_cancel"))
                NeuButton(
                    stringResource(if (consent) R.string.consent_create_account else R.string.save_button), { if (accepted) parsed?.let(onConfirm) },
                    Modifier.weight(1f).testTag("birth_date_ok"), primary = true, enabled = parsed != null && accepted,
                )
            }
        }
    }
    reading?.let { doc -> LegalTextSheet(doc) { reading = null } }
}

@Composable
private fun BirthDateFields(
    day: String, month: String, year: String, onDay: (String) -> Unit, onMonth: (String) -> Unit, onYear: (String) -> Unit,
    tagPrefix: String = "birth",
) {
    val numeric = KeyboardOptions(keyboardType = KeyboardType.Number)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NeuTextField(day, { onDay(it.filter(Char::isDigit).take(2)) }, stringResource(R.string.birth_day), Modifier.weight(1f), keyboardOptions = numeric, fieldModifier = Modifier.testTag("${tagPrefix}_day"))
        NeuTextField(month, { onMonth(it.filter(Char::isDigit).take(2)) }, stringResource(R.string.birth_month), Modifier.weight(1f), keyboardOptions = numeric, fieldModifier = Modifier.testTag("${tagPrefix}_month"))
        NeuTextField(year, { onYear(it.filter(Char::isDigit).take(4)) }, stringResource(R.string.birth_year), Modifier.weight(1.4f), keyboardOptions = numeric, fieldModifier = Modifier.testTag("${tagPrefix}_year"))
    }
}

/** Geçerli doğum tarihi (1900'den bugüne) ya da null. */
fun parseBirthDate(day: String, month: String, year: String): LocalDate? = runCatching {
    val d = LocalDate.of(year.toInt(), month.toInt(), day.toInt())
    d.takeIf { it.year >= 1900 && !it.isAfter(LocalDate.now()) }
}.getOrNull()

/** v33 Ayarlar'da sunucu eşitleme durumu: bekleyen işler, katalog sürümü ve son denetim; "Şimdi eşitle". */
@Composable
fun SyncStatusCard(vm: AccountViewModel) {
    val colors = NeuTheme.colors
    val pending by vm.pendingCount.collectAsState()
    val online by vm.online.collectAsState()
    NeuSurface(modifier = Modifier.fillMaxWidth().testTag("sync_card"), elevation = 6.dp) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Filled.CloudSync, null, tint = colors.accent)
                Text(stringResource(R.string.sync_title), style = MaterialTheme.typography.titleMedium)
            }
            Text(stringResource(R.string.sync_text), style = MaterialTheme.typography.bodySmall, color = colors.contentMuted)
            Text(
                if (pending > 0) stringResource(R.string.sync_pending, pending) else stringResource(R.string.sync_nothing_pending),
                style = MaterialTheme.typography.bodyMedium, color = if (pending > 0) colors.accent else colors.contentMuted,
                modifier = Modifier.testTag("sync_pending"),
            )
            val last = vm.catalogLastCheck
            Text(
                stringResource(
                    R.string.sync_catalog, vm.catalogVersion,
                    if (last > 0) DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(last)) else stringResource(R.string.sync_never),
                ),
                style = MaterialTheme.typography.bodySmall, color = colors.contentMuted,
            )
            if (!online) Text(stringResource(R.string.sync_offline), style = MaterialTheme.typography.bodySmall, color = colors.danger)
            // v43 elle eşitleme kaldırıldı: eşitleme her zaman kendiliğinden
        }
    }
}

/**
 * v33 Hesap ayarları; v34 düzen: profil başlığı, düzenlenebilir bilgiler (ad, soyad, doğum tarihi), Google'dan alınan
 * bilgiler yazı alanlarıyla aynı boyda salt okunur kutularda, Google'dan yenile ve en altta **Hesabı sil** (onay metni
 * yazılarak). Hesabı değiştir kaldırıldı. Yalnız internet varken kullanılır.
 */
@Composable
fun AccountSettingsScreen(onBack: () -> Unit, vm: AccountViewModel) {
    val colors = NeuTheme.colors
    val user by vm.user.collectAsState()
    val active by vm.active.collectAsState()
    val online by vm.online.collectAsState()
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val messageText = state.message?.let { stringResource(it) }
    var deleteOpen by rememberSaveable { mutableStateOf(false) }
    // Silindiyse (ya da girişsizken) "hesabın silindi" paneli kalır
    var showDeleted by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.deleted) {
        if (state.deleted) { deleteOpen = false; showDeleted = true; vm.consumeDeleted() }
    }
    LaunchedEffect(state.message) {
        if (messageText != null) { snackbar.showSnackbar(messageText); vm.consumeMessage() }
    }
    Scaffold(
        containerColor = colors.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            NeuTopBar(
                title = stringResource(R.string.account_settings),
                navigation = { NeuIconButton(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), onBack) },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp)
                .wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = 640.dp).testTag("account_screen"),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            val u = active ?: user
            if (!online) {
                Text(stringResource(R.string.account_offline), style = MaterialTheme.typography.bodyMedium, color = colors.danger, modifier = Modifier.testTag("account_offline"))
            }
            if (u == null || active == null) {
                if (showDeleted) {
                    NeuSurface(modifier = Modifier.fillMaxWidth().testTag("account_deleted_panel"), elevation = 6.dp) {
                        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Icon(Icons.Filled.CheckCircle, null, tint = colors.accent, modifier = Modifier.size(28.dp))
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.account_deleted_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(stringResource(R.string.account_deleted_text), style = MaterialTheme.typography.bodySmall, color = colors.contentMuted)
                            }
                        }
                    }
                }
                // Hesap bağlı değil: Google hesabıyla giriş (hesap seçici açılır)
                Text(stringResource(R.string.account_signin_text), style = MaterialTheme.typography.bodyMedium, color = colors.contentMuted)
                GoogleSignInButton(
                    { context.findActivity()?.let { vm.signIn(it) } },
                    Modifier.fillMaxWidth().testTag("account_screen_sign_in"), enabled = online && vm.available && !state.busy,
                )
                AccountDialogs(vm)
                return@Column
            }
            // Profil başlığı: halkalı fotoğraf, ad, e-posta
            NeuSurface(modifier = Modifier.fillMaxWidth().testTag("account_header"), elevation = 6.dp) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    RingAvatar(u, 64.dp)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(AccountViewModel.nameOf(u), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(u.email.orEmpty(), style = MaterialTheme.typography.bodySmall, color = colors.contentMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                            if (u.emailVerified) Icon(Icons.Filled.Verified, stringResource(R.string.account_email_verified), tint = colors.accent, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
            // Düzenlenebilir bilgiler
            SectionHeader(Icons.Filled.Badge, stringResource(R.string.account_personal_title))
            var given by rememberSaveable(u.id) { mutableStateOf(u.givenName.orEmpty()) }
            var family by rememberSaveable(u.id) { mutableStateOf(u.familyName.orEmpty()) }
            val birth = u.birthDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            var day by rememberSaveable(u.id) { mutableStateOf(birth?.dayOfMonth?.toString().orEmpty()) }
            var month by rememberSaveable(u.id) { mutableStateOf(birth?.monthValue?.toString().orEmpty()) }
            var year by rememberSaveable(u.id) { mutableStateOf(birth?.year?.toString().orEmpty()) }
            NeuTextField(given, { given = it.take(100) }, stringResource(R.string.account_given_name), Modifier.fillMaxWidth(), fieldModifier = Modifier.testTag("account_given"))
            NeuTextField(family, { family = it.take(100) }, stringResource(R.string.account_family_name), Modifier.fillMaxWidth(), fieldModifier = Modifier.testTag("account_family"))
            Text(stringResource(R.string.birth_date_label), style = MaterialTheme.typography.labelMedium, color = colors.contentMuted)
            BirthDateFields(day, month, year, { day = it }, { month = it }, { year = it }, tagPrefix = "account_birth")
            val parsed = parseBirthDate(day, month, year)
            if (year.length == 4 && parsed == null) Text(stringResource(R.string.birth_date_invalid), style = MaterialTheme.typography.bodySmall, color = colors.danger)
            NeuButton(
                stringResource(R.string.save_button), { vm.saveProfile(given, family, parsed) },
                Modifier.fillMaxWidth().testTag("account_save"), primary = true, enabled = online && parsed != null,
            )
            // Google'dan alınan bilgiler: yazı alanlarıyla aynı boyda salt okunur kutular
            Spacer(Modifier.size(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GoogleMark(20.dp)
                Text(stringResource(R.string.account_google_info), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            Text(stringResource(R.string.account_google_info_hint), style = MaterialTheme.typography.bodySmall, color = colors.contentMuted)
            Column(Modifier.fillMaxWidth().testTag("account_google_fields"), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                NeuReadOnlyField(
                    stringResource(R.string.account_email), u.email.orEmpty().ifEmpty { "—" }, Modifier.fillMaxWidth().testTag("account_info_email"),
                    trailing = {
                        StatusPill(
                            stringResource(if (u.emailVerified) R.string.account_email_verified else R.string.account_email_unverified),
                            good = u.emailVerified,
                        )
                    },
                )
                NeuReadOnlyField(
                    stringResource(R.string.account_photo), stringResource(if (u.photoUrl.isNullOrBlank()) R.string.account_photo_none else R.string.account_photo_google),
                    Modifier.fillMaxWidth().testTag("account_info_photo"),
                    leading = { ProfileAvatar(u, 26.dp) },
                )
                NeuReadOnlyField(
                    stringResource(R.string.account_created),
                    u.accountCreatedAt?.let { DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it)) } ?: "—",
                    Modifier.fillMaxWidth().testTag("account_info_created"),
                )
                // v36 Firebase/Google kimliği, hesap kimliği ve sunucu durumu gibi teknik alanlar gösterilmez
            }
            NeuButton(stringResource(R.string.account_refresh), vm::refreshFromGoogle, Modifier.fillMaxWidth().testTag("account_refresh"), enabled = online && !state.busy, icon = Icons.Filled.Refresh)
            // v37 oturum: çıkış yap (geri alınabilir; silmeden ayrı kart)
            AccountSessionCard(vm, enabled = !state.busy)
            // Tehlikeli bölge: hesabı sil (çıkıştan geniş boşluk ve ayraçla ayrılır)
            Spacer(Modifier.size(14.dp))
            Row(Modifier.fillMaxWidth().testTag("account_danger_zone"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f).height(1.dp).background(colors.danger.copy(alpha = 0.35f)))
                Text(stringResource(R.string.account_danger_zone), style = MaterialTheme.typography.labelMedium, color = colors.danger, fontWeight = FontWeight.SemiBold)
                Box(Modifier.weight(1f).height(1.dp).background(colors.danger.copy(alpha = 0.35f)))
            }
            NeuSurface(modifier = Modifier.fillMaxWidth(), elevation = 4.dp, inset = true) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Filled.WarningAmber, null, tint = colors.danger)
                        Text(stringResource(R.string.account_delete), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = colors.danger)
                    }
                    Text(stringResource(R.string.account_delete_short), style = MaterialTheme.typography.bodySmall, color = colors.contentMuted)
                    NeuButton(
                        stringResource(R.string.account_delete), { deleteOpen = true },
                        Modifier.fillMaxWidth().testTag("account_delete"), enabled = online && !state.busy,
                        icon = Icons.Filled.DeleteForever, contentColor = colors.danger,
                    )
                }
            }
            AccountDialogs(vm)
        }
    }
    if (deleteOpen) {
        DeleteAccountSheet(
            deleting = state.deleting,
            onDelete = { context.findActivity()?.let { vm.deleteAccount(it) } },
            onDismiss = { if (!state.deleting) deleteOpen = false },
        )
    }
}

/** Uzun kimliklerin kısaltılmışı: baştan 6, sondan 4 karakter. */
internal fun shortId(id: String): String = if (id.length <= 14) id else id.take(6) + "…" + id.takeLast(4)

@Composable
private fun SectionHeader(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, null, tint = NeuTheme.colors.accent, modifier = Modifier.size(20.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun StatusPill(text: String, good: Boolean) {
    val colors = NeuTheme.colors
    val tint = if (good) Color(0xFF16A34A) else colors.danger
    Row(
        Modifier.clip(CircleShape).background(tint.copy(alpha = 0.12f)).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(if (good) Icons.Filled.Verified else Icons.Filled.ErrorOutline, null, tint = tint, modifier = Modifier.size(14.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = tint, fontWeight = FontWeight.SemiBold)
    }
}

/** v34 onay metni [phrase] mi (baştaki/sondaki boşluklar ve büyük/küçük harf önemsiz; dile göre küçük harf). */
fun deletePhraseMatches(input: String, phrase: String, locale: Locale): Boolean {
    if (input.isBlank()) return false
    // Dilin küçük harf kuralı (ör. Türkçede I → ı) ve Türkçe kural: ikisinden biri tutarsa onaylanır
    return listOf(locale, Locale.forLanguageTag("tr"), Locale.ROOT).any { l ->
        fun norm(s: String) = s.trim().replace(Regex("\\s+"), " ").lowercase(l)
        norm(input) == norm(phrase)
    }
}

/**
 * v34 hesabı silme penceresi: neyin silineceği yazar; silme düğmesi yalnız onay metni ("hesabımı silmek istiyorum")
 * aynen yazılınca açılır. Silerken yazı (sonsuz dönen gösterge yok).
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun DeleteAccountSheet(deleting: Boolean, onDelete: () -> Unit, onDismiss: () -> Unit) {
    val colors = NeuTheme.colors
    val phrase = stringResource(R.string.account_delete_phrase)
    var typed by rememberSaveable { mutableStateOf("") }
    val config = androidx.compose.ui.platform.LocalConfiguration.current
    val locale = androidx.core.os.ConfigurationCompat.getLocales(config)[0] ?: Locale.getDefault()
    val ok = deletePhraseMatches(typed, phrase, locale)
    NeuSheet(onDismiss) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState())
                .padding(start = 22.dp, end = 22.dp, bottom = 24.dp).testTag("account_delete_sheet"),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(colors.danger.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.DeleteForever, null, tint = colors.danger)
                }
                Text(stringResource(R.string.account_delete_title), style = MaterialTheme.typography.headlineSmall)
            }
            Text(stringResource(R.string.account_delete_text), style = MaterialTheme.typography.bodyMedium, color = colors.contentMuted)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(R.string.account_delete_item_server, R.string.account_delete_item_firebase, R.string.account_delete_item_device, R.string.account_delete_item_pending).forEach {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(colors.danger))
                        Text(stringResource(it), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Text(stringResource(R.string.account_delete_keeps), style = MaterialTheme.typography.bodySmall, color = colors.contentMuted)
            Text(stringResource(R.string.account_delete_type), style = MaterialTheme.typography.bodyMedium)
            NeuSurface(modifier = Modifier.fillMaxWidth(), elevation = 3.dp, inset = true) {
                Text(
                    phrase, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = colors.danger,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp).testTag("account_delete_phrase"),
                )
            }
            NeuTextField(
                typed, { typed = it.take(80) }, stringResource(R.string.account_delete_input_label), Modifier.fillMaxWidth(),
                placeholder = phrase, fieldModifier = Modifier.testTag("account_delete_input"),
                isError = typed.isNotBlank() && !ok,
                supportingText = if (typed.isNotBlank() && !ok) stringResource(R.string.account_delete_mismatch) else null,
            )
            if (deleting) {
                Text(stringResource(R.string.account_deleting), style = MaterialTheme.typography.bodyMedium, color = colors.danger, modifier = Modifier.testTag("account_deleting"))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                NeuButton(stringResource(R.string.cancel_button), onDismiss, Modifier.weight(1f).testTag("account_delete_cancel"), enabled = !deleting)
                NeuButton(
                    stringResource(R.string.account_delete_confirm), onDelete, Modifier.weight(1f).testTag("account_delete_confirm"),
                    enabled = ok && !deleting, icon = Icons.Filled.DeleteForever, contentColor = colors.danger,
                )
            }
        }
    }
}

/** v34 Google "G" işareti (kodla çizilir; dört renkli yay ve yatay çubuk). */
@Composable
fun GoogleMark(size: Dp, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier.size(size)) {
        val w = this.size.minDimension
        val stroke = w * 0.2f
        val inset = stroke / 2f
        val arc = androidx.compose.ui.geometry.Size(w - stroke, w - stroke)
        val tl = androidx.compose.ui.geometry.Offset(inset, inset)
        val style = androidx.compose.ui.graphics.drawscope.Stroke(stroke)
        drawArc(Color(0xFFEA4335), 198f, 119f, false, tl, arc, style = style)
        drawArc(Color(0xFFFBBC05), 143f, 58f, false, tl, arc, style = style)
        drawArc(Color(0xFF34A853), 42f, 104f, false, tl, arc, style = style)
        drawArc(Color(0xFF4285F4), -2f, 47f, false, tl, arc, style = style)
        drawRect(Color(0xFF4285F4), topLeft = androidx.compose.ui.geometry.Offset(w * 0.5f, w * 0.5f - stroke / 2f), size = androidx.compose.ui.geometry.Size(w * 0.5f, stroke))
    }
}

/** v34 Google ile giriş düğmesi: beyaz daire içinde G işareti ve yazı (birincil renk). */
@Composable
fun GoogleSignInButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, text: String = stringResource(R.string.account_signin_google)) {
    val colors = NeuTheme.colors
    Row(
        modifier.clip(CircleShape)
            .background(if (enabled) Brush.linearGradient(listOf(colors.gradientStart, colors.gradientEnd)) else Brush.linearGradient(listOf(colors.contentMuted.copy(alpha = 0.35f), colors.contentMuted.copy(alpha = 0.35f))))
            .clickable(enabled = enabled, role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
            .padding(start = 6.dp, end = 18.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) { GoogleMark(18.dp) }
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** v34 gradyan halkalı profil fotoğrafı (ana sayfa ve hesap ayarları). */
@Composable
fun RingAvatar(user: LocalUserEntity, size: Dp, modifier: Modifier = Modifier, background: Color = NeuTheme.colors.background) {
    val c = NeuTheme.colors
    val ring = Brush.sweepGradient(listOf(c.gradientStart, c.gradientEnd, BrandColors.Rose, BrandColors.Amber, c.gradientStart))
    Box(
        modifier.size(size).clip(CircleShape).background(ring).padding(2.5.dp).clip(CircleShape)
            .background(background).padding(2.5.dp),
        contentAlignment = Alignment.Center,
    ) {
        ProfileAvatar(user, size - 10.dp)
    }
}

/**
 * v34 ana sayfanın en üstünde giriş önerisi (internet varken ve giriş yapılmamışken): G işareti, kısa başlık ve metin,
 * dokununca doğrudan Google hesap seçici açılır; ilk girişte doğum tarihi sorulur.
 */
@Composable
fun SignInSuggestionCard(vm: AccountViewModel, modifier: Modifier = Modifier) {
    val colors = NeuTheme.colors
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    NeuSurface(modifier = modifier.fillMaxWidth().testTag("home_sign_in_card"), elevation = 8.dp) {
        Box(Modifier.fillMaxWidth()) {
            // Köşede yumuşak marka parıltısı
            Box(
                Modifier.matchParentSize().background(
                    Brush.radialGradient(listOf(colors.gradientEnd.copy(alpha = 0.16f), Color.Transparent), center = androidx.compose.ui.geometry.Offset(40f, 30f), radius = 520f),
                ),
            )
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    // Profil simgesi marka gradyanında; G işareti düğmede
                    Box(
                        Modifier.size(48.dp).clip(CircleShape).background(Brush.linearGradient(listOf(colors.gradientStart, colors.gradientEnd))),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.Person, null, tint = Color.White, modifier = Modifier.size(28.dp)) }
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.home_signin_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.home_signin_text), style = MaterialTheme.typography.bodySmall, color = colors.contentMuted)
                    }
                }
                GoogleSignInButton(
                    { context.findActivity()?.let { vm.signIn(it) } },
                    Modifier.fillMaxWidth().testTag("home_sign_in"), enabled = !state.busy && vm.available,
                    text = stringResource(R.string.home_signin_button),
                )
                if (state.busy) Text(stringResource(R.string.account_busy), style = MaterialTheme.typography.bodySmall, color = colors.accent)
                state.message?.takeIf { it != R.string.birth_date_invalid && it != R.string.account_signed_in }?.let {
                    Text(stringResource(it) + (state.detail?.let { d -> "\n" + com.karapp.drawguidance.i18n.I18n.tr(d) } ?: ""), style = MaterialTheme.typography.bodySmall, color = colors.contentMuted, modifier = Modifier.testTag("home_sign_in_message"))
                }
            }
        }
    }
    AccountDialogs(vm)
}
