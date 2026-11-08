/** Yapılandırılmış veri (schema.org). "<" ve satır ayırıcılar kaçırılır: içerikten </script> enjeksiyonu yapılamaz. */
const LS = String.fromCharCode(0x2028);
const PS = String.fromCharCode(0x2029);

export function JsonLd({ data }: { data: object }) {
  const json = JSON.stringify(data).replace(/</g, "\\u003c").split(LS).join("\\u2028").split(PS).join("\\u2029");
  return <script type="application/ld+json" dangerouslySetInnerHTML={{ __html: json }} />;
}
