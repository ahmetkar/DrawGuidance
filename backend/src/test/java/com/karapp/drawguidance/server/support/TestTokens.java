package com.karapp.drawguidance.server.support;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

/**
 * Testlerde Firebase ID jetonu benzeri jetonlar: yerel üretilmiş RSA anahtarıyla RS256 imzalı, Firebase'in
 * taşıdığı alanlarla ({@code iss}, {@code aud}, {@code sub}, {@code iat}, {@code exp}, {@code auth_time}, …).
 */
public final class TestTokens {
    public static final String PROJECT = "dg-test";
    public static final String ISSUER = "https://securetoken.google.com/" + PROJECT;

    /** Sunucunun (test çözücüsünün) güvendiği anahtar. */
    public static final RSAKey KEY = generate("test-key");
    /** Güvenilmeyen başka bir anahtar (imza hatası için). */
    public static final RSAKey OTHER = generate("other-key");

    private TestTokens() {}

    private static RSAKey generate(String kid) {
        try {
            return new RSAKeyGenerator(2048).keyID(kid).generate();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String valid(String uid) {
        Instant now = Instant.now();
        return mint(KEY, uid, PROJECT, ISSUER, now.minusSeconds(30), now.plus(Duration.ofHours(1)));
    }

    /** Alanlardan biri {@code null} ise jetona konmaz. */
    public static String mint(RSAKey key, String sub, String aud, String iss, Instant iat, Instant exp) {
        JWTClaimsSet.Builder c = new JWTClaimsSet.Builder();
        if (sub != null) c.subject(sub).claim("user_id", sub);
        if (aud != null) c.audience(aud);
        if (iss != null) c.issuer(iss);
        if (iat != null) c.issueTime(Date.from(iat)).claim("auth_time", iat.getEpochSecond());
        if (exp != null) c.expirationTime(Date.from(exp));
        c.claim("email", "ahmet@example.com").claim("email_verified", true)
                .claim("firebase", Map.of("sign_in_provider", "google.com"));
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).type(JOSEObjectType.JWT).build(), c.build());
        try {
            jwt.sign(new RSASSASigner(key));
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
        return jwt.serialize();
    }
}
