"use client";

import { startAuthentication, startRegistration } from "@simplewebauthn/browser";
import { useRouter } from "next/navigation";
import { useId, useState, type FormEvent } from "react";

async function post(step: string, body: object): Promise<Response> {
  return fetch(`/api/admin/webauthn/${step}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    credentials: "same-origin",
    body: JSON.stringify(body),
  });
}

function message(status: number): string {
  if (status === 429) return "Çok fazla deneme. Bir süre sonra yeniden deneyin.";
  if (status === 503) return "Veritabanı yapılandırılmamış (DATABASE_URL).";
  if (status === 403) return "İzin verilmedi. Bilgileri denetleyin.";
  return "Doğrulanamadı. Yeniden deneyin.";
}

/** Passkey ile giriş (kullanıcı adı ya da parola yok). */
export function PasskeyLogin() {
  const router = useRouter();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  async function login() {
    setBusy(true);
    setError(null);
    try {
      const opt = await post("login-options", {});
      if (!opt.ok) throw new Error(message(opt.status));
      const response = await startAuthentication({ optionsJSON: await opt.json() });
      const res = await post("login-verify", { response });
      if (!res.ok) throw new Error(message(res.status));
      router.replace("/admin");
      router.refresh();
    } catch (e) {
      setError(e instanceof Error && e.message && !e.name.startsWith("NotAllowed") ? e.message : "Giriş iptal edildi ya da zaman aşımına uğradı.");
      setBusy(false);
    }
  }
  return (
    <div className="stack">
      {error && (
        <p className="status error" role="alert">
          {error}
        </p>
      )}
      <button className="button large" type="button" onClick={login} disabled={busy}>
        {busy ? "Doğrulanıyor…" : "Passkey ile giriş yap"}
      </button>
    </div>
  );
}

/** İlk kurulum (kurulum anahtarıyla) ya da oturumluyken yeni passkey ekleme. */
export function PasskeyRegister({ setup }: { setup: boolean }) {
  const router = useRouter();
  const id = useId();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState(false);
  async function onSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const form = new FormData(e.currentTarget);
    setBusy(true);
    setError(null);
    try {
      const opt = await post("register-options", { setupToken: form.get("token") ?? "", name: form.get("name") ?? "" });
      if (!opt.ok) throw new Error(message(opt.status));
      const response = await startRegistration({ optionsJSON: await opt.json() });
      const res = await post("register-verify", { response, nickname: form.get("nickname") ?? "" });
      if (!res.ok) throw new Error(message(res.status));
      if (setup) router.replace("/admin");
      else setDone(true);
      router.refresh();
    } catch (err) {
      setError(err instanceof Error && err.message && !err.name.startsWith("NotAllowed") && !err.name.startsWith("InvalidState") ? err.message : "Kayıt iptal edildi ya da bu anahtar zaten kayıtlı.");
    } finally {
      setBusy(false);
    }
  }
  return (
    <form onSubmit={onSubmit} className="stack">
      {error && (
        <p className="status error" role="alert">
          {error}
        </p>
      )}
      {done && <p className="status success">Passkey eklendi.</p>}
      {setup && (
        <>
          <div className="field">
            <label htmlFor={`${id}-token`}>Kurulum anahtarı (ADMIN_SETUP_TOKEN)</label>
            <input id={`${id}-token`} name="token" type="password" autoComplete="off" required minLength={32} maxLength={256} />
          </div>
          <div className="field">
            <label htmlFor={`${id}-name`}>Adınız</label>
            <input id={`${id}-name`} name="name" type="text" required maxLength={60} autoComplete="name" />
          </div>
        </>
      )}
      <div className="field">
        <label htmlFor={`${id}-nick`}>Anahtarın adı (ör. iPhone, Windows Hello)</label>
        <input id={`${id}-nick`} name="nickname" type="text" maxLength={60} />
      </div>
      <button className="button" type="submit" disabled={busy}>
        {busy ? "Bekleniyor…" : setup ? "Passkey oluştur ve kur" : "Yeni passkey ekle"}
      </button>
    </form>
  );
}
