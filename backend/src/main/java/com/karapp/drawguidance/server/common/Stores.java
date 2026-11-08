package com.karapp.drawguidance.server.common;

/**
 * Koleksiyon adları (v33: kullanıcılar ve katalog; v37: hata raporları; v39: abonelikler ve Çizim Görevi ilerlemesi;
 * v40: web sitesinden gelen hesap silme talepleri ve destek mesajları).
 */
public final class Stores {
    private Stores() {}

    /** Kullanıcı profilleri ({@code _id} = istemcinin ürettiği UUID, {@code firebaseUid} benzersiz). */
    public static final String USERS = "users";

    /** Şekil şablonları ({@code _id} = Template.key). */
    public static final String CATALOG_TEMPLATES = "catalog_templates";
    /** Hazır vektör şekiller ({@code _id} = Vector.id). */
    public static final String CATALOG_VECTORS = "catalog_vectors";
    /** Modların temel sinir ağı ağırlıkları ({@code _id} = ModeNet.mode). */
    public static final String CATALOG_MODENETS = "catalog_modenets";
    /** Katalog sürümü, sayılar ve eşitleme kilidi (tek belge, {@code _id} = "catalog"). */
    public static final String CATALOG_META = "catalog_meta";

    /** v37 uygulamanın gönderdiği hata/çökme raporları ({@code _id} = sunucunun ürettiği UUID, {@code receivedAt} dizinli). */
    public static final String ERROR_REPORTS = "error_reports";
    /** v37 aynı hatanın raporları ({@code _id} = parmak izi; sayı, ilk/son görülme). */
    public static final String ERROR_REPORT_GROUPS = "error_report_groups";

    /** v39 abonelik planları ({@code _id} = plan kimliği: {@code free}, {@code premium}; açılışta tohumlanır). */
    public static final String PLANS = "plans";
    /** v39 planların erişim hakları ({@code _id} = {@code <plan>:<özellik>}; {@code planId} + {@code feature} benzersiz). */
    public static final String PLAN_FEATURES = "plan_features";
    /** v39 kullanıcı başına güncel abonelik ({@code _id} = Firebase uid; {@code purchaseToken} benzersiz). */
    public static final String SUBSCRIPTIONS = "subscriptions";
    /** v39 abonelik hareketleri (yalnız eklenir: satın alma, yenileme, iptal …; {@code orderId} + {@code type} benzersiz). */
    public static final String SUBSCRIPTION_TRANSACTIONS = "subscription_transactions";
    /** v39 Google Play gerçek zamanlı bildirimleri ({@code _id} = Pub/Sub messageId; tekrar işlemeyi önler). */
    public static final String BILLING_WEBHOOK_EVENTS = "billing_webhook_events";

    /** v39 çocuk modu "Çizim Görevi" ilerlemesi ({@code _id} = Firebase uid; {@code firebaseUid} benzersiz). */
    public static final String CHALLENGE_PROGRESS = "challenge_progress";

    /**
     * v40 web sitesindeki girişsiz hesap silme talepleri ({@code _id} = UUID; {@code email} küçük harfli, {@code status}
     * PENDING/COMPLETED, {@code createdAt}; ham IP yok, yalnız {@code ipHash}). 365 gün sonra TTL ile silinir.
     */
    public static final String DELETION_REQUESTS = "deletion_requests";
    /** v40 web sitesindeki destek/iletişim formu mesajları ({@code _id} = UUID). 730 gün sonra TTL ile silinir. */
    public static final String SUPPORT_MESSAGES = "support_messages";

    /** v40 hesabı silinen kullanıcıların jeton iptal zamanı ({@code _id} = Firebase uid; {@code expireAt} TTL, 2 saat). */
    public static final String TOKEN_REVOCATIONS = "token_revocations";

    /** v40 uygulamadan mod başına geri bildirim ({@code _id} = UUID; {@code uid}; cihaz bilgisi ve e-posta yalnız izinle). */
    public static final String FEEDBACK = "feedback";
    /** v40 içerik eşitleme izni: kullanıcının kalıcı sıra numarası ({@code _id} = Firebase uid, {@code seq}). */
    public static final String SYNC_GROUPS = "sync_groups";
    /** v40 atomik sayaçlar ({@code _id} = sayaç adı, ör. {@code sync_seq}; {@code value}). */
    public static final String COUNTERS = "counters";
}
