package com.karapp.drawguidance.server.distribution.cli;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;

/**
 * v54 yönetim komut satırının şifresi. Komut satırı ({@code cli}) şifre doğrulanmadan hiçbir komutu (paket,
 * bildirim, duyuru, sürüm, gönderim, kullanıcı, cihaz…) çalıştırmaz.
 *
 * <ul>
 *   <li>Şifrenin kendisi sunucuda tutulmaz; yalnız PBKDF2-HMAC-SHA256 özeti ({@code pbkdf2-sha256:<tur>:<tuz>:<özet>}; ayraç ':' — compose .env'de '$' değişken sayılmasın).
 *       Özet {@code CLI_PASSWORD_HASH} ya da Docker sırrı olarak {@code CLI_PASSWORD_HASH_FILE} ile verilir.</li>
 *   <li>Şifre terminalde gizli sorulur (yankısız); terminal yoksa standart girdinin ilk satırı okunur (betikle
 *       {@code printf '%s\n' "$SIFRE" | docker exec -i …}). Komut satırı argümanında şifre kabul edilmez (süreç
 *       listesinde görünmesin).</li>
 *   <li>Terminalde en çok 3 deneme; her yanlış denemeden sonra 2 sn beklenir. Karşılaştırma sabit sürelidir.</li>
 *   <li>Özet üretmek için {@code cli sifre-ozeti} (şifre sormaz doğrulamaz; yalnız yeni özet yazar).</li>
 * </ul>
 */
public final class CliAuth {
    public static final String ENV = "CLI_PASSWORD_HASH";
    static final String PREFIX = "pbkdf2-sha256";
    static final int ITERATIONS = 310_000;
    static final int MIN_LENGTH = 10;
    private static final int MAX_TRIES = 3;

    private CliAuth() {}

    /** Ortamdan (ya da sır dosyasından) kayıtlı özet; yoksa null. */
    public static String storedHash(Map<String, String> env) {
        String direct = env.get(ENV);
        if (direct != null && !direct.isBlank()) return direct.trim();
        String file = env.get(ENV + "_FILE");
        if (file == null || file.isBlank()) return null;
        try {
            Path p = Path.of(file.trim());
            if (Files.size(p) > 4096) return null;
            return Files.readString(p, StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            return null;
        }
    }

    public static String hash(char[] password) {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        byte[] dk = derive(password, salt, ITERATIONS);
        return PREFIX + ":" + ITERATIONS + ":" + Base64.getEncoder().encodeToString(salt) + ":" + Base64.getEncoder().encodeToString(dk);
    }

    public static boolean verify(char[] password, String stored) {
        if (password == null || stored == null) return false;
        String[] f = stored.trim().split(":");
        if (f.length != 4 || !PREFIX.equals(f[0])) return false;
        try {
            int iterations = Integer.parseInt(f[1]);
            if (iterations < 100_000 || iterations > 10_000_000) return false;
            byte[] salt = Base64.getDecoder().decode(f[2]);
            byte[] expected = Base64.getDecoder().decode(f[3]);
            byte[] actual = derive(password, salt, iterations);
            return MessageDigest.isEqual(expected, actual);
        } catch (RuntimeException e) {
            return false;
        }
    }

    static byte[] derive(char[] password, byte[] salt, int iterations) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, 256);
            try {
                return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            } finally {
                spec.clearPassword();
            }
        } catch (Exception e) {
            throw new IllegalStateException("PBKDF2 kullanılamıyor", e);
        }
    }

    /**
     * Komutlardan önce şifre sorar ve doğrular. Doğruysa true; özet ayarlı değilse ya da şifre yanlışsa iletiyi yazar
     * ve false.
     */
    public static boolean authenticate(Map<String, String> env, PrintStream out) {
        String stored = storedHash(env);
        if (stored == null || stored.isBlank()) {
            out.println("Yönetim şifresi ayarlı değil: önce 'cli sifre-ozeti' ile özet üretip CLI_PASSWORD_HASH_FILE (Docker sırrı) olarak verin.");
            return false;
        }
        Console console = System.console();
        int tries = console == null ? 1 : MAX_TRIES;
        for (int i = 0; i < tries; i++) {
            char[] pw = console != null ? console.readPassword("Yönetim şifresi: ") : readLine();
            try {
                if (verify(pw, stored)) return true;
            } finally {
                if (pw != null) Arrays.fill(pw, '\0');
            }
            out.println("Şifre yanlış.");
            sleep();
        }
        return false;
    }

    /** Yeni özet üretir: şifre iki kez sorulur (en az 10 karakter). */
    public static int printNewHash(PrintStream out) {
        Console console = System.console();
        char[] a = console != null ? console.readPassword("Yeni yönetim şifresi: ") : readLine();
        char[] b = console != null ? console.readPassword("Yeniden: ") : a;
        try {
            if (a == null || a.length < MIN_LENGTH) {
                out.println("Şifre en az " + MIN_LENGTH + " karakter olmalı.");
                return 2;
            }
            if (!Arrays.equals(a, b)) {
                out.println("Şifreler aynı değil.");
                return 2;
            }
            out.println(hash(a));
            return 0;
        } finally {
            if (a != null) Arrays.fill(a, '\0');
            if (b != null && b != a) Arrays.fill(b, '\0');
        }
    }

    private static char[] readLine() {
        try {
            String line = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)).readLine();
            return line == null ? null : line.toCharArray();
        } catch (IOException e) {
            return null;
        }
    }

    private static void sleep() {
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
