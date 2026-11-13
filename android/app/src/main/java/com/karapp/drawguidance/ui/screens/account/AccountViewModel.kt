package com.karapp.drawguidance.ui.screens.account

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karapp.drawguidance.R
import com.karapp.drawguidance.data.account.AccountRepository
import com.karapp.drawguidance.data.account.AuthException
import com.karapp.drawguidance.data.account.GoogleAccount
import com.karapp.drawguidance.data.account.SignInMethod
import com.karapp.drawguidance.data.account.SignInResult
import com.karapp.drawguidance.data.catalog.CatalogStore
import com.karapp.drawguidance.data.local.LocalUserEntity
import com.karapp.drawguidance.data.sync.SyncEngine
import com.karapp.drawguidance.data.sync.SyncTypes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** v33 hesap ekranlarının durumu: giriş sürüyor mu, doğum tarihi bekleniyor mu, gösterilecek ileti. */
data class AccountUiState(
    val busy: Boolean = false,
    /** İlk girişte doğum tarihi sorulacak hesap. */
    val pendingBirth: GoogleAccount? = null,
    /** Kullanıcıya gösterilecek ileti (string kaynağı). */
    val message: Int? = null,
    /** v34 hesap siliniyor. */
    val deleting: Boolean = false,
    /** v34 hesap silindi (hesap ayarları kapanır). */
    val deleted: Boolean = false,
    /** v35 hatanın ayrıntısı (ör. Google hata kodu); iletinin altında gösterilir. */
    val detail: String? = null,
    /** v40 giriş denemesinin sonucu (sonuç penceresi; kapatılınca null). */
    val signInOutcome: SignInOutcome? = null,
)

/**
 * v33 Ayarlar'daki hesap kartı, hesap ayarları sayfası ve ana sayfadaki profil: Google ile giriş/çıkış, doğum tarihi,
 * ad-soyad, hesabı silme (v34; hesabı değiştir kaldırıldı); sunucu eşitleme durumu (bekleyen işler, katalog).
 */
