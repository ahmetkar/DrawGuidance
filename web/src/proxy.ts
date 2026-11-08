import { NextResponse, type NextRequest } from "next/server";

// v42 yönetim paneli: oturum çerezi olmayan istek giriş sayfasına yönlendirilir (hızlı ön denetim). Asıl denetim her
// sayfa ve işlemde veritabanındaki oturumla yapılır (requireAdmin); çerezin varlığı tek başına yetki vermez.

const COOKIE = process.env.NODE_ENV === "production" ? "__Host-dg_admin" : "dg_admin";
const OPEN = new Set(["/admin/login", "/admin/setup"]);

export function proxy(request: NextRequest) {
  const { pathname } = request.nextUrl;
  if (OPEN.has(pathname)) return NextResponse.next();
  if (!request.cookies.get(COOKIE)?.value) {
    return NextResponse.redirect(new URL("/admin/login", request.url));
  }
  return NextResponse.next();
}

export const config = {
  matcher: ["/admin", "/admin/:path*"],
};
