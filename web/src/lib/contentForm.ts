// v42 yönetim panelinin içerik formu: varsayılan içeriğin yapısından alanlar üretilir ve form verisi yine bu yapıya
// göre okunur. Yalnız şablondaki yollar okunur (bilinmeyen anahtar eklenemez; prototip kirletme yapılamaz).

export type FieldKind = "text" | "textarea" | "checkbox" | "url";
export type Field = { path: string; kind: FieldKind; value: string | boolean; label: string };

/** Şablondaki her yaprak için alan; [current] varsa değeri ondan. */
export function fieldsOf(template: unknown, current: unknown, prefix = ""): Field[] {
  if (Array.isArray(template)) {
    return template.flatMap((item, i) => fieldsOf(item, Array.isArray(current) ? current[i] : undefined, join(prefix, String(i))));
  }
  if (template && typeof template === "object") {
    return Object.keys(template).flatMap((k) =>
      fieldsOf((template as Record<string, unknown>)[k], current && typeof current === "object" ? (current as Record<string, unknown>)[k] : undefined, join(prefix, k)),
    );
  }
  const value = current === undefined ? template : current;
  if (typeof template === "boolean") return [{ path: prefix, kind: "checkbox", value: Boolean(value), label: labelOf(prefix) }];
  const s = typeof value === "string" ? value : String(value ?? "");
  const kind: FieldKind = /(^|\.)url$/.test(prefix) ? "url" : String(template).length > 90 || s.length > 90 ? "textarea" : "text";
  return [{ path: prefix, kind, value: s, label: labelOf(prefix) }];
}

/** Form verisinden şablon yapısında nesne (yaprak türü şablondaki gibi; eksik alan boş metin / false). */
export function readForm(template: unknown, get: (name: string) => string | null, prefix = ""): unknown {
  if (Array.isArray(template)) return template.map((item, i) => readForm(item, get, join(prefix, String(i))));
  if (template && typeof template === "object") {
    const out: Record<string, unknown> = Object.create(null);
    for (const k of Object.keys(template)) out[k] = readForm((template as Record<string, unknown>)[k], get, join(prefix, k));
    return { ...out };
  }
  if (typeof template === "boolean") return get(prefix) === "on";
  return (get(prefix) ?? "").replace(/\r\n/g, "\n");
}

function join(a: string, b: string): string {
  return a ? `${a}.${b}` : b;
}

/** Panelde gösterilen Türkçe alan adları. */
const NAMES: Record<string, string> = {
  heroBadge: "Üst rozet",
  heroTitle: "Ana başlık",
  heroSubtitle: "Alt başlık",
  heroPrimary: "Birinci düğme",
  heroSecondary: "İkinci düğme",
  stats: "Rakam",
  value: "Değer",
  label: "Etiket",
  featuresTitle: "Özellikler başlığı",
  featuresIntro: "Özellikler açıklaması",
  features: "Özellik",
  showcaseTitle: "Vitrin başlığı",
  showcase: "Vitrin",
  faqTitle: "SSS başlığı",
  faq: "Soru",
  q: "Soru",
  a: "Cevap",
  ctaTitle: "Çağrı başlığı",
  ctaText: "Çağrı metni",
  ctaButton: "Çağrı düğmesi",
  title: "Başlık",
  text: "Metin",
  intro: "Giriş",
  missionTitle: "Misyon başlığı",
  mission: "Misyon",
  valuesTitle: "Değerler başlığı",
  values: "Değer",
  storyTitle: "Hikâye başlığı",
  story: "Hikâye",
  android: "Google Play (Android)",
  windows: "Windows",
  ios: "iOS",
  description: "Açıklama",
  url: "Bağlantı (https://…)",
  button: "Düğme yazısı",
  available: "Yayında (işaretli değilse \"Yakında\")",
  note: "Alt not",
};

/** Alan adı: yol okunur hâle getirilir ("features.0.title" → "Özellik 1 · Başlık"). */
function labelOf(path: string): string {
  return path
    .split(".")
    .map((p) => (/^\d+$/.test(p) ? String(Number(p) + 1) : NAMES[p] ?? p))
    .join(" · ")
    .replace(/ · (\d+)/g, " $1");
}
