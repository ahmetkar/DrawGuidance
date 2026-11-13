package com.karapp.drawguidance.server.billing;

import com.karapp.drawguidance.server.common.ApiException;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * v50 App Store uçları.
 *
 * <ul>
 *   <li>{@code POST /api/v1/subscriptions/verify-apple} (Firebase jetonu zorunlu): iOS satın almadan, geri yüklemeden
 *       ve açılışta değişmiş bir işlem görünce çağırır. Yanıt {@code /subscriptions/me} ile aynı.</li>
 *   <li>{@code POST /api/v1/billing/apple-notifications} (herkese açık): App Store Server Notifications V2. Gövde
 *       {@code {"signedPayload": "..."}}; kimlik Apple'ın imzasıyla doğrulanır. 2xx yanıt bildirimi onaylar, başka her
 *       yanıtta Apple yeniden gönderir.</li>
 * </ul>
 */
@RestController
public class AppleController {
    public static final String VERIFY_PATH = SubscriptionController.PATH + "/verify-apple";
    public static final String NOTIFICATIONS_PATH = "/api/v1/billing/apple-notifications";

    private final AppleSubscriptionService apple;

    public AppleController(AppleSubscriptionService apple) { this.apple = apple; }

    @PostMapping(VERIFY_PATH)
    public SubscriptionView verify(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AppleVerifyRequest body) {
        return apple.verify(jwt.getSubject(), body);
    }

    @PostMapping(value = NOTIFICATIONS_PATH, consumes = MediaType.APPLICATION_JSON_VALUE)
    public AppleSubscriptionService.Outcome notification(@RequestBody Map<String, Object> body) {
        Object payload = body == null ? null : body.get("signedPayload");
        if (!(payload instanceof String s) || s.isBlank() || s.length() > 100_000) {
            throw ApiException.badRequest("bad_request", "signedPayload yok");
        }
        return apple.handleNotification(s);
    }
}
