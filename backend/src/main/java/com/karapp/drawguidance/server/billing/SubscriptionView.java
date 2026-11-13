package com.karapp.drawguidance.server.billing;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * v39 {@code GET /api/v1/subscriptions/me} ve {@code POST /api/v1/subscriptions/verify} yanıtı.
 *
 * @param planId               etkin plan: hak varsa aboneliğin planı, yoksa {@code free}
 * @param status               saklanan aboneliğin durumu (bitiş zamanı geçmişse {@code EXPIRED}); abonelik yoksa {@code null}
 * @param active               Premium hakkı şu an var mı
 * @param productId            Play ürün kimliği (abonelik yoksa {@code null})
 * @param basePlanId           Play temel plan kimliği
 * @param startTime            aboneliğin başlangıcı
 * @param expiryTime           ödenen dönemin (ya da ek sürenin) sonu
 * @param autoRenewing         dönem sonunda yenilenecek mi
 * @param acknowledged         satın alma Play'de onaylandı mı
 * @param orderId              son başarılı siparişin numarası (ör. {@code GPA.1234-5678-9012-34567..1})
 * @param features             etkin planın özellik anahtarları
 * @param limits               sınırlı özellikler (anahtar → sayı); şimdilik boş
 * @param obfuscatedAccountId  uygulamanın {@code BillingFlowParams.setObfuscatedAccountId} değeri: Firebase uid'sinin
 *                             UTF-8 baytlarının SHA-256 özeti, küçük harfli 64 onaltılık karakter
 * @param updatedAt            kaydın son güncellenmesi
 * @param cancelTime           v40 kullanıcının iptal ettiği zaman (Play {@code canceledStateContext}; yoksa {@code null})
 * @param cancelReason         v40 iptal nedeni: USER, SYSTEM, DEVELOPER, REPLACEMENT (iptal yoksa {@code null})
 * @param autoResumeTime       v40 duraklatılmış aboneliğin kendiliğinden devam edeceği zaman (Play
 *                             {@code pausedStateContext}; yoksa {@code null})
 * @param store                v50 aboneliğin mağazası: {@code GOOGLE_PLAY} ya da {@code APP_STORE} (abonelik yoksa
 *                             {@code null}). Uygulama "aboneliği yönet" bağlantısını buna göre açar
 * @param appAccountToken      v50 iOS'un {@code Product.PurchaseOption.appAccountToken} değeri (Firebase uid'sinden
 *                             türetilen UUID; bkz. {@code AppleStates.appAccountToken})
 */
public record SubscriptionView(String planId, String status, boolean active, String productId, String basePlanId,
                               Instant startTime, Instant expiryTime, Boolean autoRenewing, Boolean acknowledged, String orderId,
                               List<String> features, Map<String, Long> limits, String obfuscatedAccountId, Instant updatedAt,
                               Instant cancelTime, String cancelReason, Instant autoResumeTime, String store, String appAccountToken) {}
