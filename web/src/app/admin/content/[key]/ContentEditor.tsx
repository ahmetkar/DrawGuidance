"use client";

import { useActionState } from "react";
import type { Field } from "@/lib/contentForm";
import { saveContent, type ActionResult } from "../../actions";

/** İçerik formu: alanlar sunucuda yapıdan üretilir; kaydedince sunucu doğrular. */
export function ContentEditor({ contentKey, locale, fields }: { contentKey: string; locale: string; fields: Field[] }) {
  const [state, action, pending] = useActionState<ActionResult, FormData>(saveContent, { ok: false, message: "" });
  const groups = new Map<string, Field[]>();
  for (const f of fields) {
    const g = f.path.includes(".") ? f.path.split(".").slice(0, 2).join(".").replace(/\.[a-zA-Z]+$/, "") : "_";
    groups.set(g, [...(groups.get(g) ?? []), f]);
  }
  return (
    <form action={action} className="editor">
      <input type="hidden" name="_key" value={contentKey} />
      <input type="hidden" name="_locale" value={locale} />
      {state.message && (
        <p className={state.ok ? "status success" : "status error"} role={state.ok ? "status" : "alert"}>
          {state.message}
          {state.issues?.length ? ` (${state.issues.join(", ")})` : ""}
        </p>
      )}
      {[...groups.entries()].map(([g, list]) => (
        <fieldset key={g} className="editor-group">
          {g !== "_" && <legend>{list[0]!.label.split(" · ").slice(0, -1).join(" · ") || g}</legend>}
          {list.map((f) => (
            <div className={f.kind === "checkbox" ? "check" : "field"} key={f.path}>
              {f.kind === "checkbox" ? (
                <>
                  <input id={f.path} name={f.path} type="checkbox" defaultChecked={Boolean(f.value)} />
                  <label htmlFor={f.path}>{f.label}</label>
                </>
              ) : (
                <>
                  <label htmlFor={f.path}>{f.label}</label>
                  {f.kind === "textarea" ? (
                    <textarea id={f.path} name={f.path} defaultValue={String(f.value)} rows={4} maxLength={2000} />
                  ) : (
                    <input id={f.path} name={f.path} type={f.kind === "url" ? "url" : "text"} defaultValue={String(f.value)} maxLength={f.kind === "url" ? 2048 : 400} />
                  )}
                </>
              )}
            </div>
          ))}
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
