package com.karapp.drawguidance.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.karapp.drawguidance.R
import com.karapp.drawguidance.data.account.AccountRepository
import com.karapp.drawguidance.ui.theme.NeuTheme
import org.koin.compose.koinInject

/**
 * v40 "Giriş gerekli" uyarısı: girişsiz kullanıcı sorun bildirmek ya da geri bildirim göndermek isteyince. "Giriş yap"
 * Google ile girişi başlatır ([onSignIn]; doğum tarihi ve sonuç pencerelerini çağıranın AccountDialogs'u gösterir).
 */
@Composable
fun SignInRequiredDialog(onSignIn: () -> Unit, onDismiss: () -> Unit, text: Int = R.string.v40_signin_required_text) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NeuTheme.colors.background,
        modifier = Modifier.testTag("signin_required"),
        icon = { Icon(Icons.AutoMirrored.Filled.Login, null, tint = NeuTheme.colors.accent) },
        title = { Text(stringResource(R.string.v40_signin_required_title)) },
        text = { Text(stringResource(text)) },
        confirmButton = {
            TextButton(onClick = { onDismiss(); onSignIn() }, modifier = Modifier.testTag("signin_required_sign_in")) {
                Text(stringResource(R.string.v40_sign_in))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("signin_required_cancel")) { Text(stringResource(R.string.cancel_button)) }
        },
    )
}

/** v40 cihazda giriş yapılmış mı (sorun bildirme ve geri bildirim için; bağlantı aranmaz, iş olay tablosunda bekler). */
@Composable
fun rememberSignedIn(): Boolean {
    val account = koinInject<AccountRepository>()
    val user by account.user.collectAsState()
    return user?.signedIn == true
}

/** v40 bağlamın etkinliği (Google girişi etkinlik ister). */
fun android.content.Context.activityOrNull(): android.app.Activity? =
    generateSequence(this) { (it as? android.content.ContextWrapper)?.baseContext }.filterIsInstance<android.app.Activity>().firstOrNull()
