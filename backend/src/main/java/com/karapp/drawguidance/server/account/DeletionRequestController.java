package com.karapp.drawguidance.server.account;

import com.karapp.drawguidance.server.web.PublicForms;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * v40 girişsiz hesap silme talebi ({@code POST /api/v1/account/deletion-requests}, herkese açık): Google Play'in
 * istediği "hesap ve veri silme" sayfasındaki form. Oturum açamayan kullanıcı e-postasını bırakır; talep elle işlenir.
 * (Oturum açabilen kullanıcı sayfada Google ile girip doğrudan {@code DELETE /api/v1/users/me} çağırır.)
 *
 * <p>Gövde: {@code {"email": "...", "reason": "..." (isteğe bağlı, en çok 1000), "locale": "tr" (isteğe bağlı)}}.
 * Sıra: IP başına sınır (429 {@code rate_limited}) → gövde (413/400) → alanlar (400 {@code invalid_email},
 * {@code too_long}) → kayıt → 202 {@code {"status": "received"}}. Aynı e-postanın son 24 saatte bekleyen talebi varsa
 * yenisi yazılmaz ama yanıt aynıdır (e-postanın kayıtlı olup olmadığı yanıttan anlaşılmaz).
 */
@RestController
@RequestMapping(DeletionRequestController.PATH)
public class DeletionRequestController {
    public static final String PATH = "/api/v1/account/deletion-requests";

    private final DeletionRequestService requests;
    private final PublicForms forms;

    public DeletionRequestController(DeletionRequestService requests, PublicForms forms) {
        this.requests = requests;
        this.forms = forms;
    }

    /** Gövde (bilinmeyen alanlar yok sayılır). */
    public record Body(String email, String reason, String locale) {}

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> post(HttpServletRequest request) {
        String ip = forms.clientIp(request);
        ResponseEntity<Object> limited = forms.limit("deletion", ip);
        if (limited != null) return limited;
        Body body = forms.readJson(request, Body.class);
        String email = PublicForms.email(body.email());
        String reason = PublicForms.text("reason", body.reason(), DeletionRequestService.MAX_REASON, false);
        requests.submit(email, reason, PublicForms.locale(body.locale()), forms.ipHash(ip), PublicForms.userAgent(request));
        return PublicForms.received();
    }
}
