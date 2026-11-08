import type { Metadata } from "next";
import { getImages } from "@/lib/server/content";
import { requireAdmin } from "@/lib/server/session";
import { AdminShell } from "../AdminShell";
import { ImagesEditor } from "./ImagesEditor";

export const metadata: Metadata = { title: "Görseller" };
export const dynamic = "force-dynamic";

export default async function ImagesPage() {
  const admin = await requireAdmin();
  const images = await getImages();
  return (
    <AdminShell name={admin.name} active="images">
      <h1>Görseller</h1>
      <p className="meta">
        Görseli bir yere (ör. Vercel Blob, Cloudinary, GitHub) yükleyip https adresini yapıştırın. Adres boşken sitede yer tutucu
        görünür. Alternatif metin ekran okuyucular ve arama motorları içindir.
      </p>
      <ImagesEditor images={images} />
    </AdminShell>
  );
}
