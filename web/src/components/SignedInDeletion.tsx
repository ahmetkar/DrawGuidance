"use client";

import { useEffect, useId, useRef, useState } from "react";
import type { Auth, User } from "@firebase/auth";
import { deleteMyAccount } from "@/lib/api";
import type { FirebaseWebConfig } from "@/lib/config";
import { getDictionary, type Locale } from "@/lib/i18n";

type Step = "loading" | "signedOut" | "signingIn" | "confirm" | "deleting" | "done" | "doneAuthKept";
type AuthModule = typeof import("@firebase/auth");

/**
 * Firebase yalnız bu bileşen açılınca yüklenir (sitenin öteki sayfalarında Firebase kodu yok). Giriş penceresi
 * tıklamayla hemen açılsın diye (tarayıcılar gecikmeli açılır pencereyi engeller) modül önceden hazırlanır.
 */
let loading: Promise<{ auth: Auth; mod: AuthModule }> | null = null;

function loadAuth(config: FirebaseWebConfig, locale: Locale): Promise<{ auth: Auth; mod: AuthModule }> {
  // Tek örnek: initializeAuth ikinci kez çağrılamaz
  loading ??= initAuth(config, locale).catch((e) => {
    loading = null;
    throw e;
  });
  return loading;
}

async function initAuth(config: FirebaseWebConfig, locale: Locale): Promise<{ auth: Auth; mod: AuthModule }> {
  const [{ initializeApp, getApps }, mod] = await Promise.all([import("@firebase/app"), import("@firebase/auth")]);
  const app = getApps()[0] ?? initializeApp(config);
  // Oturum yalnız bu sekmede tutulur: sekme kapanınca giriş bilgisi kalmaz
  const auth = mod.initializeAuth(app, { persistence: mod.browserSessionPersistence, popupRedirectResolver: mod.browserPopupRedirectResolver });
  auth.languageCode = locale;
  return { auth, mod };
}

function firebaseCode(e: unknown): string {
  return typeof e === "object" && e !== null && "code" in e ? String((e as { code: unknown }).code) : "";
}

/**
 * Oturumlu silme: Google ile giriş (Firebase JS SDK, açılır pencere) → açık onay → sunucuda
 * DELETE /api/v1/users/me (Firebase ID jetonuyla) → Firebase oturum hesabının silinmesi → çıkış.
 */
