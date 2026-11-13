package com.karapp.drawguidance.ui.screens.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.filled.Block
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.karapp.drawguidance.R
import com.karapp.drawguidance.data.account.AuthException
import com.karapp.drawguidance.data.remote.v1.RemoteException
import com.karapp.drawguidance.ui.theme.NeuTheme

/** v40 Google ile girişin sonucu (sonuç penceresinde kısa açıklama ve uygun düğme). */
enum class SignInOutcome {
    SUCCESS, CANCELLED, NO_NETWORK, ACCOUNT_PROBLEM, SERVER_UNREACHABLE, UNKNOWN;

    /** "Tekrar dene" düğmesi gösterilir mi (başarıda ve bilinmeyende değil… bilinmeyende de denenebilir). */
    val canRetry: Boolean get() = this != SUCCESS

    companion object {
        /**
         * Hatadan sonuç (saf, birim testli): iptal; ağ hatası bağlantı yoksa "internet yok", varsa "sunucuya
         * ulaşılamadı"; hesap bulunamadı ya da Google/Firebase hesabı reddettiyse "hesap sorunu"; gerisi bilinmeyen.
         */
        fun of(error: Throwable?, online: Boolean): SignInOutcome = when (error) {
            null -> SUCCESS
            is AuthException -> when (error.reason) {
                AuthException.Reason.CANCELLED -> CANCELLED
                AuthException.Reason.NETWORK -> if (online) SERVER_UNREACHABLE else NO_NETWORK
                AuthException.Reason.NO_ACCOUNT -> ACCOUNT_PROBLEM
                AuthException.Reason.NOT_CONFIGURED -> UNKNOWN
                AuthException.Reason.FAILED -> if (!online) NO_NETWORK else UNKNOWN
            }
            is RemoteException -> if (!online) NO_NETWORK else if (error.isUnauthorized || error.status == 403) ACCOUNT_PROBLEM else SERVER_UNREACHABLE
            is java.io.IOException -> if (online) SERVER_UNREACHABLE else NO_NETWORK
            else -> if (!online) NO_NETWORK else UNKNOWN
        }
    }
}

/** v40 giriş sonucu penceresi: başlık, kısa açıklama; başarıda "Tamam", hatalarda "Tekrar dene" ve "Tamam". */
@Composable
fun SignInResultDialog(outcome: SignInOutcome, onRetry: () -> Unit, onDismiss: () -> Unit) {
    val c = NeuTheme.colors
    val (icon, title, text) = when (outcome) {
        SignInOutcome.SUCCESS -> Triple(Icons.Filled.CheckCircle, R.string.v40_signin_ok_title, R.string.v40_signin_ok_text)
        SignInOutcome.CANCELLED -> Triple(Icons.Filled.Block, R.string.v40_signin_cancel_title, R.string.v40_signin_cancel_text)
        SignInOutcome.NO_NETWORK -> Triple(Icons.Filled.WifiOff, R.string.v40_signin_offline_title, R.string.v40_signin_offline_text)
        SignInOutcome.ACCOUNT_PROBLEM -> Triple(Icons.Filled.ManageAccounts, R.string.v40_signin_account_title, R.string.v40_signin_account_text)
        SignInOutcome.SERVER_UNREACHABLE -> Triple(Icons.Filled.CloudOff, R.string.v40_signin_server_title, R.string.v40_signin_server_text)
        SignInOutcome.UNKNOWN -> Triple(Icons.Filled.ErrorOutline, R.string.v40_signin_unknown_title, R.string.v40_signin_unknown_text)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.background,
        modifier = Modifier.testTag("signin_result_${outcome.name.lowercase()}"),
        icon = { Icon(icon, null, tint = if (outcome == SignInOutcome.SUCCESS) c.accent else c.danger) },
        title = { Text(stringResource(title)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { Text(stringResource(text)) } },
        confirmButton = {
            if (outcome.canRetry) {
                TextButton(onClick = { onDismiss(); onRetry() }, modifier = Modifier.testTag("signin_result_retry")) {
                    Text(stringResource(if (outcome == SignInOutcome.ACCOUNT_PROBLEM) R.string.v40_signin_other_account else R.string.v40_retry))
                }
            } else {
                TextButton(onClick = onDismiss, modifier = Modifier.testTag("signin_result_ok")) { Text(stringResource(R.string.v40_ok)) }
            }
        },
        dismissButton = if (outcome.canRetry) {
            { TextButton(onClick = onDismiss, modifier = Modifier.testTag("signin_result_ok")) { Text(stringResource(R.string.v40_ok)) } }
        } else null,
    )
}

/** v40 hesap ekranlarında giriş sonucu penceresi (AccountDialogs çağırır). */
@Composable
internal fun SignInResultHost(vm: AccountViewModel, outcome: SignInOutcome?) {
    val context = LocalContext.current
    outcome?.let { o -> SignInResultDialog(o, onRetry = { context.findActivity()?.let { vm.signIn(it, vm.lastMethod) } }, onDismiss = vm::consumeSignInOutcome) }
}
