package com.karapp.drawguidance.server.billing;

import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * v39 kullanıcının aboneliği (Firebase ID jetonu zorunlu). {@code /me}: oturum açılınca çağrılır (abonelik yoksa
 * Ücretsiz). {@code /verify}: satın almadan sonra ve uygulama değişmiş bir satın alma gördüğünde.
 */
@RestController
@RequestMapping(SubscriptionController.PATH)
public class SubscriptionController {
    public static final String PATH = "/api/v1/subscriptions";

    private final SubscriptionService subscriptions;
    private final ExemptAccounts exempt;

    public SubscriptionController(SubscriptionService subscriptions, ExemptAccounts exempt) {
        this.subscriptions = subscriptions;
        this.exempt = exempt;
    }

    private SubscriptionView forUser(Jwt jwt, SubscriptionView v) { return exempt.isExempt(jwt) ? ExemptAccounts.grant(v) : v; }

    @GetMapping("/me")
    public SubscriptionView me(@AuthenticationPrincipal Jwt jwt) {
        return forUser(jwt, subscriptions.me(jwt.getSubject()));
    }

    @PostMapping("/verify")
    public SubscriptionView verify(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody VerifyRequest body) {
        return forUser(jwt, subscriptions.verify(jwt.getSubject(), body));
    }

    /** Hareket geçmişi (sipariş numaraları, yenilemeler, iptaller; yeniden eskiye). */
    public record History(List<Map<String, Object>> items) {}

    @GetMapping("/me/history")
    public History history(@AuthenticationPrincipal Jwt jwt, @RequestParam(value = "limit", defaultValue = "50") int limit) {
        return new History(subscriptions.history(jwt.getSubject(), limit));
    }
}
