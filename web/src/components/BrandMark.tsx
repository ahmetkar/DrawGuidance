/** Basit marka simgesi (kalem ve rehber çizgisi); dekoratif. */
export function BrandMark() {
  return (
    <svg className="brand-mark" viewBox="0 0 32 32" aria-hidden="true" focusable="false">
      <rect width="32" height="32" rx="9" fill="var(--accent)" />
      <path d="M7 23c4-1 6-5 9-9s6-6 9-6" fill="none" stroke="var(--on-accent)" strokeWidth="2.4" strokeLinecap="round" strokeDasharray="0.1 4.2" />
      <path d="M19.5 9.5l3 3L13 22l-4 1 1-4z" fill="var(--on-accent)" />
    </svg>
  );
}
