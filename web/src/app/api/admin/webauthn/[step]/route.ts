import { NextResponse, type NextRequest } from "next/server";
import { safeEqual, strongSecret } from "@/lib/security";
import { dbConfigured } from "@/lib/server/db";
import { audit, rateLimit, requestInfo } from "@/lib/server/guard";
import { adminExists, createSession, currentAdmin } from "@/lib/server/session";
import {
  authenticationOptions,
  registrationOptions,
  verifyAuthentication,
  verifyRegistration,
} from "@/lib/server/webauthn";

// v42 passkey uçları (yalnız POST, JSON, aynı köken):
//   register-options / register-verify: ilk kurulum (ADMIN_SETUP_TOKEN ile, yönetici yokken) ya da oturumlu yöneticinin
//   yeni anahtarı; login-options / login-verify: giriş. Yanıtlar ayrıntı vermez (hangi adımın neden reddedildiği
//   saldırgana söylenmez); her deneme IP özetine göre sınırlanır ve kaydedilir.

export const dynamic = "force-dynamic";

const NO_STORE = { "Cache-Control": "no-store" };

function fail(status = 400) {
  return NextResponse.json({ ok: false }, { status, headers: NO_STORE });
}

export async function POST(req: NextRequest, ctx: RouteContext<"/api/admin/webauthn/[step]">) {
  const { step } = await ctx.params;
  if (!dbConfigured()) return fail(503);
  const info = await requestInfo();
  if (!info.sameOrigin) return fail(403);
  if (!(req.headers.get("content-type") || "").startsWith("application/json")) return fail(415);
  const length = Number(req.headers.get("content-length") || "0");
  if (length > 20_000) return fail(413);
  if (!(await rateLimit(`webauthn:${info.ipHash}`, 20, 900))) return fail(429);
  let body: Record<string, unknown>;
  try {
    const parsed: unknown = await req.json();
    if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) return fail();
    body = parsed as Record<string, unknown>;
  } catch {
    return fail();
  }

  switch (step) {
    case "register-options": {
      const admin = await currentAdmin();
      if (admin) return NextResponse.json(await registrationOptions({ userId: admin.userId }), { headers: NO_STORE });
      // İlk kurulum: yönetici yoksa ve kurulum anahtarı doğruysa
      const token = process.env.ADMIN_SETUP_TOKEN;
      if (!strongSecret(token) || (await adminExists())) return fail(403);
      if (!(await rateLimit(`setup:${info.ipHash}`, 5, 3600))) return fail(429);
      const given = typeof body.setupToken === "string" ? body.setupToken : "";
      if (!safeEqual(given, token.trim())) {
        await audit(null, "setup.denied", "", info.ipHash);
        return fail(403);
      }
      const name = typeof body.name === "string" ? body.name.trim().slice(0, 60) : "";
      return NextResponse.json(await registrationOptions({ newUserName: name || "Yönetici" }), { headers: NO_STORE });
    }
    case "register-verify": {
      const admin = await currentAdmin();
      const nickname = typeof body.nickname === "string" ? body.nickname.trim().slice(0, 60) : "";
      const userId = await verifyRegistration(body.response as never, nickname || "Passkey");
      if (!userId || (admin && admin.userId !== userId)) {
        await audit(admin?.userId ?? null, "passkey.register_failed", "", info.ipHash);
        return fail(401);
      }
      await audit(userId, admin ? "passkey.add" : "setup.done", nickname, info.ipHash);
      if (!admin) await createSession(userId, info.ipHash, info.userAgent);
      return NextResponse.json({ ok: true }, { headers: NO_STORE });
    }
    case "login-options": {
      if (!(await adminExists())) return fail(404);
      return NextResponse.json(await authenticationOptions(), { headers: NO_STORE });
    }
    case "login-verify": {
      if (!(await rateLimit(`login:${info.ipHash}`, 10, 900))) return fail(429);
      const userId = await verifyAuthentication(body.response as never);
      if (!userId) {
        await audit(null, "login.failed", "", info.ipHash);
        return fail(401);
      }
      await createSession(userId, info.ipHash, info.userAgent);
      await audit(userId, "login", "", info.ipHash);
      return NextResponse.json({ ok: true }, { headers: NO_STORE });
    }
    default:
      return fail(404);
  }
}
