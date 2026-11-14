package com.karapp.drawguidance.server.billing.apple;

/**
 * v50 App Store hatası. {@link #invalid()}: veri Apple tarafından imzalanmamış, başka uygulamaya ait ya da işlem
 * bulunamadı (kalıcı; yeniden denemek anlamsız). {@link #notConfigured()}: sunucuda anahtar ya da Apple ID eksik.
 * Öteki hatalar geçicidir (Apple'a ulaşılamadı).
 */
public class AppStoreApiException extends Exception {
    public enum Kind { INVALID, NOT_CONFIGURED, UNAVAILABLE }

    private final Kind kind;

    public AppStoreApiException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public AppStoreApiException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind kind() { return kind; }

    public boolean invalid() { return kind == Kind.INVALID; }

    public boolean notConfigured() { return kind == Kind.NOT_CONFIGURED; }
}
