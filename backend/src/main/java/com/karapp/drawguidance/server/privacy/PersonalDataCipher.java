package com.karapp.drawguidance.server.privacy;

import com.karapp.drawguidance.server.distribution.DeviceCrypto;
import org.springframework.stereotype.Component;

/**
 * v49.2 hata raporu, geri bildirim ve web formlarındaki cihaz bilgisinin (telefon modeli, tarayıcı bilgisi) veritabanında
 * şifreli tutulması. Cihaz kayıtlarıyla aynı anahtar ({@code DEVICE_DATA_KEY}) ve aynı biçim kullanılır: AES-256-GCM,
 * {@code v1.<iv>.<şifreli>} ({@link DeviceCrypto}).
 *
 * <p>Ek doğrulanan veri (AAD) olarak "kayıt türü + belge kimliği" kullanılır ({@link #FEEDBACK} + id gibi): şifreli
 * değer başka bir belgeye ya da başka bir koleksiyona kopyalanırsa çözülmez.
 */
@Component
public class PersonalDataCipher {
    /** Kayıt türleri (AAD öneki). */
    public static final String FEEDBACK = "feedback:", REPORT = "report:", SUPPORT = "support:", DELETION = "deletion:";

    private final DeviceCrypto crypto;

    public PersonalDataCipher(DeviceCrypto crypto) {
        this.crypto = crypto;
    }

    /** Şifreler; [plain] null ya da boşsa null. */
    public String seal(String kind, String id, String plain) {
        if (plain == null || plain.isEmpty()) return null;
        return crypto.encrypt(plain, kind + id);
    }

    /** Çözer; çözülemezse (anahtar değişmiş, veri bozulmuş, başka belgenin verisi) null. */
    public String open(String kind, String id, String sealed) {
        return sealed == null ? null : crypto.decrypt(sealed, kind + id);
    }

    /** Anahtar ayarlardan geldi mi? Geçici anahtarla eski kayıtlar şifrelenmez (sonra okunamazdı). */
    public boolean persistent() { return crypto.persistent(); }
}
