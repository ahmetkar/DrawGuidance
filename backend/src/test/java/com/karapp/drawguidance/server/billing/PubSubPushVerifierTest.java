package com.karapp.drawguidance.server.billing;

import com.karapp.drawguidance.server.common.ApiException;
import com.karapp.drawguidance.server.config.DrawGuidanceProperties;
import com.karapp.drawguidance.server.support.TestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** v39 Pub/Sub itme isteğinin OIDC jetonu denetimi (yerel RSA anahtarıyla imzalı Google benzeri jetonlar). */
class PubSubPushVerifierTest {
    static final String AUD = "https://dg.example.com/api/v1/billing/rtdn";

    static PubSubPushVerifier verifier(String audience, String email) throws Exception {
        DrawGuidanceProperties.PubSub cfg = new DrawGuidanceProperties.PubSub(audience, email, null, false);
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(TestTokens.KEY.toRSAPublicKey()).build();
        decoder.setJwtValidator(PubSubPushVerifier.validator(cfg));
        return new PubSubPushVerifier(cfg, decoder);
    }

    static String token(String aud, String iss) {
        Instant now = Instant.now();
        // TestTokens jetonu email = ahmet@example.com, email_verified = true taşır
        return "Bearer " + TestTokens.mint(TestTokens.KEY, "pubsub-sa", aud, iss, now.minusSeconds(10), now.plus(Duration.ofHours(1)));
    }

    static int status(PubSubPushVerifier v, String header) {
        return assertThrows(ApiException.class, () -> v.check(header)).status().value();
    }

    @Test
    void disabledWithoutAudience() throws Exception {
        PubSubPushVerifier v = verifier(null, null);
        assertFalse(v.enabled());
        assertDoesNotThrow(() -> v.check(null));
        assertDoesNotThrow(() -> v.check("Bearer bozuk"));
    }

    @Test
    void checksSignatureIssuerAudienceAndEmail() throws Exception {
        PubSubPushVerifier v = verifier(AUD, "ahmet@example.com");
        assertDoesNotThrow(() -> v.check(token(AUD, "https://accounts.google.com")));
        assertDoesNotThrow(() -> v.check(token(AUD, "accounts.google.com")));
        assertEquals(401, status(v, null));
        assertEquals(401, status(v, "Basic abc"));
        assertEquals(401, status(v, token("https://baska.example.com", "https://accounts.google.com")));
        assertEquals(401, status(v, token(AUD, "https://securetoken.google.com/x")));
        Instant now = Instant.now();
        assertEquals(401, status(v, "Bearer " + TestTokens.mint(TestTokens.OTHER, "s", AUD, "https://accounts.google.com", now, now.plusSeconds(600))));
        assertEquals(401, status(v, "Bearer " + TestTokens.mint(TestTokens.KEY, "s", AUD, "https://accounts.google.com",
                now.minus(Duration.ofHours(3)), now.minus(Duration.ofHours(2)))));
        assertEquals(401, status(verifier(AUD, "pubsub@dg.iam.gserviceaccount.com"), token(AUD, "https://accounts.google.com")));
    }

    /** v40 üretimde zorunlu: hedef kitle ya da hizmet hesabı eksikse her istek 503; ikisi de varsa olağan denetim. */
    @Test
    void requiredModeRejectsEverythingUntilAudienceAndEmailAreSet() throws Exception {
        for (String[] cfg : new String[][]{{null, null}, {AUD, null}, {null, "ahmet@example.com"}}) {
            DrawGuidanceProperties.PubSub p = new DrawGuidanceProperties.PubSub(cfg[0], cfg[1], null, true);
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(TestTokens.KEY.toRSAPublicKey()).build();
            decoder.setJwtValidator(PubSubPushVerifier.validator(p));
            PubSubPushVerifier v = new PubSubPushVerifier(p, decoder);
            assertEquals(503, status(v, null));
            assertEquals(503, status(v, token(AUD, "https://accounts.google.com")));
        }
        DrawGuidanceProperties.PubSub ok = new DrawGuidanceProperties.PubSub(AUD, "ahmet@example.com", null, true);
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(TestTokens.KEY.toRSAPublicKey()).build();
        decoder.setJwtValidator(PubSubPushVerifier.validator(ok));
        PubSubPushVerifier v = new PubSubPushVerifier(ok, decoder);
        assertDoesNotThrow(() -> v.check(token(AUD, "https://accounts.google.com")));
        assertEquals(401, status(v, null));
    }
}
