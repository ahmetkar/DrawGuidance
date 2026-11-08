import "server-only";
import { neon, neonConfig, type NeonQueryFunction } from "@neondatabase/serverless";

// v42 Neon Postgres bağlantısı (Vercel → Storage → Neon; DATABASE_URL). Sorgular yalnız etiketli şablonla yazılır:
// değerler her zaman parametre olarak gider (SQL enjeksiyonu yapılamaz); metin birleştirerek sorgu kurulmaz.

let client: NeonQueryFunction<false, false> | null | undefined;

/** Veritabanı (DATABASE_URL yoksa null: site varsayılan içerikle çalışır, yönetim paneli kapalıdır). */
export function db(): NeonQueryFunction<false, false> | null {
  if (client !== undefined) return client;
  const url = process.env.DATABASE_URL?.trim();
  // Yalnız yerel deneme için (local-neon-http-proxy): Vercel'de tanımlanmaz
  const local = process.env.NEON_FETCH_ENDPOINT?.trim();
  if (local) neonConfig.fetchEndpoint = local;
  client = url ? neon(url) : null;
  return client;
}

export function dbConfigured(): boolean {
  return Boolean(process.env.DATABASE_URL?.trim());
}
