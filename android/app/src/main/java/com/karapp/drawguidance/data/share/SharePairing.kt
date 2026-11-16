package com.karapp.drawguidance.data.share

import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * v54 Wi-Fi paylaşımı ve canlı yayın için eşleşme şifreli bağlantı (Android ve iOS'ta bayt düzeyinde aynı; iOS
 * `SharePairing.swift`).
 *
 * 1. Bağlanan (A) tanışmasında geçici P-256 açık anahtarını gönderir; kabul eden (B) kendi anahtarıyla yanıtlar.
 * 2. Ortak sır Z = ECDH. Eşleşme sırrı S: ilk eşleşmede B'nin ekranında görünen 6 haneli şifre (A'da girilir), daha
 *    önce eşleşmiş cihazlarda saklanan güven anahtarı. Tanışma çerçevelerinin özeti T.
 * 3. PRK = HMAC-SHA256(SHA-256("dgshare-v2-pair" ‖ S), Z ‖ T); anahtarlar PRK'dan etiketle türetilir (a->b, b->a,
 *    confirm, trust).
 * 4. Önce A doğrulama kodunu (HMAC(confirm, "A")) gönderir; B doğrularsa kendi kodunu (HMAC(confirm, "B")) gönderir.
 *    Yanlış şifrede bağlantı kapanır; her bağlantı isteğinde yeni şifre üretilir.
 * 5. Sonraki her çerçeve AES-256-GCM ile şifrelenir: yöne göre ayrı anahtar, nonce = 4 sıfır bayt ‖ 8 bayt sayaç.
 *
 * Ağı dinleyen biri Z'yi bilmediğinden şifreyi deneyerek bulamaz; araya giren (aktif) saldırgan her bağlantıda tek
 * tahmin hakkına sahiptir.
 */
object SharePairing {
    const val VERSION = 2
    const val CODE_DIGITS = 6
    private const val CURVE = "secp256r1"
    private val random = SecureRandom()

    class Keys(val aToB: ByteArray, val bToA: ByteArray, val confirm: ByteArray, val trust: ByteArray)

    fun newKeyPair(): KeyPair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec(CURVE), random) }.generateKeyPair()

    /** Açık anahtarın sıkıştırılmamış X9.63 biçimi (0x04 ‖ X ‖ Y, 65 bayt; iOS `x963Representation`). */
    fun publicRaw(kp: KeyPair): ByteArray {
        val w = (kp.public as ECPublicKey).w
        return byteArrayOf(4) + fixed(w.affineX) + fixed(w.affineY)
    }

    /** ECDH ortak sırrı (X koordinatı, 32 bayt). */
    fun sharedSecret(kp: KeyPair, peerRaw: ByteArray): ByteArray {
        require(peerRaw.size == 65 && peerRaw[0].toInt() == 4) { "açık anahtar bozuk" }
        val params: ECParameterSpec = (kp.public as ECPublicKey).params
        val point = ECPoint(BigInteger(1, peerRaw.copyOfRange(1, 33)), BigInteger(1, peerRaw.copyOfRange(33, 65)))
        val peer = KeyFactory.getInstance("EC").generatePublic(ECPublicKeySpec(point, params))
        return KeyAgreement.getInstance("ECDH").run { init(kp.private); doPhase(peer, true); generateSecret() }
    }

    /** Ekranda gösterilecek 6 haneli eşleşme şifresi. */
    fun newCode(): String = (0 until CODE_DIGITS).joinToString("") { random.nextInt(10).toString() }

    /** Girilen şifrenin karşılaştırılan biçimi (boşluklar atılır). */
    fun normalizeCode(code: String): String = code.filter { it.isDigit() }

    fun transcript(helloRequest: ByteArray, helloReply: ByteArray): ByteArray = sha256(helloRequest + helloReply)

    fun derive(z: ByteArray, secret: ByteArray, transcript: ByteArray): Keys {
        val salt = sha256("dgshare-v2-pair".encodeToByteArray() + secret)
        val prk = hmac(salt, z + transcript)
        fun expand(label: String) = hmac(prk, label.encodeToByteArray() + byteArrayOf(1))
        return Keys(expand("a->b"), expand("b->a"), expand("confirm"), expand("trust"))
    }

    fun confirmTag(keys: Keys, role: String): ByteArray = hmac(keys.confirm, role.encodeToByteArray())

    /** Sabit süreli karşılaştırma. */
    fun same(a: ByteArray, b: ByteArray): Boolean = MessageDigest.isEqual(a, b)

    /** Tek yönde şifreleyici: her çerçevede sayaç bir artar. */
    class Sealer(key: ByteArray) {
        private val spec = SecretKeySpec(key, "AES")
        private var counter = 0L

        @Synchronized
        fun seal(plain: ByteArray): ByteArray {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.ENCRYPT_MODE, spec, GCMParameterSpec(128, nonce(counter++)))
            return c.doFinal(plain)
        }
    }

    /** Tek yönde çözücü: çerçeveler sırayla gelir; bozuk ya da sırası değişmiş çerçeve hata verir. */
    class Opener(key: ByteArray) {
        private val spec = SecretKeySpec(key, "AES")
        private var counter = 0L

        @Synchronized
        fun open(sealed: ByteArray): ByteArray {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, spec, GCMParameterSpec(128, nonce(counter++)))
            return c.doFinal(sealed)
        }
    }

    private fun nonce(counter: Long): ByteArray {
        val n = ByteArray(12)
        for (i in 0 until 8) n[4 + i] = (counter ushr (56 - 8 * i)).toByte()
        return n
    }

    private fun fixed(v: BigInteger): ByteArray {
        val b = v.toByteArray()
        return when {
            b.size == 32 -> b
            b.size > 32 -> b.copyOfRange(b.size - 32, b.size)
            else -> ByteArray(32 - b.size) + b
        }
    }

    fun sha256(b: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(b)

    fun hmac(key: ByteArray, data: ByteArray): ByteArray = Mac.getInstance("HmacSHA256").run { init(SecretKeySpec(key, "HmacSHA256")); doFinal(data) }
}
