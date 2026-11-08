"use client";

import { useEffect, useId, useRef, useState, type FormEvent } from "react";
import { sendDeletionRequest } from "@/lib/api";
import { getDictionary, type Locale } from "@/lib/i18n";
import { errorMessage } from "./formErrors";

const MAX_REASON = 1000;

/** Girişsiz silme talebi: e-posta, isteğe bağlı neden ve onay kutusu → POST /api/v1/account/deletion-requests. */
export function DeletionRequestForm({ locale }: { locale: Locale }) {
  const t = getDictionary(locale);
  const id = useId();
  const [email, setEmail] = useState("");
  const [reason, setReason] = useState("");
  const [consent, setConsent] = useState(false);
  const [state, setState] = useState<"idle" | "sending" | "done">("idle");
  const [error, setError] = useState<string | null>(null);
  const statusRef = useRef<HTMLDivElement>(null);

  // Sonuç ya da hata çıkınca ekran okuyucu ve klavye kullanıcısı için odak oraya taşınır
  useEffect(() => {
    if (state === "done" || error) statusRef.current?.focus();
  }, [state, error]);

  async function onSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (state === "sending") return;
    if (!consent) {
      setError(t.form.errors.consent);
      return;
    }
    setError(null);
    setState("sending");
    const res = await sendDeletionRequest({ email: email.trim(), reason: reason.trim(), locale });
    if (res.ok) {
      setState("done");
    } else {
      setState("idle");
      setError(errorMessage(t, res.error));
    }
  }

  if (state === "done") {
    return (
      <div className="status success" role="status" tabIndex={-1} ref={statusRef}>
        {t.deletionForm.success}
      </div>
    );
  }

  return (
    <form onSubmit={onSubmit}>
      <div ref={statusRef} tabIndex={-1} aria-live="polite">
        {error && (
          <p className="status error" role="alert">
            {error}
          </p>
        )}
      </div>
      <div className="field">
        <label htmlFor={`${id}-email`}>{t.form.email}</label>
        <input
          id={`${id}-email`}
          type="email"
          name="email"
          autoComplete="email"
          inputMode="email"
          required
          maxLength={254}
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          aria-describedby={`${id}-email-hint`}
        />
        <span className="hint" id={`${id}-email-hint`}>
          {t.form.emailHint}
        </span>
      </div>
      <div className="field">
        <label htmlFor={`${id}-reason`}>{t.form.reason}</label>
        <textarea
          id={`${id}-reason`}
          name="reason"
          maxLength={MAX_REASON}
          value={reason}
          onChange={(e) => setReason(e.target.value)}
          aria-describedby={`${id}-reason-count`}
        />
        <span className="hint" id={`${id}-reason-count`}>
          {t.form.chars(reason.length, MAX_REASON)}
        </span>
      </div>
      <div className="check">
        <input id={`${id}-consent`} type="checkbox" checked={consent} onChange={(e) => setConsent(e.target.checked)} />
        <label htmlFor={`${id}-consent`}>{t.deletionForm.consent}</label>
      </div>
      <button className="button danger" type="submit" disabled={state === "sending"}>
        {state === "sending" ? t.form.sending : t.deletionForm.submit}
      </button>
    </form>
  );
}
