"use client";

import { useEffect, useId, useRef, useState, type FormEvent } from "react";
import { sendSupportMessage } from "@/lib/api";
import { getDictionary, type Locale } from "@/lib/i18n";
import { errorMessage } from "./formErrors";

const MAX_SUBJECT = 150;
const MAX_MESSAGE = 5000;

/** Destek/iletişim formu → POST /api/v1/support/messages. */
export function SupportForm({ locale }: { locale: Locale }) {
  const t = getDictionary(locale);
  const id = useId();
  const [email, setEmail] = useState("");
  const [subject, setSubject] = useState("");
  const [message, setMessage] = useState("");
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
    if (!subject.trim() || !message.trim()) {
      setError(t.form.errors.validation);
      return;
    }
    setError(null);
    setState("sending");
    const res = await sendSupportMessage({ email: email.trim(), subject: subject.trim(), message: message.trim(), locale });
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
        {t.support.success}
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
        />
      </div>
      <div className="field">
        <label htmlFor={`${id}-subject`}>{t.form.subject}</label>
        <input
          id={`${id}-subject`}
          type="text"
          name="subject"
          required
          maxLength={MAX_SUBJECT}
          value={subject}
          onChange={(e) => setSubject(e.target.value)}
        />
      </div>
      <div className="field">
        <label htmlFor={`${id}-message`}>{t.form.message}</label>
        <textarea
          id={`${id}-message`}
          name="message"
          required
          rows={7}
          maxLength={MAX_MESSAGE}
          value={message}
          onChange={(e) => setMessage(e.target.value)}
          aria-describedby={`${id}-message-count`}
        />
        <span className="hint" id={`${id}-message-count`}>
          {t.form.chars(message.length, MAX_MESSAGE)}
        </span>
      </div>
      <button className="button" type="submit" disabled={state === "sending"}>
        {state === "sending" ? t.form.sending : t.form.send}
      </button>
    </form>
  );
}