class AccountViewModel(
    private val account: AccountRepository,
    private val engine: SyncEngine,
    private val catalog: com.karapp.drawguidance.data.catalog.CatalogSyncer,
    private val store: CatalogStore,
) : ViewModel() {
    val user: StateFlow<LocalUserEntity?> get() = account.user
    /** Giriş yapılmış ve internet var. */
    val active: StateFlow<LocalUserEntity?> get() = account.active
    val online: StateFlow<Boolean> get() = account.online
    val available: Boolean get() = account.available
    val appleAvailable: Boolean get() = account.appleAvailable
    /** Son denenen giriş yöntemi ("yeniden dene" aynı yöntemle açılır). */
    var lastMethod: SignInMethod = SignInMethod.GOOGLE
        private set
    /** Bekleyen iş sayısı (ekranda tek akış; her çizimde yeni akış açılmasın). */
    val pendingCount: StateFlow<Int> = engine.pendingCount.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000), 0)

    private val _state = MutableStateFlow(AccountUiState())
    val state: StateFlow<AccountUiState> = _state.asStateFlow()

    /** Kataloğun sürümü ve son denetim zamanı. */
    val catalogVersion: Long get() = store.appliedVersion
    val catalogLastCheck: Long get() = store.lastCheck
    /** v39 son eşitleme (bu açılışta yapıldıysa; yoksa katalog denetim zamanı gösterilir). */
    val lastSync: StateFlow<Long?> get() = engine.lastRun

    fun signIn(activity: Activity, method: SignInMethod = SignInMethod.GOOGLE) {
        if (_state.value.busy) return
        lastMethod = method
        _state.value = AccountUiState(busy = true)
        viewModelScope.launch {
            _state.value = try {
                when (val r = account.signIn(activity, method)) {
                    is SignInResult.Done -> AccountUiState(message = R.string.account_signed_in, signInOutcome = SignInOutcome.SUCCESS)
                    is SignInResult.NeedsBirthDate -> AccountUiState(pendingBirth = r.account)
                }
            } catch (e: AuthException) {
                com.karapp.drawguidance.diagnostics.AppLog.w(TAG, "giriş: ${e.reason} ${e.message}", e)
                AccountUiState(message = messageOf(e.reason), detail = e.message?.takeIf { it != e.reason.name }, signInOutcome = SignInOutcome.of(e, online.value))
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                com.karapp.drawguidance.diagnostics.AppLog.w(TAG, "giriş başarısız", e)
                AccountUiState(message = R.string.account_error_failed, detail = e.message, signInOutcome = SignInOutcome.of(e, online.value))
            }
        }
    }

    /** İlk girişte sorulan doğum tarihi. */
    /** İlk girişte doğum tarihi; v36 hesap açılırken kullanım şartları ve KVKK metni onaylanmış olmalı ([termsVersion]). */
    fun completeBirth(date: LocalDate, termsVersion: String? = null) {
        val pending = _state.value.pendingBirth ?: return
        viewModelScope.launch {
            _state.value = runCatching { account.complete(pending, date, termsVersion) }
                .fold({ AccountUiState(message = R.string.account_signed_in, signInOutcome = SignInOutcome.SUCCESS) }, { AccountUiState(pendingBirth = pending, message = R.string.birth_date_invalid) })
        }
    }

    /** Doğum tarihi verilmezse giriş tamamlanmaz (oturum kapatılır). */
    fun cancelBirth() {
        _state.value = AccountUiState()
        viewModelScope.launch { account.signOut() }
    }

    fun saveProfile(givenName: String, familyName: String, birthDate: LocalDate?) {
        viewModelScope.launch {
            _state.value = runCatching { account.updateProfile(givenName, familyName, birthDate) }
                .fold({ AccountUiState(message = R.string.account_saved) }, { AccountUiState(message = R.string.birth_date_invalid) })
        }
    }

    fun refreshFromGoogle() {
        viewModelScope.launch {
            _state.value = AccountUiState(busy = true)
            runCatching { account.refreshFromGoogle() }
            _state.value = AccountUiState(message = R.string.account_refreshed)
        }
    }

    fun signOut() {
        viewModelScope.launch {
            account.signOut()
            _state.value = AccountUiState(message = R.string.account_signed_out)
        }
    }

    /**
     * v34 hesabı siler (sunucu, Firebase, cihaz; bekleyen sunucu işi). Kimlik yeniden doğrulanır; sunucuya
     * ulaşılamazsa hiçbir şey silinmez.
     */
    fun deleteAccount(activity: Activity) {
        if (_state.value.busy) return
        _state.value = AccountUiState(busy = true, deleting = true)
        viewModelScope.launch {
            _state.value = try {
                when (account.deleteAccount(activity)) {
                    com.karapp.drawguidance.data.account.DeleteResult.DONE -> AccountUiState(message = R.string.account_deleted, deleted = true)
                    com.karapp.drawguidance.data.account.DeleteResult.FIREBASE_FAILED -> AccountUiState(message = R.string.account_delete_partial, deleted = true)
                }
            } catch (e: AuthException) {
                android.util.Log.w(TAG, "hesap silme: kimlik doğrulanamadı ${e.reason}", e)
                AccountUiState(message = if (e.reason == AuthException.Reason.CANCELLED) R.string.account_delete_cancelled else messageOf(e.reason))
            } catch (e: Exception) {
                android.util.Log.w(TAG, "hesap silinemedi", e)
                AccountUiState(message = R.string.account_delete_server_failed)
            }
        }
    }

    /** Katalog ve bekleyen işler hemen denenir. */
    fun syncNow() {
        viewModelScope.launch {
            engine.enqueue(SyncTypes.CATALOG_SYNC)
            // v39 çizim görevi ilerlemesi de eşitlenir
            engine.enqueue(com.karapp.drawguidance.data.challenge.ChallengeProgressRepository.SYNC_TYPE)
            engine.process()
        }
    }

    fun consumeMessage() { _state.value = _state.value.copy(message = null) }

    /** v40 giriş sonucu penceresi kapandı. */
    fun consumeSignInOutcome() { _state.value = _state.value.copy(signInOutcome = null) }

    /** v34 silme sonrası hesap ayarları kapandı. */
    fun consumeDeleted() { _state.value = _state.value.copy(deleted = false) }

    private fun messageOf(reason: AuthException.Reason): Int = when (reason) {
        AuthException.Reason.CANCELLED -> R.string.account_error_cancelled
        AuthException.Reason.NO_ACCOUNT -> R.string.account_error_no_account
        AuthException.Reason.NOT_CONFIGURED -> R.string.account_error_not_configured
        AuthException.Reason.NETWORK -> R.string.account_error_network
        AuthException.Reason.FAILED -> R.string.account_error_failed
    }

    /** Ana sayfa / ayarlar için gösterilen ad. */
    companion object {
        private const val TAG = "Account"

        fun nameOf(u: LocalUserEntity): String =
            listOfNotNull(u.givenName, u.familyName).joinToString(" ").ifBlank { u.displayName ?: u.email ?: "" }
    }
}
