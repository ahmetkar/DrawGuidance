package com.karapp.drawguidance.server.billing;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** v44 geçici istisna hesabı: yalnız doğrulanmış e-postayla; plan değişmez, haklar Premium'un hepsi. */
class ExemptAccountsTest {
    private static Jwt jwt(String email, boolean verified) {
        return Jwt.withTokenValue("t").header("alg", "RS256").subject("uid-1").issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60))
                .claim("email", email).claim("email_verified", verified).build();
    }

    @Test
    void onlyVerifiedListedEmailIsExempt() {
        ExemptAccounts e = new ExemptAccounts("ahmetkar8040@gmail.com");
        assertTrue(e.isExempt(jwt("AhmetKar8040@gmail.com", true)));
        assertFalse(e.isExempt(jwt("ahmetkar8040@gmail.com", false)), "doğrulanmamış e-posta");
        assertFalse(e.isExempt(jwt("baska@gmail.com", true)));
        assertFalse(new ExemptAccounts("").isExempt(jwt("ahmetkar8040@gmail.com", true)));
    }

    @Test
    void grantKeepsPlanAndGivesAllFeatures() {
        SubscriptionView free = new SubscriptionView("free", null, false, null, null, null, null, null, null, null, List.of(), Map.of(), "h",
                null, null, null, null);
        SubscriptionView g = ExemptAccounts.grant(free);
        assertEquals("free", g.planId());
        assertFalse(g.active());
        assertEquals(Plans.FEATURE_KEYS, g.features());
        assertTrue(g.features().contains("model3d.export"));
    }
}
