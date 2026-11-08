import "server-only";
import {
  generateAuthenticationOptions,
  generateRegistrationOptions,
  verifyAuthenticationResponse,
  verifyRegistrationResponse,
} from "@simplewebauthn/server";
import type { AuthenticationResponseJSON, RegistrationResponseJSON } from "@simplewebauthn/server";
import { randomBytes } from "node:crypto";
import { cookies } from "next/headers";
import { SITE_URL } from "@/lib/config";
import { allowedOrigins } from "./guard";
import { db } from "./db";

// v42 yönetim paneli girişi: Passkey (WebAuthn, FIDO2). Parola yok; anahtar cihazda (Face ID, parmak izi, Windows
// Hello, güvenlik anahtarı) kalır, sunucu yalnız açık anahtarı saklar. Kullanıcı doğrulaması zorunlu, imza sayacı
// denetlenir, meydan okuma tek kullanımlık ve 5 dakikalık (veritabanında; çerezde yalnız kimliği).

const RP_NAME = "Çizim Rehberi yönetim";
const CHALLENGE_COOKIE = process.env.NODE_ENV === "production" ? "__Host-dg_wa" : "dg_wa";
const CHALLENGE_SECONDS = 300;

export function rpId(): string {
  return process.env.ADMIN_RP_ID?.trim() || new URL(SITE_URL).hostname;
}

/** Geliştirmede localhost (WebAuthn localhost'a izin verir). */
function effectiveRpId(): string {
  return process.env.NODE_ENV !== "production" && !process.env.ADMIN_RP_ID ? "localhost" : rpId();
}

async function storeChallenge(
  challenge: string,
  purpose: "register" | "login",
  user: { id?: string; name?: string; webauthnId?: string },
): Promise<void> {
  const sql = db();
  if (!sql) throw new Error("db");
  const id = randomBytes(24).toString("base64url");
  await sql`DELETE FROM webauthn_challenges WHERE expires_at < now()`;
  await sql`
    INSERT INTO webauthn_challenges (id, challenge, purpose, user_id, user_name, webauthn_id, expires_at)
    VALUES (${id}, ${challenge}, ${purpose}, ${user.id ?? null}, ${user.name ?? null}, ${user.webauthnId ?? null},
            now() + make_interval(secs => ${CHALLENGE_SECONDS}))
  `;
  (await cookies()).set(CHALLENGE_COOKIE, id, {
    httpOnly: true,
    secure: process.env.NODE_ENV === "production",
    sameSite: "strict",
    path: "/",
    maxAge: CHALLENGE_SECONDS,
  });
}

type ChallengeRow = { challenge: string; user_id: string | null; user_name: string | null; webauthn_id: string | null };

/** Meydan okumayı alır ve hemen siler (tek kullanımlık). */
async function takeChallenge(purpose: "register" | "login"): Promise<ChallengeRow | null> {
  const sql = db();
  if (!sql) return null;
  const jar = await cookies();
  const id = jar.get(CHALLENGE_COOKIE)?.value;
  jar.delete(CHALLENGE_COOKIE);
  if (!id || id.length > 64) return null;
  const rows = (await sql`
    DELETE FROM webauthn_challenges WHERE id = ${id} AND purpose = ${purpose} AND expires_at > now()
    RETURNING challenge, user_id, user_name, webauthn_id
  `) as ChallengeRow[];
  return rows[0] ?? null;
}

/** Yeni passkey kaydı: ilk kurulumda [newUser] (ad), sonradan oturumlu yöneticinin [userId]'si. */
export async function registrationOptions(input: { userId?: string; newUserName?: string }) {
  const sql = db();
  if (!sql) throw new Error("db");
  let name: string;
  let webauthnId: string;
  let exclude: { id: string; transports?: string[] }[] = [];
  if (input.userId) {
    const rows = (await sql`SELECT name, webauthn_id FROM admin_users WHERE id = ${input.userId}`) as { name: string; webauthn_id: string }[];
    if (!rows[0]) throw new Error("user");
    name = rows[0].name;
    webauthnId = rows[0].webauthn_id;
    const creds = (await sql`SELECT id, transports FROM admin_credentials WHERE user_id = ${input.userId}`) as { id: string; transports: string[] }[];
    exclude = creds.map((c) => ({ id: c.id, transports: c.transports }));
  } else {
    name = (input.newUserName || "Yönetici").slice(0, 60);
    webauthnId = randomBytes(32).toString("base64url");
  }
  const options = await generateRegistrationOptions({
    rpName: RP_NAME,
    rpID: effectiveRpId(),
    userName: name,
    userDisplayName: name,
    userID: new Uint8Array(Buffer.from(webauthnId, "base64url")),
    attestationType: "none",
    excludeCredentials: exclude,
    authenticatorSelection: { residentKey: "required", userVerification: "required" },
    // ES256 ve RS256 (Windows Hello)
    supportedAlgorithmIDs: [-7, -257],
    timeout: CHALLENGE_SECONDS * 1000,
  });
  await storeChallenge(options.challenge, "register", { id: input.userId, name, webauthnId });
  return options;
}

