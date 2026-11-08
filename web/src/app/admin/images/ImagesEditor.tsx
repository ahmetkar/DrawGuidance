"use client";

import { useActionState } from "react";
import type { ImageSlot, SiteImage } from "@/lib/contentSchema";
import { saveImages, type ActionResult } from "../actions";

const LABELS: Record<ImageSlot, string> = {
  hero: "Ana sayfa — karşılama görseli (720×820)",
  showcase1: "Ana sayfa — vitrin 1 (640×420)",
  showcase2: "Ana sayfa — vitrin 2 (640×420)",
  showcase3: "Ana sayfa — vitrin 3 (640×420)",
  about: "Hakkımızda — üst görsel (640×480)",
  download: "İndir — üst görsel (640×480)",
};

/** Görsel yuvaları: https adresi ve iki dilde alternatif metin. Boş adres yer tutucu gösterir. */
export function ImagesEditor({ images }: { images: Record<ImageSlot, SiteImage> }) {
  const [state, action, pending] = useActionState<ActionResult, FormData>(saveImages, { ok: false, message: "" });
  return (
    <form action={action} className="editor">
      {state.message && (
        <p className={state.ok ? "status success" : "status error"} role={state.ok ? "status" : "alert"}>
          {state.message}
          {state.issues?.length ? ` (${state.issues.join(", ")})` : ""}
        </p>
      )}
      {(Object.keys(LABELS) as ImageSlot[]).map((slot) => (
        <fieldset key={slot} className="editor-group image-slot">
          <legend>{LABELS[slot]}</legend>
          <div className="slot-row">
            <div className="slot-preview" aria-hidden="true">
              {images[slot].url ? (
                // eslint-disable-next-line @next/next/no-img-element
                <img src={images[slot].url} alt="" referrerPolicy="no-referrer" />
              ) : (
                <span>Yer tutucu</span>
              )}
            </div>
            <div className="slot-fields">
              <div className="field">
                <label htmlFor={`${slot}.url`}>Görsel adresi (https://…)</label>
                <input id={`${slot}.url`} name={`${slot}.url`} type="url" inputMode="url" defaultValue={images[slot].url} maxLength={2048} placeholder="https://" />
              </div>
              <div className="field-row">
                <div className="field">
                  <label htmlFor={`${slot}.altTr`}>Alternatif metin (TR)</label>
                  <input id={`${slot}.altTr`} name={`${slot}.altTr`} type="text" defaultValue={images[slot].altTr} maxLength={160} />
                </div>
                <div className="field">
                  <label htmlFor={`${slot}.altEn`}>Alt text (EN)</label>
                  <input id={`${slot}.altEn`} name={`${slot}.altEn`} type="text" defaultValue={images[slot].altEn} maxLength={160} />
                </div>
              </div>
            </div>
          </div>
        </fieldset>
      ))}
      <div className="sticky-actions">
        <button className="button" type="submit" disabled={pending}>
          {pending ? "Kaydediliyor…" : "Kaydet"}
        </button>
      </div>
    </form>
  );
}
