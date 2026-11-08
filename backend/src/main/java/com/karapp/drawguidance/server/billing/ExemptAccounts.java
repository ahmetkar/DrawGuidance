package com.karapp.drawguidance.server.billing;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * v44 geçici istisna hesapları: Premium'un bütün haklarını alır (plan ve abonelik kaydı değişmez; abonelik ekranı
 * gerçek durumu gösterir). Yalnız Firebase jetonundaki <b>doğrulanmış</b> e-postaya bakılır (kullanıcının yazdığı
 * profil e-postasına değil). Ayar: {@code drawguidance.billing.exempt-emails} (virgülle; boşsa kimse).
 */
@Component
public class ExemptAccounts {
    private final Set<String> emails;

    public ExemptAccounts(@Value("${drawguidance.billing.exempt-emails:ahmetkar8040@gmail.com}") String list) {
        this.emails = Arrays.stream(list.split(",")).map(s -> s.trim().toLowerCase(Locale.ROOT)).filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    public boolean isExempt(Jwt jwt) {
        if (jwt == null) return false;
        Object verified = jwt.getClaim("email_verified");
        String email = jwt.getClaimAsString("email");
        return email != null && Boolean.TRUE.equals(verified) && emails.contains(email.trim().toLowerCase(Locale.ROOT));
    }

    /** İstisna hesabın görünümü: haklar Premium'un hepsi, gerisi aynı. */
    public static SubscriptionView grant(SubscriptionView v) {
        return new SubscriptionView(v.planId(), v.status(), v.active(), v.productId(), v.basePlanId(), v.startTime(), v.expiryTime(),
                v.autoRenewing(), v.acknowledged(), v.orderId(), Plans.FEATURE_KEYS, v.limits(), v.obfuscatedAccountId(), v.updatedAt(),
                v.cancelTime(), v.cancelReason(), v.autoResumeTime());
    }
}
