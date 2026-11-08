import { ImageResponse } from "next/og";
import { getDictionary, isLocale } from "@/lib/i18n";

// v42 paylaşım görseli (Open Graph / Twitter): uygulamanın adı ve sloganı, marka renginde degrade zemin.
export const alt = "DrawGuidance";
export const size = { width: 1200, height: 630 };
export const contentType = "image/png";

export default async function OpenGraphImage({ params }: { params: Promise<{ lang: string }> }) {
  const { lang } = await params;
  const t = getDictionary(isLocale(lang) ? lang : "tr");
  return new ImageResponse(
    (
      <div
        style={{
          width: "100%",
          height: "100%",
          display: "flex",
          flexDirection: "column",
          justifyContent: "center",
          padding: "80px",
          background: "linear-gradient(135deg, #2b1f73 0%, #5b47c9 55%, #8f7cff 100%)",
          color: "white",
        }}
      >
        <div style={{ fontSize: 34, opacity: 0.85, marginBottom: 24 }}>KarApplication</div>
        <div style={{ fontSize: 84, fontWeight: 800, lineHeight: 1.05 }}>{t.appName}</div>
        <div style={{ fontSize: 38, marginTop: 28, maxWidth: 940, lineHeight: 1.3 }}>{t.home.tagline}</div>
      </div>
    ),
    size,
  );
}
