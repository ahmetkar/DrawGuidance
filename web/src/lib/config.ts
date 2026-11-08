// Sitenin ayarları. NEXT_PUBLIC_* değişkenleri derleme anında koda gömülür: Vercel'de değiştirince yeniden dağıtın.

/** Uygulamanın adları ve Android paket adı. */
export const APP_NAME_TR = "Çizim Rehberi";
export const APP_NAME_EN = "DrawGuidance";
export const PACKAGE_NAME = "com.karapp.drawguidance";

/** Geliştirici adı: Play Console'daki geliştirici adıyla aynı olmalı. */
export const DEVELOPER_NAME = "KarApplication";

/**
 * Google Play mağaza bağlantısı. Uygulama yayınlanana kadar yer tutucudur; paket adı değişmediği için yayından sonra
 * da bu adres geçerli olur.
 */
export const PLAY_STORE_URL = `https://play.google.com/store/apps/details?id=${PACKAGE_NAME}`;

/** Google Play abonelik yönetimi (iptal) sayfası. */
export const PLAY_SUBSCRIPTIONS_URL = "https://play.google.com/store/account/subscriptions";

/** Metinlerin yürürlük tarihi (değiştirince güncelleyin). */
export const LEGAL_EFFECTIVE = { tr: "25 Ekim 2026", en: "October 25, 2026" } as const;

const DEFAULT_API_BASE_URL = "https://drawapi.ahmetkar.com";

/** Sunucu adresi (sonunda / olmadan). */
export const API_BASE_URL = (process.env.NEXT_PUBLIC_API_BASE_URL || DEFAULT_API_BASE_URL).replace(/\/+$/, "");

/** Herkese açık iletişim e-postası (Play Console'daki iletişim adresi); ortam değişkeniyle değiştirilebilir. */
export const CONTACT_EMAIL = (process.env.NEXT_PUBLIC_CONTACT_EMAIL || "ahmetkar.1258@gmail.com").trim();

/** Sitenin üretim adresi (Vercel'de özel alan adı); site haritası, kanonik ve hreflang adresleri bununla kurulur. */
export const SITE_URL = (process.env.NEXT_PUBLIC_SITE_URL || "https://drawguidance.ahmetkar.com").replace(/\/+$/, "");

export type FirebaseWebConfig = {
  apiKey: string;
  authDomain: string;
  projectId: string;
  appId: string;
};

/**
 * Firebase web yapılandırması. Dört değerden biri eksikse `null`: oturumlu silme yolu gizlenir, yalnız talep formu
 * gösterilir. (Firebase web API anahtarı gizli değildir; kimlik doğrulamayı yetkili alan adları ve sunucudaki jeton
 * denetimi korur.)
 */
export const FIREBASE_CONFIG: FirebaseWebConfig | null = (() => {
  const apiKey = process.env.NEXT_PUBLIC_FIREBASE_API_KEY?.trim();
  const authDomain = process.env.NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN?.trim();
  const projectId = process.env.NEXT_PUBLIC_FIREBASE_PROJECT_ID?.trim();
  const appId = process.env.NEXT_PUBLIC_FIREBASE_APP_ID?.trim();
  if (!apiKey || !authDomain || !projectId || !appId) return null;
  return { apiKey, authDomain, projectId, appId };
})();
