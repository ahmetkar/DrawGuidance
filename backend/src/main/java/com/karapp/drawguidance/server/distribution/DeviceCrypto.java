package com.karapp.drawguidance.server.distribution;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * v43 cihaz bilgilerinin sunucuda şifreli tutulması.
 *
 * <ul>
 *   <li><b>Cihaz anahtarı</b> ({@link #deviceKey}): cihazın ürettiği kimlik ({@code deviceId}) saklanmaz; ondan
 *       HMAC-SHA256 ile türetilen değer belge kimliği olur. Aynı cihaz her seferinde aynı belgeyi bulur, ama
 *       veritabanından cihaz kimliği geri çıkarılamaz.</li>
 *   <li><b>Bilgiler</b> ({@link #encrypt}/{@link #decrypt}): model, üretici, Android sürümü, dil, FCM jetonu
 *       AES-256-GCM ile (her kayıtta rastgele 12 baytlık IV, 128 bit doğrulama etiketi) şifrelenir. Biçim:
 *       {@code v1.<base64url(iv)>.<base64url(şifreli+etiket)>}. Ek doğrulanan veri (AAD) olarak cihaz anahtarı
 *       kullanılır: bir cihazın şifreli alanı başka cihazın belgesine kopyalanırsa çözülmez.</li>
 *   <li>Ana anahtardan iki ayrı alt anahtar türetilir (HMAC-SHA256 ile, farklı etiketler): şifreleme ve HMAC aynı
 *       anahtarı paylaşmaz.</li>
 * </ul>
 */
public final class DeviceCrypto {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String PREFIX = "v1.";

    private final SecretKeySpec aesKey;
    private final SecretKeySpec macKey;
    private final boolean persistent;

    public DeviceCrypto(byte[] master) { this(master, true); }

    private DeviceCrypto(byte[] master, boolean persistent) {
        this.persistent = persistent;
        if (master == null || master.length < 32) throw new IllegalArgumentException("Cihaz şifreleme anahtarı en az 32 bayt olmalı");
        this.aesKey = new SecretKeySpec(derive(master, "dg-device-aes-v1"), "AES");
        this.macKey = new SecretKeySpec(derive(master, "dg-device-mac-v1"), "HmacSHA256");
    }

    /** Base64 (standart ya da URL) anahtardan. */
    public static DeviceCrypto fromBase64(String key) {
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(key.trim());
        } catch (IllegalArgumentException e) {
            raw = Base64.getUrlDecoder().decode(key.trim());
        }
        return new DeviceCrypto(raw);
    }

    /**
     * v49.2 anahtar ayarlı değilken kullanılan geçici rastgele anahtar: yeniden başlatınca kaybolur. Eski kayıtları
     * şifreleme gibi geri alınamayan işler {@link #persistent()} false ise yapılmaz.
     */
    public static DeviceCrypto ephemeral() {
        byte[] k = new byte[32];
        RANDOM.nextBytes(k);
        return new DeviceCrypto(k, false);
    }

    /** Anahtar ayarlardan mı geldi (kalıcı mı)? */
    public boolean persistent() { return persistent; }

    /** Rastgele 32 baytlık anahtar (base64). */
    public static String newKey() {
        byte[] k = new byte[32];
        RANDOM.nextBytes(k);
        return Base64.getEncoder().encodeToString(k);
    }

    /** Cihaz kimliğinden belge kimliği (64 onaltılık karakter). */
    public String deviceKey(String deviceId) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(macKey);
            return HexFormat.of().formatHex(mac.doFinal(deviceId.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public String encrypt(String plain, String deviceKey) {
        if (plain == null) return null;
        try {
            byte[] iv = new byte[12];
            RANDOM.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, aesKey, new GCMParameterSpec(128, iv));
            c.updateAAD(deviceKey.getBytes(StandardCharsets.UTF_8));
            byte[] out = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            Base64.Encoder b = Base64.getUrlEncoder().withoutPadding();
            return PREFIX + b.encodeToString(iv) + "." + b.encodeToString(out);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Şifrelenemedi", e);
        }
    }

    /** Çözülemezse (anahtar değişmiş, veri bozulmuş, başka cihazın verisi) null. */
    public String decrypt(String sealed, String deviceKey) {
        if (sealed == null || !sealed.startsWith(PREFIX)) return null;
        String[] parts = sealed.substring(PREFIX.length()).split("\\.");
        if (parts.length != 2) return null;
        try {
            Base64.Decoder d = Base64.getUrlDecoder();
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, aesKey, new GCMParameterSpec(128, d.decode(parts[0])));
            c.updateAAD(deviceKey.getBytes(StandardCharsets.UTF_8));
            return new String(c.doFinal(d.decode(parts[1])), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            return null;
        }
    }

    private static byte[] derive(byte[] master, String label) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(master, "HmacSHA256"));
            return mac.doFinal(label.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
