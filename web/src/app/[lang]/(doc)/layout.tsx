/** Yasal metinler ve destek: okunaklı dar sütun (v42 kurumsal tasarımda geniş sayfalardan ayrı). */
export default function DocLayout({ children }: { children: React.ReactNode }) {
  return <div className="container narrow doc">{children}</div>;
}
