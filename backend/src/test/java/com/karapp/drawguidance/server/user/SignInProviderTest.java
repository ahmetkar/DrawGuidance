package com.karapp.drawguidance.server.user;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Google ve Apple ile giriş: Firebase jetonundaki giriş sağlayıcısı kayda yazılır, diğerleri yok sayılır. */
class SignInProviderTest {

    private static Jwt jwt(Object firebase) {
        Jwt.Builder b = Jwt.withTokenValue("t").header("alg", "RS256").subject("uid-1")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60));
        if (firebase != null) b.claim("firebase", firebase);
        return b.build();
    }

    @Test
    void googleAndAppleAreRecorded() {
        assertEquals("google.com", UserService.signInProvider(jwt(Map.of("sign_in_provider", "google.com"))));
        assertEquals("apple.com", UserService.signInProvider(jwt(Map.of("sign_in_provider", "apple.com"))));
    }

    @Test
    void otherOrMissingProvidersAreIgnored() {
        assertNull(UserService.signInProvider(jwt(Map.of("sign_in_provider", "password"))));
        assertNull(UserService.signInProvider(jwt(Map.of("sign_in_provider", "anonymous"))));
        assertNull(UserService.signInProvider(jwt(Map.of())));
        assertNull(UserService.signInProvider(jwt("google.com")));
        assertNull(UserService.signInProvider(jwt(null)));
    }
}
