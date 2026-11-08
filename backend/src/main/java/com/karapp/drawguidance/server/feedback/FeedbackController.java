package com.karapp.drawguidance.server.feedback;

import com.karapp.drawguidance.server.common.ApiError;
import com.karapp.drawguidance.server.web.PublicForms;
import com.karapp.drawguidance.server.web.TokenBucketLimiter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.util.Map;

/**
 * v40 {@code POST /api/v1/feedback} (Firebase jetonu zorunlu): mod başına geri bildirim. Gövde: {@code {"mode":
 * "ART", "feature": "art.layers" (isteğe bağlı), "rating": 1–5, "text": "..." (isteğe bağlı, en çok 5000), "consent":
 * true, "device": {"model", "androidVersion", "appVersion", "versionCode", "locale"}}}. Cihaz bilgisi ve jetondaki
 * e-posta yalnız {@code consent=true} ise saklanır (telefon modeli şifreli). Sıra: kullanıcı başına sınır (429) → gövde (413/400) → alanlar
 * (400 {@code invalid_mode}, {@code invalid_rating}, {@code invalid_feature}, {@code too_long}) → kayıt → 202
 * {@code {"id": "..."}}.
 */
@RestController
@RequestMapping(FeedbackController.PATH)
public class FeedbackController {
    public static final String PATH = "/api/v1/feedback";

    private final FeedbackService feedback;
    private final PublicForms forms;
    private final TokenBucketLimiter limiter;

    public FeedbackController(FeedbackService feedback, PublicForms forms, FeedbackProperties props) {
        this.feedback = feedback;
        this.forms = forms;
        this.limiter = new TokenBucketLimiter(props.limit(), props.window(), Clock.systemUTC());
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> post(@AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        TokenBucketLimiter.Decision d = limiter.acquire("fb:" + jwt.getSubject());
        if (!d.allowed()) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).header(HttpHeaders.RETRY_AFTER, String.valueOf(d.retryAfterSeconds()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ApiError("rate_limited", "Çok fazla geri bildirim gönderildi; biraz sonra yeniden deneyin"));
        }
        FeedbackService.Body body = forms.readJson(request, FeedbackService.Body.class);
        String id = feedback.submit(jwt.getSubject(), jwt.getClaimAsString("email"), body);
        return ResponseEntity.status(HttpStatus.ACCEPTED).contentType(MediaType.APPLICATION_JSON).body(Map.of("id", id));
    }
}
