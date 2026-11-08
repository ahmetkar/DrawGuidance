// v42 yönetim panelinden düzenlenen sayfa içerikleri: türler, varsayılanlar (iki dilde) ve doğrulama.
// Bu dosya sunucuya özgü kod içermez (testlerde ve formlarda da kullanılır); veritabanı okuma src/lib/content.ts'te.
import { z } from "zod";

export const CONTENT_LOCALES = ["tr", "en"] as const;
export type ContentLocale = (typeof CONTENT_LOCALES)[number];

const line = (max: number) => z.string().trim().min(1).max(max);
const text = (max: number) => z.string().trim().max(max);

/** Yalnız https (ya da kök göreli) adresler; javascript:, data: vb. reddedilir. */
export const safeUrl = z
  .string()
  .trim()
  .max(2048)
  .refine((v) => v === "" || isSafeUrl(v), { message: "invalid_url" });

export function isSafeUrl(v: string): boolean {
  if (v.startsWith("/") && !v.startsWith("//")) return !/[\s<>"'`]/.test(v);
  try {
    const u = new URL(v);
    return u.protocol === "https:" && !u.username && !u.password && !/[\s<>"'`]/.test(v);
  } catch {
    return false;
  }
}

export const homeSchema = z.object({
  heroBadge: text(60),
  heroTitle: line(120),
  heroSubtitle: text(400),
  heroPrimary: line(40),
  heroSecondary: line(40),
  stats: z.array(z.object({ value: line(20), label: line(60) })).length(3),
  featuresTitle: line(120),
  featuresIntro: text(300),
  features: z.array(z.object({ title: line(60), text: text(240) })).length(6),
  showcaseTitle: line(120),
  showcase: z.array(z.object({ title: line(80), text: text(400) })).length(3),
  faqTitle: line(120),
  faq: z.array(z.object({ q: line(160), a: text(600) })).length(4),
  ctaTitle: line(120),
  ctaText: text(300),
  ctaButton: line(40),
});

export const aboutSchema = z.object({
  title: line(120),
  intro: text(800),
  missionTitle: line(80),
  mission: text(1200),
  valuesTitle: line(80),
  values: z.array(z.object({ title: line(60), text: text(300) })).length(3),
  storyTitle: line(80),
  story: text(2000),
});

const platform = z.object({
  title: line(60),
  description: text(400),
  url: safeUrl,
  button: line(40),
  available: z.boolean(),
});

export const downloadSchema = z.object({
  title: line(120),
  intro: text(500),
  android: platform,
  windows: platform,
  ios: platform,
  note: text(400),
});

export type HomeContent = z.infer<typeof homeSchema>;
export type AboutContent = z.infer<typeof aboutSchema>;
export type DownloadContent = z.infer<typeof downloadSchema>;

export const CONTENT_KEYS = ["home", "about", "download"] as const;
export type ContentKey = (typeof CONTENT_KEYS)[number];

export const schemas = { home: homeSchema, about: aboutSchema, download: downloadSchema } as const;

export type ContentMap = { home: HomeContent; about: AboutContent; download: DownloadContent };

/** Görsel yuvaları: yönetim panelinden adres verilene kadar yer tutucu çizilir. */
export const IMAGE_SLOTS = ["hero", "showcase1", "showcase2", "showcase3", "about", "download"] as const;
export type ImageSlot = (typeof IMAGE_SLOTS)[number];
export type SiteImage = { url: string; altTr: string; altEn: string };

export const imageSchema = z.object({
  url: safeUrl.refine((v) => v === "" || v.startsWith("https://") || v.startsWith("/"), { message: "invalid_url" }),
  altTr: text(160),
  altEn: text(160),
});

const PLAY = "https://play.google.com/store/apps/details?id=com.karapp.drawguidance";

export const DEFAULTS: Record<ContentLocale, ContentMap> = {
  tr: {
    home: {
      heroBadge: "Android için yapay zekâ destekli çizim",
      heroTitle: "Çiz, not al, çizgi roman yap — tek uygulamada.",
      heroSubtitle:
        "Çizim Rehberi; resim, not, çizgi roman, çocuk ve 3B şekil modlarıyla her yaştan kullanıcıya rehberlik eden bir çizim uygulamasıdır. Öneriler ve tanıma cihazınızda çalışır.",
      heroPrimary: "Uygulamayı indir",
      heroSecondary: "Daha fazla bilgi",
      stats: [
        { value: "5", label: "çizim modu" },
        { value: "600+", label: "hazır karakter pozu" },
        { value: "7", label: "dil desteği" },
      ],
      featuresTitle: "Yaratıcılığın için gereken her şey",
      featuresIntro: "Basit bir eskizden baskıya hazır çizgi romana kadar aynı uygulamada çalış.",
      features: [
        { title: "Beş çizim modu", text: "Resim, not, çizgi roman, çocuk ve 3B şekil modları; her biri kendi araçlarıyla." },
        { title: "Cihazda yapay zekâ", text: "Şekil önerisi, el yazısı ve çizim tanıma cihazda çalışır; çizimlerin sende kalır." },
        { title: "Çizgi roman stüdyosu", text: "Panel şablonları, konuşma balonları, tema paketleri, karakterler, efektler ve mekânlar." },
        { title: "Baskıya hazır", text: "CMYK PDF, CBZ, PNG ve JPEG olarak tek pencereden kaydet." },
        { title: "Çocuklar için güvenli", text: "13 yaş altı için reklamsız, sade ve güvenli bir çizim alanı." },
        { title: "Senkronizasyon", text: "Google hesabınla giriş yap, çizimlerin cihazların arasında eşitlensin." },
      ],
      showcaseTitle: "Her yaşa uygun çizim deneyimi",
      showcase: [
        { title: "Çizgi romanını baştan sona üret", text: "Hazır sayfa düzenleri, tema paketleri ve baskıya hazır dışa aktarma ile hikâyeni okuyucuya ulaştır." },
        { title: "Notlarını düzenle", text: "Formüller, diyagramlar ve el yazısı tanımayla derslerini ve toplantılarını kolayca not al." },
        { title: "Adım adım öğren", text: "Eğitim modu her aracı uygulamalı olarak gösterir; çocuk modu çizim görevleriyle eğlendirir." },
      ],
      faqTitle: "Sık sorulan sorular",
      faq: [
        { q: "Uygulama ücretsiz mi?", a: "Evet. Temel özellikler ücretsizdir; Premium reklamsız kullanım, gelişmiş içerikler ve yeni özellikler sunar." },
        { q: "Çizimlerim nerede saklanıyor?", a: "Çizimlerin cihazında saklanır. Giriş yaparsan senkronizasyon için sunucumuzda da yedeklenir." },
        { q: "Oluşturduğum çizgi romanları satabilir miyim?", a: "Evet. Kullanım şartlarındaki koşullarla çizgi romanlarını dağıtabilir ve ticari amaçla kullanabilirsin." },
        { q: "Hangi cihazlarda çalışıyor?", a: "Android 6.0 ve üzeri telefon ve tabletlerde çalışır. Windows ve iOS sürümleri hazırlanıyor." },
      ],
      ctaTitle: "Çizmeye bugün başla",
      ctaText: "Uygulamayı ücretsiz indir; ilk çizimini birkaç dakikada tamamla.",
      ctaButton: "İndirme seçenekleri",
    },
    about: {
      title: "Hakkımızda",
      intro:
        "Çizim Rehberi, KarApplication tarafından geliştirilen bağımsız bir çizim uygulamasıdır. Amacımız, çizmeyi herkes için kolay, öğretici ve keyifli hâle getirmek.",
      missionTitle: "Misyonumuz",
      mission:
        "Yapay zekâyı yaratıcılığın yerine değil, yanında kullanıyoruz. Önerilerimiz cihazda çalışır, kullanıcının çizimi her zaman kullanıcıya aittir.",
      valuesTitle: "Değerlerimiz",
      values: [
        { title: "Gizlilik", text: "Gereksiz veri toplamıyoruz; çocuk modunda reklam ve izleme yok." },
        { title: "Erişilebilirlik", text: "Yedi dil, sade arayüz ve her yaştan kullanıcıya uygun araçlar." },
        { title: "Özgünlük", text: "Uygulamadaki karakterler, şekiller ve efektler özgündür; telif sorunu yaşamazsın." },
      ],
      storyTitle: "Hikâyemiz",
      story:
        "Çizim Rehberi, not alırken şekil çizmenin zorluğunu çözmek için başladı. Zamanla resim, çizgi roman, çocuk ve 3B modlarıyla büyüdü ve bugün binlerce kullanıcının çizim yardımcısı olmayı hedefliyor.",
    },
    download: {
      title: "Uygulamayı indir",
      intro: "Çizim Rehberi'ni cihazına uygun mağazadan indir. Yeni platformlar hazır olduğunda bu sayfada duyurulur.",
      android: {
        title: "Google Play (Android)",
        description: "Android 6.0 ve üzeri telefon ve tabletler için. Ücretsiz indir, Premium'u istediğin zaman dene.",
        url: PLAY,
        button: "Google Play'den indir",
        available: true,
      },
      windows: {
        title: "Windows",
        description: "Kalem destekli Windows bilgisayarlar ve tabletler için masaüstü sürümü hazırlanıyor.",
        url: "",
        button: "Yakında",
        available: false,
      },
      ios: {
        title: "iOS (iPhone ve iPad)",
        description: "Apple Pencil desteğiyle iPad ve iPhone sürümü hazırlanıyor.",
        url: "",
        button: "Yakında",
        available: false,
      },
      note: "Uygulamayı yalnız resmi mağazalardan indir. Başka sitelerden indirilen dosyalar güvenli olmayabilir.",
    },
  },
  en: {
    home: {
      heroBadge: "AI-assisted drawing for Android",
      heroTitle: "Draw, take notes, make comics — in one app.",
      heroSubtitle:
        "DrawGuidance guides users of all ages with modes for pictures, notes, comics, kids and 3D shapes. Suggestions and recognition run on your device.",
      heroPrimary: "Download the app",
      heroSecondary: "Learn more",
      stats: [
        { value: "5", label: "drawing modes" },
        { value: "600+", label: "ready character poses" },
        { value: "7", label: "languages" },
      ],
      featuresTitle: "Everything your creativity needs",
      featuresIntro: "From a quick sketch to a print-ready comic, work in the same app.",
      features: [
        { title: "Five drawing modes", text: "Pictures, notes, comics, kids and 3D shapes, each with its own tools." },
        { title: "On-device AI", text: "Shape suggestions, handwriting and drawing recognition run on your device; your drawings stay yours." },
        { title: "Comic studio", text: "Panel templates, speech balloons, theme packs, characters, effects and locations." },
        { title: "Print-ready", text: "Save as CMYK PDF, CBZ, PNG and JPEG from a single window." },
        { title: "Safe for kids", text: "An ad-free, simple and safe drawing space for children under 13." },
        { title: "Sync", text: "Sign in with Google and keep your drawings in sync across devices." },
      ],
      showcaseTitle: "A drawing experience for every age",
      showcase: [
        { title: "Make your comic from start to finish", text: "Bring your story to readers with page layouts, theme packs and print-ready export." },
        { title: "Organize your notes", text: "Take class and meeting notes with formulas, diagrams and handwriting recognition." },
        { title: "Learn step by step", text: "Tutorial mode shows every tool hands-on; kids mode adds drawing challenges." },
      ],
      faqTitle: "Frequently asked questions",
      faq: [
        { q: "Is the app free?", a: "Yes. The basics are free; Premium adds an ad-free experience, advanced content and new features." },
        { q: "Where are my drawings stored?", a: "On your device. If you sign in, they are also backed up on our server for sync." },
        { q: "Can I sell the comics I make?", a: "Yes. Under the terms of use you may distribute your comics and use them commercially." },
        { q: "Which devices are supported?", a: "Android 6.0+ phones and tablets. Windows and iOS versions are in progress." },
      ],
      ctaTitle: "Start drawing today",
      ctaText: "Download the app for free and finish your first drawing in minutes.",
      ctaButton: "Download options",
    },
    about: {
      title: "About us",
      intro:
        "DrawGuidance is an independent drawing app developed by KarApplication. Our goal is to make drawing easy, instructive and fun for everyone.",
      missionTitle: "Our mission",
      mission:
        "We use AI alongside creativity, not instead of it. Our suggestions run on the device, and a user's drawing always belongs to the user.",
      valuesTitle: "Our values",
      values: [
        { title: "Privacy", text: "We do not collect unnecessary data; kids mode has no ads or tracking." },
        { title: "Accessibility", text: "Seven languages, a simple interface and tools for users of all ages." },
        { title: "Originality", text: "Characters, shapes and effects in the app are original, so you avoid copyright issues." },
      ],
      storyTitle: "Our story",
      story:
        "DrawGuidance started as a way to make drawing shapes easier while taking notes. It grew with picture, comic, kids and 3D modes and aims to be the drawing companion of thousands of users.",
    },
    download: {
      title: "Download the app",
      intro: "Get DrawGuidance from the store for your device. New platforms will be announced on this page.",
      android: {
        title: "Google Play (Android)",
        description: "For phones and tablets with Android 6.0 or later. Download for free and try Premium any time.",
        url: PLAY,
        button: "Get it on Google Play",
        available: true,
      },
      windows: {
        title: "Windows",
        description: "A desktop version for pen-enabled Windows computers and tablets is in progress.",
        url: "",
        button: "Coming soon",
        available: false,
      },
      ios: {
        title: "iOS (iPhone and iPad)",
        description: "An iPad and iPhone version with Apple Pencil support is in progress.",
        url: "",
        button: "Coming soon",
        available: false,
      },
      note: "Download the app only from official stores. Files from other sites may not be safe.",
    },
  },
};

/** Kayıtlı içerik bozuk ya da eksikse varsayılanla tamamlanır (alan alan değil, bütün olarak). */
export function parseContent<K extends ContentKey>(key: K, locale: ContentLocale, raw: unknown): ContentMap[K] {
  const parsed = schemas[key].safeParse(raw);
  return (parsed.success ? parsed.data : DEFAULTS[locale][key]) as ContentMap[K];
}
