package com.karapp.drawguidance.server.support;

import com.karapp.drawguidance.server.web.PublicForms;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * v40 destek/iletişim formu ({@code POST /api/v1/support/messages}, herkese açık).
 *
 * <p>Gövde: {@code {"email": "...", "subject": "..." (1–150), "message": "..." (1–5000), "locale": "tr" (isteğe
 * bağlı)}}. Sıra: IP başına sınır (429 {@code rate_limited}) → gövde (413/400) → alanlar (400 {@code invalid_email},
 * {@code validation}, {@code too_long}) → kayıt → 202 {@code {"status": "received"}}.
 */
@RestController
@RequestMapping(SupportMessageController.PATH)
public class SupportMessageController {
    public static final String PATH = "/api/v1/support/messages";

    private final SupportMessageService messages;
    private final PublicForms forms;

    public SupportMessageController(SupportMessageService messages, PublicForms forms) {
        this.messages = messages;
        this.forms = forms;
    }

    /** Gövde (bilinmeyen alanlar yok sayılır). */
    public record Body(String email, String subject, String message, String locale) {}

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> post(HttpServletRequest request) {
        String ip = forms.clientIp(request);
        ResponseEntity<Object> limited = forms.limit("support", ip);
        if (limited != null) return limited;
        Body body = forms.readJson(request, Body.class);
        String email = PublicForms.email(body.email());
        String subject = PublicForms.text("subject", body.subject(), SupportMessageService.MAX_SUBJECT, true).replaceAll("\\s+", " ");
        String message = PublicForms.text("message", body.message(), SupportMessageService.MAX_MESSAGE, true);
        messages.submit(email, subject, message, PublicForms.locale(body.locale()), forms.ipHash(ip), PublicForms.userAgent(request));
        return PublicForms.received();
    }
}
