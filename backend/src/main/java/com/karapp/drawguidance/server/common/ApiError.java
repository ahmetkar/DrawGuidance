package com.karapp.drawguidance.server.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Bütün hataların ortak gövdesi (sözleşme): {@code {"error": "<kod>", "message": "<Türkçe açıklama>"}}. MVC
 * hataları, güvenlik süzgecinin 401/403 yanıtları ve {@code /error} sayfası aynı biçimi kullanır.
 */
public record ApiError(String error, String message) {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** HTTP durumundan öntanımlı kod (özel kodu olmayan hatalar için). */
    public static String codeFor(int status) {
        return switch (status) {
            case 400 -> "bad_request";
            case 401 -> "unauthorized";
            case 403 -> "forbidden";
            case 404 -> "not_found";
            case 405 -> "method_not_allowed";
            case 406 -> "not_acceptable";
            case 409 -> "conflict";
            case 413 -> "too_large";
            case 415 -> "unsupported_media_type";
            case 429 -> "rate_limited";
            case 500 -> "internal";
            case 503 -> "service_unavailable";
            default -> "http_" + status;
        };
    }

    /** HTTP durumundan öntanımlı Türkçe ileti. */
    public static String messageFor(int status) {
        return switch (status) {
            case 400 -> "Geçersiz istek";
            case 401 -> "Kimlik doğrulaması gerekli";
            case 403 -> "Bu işlem için yetkiniz yok";
            case 404 -> "Böyle bir uç yok";
            case 405 -> "Bu uç bu yöntemi desteklemiyor";
            case 406 -> "İstenen yanıt biçimi desteklenmiyor";
            case 409 -> "Kayıt çakışması";
            case 413 -> "İstek gövdesi çok büyük";
            case 415 -> "Gövde biçimi desteklenmiyor (application/json bekleniyor)";
            case 429 -> "Çok fazla istek; biraz sonra yeniden deneyin";
            case 500 -> "Sunucu hatası";
            case 503 -> "Hizmet şu an kullanılamıyor";
            default -> "İstek işlenemedi (HTTP " + status + ")";
        };
    }

    /** Süzgeç katmanından (Spring Security) doğrudan yazmak için. */
    public static void write(HttpServletResponse response, int status, String code, String message) throws IOException {
        if (response.isCommitted()) return;
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getOutputStream().write(JSON.writeValueAsBytes(new ApiError(code, message)));
    }
}
