package com.karapp.drawguidance.server.web;

import com.karapp.drawguidance.server.billing.RtdnController;
import com.karapp.drawguidance.server.billing.SubscriptionController;
import com.karapp.drawguidance.server.common.ApiError;
import com.karapp.drawguidance.server.config.DrawGuidanceProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Clock;
import java.util.Set;

/**
 * v40 API istek sınırları (kimlik denetiminden sonra, denetleyiciden önce; bellekte jeton kovası, sunucu kopyası
 * başına). Ters vekildeki (Traefik) genel IP sınırının arkasında ikinci katmandır.
 *
 * <ul>
 *   <li><b>IP başına</b> bütün {@code /api/v1/**} istekleri (öntanımlı dakikada 600). Pub/Sub itme ucu
 *       ({@code /api/v1/billing/rtdn}) sayılmaz: Google'ın paylaşılan adreslerinden gelir, OIDC jetonuyla korunur.</li>
 *   <li><b>Kullanıcı (Firebase uid) başına</b> yazma istekleri ({@code POST/PUT/PATCH/DELETE}; öntanımlı saatte 120)
 *       ve ayrıca abonelik doğrulaması ({@code POST /api/v1/subscriptions/verify}, her biri Google Play API çağrısı;
 *       öntanımlı saatte 30).</li>
 *   <li>Aşılırsa 429 {@code rate_limited} ve {@code Retry-After}. İstemci adresi {@link PublicForms#clientIp} ile
 *       (güvenilen vekil arkasında gerçek istemci).</li>
 * </ul>
 */
public class ApiRateLimitInterceptor implements HandlerInterceptor {
    static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");
    static final String VERIFY = SubscriptionController.PATH + "/verify";

    private final PublicForms forms;
    private final TokenBucketLimiter perIp;
    private final TokenBucketLimiter writes;
    private final TokenBucketLimiter verifies;

    public ApiRateLimitInterceptor(DrawGuidanceProperties props, PublicForms forms) {
        this(props, forms, Clock.systemUTC());
    }

    ApiRateLimitInterceptor(DrawGuidanceProperties props, PublicForms forms, Clock clock) {
        DrawGuidanceProperties.Limits l = props.limits();
        this.forms = forms;
        this.perIp = new TokenBucketLimiter(l.ipLimit(), l.ipWindow(), clock);
        this.writes = new TokenBucketLimiter(l.writeLimit(), l.writeWindow(), clock);
        this.verifies = new TokenBucketLimiter(l.verifyLimit(), l.verifyWindow(), clock);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (path.equals(RtdnController.PATH) || "OPTIONS".equals(request.getMethod())) return true;
        if (!allowed(perIp.acquire("ip:" + forms.clientIp(request)), response)) return false;
        String uid = uid();
        if (uid == null || !WRITE_METHODS.contains(request.getMethod())) return true;
        if (!allowed(writes.acquire("w:" + uid), response)) return false;
        return !path.equals(VERIFY) || allowed(verifies.acquire("v:" + uid), response);
    }

    private static String uid() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a instanceof JwtAuthenticationToken jwt ? jwt.getToken().getSubject() : null;
    }

    private static boolean allowed(TokenBucketLimiter.Decision d, HttpServletResponse response) throws java.io.IOException {
        if (d.allowed()) return true;
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(d.retryAfterSeconds()));
        ApiError.write(response, 429, "rate_limited", "Çok fazla istek gönderildi; biraz sonra yeniden deneyin");
        return false;
    }
}
