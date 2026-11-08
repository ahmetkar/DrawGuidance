package com.karapp.drawguidance.server.billing.play;

/**
 * v39 Google Play Developer API hatası. {@code status}: Play'in HTTP durumu (ağ hatasında 0).
 * 400/404/410 jetonun geçersiz ya da bilinmediğini gösterir (yeniden denemek işe yaramaz); diğerleri geçicidir.
 */
public class PlayApiException extends Exception {
    private final int status;

    public PlayApiException(int status, String message) {
        super(message);
        this.status = status;
    }

    public PlayApiException(String message, Throwable cause) {
        super(message, cause);
        this.status = 0;
    }

    public int status() { return status; }

    public boolean invalidToken() { return status == 400 || status == 404 || status == 410; }
}
