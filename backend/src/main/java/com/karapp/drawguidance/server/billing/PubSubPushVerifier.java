package com.karapp.drawguidance.server.billing;

import com.karapp.drawguidance.server.common.ApiException;
import com.karapp.drawguidance.server.config.DrawGuidanceProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;

import java.time.Duration;
import java.util.List;
import java.util.Set;

/**
 * v39 Pub/Sub itme isteğinin OIDC jetonu ({@code Authorization: Bearer <Google kimlik jetonu>}). Yalnız
 * {@code drawguidance.billing.pubsub.audience} ayarlıysa denetlenir: imza (Google açık anahtarları), {@code iss}
 * ({@code accounts.google.com}), {@code aud} = ayardaki hedef kitle, süre ve ayarlıysa {@code email} (doğrulanmış)
 * = itme aboneliğinin hizmet hesabı.
 *
 * <p>v40 {@code required} (üretim profilinde açık): hedef kitle ve hizmet hesabı e-postası ikisi de ayarlı olmalı;
 * değilse her istek 503 {@code rtdn_not_configured} ile reddedilir (Pub/Sub iletiyi 7 güne kadar yeniden dener, ayar
 * düzeltilince işlenir). Yalnız hedef kitle denetimi yetmez: herkes kendi Google hizmet hesabıyla istediği hedef kitleye
 * imzalı kimlik jetonu üretebilir; jetonun bizim itme aboneliğimizden geldiğini {@code email} kanıtlar.
 */
public class PubSubPushVerifier {
    private static final Logger log = LoggerFactory.getLogger(PubSubPushVerifier.class);
    static final Set<String> ISSUERS = Set.of("accounts.google.com", "https://accounts.google.com");

    private final String audience;
    private final JwtDecoder decoder;
    private final boolean required;
    private final boolean complete;

    /** {@code decoder}: imzayı doğrulayan çözücü (kurallar burada eklenir); {@code audience} boşsa kullanılmaz. */
    public PubSubPushVerifier(DrawGuidanceProperties.PubSub pubsub, JwtDecoder decoder) {
        this.audience = pubsub.audience();
        this.decoder = decoder;
        this.required = Boolean.TRUE.equals(pubsub.required());
        this.complete = pubsub.audience() != null && pubsub.serviceAccountEmail() != null;
        if (required && !complete) {
            log.error("Pub/Sub OIDC doğrulaması zorunlu ama PUBSUB_PUSH_AUDIENCE ya da PUBSUB_PUSH_SERVICE_ACCOUNT boş: "
                    + "bütün RTDN istekleri 503 ile reddedilecek");
        } else if (!required && audience == null) {
            log.warn("Pub/Sub OIDC doğrulaması kapalı (PUBSUB_PUSH_AUDIENCE boş): RTDN ucu jetonsuz istekleri kabul ediyor");
        }
    }

    public boolean enabled() { return audience != null; }

    /** v40 doğrulama zorunlu mu (üretim profili). */
    public boolean required() { return required; }

    public static OAuth2TokenValidator<Jwt> validator(DrawGuidanceProperties.PubSub pubsub) {
        String audience = pubsub.audience(), email = pubsub.serviceAccountEmail();
        return new DelegatingOAuth2TokenValidator<>(new JwtTimestampValidator(Duration.ofSeconds(60)), jwt -> {
            String iss = jwt.getClaimAsString("iss");
            List<String> aud = jwt.getAudience();
            if (iss == null || !ISSUERS.contains(iss)) return fail("iss Google değil");
            if (aud == null || !aud.contains(audience)) return fail("aud eşleşmiyor");
            if (email != null && (!email.equals(jwt.getClaimAsString("email")) || !Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified")))) {
                return fail("email eşleşmiyor");
            }
            return OAuth2TokenValidatorResult.success();
        });
    }

    private static OAuth2TokenValidatorResult fail(String why) {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", why, null));
    }

    /** Ayar yoksa her isteği kabul eder; varsa jeton yok/geçersiz → 401, anahtarlar alınamadı → 503. */
    public void check(String authorization) {
        if (required && !complete) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "rtdn_not_configured", "Bildirim ucu sunucuda yapılandırılmamış");
        }
        if (!enabled()) return;
        if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "unauthorized", "Pub/Sub OIDC jetonu gerekli");
        }
        try {
            decoder.decode(authorization.substring(7).trim());
        } catch (BadJwtException e) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "invalid_token", "Pub/Sub OIDC jetonu geçersiz");
        } catch (JwtException e) {
            log.warn("Pub/Sub jetonu doğrulanamadı (Google anahtarları alınamadı mı?): {}", e.getMessage());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "auth_unavailable", "Kimlik doğrulama şu an yapılamıyor");
        }
    }
}
