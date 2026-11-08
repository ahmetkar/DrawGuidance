import type { NextConfig } from "next";

// Güvenlik başlıkları ve kısa adresler. CSP yalnız gerekenlere izin verir: sitenin kendisi, sunucu (API) ve
// oturumlu silme için Firebase Authentication / Google girişi. Değerler derleme anında ortam değişkenlerinden okunur.

const isDev = process.env.NODE_ENV !== "production";

function origin(url: string | undefined): string | null {
  if (!url) return null;
  try {
    return new URL(url).origin;
  } catch {
    return null;
  }
}

const apiOrigin = origin(process.env.NEXT_PUBLIC_API_BASE_URL || "https://drawapi.ahmetkar.com");
const authDomain = process.env.NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN?.trim().replace(/^https?:\/\//, "").replace(/\/.*$/, "");
const firebaseEnabled = Boolean(
  process.env.NEXT_PUBLIC_FIREBASE_API_KEY &&
    authDomain &&
    process.env.NEXT_PUBLIC_FIREBASE_PROJECT_ID &&
    process.env.NEXT_PUBLIC_FIREBASE_APP_ID,
);

const scriptSrc = ["'self'", "'unsafe-inline'"];
const connectSrc = ["'self'"];
const frameSrc: string[] = [];
if (apiOrigin) connectSrc.push(apiOrigin);
if (firebaseEnabled) {
  // Google girişi açılır penceresi: gapi (apis.google.com), kimlik uçları ve authDomain'deki yardımcı çerçeve
  scriptSrc.push("https://apis.google.com");
  connectSrc.push("https://identitytoolkit.googleapis.com", "https://securetoken.googleapis.com", "https://apis.google.com");
  frameSrc.push(`https://${authDomain}`, "https://accounts.google.com");
}
if (isDev) {
  scriptSrc.push("'unsafe-eval'");
  connectSrc.push("ws:", "http://localhost:4747");
}

const csp = [
  "default-src 'self'",
  `script-src ${scriptSrc.join(" ")}`,
  "style-src 'self' 'unsafe-inline'",
  // v42 yönetim panelinden verilen görseller https adresinden gelir
  "img-src 'self' data: blob: https:",
  "font-src 'self'",
  `connect-src ${connectSrc.join(" ")}`,
  `frame-src ${frameSrc.length ? frameSrc.join(" ") : "'none'"}`,
  "object-src 'none'",
  "base-uri 'self'",
  "form-action 'self'",
  "frame-ancestors 'none'",
  "manifest-src 'self'",
  "worker-src 'self'",
  ...(isDev ? [] : ["upgrade-insecure-requests"]),
].join("; ");

const securityHeaders = [
  { key: "Content-Security-Policy", value: csp },
  { key: "Strict-Transport-Security", value: "max-age=63072000; includeSubDomains" },
  { key: "X-Content-Type-Options", value: "nosniff" },
  { key: "X-Frame-Options", value: "DENY" },
  { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
  { key: "Permissions-Policy", value: "camera=(), microphone=(), geolocation=(), payment=(), usb=(), browsing-topics=()" },
  // Google girişi açılır penceresi açanla konuşabilmeli
  { key: "Cross-Origin-Opener-Policy", value: "same-origin-allow-popups" },
  { key: "X-DNS-Prefetch-Control", value: "off" },
];

const nextConfig: NextConfig = {
  poweredByHeader: false,
  reactStrictMode: true,
  experimental: {
    // Kök düzen /[lang] altında: hiçbir yola uymayan adresler app/global-not-found.tsx ile 404
    globalNotFound: true,
  },
  async headers() {
    return [
      { source: "/:path*", headers: securityHeaders },
      {
        // v42 yönetim paneli ve uçları: arama motorlarına kapalı, önbelleğe alınmaz
        source: "/admin/:path*",
        headers: [
          { key: "X-Robots-Tag", value: "noindex, nofollow, noarchive" },
          { key: "Cache-Control", value: "no-store, max-age=0" },
        ],
      },
      {
        source: "/admin",
        headers: [
          { key: "X-Robots-Tag", value: "noindex, nofollow, noarchive" },
          { key: "Cache-Control", value: "no-store, max-age=0" },
        ],
      },
      {
        source: "/api/admin/:path*",
        headers: [
          { key: "X-Robots-Tag", value: "noindex" },
          { key: "Cache-Control", value: "no-store, max-age=0" },
        ],
      },
      {
        source: "/app-ads.txt",
        headers: [
          { key: "Content-Type", value: "text/plain; charset=utf-8" },
          { key: "Cache-Control", value: "public, max-age=3600" },
        ],
      },
    ];
  },
  async redirects() {
    // Türkçe öntanımlı; Play Console ve uygulama için kısa adresler
    return [
      { source: "/", destination: "/tr", permanent: false },
      { source: "/privacy", destination: "/tr/privacy", permanent: false },
      { source: "/gizlilik", destination: "/tr/privacy", permanent: false },
      { source: "/terms", destination: "/tr/terms", permanent: false },
      { source: "/kullanim-sartlari", destination: "/tr/terms", permanent: false },
      { source: "/delete-account", destination: "/tr/delete-account", permanent: false },
      { source: "/hesap-silme", destination: "/tr/delete-account", permanent: false },
      { source: "/support", destination: "/tr/support", permanent: false },
      { source: "/destek", destination: "/tr/support", permanent: false },
      // v42 kurumsal sayfalar
      { source: "/hakkimizda", destination: "/tr/about", permanent: false },
      { source: "/about", destination: "/tr/about", permanent: false },
      { source: "/indir", destination: "/tr/download", permanent: false },
      { source: "/download", destination: "/tr/download", permanent: false },
      { source: "/iletisim", destination: "/tr/contact", permanent: false },
      { source: "/contact", destination: "/tr/contact", permanent: false },
    ];
  },
};

export default nextConfig;
