// v42 web veritabanını kurar: db/schema.sql'deki komutları sırayla çalıştırır (tekrar çalıştırmak güvenli).
// Kullanım: DATABASE_URL=postgres://... npm run db:migrate
import { readFileSync } from "node:fs";
import { neon, neonConfig } from "@neondatabase/serverless";

const url = process.env.DATABASE_URL;
if (!url) {
  console.error("DATABASE_URL yok (Vercel → Storage → Neon bağlantı adresi).");
  process.exit(1);
}
if (process.env.NEON_FETCH_ENDPOINT) neonConfig.fetchEndpoint = process.env.NEON_FETCH_ENDPOINT;
const sql = neon(url);
const text = readFileSync(new URL("../db/schema.sql", import.meta.url), "utf8");
const statements = text
  .split(/;\s*\n/)
  .map((s) => s.replace(/^\s*--.*$/gm, "").trim())
  .filter(Boolean);
for (const s of statements) {
  await sql.query(s);
}
console.log(`Tamam: ${statements.length} komut çalıştı.`);
