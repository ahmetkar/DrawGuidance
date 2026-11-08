"use client";

import { useActionState, useEffect, useId, useRef, useState } from "react";
import { getDictionary, type Locale } from "@/lib/i18n";
import { sendContact, type ContactState } from "./actions";

const MAX_MESSAGE = 5000;

/** v42 iletişim formu (Server Action; JavaScript kapalıyken de gönderilir). */
export function ContactForm({ locale }: { locale: Locale }) {
  const t = getDictionary(locale);
  const id = useId();
  const [state, action, pending] = useActionState<ContactState, FormData>(sendContact, { status: "idle" });
  const [length, setLength] = useState(0);
  const statusRef = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (state.status !== "idle") statusRef.current?.focus();
  }, [state]);

  if (state.status === "ok") {
    return (
      <div className="status success" role="status" tabIndex={-1} ref={statusRef}>
        {t.pages.contactSuccess}
      </div>
    );
  }
  const error =
    state.status === "error"
      ? state.error === "validation"
        ? t.form.errors.validation
        : state.error === "rate_limited"
          ? t.form.errors.rate_limited
          : state.error === "unavailable"
            ? t.pages.contactUnavailable
            : t.form.errors.generic
      : null;
  const invalid = (f: string) => (state.fields?.includes(f) ? true : undefined);
  return (
    <form action={action} noValidate={false}>
      <div ref={statusRef} tabIndex={-1} aria-live="polite">
        {error && (
          <p className="status error" role="alert">
            {error}
          </p>
        )}
      </div>
      <input type="hidden" name="locale" value={locale} />
      {/* Botlar için tuzak: görünmez, ekran okuyucudan gizli */}
      <div className="hp" aria-hidden="true">
        <label htmlFor={`${id}-website`}>Website</label>
        <input id={`${id}-website`} type="text" name="website" tabIndex={-1} autoComplete="off" />
      </div>
      <div className="field-row">
        <div className="field">
          <label htmlFor={`${id}-name`}>{t.pages.contactName}</label>
          <input id={`${id}-name`} type="text" name="name" autoComplete="name" required minLength={2} maxLength={100} aria-invalid={invalid("name")} />
        </div>
        <div className="field">
          <label htmlFor={`${id}-email`}>{t.form.email}</label>
          <input id={`${id}-email`} type="email" name="email" autoComplete="email" inputMode="email" required maxLength={254} aria-invalid={invalid("email")} />
        </div>
      </div>
      <div className="field">
        <label htmlFor={`${id}-subject`}>{t.pages.contactSubject}</label>
        <input id={`${id}-subject`} type="text" name="subject" required minLength={2} maxLength={150} aria-invalid={invalid("subject")} />
      </div>
      <div className="field">
        <label htmlFor={`${id}-message`}>{t.form.message}</label>
        <textarea
          id={`${id}-message`}
          name="message"
          required
          minLength={10}
          rows={7}
          maxLength={MAX_MESSAGE}
          aria-invalid={invalid("message")}
          aria-describedby={`${id}-count`}
          onChange={(e) => setLength(e.target.value.length)}
        />
        <span className="hint" id={`${id}-count`}>
          {t.form.chars(length, MAX_MESSAGE)}
        </span>
      </div>
      <button className="button" type="submit" disabled={pending}>
        {pending ? t.form.sending : t.form.send}
      </button>
    </form>
  );
}