export function SignedInDeletion({ locale, config }: { locale: Locale; config: FirebaseWebConfig }) {
  const t = getDictionary(locale);
  const s = t.signedIn;
  const id = useId();
  const [step, setStep] = useState<Step>("loading");
  const [error, setError] = useState<string | null>(null);
  const [email, setEmail] = useState<string | null>(null);
  const [confirmed, setConfirmed] = useState(false);
  const authRef = useRef<{ auth: Auth; mod: AuthModule } | null>(null);
  const userRef = useRef<User | null>(null);
  const liveRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    let active = true;
    loadAuth(config, locale)
      .then((loaded) => {
        authRef.current = loaded;
        if (active) setStep("signedOut");
      })
      .catch(() => {
        if (active) {
          setStep("signedOut");
          setError(s.errors.signIn);
        }
      });
    return () => {
      active = false;
    };
    // Yapılandırma ve dil sayfa boyunca değişmez
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    if (error || step === "confirm" || step === "done" || step === "doneAuthKept") liveRef.current?.focus();
  }, [error, step]);

  async function signIn() {
    const loaded = authRef.current;
    if (!loaded) return;
    setError(null);
    setStep("signingIn");
    try {
      const provider = new loaded.mod.GoogleAuthProvider();
      provider.setCustomParameters({ prompt: "select_account" });
      // Tıklamayla aynı adımda açılır (araya bekleme girmez)
      const cred = await loaded.mod.signInWithPopup(loaded.auth, provider);
      userRef.current = cred.user;
      setEmail(cred.user.email);
      setConfirmed(false);
      setStep("confirm");
    } catch (e) {
      const code = firebaseCode(e);
      setStep("signedOut");
      if (code === "auth/popup-blocked") setError(s.errors.popupBlocked);
      else if (code === "auth/popup-closed-by-user" || code === "auth/cancelled-popup-request") setError(s.errors.popupClosed);
      else if (code === "auth/unauthorized-domain" || code === "auth/operation-not-allowed") setError(s.errors.unauthorizedDomain);
      else setError(s.errors.signIn);
    }
  }

  async function signOutNow() {
    setError(null);
    try {
      if (authRef.current) await authRef.current.mod.signOut(authRef.current.auth);
    } finally {
      userRef.current = null;
      setEmail(null);
      setConfirmed(false);
      setStep("signedOut");
    }
  }

  async function deleteNow() {
    const user = userRef.current;
    if (!user || !confirmed) return;
    setError(null);
    setStep("deleting");
    let token: string;
    try {
      token = await user.getIdToken(true);
    } catch {
      setError(s.errors.session);
      await signOutNow();
      return;
    }
    const res = await deleteMyAccount(token);
    if (!res.ok) {
      setStep("confirm");
      if (res.status === 401) {
        setError(s.errors.session);
        await signOutNow();
      } else if (res.error === "network") {
        setError(t.form.errors.network);
      } else {
        setError(s.errors.server);
      }
      return;
    }
    // Sunucudaki veriler silindi; Firebase oturum hesabını da sil (giriş az önce yapıldığı için izin verilir)
    const loaded = authRef.current;
    let authDeleted = true;
    try {
      if (!loaded) throw new Error("auth");
      await loaded.mod.deleteUser(user);
    } catch {
      authDeleted = false;
      if (loaded) await loaded.mod.signOut(loaded.auth).catch(() => undefined);
    }
    userRef.current = null;
    setEmail(null);
    setStep(authDeleted ? "done" : "doneAuthKept");
  }

  if (step === "done" || step === "doneAuthKept") {
    return (
      <div className="status success" role="status" tabIndex={-1} ref={liveRef}>
        {step === "done" ? s.success : s.authAccountKept}
      </div>
    );
  }

  return (
    <div>
      <div ref={liveRef} tabIndex={-1} aria-live="polite">
        {error && (
          <p className="status error" role="alert">
            {error}
          </p>
        )}
      </div>

      {(step === "loading" || step === "signedOut" || step === "signingIn") && (
        <div className="button-row">
          <button className="button" type="button" onClick={signIn} disabled={step !== "signedOut"}>
            <GoogleIcon />
            {step === "signingIn" ? s.signingIn : s.signIn}
          </button>
        </div>
      )}

      {(step === "confirm" || step === "deleting") && (
        <section aria-labelledby={`${id}-confirm`} className="notice">
          <h3 id={`${id}-confirm`}>{s.confirmTitle}</h3>
          <p>
            {s.signedInAs} <strong>{email ?? "—"}</strong>
          </p>
          <p>{s.confirmText}</p>
          <div className="check">
            <input
              id={`${id}-check`}
              type="checkbox"
              checked={confirmed}
              onChange={(e) => setConfirmed(e.target.checked)}
              disabled={step === "deleting"}
            />
            <label htmlFor={`${id}-check`}>{s.confirmCheck}</label>
          </div>
          <div className="button-row">
            <button className="button danger" type="button" onClick={deleteNow} disabled={!confirmed || step === "deleting"}>
              {step === "deleting" ? s.deleting : s.confirmButton}
            </button>
            <button className="button secondary" type="button" onClick={signOutNow} disabled={step === "deleting"}>
              {s.cancel}
            </button>
          </div>
        </section>
      )}
    </div>
  );
}

function GoogleIcon() {
  return (
    <svg width="18" height="18" viewBox="0 0 48 48" aria-hidden="true" focusable="false">
      <path fill="#FFC107" d="M43.6 20.5H42V20H24v8h11.3C33.7 32.7 29.2 36 24 36c-6.6 0-12-5.4-12-12s5.4-12 12-12c3.1 0 5.8 1.2 7.9 3.1l5.7-5.7C34 6.1 29.3 4 24 4 12.9 4 4 12.9 4 24s8.9 20 20 20 20-8.9 20-20c0-1.3-.1-2.4-.4-3.5z" />
      <path fill="#FF3D00" d="M6.3 14.7l6.6 4.8C14.7 15.1 19 12 24 12c3.1 0 5.8 1.2 7.9 3.1l5.7-5.7C34 6.1 29.3 4 24 4 16.3 4 9.7 8.3 6.3 14.7z" />
      <path fill="#4CAF50" d="M24 44c5.2 0 9.9-2 13.4-5.2l-6.2-5.2C29.2 35.1 26.7 36 24 36c-5.2 0-9.6-3.3-11.3-7.9l-6.5 5C9.5 39.6 16.2 44 24 44z" />
      <path fill="#1976D2" d="M43.6 20.5H42V20H24v8h11.3c-.8 2.2-2.2 4.2-4.1 5.6l6.2 5.2C37 39.2 44 34 44 24c0-1.3-.1-2.4-.4-3.5z" />
    </svg>
  );
}