/** Kaydı doğrular ve saklar. Döner: yöneticinin kimliği (ilk kurulumda yeni oluşturulan). */
export async function verifyRegistration(response: RegistrationResponseJSON, nickname: string): Promise<string | null> {
  const sql = db();
  if (!sql) return null;
  const pending = await takeChallenge("register");
  if (!pending || !pending.webauthn_id) return null;
  const result = await verifyRegistrationResponse({
    response,
    expectedChallenge: pending.challenge,
    expectedOrigin: allowedOrigins(),
    expectedRPID: effectiveRpId(),
    requireUserVerification: true,
  }).catch(() => null);
  if (!result?.verified) return null;
  const { credential, credentialDeviceType, credentialBackedUp } = result.registrationInfo;
  const publicKey = Buffer.from(credential.publicKey);
  const transports = (credential.transports ?? []).filter((t) => /^[a-z-]{2,20}$/.test(t));
  let userId = pending.user_id;
  if (!userId) {
    // İlk kurulum: yönetici ve anahtarı tek işlemde; bu arada başka biri yönetici olduysa reddedilir
    const created = (await sql`
      INSERT INTO admin_users (name, webauthn_id)
      SELECT ${pending.user_name || "Yönetici"}, ${pending.webauthn_id}
      WHERE NOT EXISTS (SELECT 1 FROM admin_users)
      RETURNING id
    `) as { id: string }[];
    if (!created[0]) return null;
    userId = created[0].id;
  }
  await sql`
    INSERT INTO admin_credentials (id, user_id, public_key, counter, transports, device_type, backed_up, nickname)
    VALUES (${credential.id}, ${userId}, decode(${publicKey.toString("hex")}, 'hex'), ${credential.counter}, ${transports}, ${credentialDeviceType},
            ${credentialBackedUp}, ${nickname.slice(0, 60)})
  `;
  return userId;
}

/** Giriş: keşfedilebilir passkey ile (kullanıcı adı sorulmaz). */
export async function authenticationOptions() {
  const options = await generateAuthenticationOptions({
    rpID: effectiveRpId(),
    userVerification: "required",
    timeout: CHALLENGE_SECONDS * 1000,
  });
  await storeChallenge(options.challenge, "login", {});
  return options;
}

/** Girişi doğrular; sayaç geri gittiyse (kopyalanmış anahtar) reddeder. Döner: yöneticinin kimliği. */
export async function verifyAuthentication(response: AuthenticationResponseJSON): Promise<string | null> {
  const sql = db();
  if (!sql) return null;
  const pending = await takeChallenge("login");
  if (!pending) return null;
  if (typeof response?.id !== "string" || response.id.length > 1024) return null;
  const rows = (await sql`
    SELECT id, user_id, encode(public_key, 'hex') AS public_key, counter, transports FROM admin_credentials WHERE id = ${response.id}
  `) as { id: string; user_id: string; public_key: Buffer | Uint8Array | string; counter: string | number; transports: string[] }[];
  const cred = rows[0];
  if (!cred) return null;
  const result = await verifyAuthenticationResponse({
    response,
    expectedChallenge: pending.challenge,
    expectedOrigin: allowedOrigins(),
    expectedRPID: effectiveRpId(),
    requireUserVerification: true,
    credential: { id: cred.id, publicKey: toBytes(cred.public_key), counter: Number(cred.counter), transports: cred.transports },
  }).catch(() => null);
  if (!result?.verified) return null;
  const next = result.authenticationInfo.newCounter;
  await sql`UPDATE admin_credentials SET counter = ${next}, last_used_at = now() WHERE id = ${cred.id}`;
  return cred.user_id;
}

/** Açık anahtar veritabanından onaltılık metin olarak okunur (encode(..., 'hex')). */
function toBytes(v: Buffer | Uint8Array | string): Uint8Array<ArrayBuffer> {
  if (typeof v === "string") return new Uint8Array(Buffer.from(v.startsWith("\\x") ? v.slice(2) : v, "hex"));
  return new Uint8Array(v);
}
