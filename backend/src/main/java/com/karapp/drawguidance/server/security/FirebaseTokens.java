package com.karapp.drawguidance.server.security;

import com.karapp.drawguidance.server.config.DrawGuidanceProperties;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Firebase ID jetonu kuralları (imza dışında; imzayı {@code JwtDecoder} Google securetoken anahtarlarıyla doğrular):
 *
 * <ul>
 *   <li>{@code iss} = {@code https://securetoken.google.com/<projectId>}</li>
 *   <li>{@code aud} = {@code <projectId>}</li>
 *   <li>{@code exp} var ve geçmemiş, {@code iat} var ve gelecekte değil ({@code auth_time} varsa o da), 60 sn pay</li>
 *   <li>{@code sub} (Firebase kullanıcı kimliği) boş değil, en çok 128 karakter</li>
 * </ul>
 */
public final class FirebaseTokens {
    public static final Duration CLOCK_SKEW = Duration.ofSeconds(60);

    private FirebaseTokens() {}

    public static OAuth2TokenValidator<Jwt> validator(DrawGuidanceProperties.Firebase firebase) {
        return validator(firebase, Clock.systemUTC());
    }

    public static OAuth2TokenValidator<Jwt> validator(DrawGuidanceProperties.Firebase firebase, Clock clock) {
        JwtTimestampValidator timestamps = new JwtTimestampValidator(CLOCK_SKEW);
        timestamps.setClock(clock);
        String projectId = firebase.projectId();
        return new DelegatingOAuth2TokenValidator<>(
                timestamps,
                new JwtIssuerValidator(firebase.issuer()),
                new JwtClaimValidator<List<String>>(JwtClaimNames.AUD, aud -> aud != null && aud.contains(projectId)),
                firebaseClaims(clock));
    }

    private static OAuth2TokenValidator<Jwt> firebaseClaims(Clock clock) {
        return jwt -> {
            Instant latest = Instant.now(clock).plus(CLOCK_SKEW);
            List<OAuth2Error> errors = new ArrayList<>();
            if (jwt.getExpiresAt() == null) errors.add(invalid("exp yok"));
            if (jwt.getIssuedAt() == null || jwt.getIssuedAt().isAfter(latest)) errors.add(invalid("iat yok ya da gelecekte"));
            try {
                Instant authTime = jwt.getClaimAsInstant("auth_time");
                if (authTime != null && authTime.isAfter(latest)) errors.add(invalid("auth_time gelecekte"));
            } catch (RuntimeException e) {
                errors.add(invalid("auth_time geçersiz"));
            }
            String sub = jwt.getSubject();
            if (sub == null || sub.isBlank() || sub.length() > 128) errors.add(invalid("sub boş ya da geçersiz"));
            return errors.isEmpty() ? OAuth2TokenValidatorResult.success() : OAuth2TokenValidatorResult.failure(errors);
        };
    }

    private static OAuth2Error invalid(String description) {
        return new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, description, null);
    }
}
