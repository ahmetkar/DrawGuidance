package com.karapp.drawguidance.data.remote.v1

import kotlinx.serialization.Serializable

/** v33 sunucunun hata gövdesi: `{"error": "<kod>", "message": "<açıklama>"}`. */
@Serializable
data class RemoteErrorDto(val error: String? = null, val message: String? = null)

/** v33 sunucudaki kullanıcı (`users` koleksiyonu): tek birincil kimlik ([id]) ve Google hesabı bilgileri. */
@Serializable
data class RemoteUser(
    val id: String,
    val firebaseUid: String,
    val displayName: String? = null,
    val givenName: String? = null,
    val familyName: String? = null,
    val email: String? = null,
    val emailVerified: Boolean = false,
    val photoUrl: String? = null,
    val accountCreatedAt: Long? = null,
    val birthDate: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val version: Long = 0,
)

/** v33 `PUT /api/v1/users/me` gövdesi (tam değiştirme). */
@Serializable
data class UserBody(
    val id: String,
    val firebaseUid: String,
    val displayName: String? = null,
    val givenName: String? = null,
    val familyName: String? = null,
    val email: String? = null,
    val emailVerified: Boolean = false,
    val photoUrl: String? = null,
    val accountCreatedAt: Long? = null,
    val birthDate: String? = null,
    val clientUpdatedAt: Long = System.currentTimeMillis(),
    /** v36 kullanım şartları/KVKK onayı. */
    val termsAcceptedAt: Long? = null,
    val termsVersion: String? = null,
)

/** v33 katalog özeti: sürüm, değişim zamanı ve kayıt sayıları; v37 ikili biçimde verilebilen bölümler. */
@Serializable
data class CatalogManifest(
    val version: Long = 0,
    val updatedAt: String? = null,
    val counts: Map<String, Int> = emptyMap(),
    val binary: CatalogBinaryInfo? = null,
) {
    /** Sunucu bu bölümü ([CatalogBinaryInfo.VECTORS], [CatalogBinaryInfo.MODE_NETS]) ikili verebilir mi. */
    fun binary(section: String): Boolean = binary?.sections?.contains(section) == true
}

/** v37 manifestteki ikili biçim bilgisi (eski sunucuda yok: JSON kullanılır). */
@Serializable
data class CatalogBinaryInfo(val mediaType: String = "", val formatVersion: Int = 0, val sections: List<String> = emptyList()) {
    companion object {
        const val VECTORS = "vectors"
        const val MODE_NETS = "modeNets"
    }
}
