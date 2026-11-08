import Link from "next/link";
import { Contact } from "@/components/Contact";
import { APP_NAME_EN, APP_NAME_TR, DEVELOPER_NAME, LEGAL_EFFECTIVE, PLAY_SUBSCRIPTIONS_URL } from "@/lib/config";
import { href } from "@/lib/i18n";

// Kullanım şartları. Uygulamadaki metin (android/app/src/main/assets/legal/terms_*.txt) temel alınarak v40'ta
// abonelik, reklam ve web sitesi bölümleri, v42'de çizgi romanların dağıtımı ve ticari kullanımı bölümü eklendi.

export function TermsTr() {
  return (
    <article>
      <h1>Kullanım şartları</h1>
      <p className="meta">Yürürlük: {LEGAL_EFFECTIVE.tr}</p>
      <p className="lead">
        Bu şartlar, {DEVELOPER_NAME} tarafından geliştirilen {APP_NAME_TR} ({APP_NAME_EN}) mobil uygulamasının
        (&quot;Uygulama&quot;) kullanımını düzenler. Uygulamayı kullanarak ya da hesap açarak bu şartları okuduğunuzu ve
        kabul ettiğinizi beyan edersiniz.
      </p>

      <h2>1. Hizmetin kapsamı</h2>
      <ul>
        <li>Uygulama; resim, not, çizgi roman, çocuk çizimi ve 3B şekil oluşturma araçları sunan bir çizim uygulamasıdır.</li>
        <li>Çizim önerileri, şekil tanıma ve el yazısı tanıma cihazınızda çalışan yapay zekâ modelleriyle yapılır.</li>
        <li>Hazır şekil, nesne, karakter, arka plan ve şablon kataloğu uygulamayla gelir ve sunucudan güncellenebilir.</li>
        <li>Uygulamanın temel özellikleri hesap açmadan da kullanılabilir. Hesap; profil bilgilerinin, aboneliğin ve eşitlenen içeriğin sunucuda saklanması içindir.</li>
      </ul>

      <h2>2. Hesap</h2>
      <ul>
        <li>Hesap Google hesabınızla açılır. Doğum tarihinizi doğru girmeniz gerekir.</li>
        <li>13 yaşından küçükler hesabı yalnız veli ya da vasinin izniyle ve gözetiminde kullanabilir.</li>
        <li>Hesabınızın güvenliğinden ve hesabınızla yapılan işlemlerden siz sorumlusunuz.</li>
        <li>
          Hesabınızı istediğiniz zaman Ayarlar &gt; Hesap ayarları &gt; Hesabı sil adımıyla ya da{" "}
          <Link href={href("tr", "delete-account")}>hesap silme sayfasından</Link> silebilirsiniz.
        </li>
      </ul>

      <h2>3. Premium abonelik</h2>
      <ul>
        <li>Uygulama ücretsizdir. Premium; reklamsız kullanım ve ek özellikler sunan, otomatik yenilenen aylık bir aboneliktir.</li>
        <li>Satın alma, ödeme, yenileme ve iade Google Play üzerinden ve Google Play koşullarına göre yapılır. Fiyat, satın almadan önce Google Play&apos;de yerel para biriminizle gösterilir.</li>
        <li>Abonelik siz iptal edene kadar her dönem sonunda yenilenir. İptal ettiğinizde ödenmiş dönemin sonuna kadar Premium sürer.</li>
        <li>
          Aboneliği <a href={PLAY_SUBSCRIPTIONS_URL} rel="noopener noreferrer">Google Play &gt; Ödemeler ve abonelikler</a> bölümünden iptal edebilirsiniz.
          Uygulamayı kaldırmak ya da hesabı silmek aboneliği iptal etmez.
        </li>
        <li>Tüketici mevzuatından doğan haklarınız saklıdır.</li>
      </ul>

      <h2>4. Reklamlar</h2>
      <ul>
        <li>Ücretsiz planda Google AdMob reklamları gösterilebilir.</li>
        <li>13 yaşından küçük hesaplarda ve çocuk tuvallerinde reklam gösterilmez.</li>
      </ul>

      <h2>5. İçeriğiniz</h2>
      <ul>
        <li>Çizimleriniz, notlarınız, 3B şekilleriniz ve resimleriniz öntanımlı olarak yalnızca cihazınızda saklanır; yalnız eşitlemeyi kullandığınızda hesabınızla sunucuya gönderilir.</li>
        <li>Oluşturduğunuz içeriğin bütün hakları size aittir. Cihazınızın yedeğini almak sizin sorumluluğunuzdadır.</li>
        <li>Uygulamayı hukuka, kamu ahlakına ve başkalarının haklarına aykırı içerik üretmek için kullanmamayı kabul edersiniz.</li>
      </ul>

      <h2>6. Çizgi romanlarınızın dağıtımı ve ticari kullanımı</h2>
      <ul>
        <li>Uygulamayla oluşturduğunuz çizgi romanlar, resimler, notlar ve öteki çalışmalar (&quot;Eserleriniz&quot;) sizindir. Eserleriniz üzerindeki manevi ve mali haklar, 5846 sayılı Fikir ve Sanat Eserleri Kanunu (FSEK) çerçevesinde eser sahibi olarak size aittir. {DEVELOPER_NAME} bu haklar üzerinde hak, pay ya da ücret talep etmez; haklar {DEVELOPER_NAME}&apos;a devredilmez.</li>
        <li>Eserlerinizi dilediğiniz gibi kullanabilirsiniz: PDF, CBZ, PNG, JPEG ya da başka bir biçimde dijital veya basılı olarak çoğaltabilir; internette, sosyal medyada, dijital çizgi roman ve e-kitap platformlarında yayımlayabilir; satabilir, ücretsiz dağıtabilir; yayınevlerine ve baskı hizmetlerine verebilir; reklam, tanıtım ve ürünlerde ticari amaçla kullanabilirsiniz. Bunun için ayrıca izin almanız, ödeme yapmanız ya da {DEVELOPER_NAME}&apos;ın adını belirtmeniz gerekmez.</li>
        <li>Eserlerinizin haklarını (ör. yayın hakkını) yayınevlerine ya da başkalarına devredebilir veya lisanslayabilirsiniz. FSEK m. 52 gereği bu sözleşmeler yazılı yapılmalı ve devredilen haklar tek tek gösterilmelidir; bu sözleşmeler sizinle karşı taraf arasındadır.</li>
        <li><strong>Hazır içerik lisansı:</strong> Eserlerinizde kullandığınız hazır karakterler, nesneler, efektler, mekân arka planları, şekiller, desenler ve şablonlar için {DEVELOPER_NAME} size dünya çapında, süresiz, bedelsiz, münhasır olmayan (basit) ve geri alınamaz bir lisans verir. Bu lisans, hazır içeriği Eserinizin parçası olarak işleme (FSEK m. 21), çoğaltma (m. 22), yayma (m. 23), temsil (m. 24) ve umuma iletim (m. 25) haklarını, ticari kullanım dahil kapsar. Eserinizi devrettiğiniz ya da lisansladığınız yayıncı, dağıtıcı ve baskı hizmetleri de Eseriniz içindeki hazır içeriği aynı kapsamda kullanabilir.</li>
        <li>Bu lisans; Eseriniz oluşturulduktan sonra Uygulamayı kaldırmanız, Premium aboneliğinizin bitmesi, hesabınızı silmeniz ya da hazır içeriğin sonraki bir sürümde katalogdan kaldırılması durumunda da sürer. Premium tema paketlerinin içeriği aboneliğiniz sürerken Eserlerinize eklenebilir; eklendiği Eserdeki lisans abonelik bitince de sürer.</li>
        <li>Bu bölüm {DEVELOPER_NAME}&apos;ın yazılı lisans beyanıdır. Bir yayınevi ya da dağıtıcı için imzalı bir lisans belgesi gerekirse aşağıdaki iletişim adresinden ücretsiz isteyebilirsiniz.</li>
        <li><strong>Sınırlar:</strong> Hazır içerik yalnız sizin yaratıcı katkınızı taşıyan bir Eserin parçası olarak kullanılabilir. Hazır içeriği tek başına ya da başlıca değeri hazır içerik olacak biçimde (ör. çıkartma paketi, clip-art, varlık ya da şablon koleksiyonu, başka uygulamalara yüklenecek bir kitaplık, değiştirilmemiş tek bir karakterin ya da nesnenin basıldığı tişört, kupa gibi ürünler) yeniden dağıtamaz, satamaz ya da alt lisanslayamazsınız.</li>
        <li>Hazır içeriği marka, logo ya da kurumsal kimlik olarak kullanamaz ve tescil ettiremezsiniz. Hazır içerik münhasır değildir; aynı içerik başka kullanıcıların eserlerinde de yer alabilir. Hazır içerik üzerinde başkalarına karşı münhasır hak iddia edemezsiniz; Eserinizin size ait özgün kısımları üzerindeki haklarınız ise saklıdır.</li>
        <li><strong>Sizin sorumluluğunuz:</strong> Eserlerinize eklediğiniz kendi resimleriniz, fotoğraflar, yazı tipleri, metinler ve başkasına ait karakterler için gereken izinleri almak size aittir. Tanınmış karakterleri, markaları ya da başkalarının eserlerini izinsiz kullanan; gerçek kişileri rızaları olmadan teşhir eden ya da kişisel verilerini hukuka aykırı biçimde açıklayan (6698 sayılı KVKK); hakaret, nefret söylemi, şiddete çağrı ya da çocukların istismarını içeren ya da başka biçimde hukuka aykırı Eserler oluşturamaz ve dağıtamazsınız. Eserlerinizin içeriğinden ve dağıtımından doğan sorumluluk size aittir.</li>
        <li><strong>Basılı yayın:</strong> Eserinizi basılı kitap ya da dergi olarak dağıtacaksanız ISBN/ISSN, bandrol (FSEK m. 81) ve derleme (6279 sayılı Çoğaltılmış Fikir ve Sanat Eserlerini Derleme Kanunu) gibi yükümlülükler yayıncıya ya da size aittir.</li>
        <li><strong>Renk ve baskı:</strong> Uygulamanın CMYK dönüşümü genel amaçlı bir baskı profiline göre yapılan yaklaşık bir dönüşümdür. Baskıdan önce matbaanızın istediği renk profilini, taşma payını ve çözünürlüğü kontrol etmeniz ve prova almanız önerilir.</li>
        <li><strong>Yapay zekâ yardımı:</strong> Çizim önerileri ve tanıma özellikleri, çizimi sizin yönettiğiniz yardımcı araçlardır. Telif koruması Türk hukukunda ve birçok ülkede insanın yaratıcı katkısına (hususiyet) bağlıdır; Eserinizin bir ülkede ne ölçüde korunacağı o ülkenin hukukuna göre belirlenir.</li>
        <li><strong>Atıf ve tanıtım:</strong> Atıf zorunlu değildir; isterseniz &quot;{APP_NAME_TR} ile hazırlandı&quot; ibaresini kullanabilirsiniz. {DEVELOPER_NAME}&apos;ın adını ya da logosunu, Eserinizin {DEVELOPER_NAME} tarafından hazırlandığı ya da onaylandığı izlenimini verecek biçimde kullanamazsınız. {DEVELOPER_NAME}, Eserlerinizi açık izniniz olmadan tanıtımda kullanmaz.</li>
      </ul>

      <h2>7. Hazır içerik ve lisanslar</h2>
      <ul>
        <li>Uygulamadaki hazır şekiller, nesneler, karakterler, efektler, mekân arka planları ve şablonlar Uygulama için özgün olarak hazırlanmıştır. Tanınmış karakterler, markalar ya da başkasına ait çizimler kullanılmaz.</li>
        <li>Hazır içeriğin fikrî hakları {DEVELOPER_NAME}&apos;a aittir. Kendi Eserlerinizde, kişisel ve ticari çalışmalarınızda 6. bölümdeki lisansla serbestçe kullanılabilir.</li>
        <li>Yazı tipleri kendi açık kaynak lisanslarıyla gelir (bkz. uygulama içindeki lisans dosyası).</li>
      </ul>

      <h2>8. Kullanım kuralları</h2>
      <p>Aşağıdakileri yapmamayı kabul edersiniz:</p>
      <ul>
        <li>Uygulamanın kaynak kodunu tersine mühendislikle elde etmeye, sunucuya yetkisiz erişmeye ya da hizmeti aksatmaya çalışmak,</li>
        <li>Başkasının hesabını kullanmak ya da kimliğe bürünmek,</li>
        <li>Uygulamayı zararlı yazılım yaymak ya da yasa dışı amaçlarla kullanmak,</li>
        <li>Hazır içeriği Uygulamadan toplu olarak çıkarıp ayrı bir koleksiyon olarak paylaşmak.</li>
      </ul>

      <h2>9. Sorumluluğun sınırlandırılması</h2>
      <ul>
        <li>Uygulama &quot;olduğu gibi&quot; sunulur. Kesintisiz ve hatasız çalışacağı garanti edilmez.</li>
        <li>Yasaların izin verdiği ölçüde {DEVELOPER_NAME}; cihazınızdaki veri kaybından ya da Uygulamanın kullanımından doğan dolaylı zararlardan sorumlu tutulamaz.</li>
      </ul>

      <h2>10. Gizlilik</h2>
      <p>
        Kişisel verilerinizin nasıl işlendiği <Link href={href("tr", "privacy")}>gizlilik politikasında</Link> açıklanır.
      </p>

      <h2>11. Değişiklikler ve fesih</h2>
      <ul>
        <li>Bu şartlar güncellenebilir. Önemli değişikliklerde Uygulama içinde bilgilendirilir, gerektiğinde yeniden onayınız istenir. Değişiklikler, önceden oluşturduğunuz Eserler için verilen lisansı daraltmaz.</li>
        <li>Şartlara aykırı kullanımda hesap askıya alınabilir ya da silinebilir.</li>
      </ul>

      <h2>12. Uygulanacak hukuk</h2>
      <p>
        Bu şartlar Türkiye Cumhuriyeti hukukuna tabidir. Tüketici işlemlerinde Tüketici Hakem Heyetleri ve Tüketici
        Mahkemeleri yetkilidir.
      </p>

      <h2>13. İletişim</h2>
      <p>
        Sorularınız için: <Contact locale="tr" />.
      </p>
    </article>
  );
}

export function TermsEn() {
  return (
    <article>
      <h1>Terms of use</h1>
      <p className="meta">Effective: {LEGAL_EFFECTIVE.en}</p>
      <p className="lead">
        These terms govern the use of the {APP_NAME_EN} ({APP_NAME_TR}) mobile app (the &quot;App&quot;) developed by{" "}
        {DEVELOPER_NAME}. By using the App or creating an account you confirm that you have read and accept these terms.
      </p>

      <h2>1. The service</h2>
      <ul>
        <li>The App is a drawing app with tools for pictures, notes, comics, kids&apos; drawings and 3D shapes.</li>
        <li>Drawing suggestions, shape recognition and handwriting recognition run on AI models on your device.</li>
        <li>A catalog of ready-made shapes, objects, characters, backgrounds and templates ships with the App and may be updated from the server.</li>
        <li>The core features work without an account. An account stores your profile, subscription and synced content on the server.</li>
      </ul>

      <h2>2. Account</h2>
      <ul>
        <li>Accounts are created with your Google account. You must enter your correct birth date.</li>
        <li>Children under 13 may use an account only with the permission and under the supervision of a parent or guardian.</li>
        <li>You are responsible for the security of your account and for actions taken with it.</li>
        <li>
          You can delete your account at any time under Settings &gt; Account settings &gt; Delete account or on the{" "}
          <Link href={href("en", "delete-account")}>account deletion page</Link>.
        </li>
      </ul>

      <h2>3. Premium subscription</h2>
      <ul>
        <li>The App is free. Premium is an auto-renewing monthly subscription that removes ads and adds extra features.</li>
        <li>Purchases, payments, renewals and refunds are handled by Google Play under Google Play&apos;s terms. The price is shown in your local currency in Google Play before you buy.</li>
        <li>The subscription renews at the end of each period until you cancel. If you cancel, Premium lasts until the end of the paid period.</li>
        <li>
          Cancel in <a href={PLAY_SUBSCRIPTIONS_URL} rel="noopener noreferrer">Google Play &gt; Payments &amp; subscriptions</a>.
          Uninstalling the App or deleting your account does not cancel the subscription.
        </li>
        <li>Your statutory consumer rights are not affected.</li>
      </ul>

      <h2>4. Ads</h2>
      <ul>
        <li>On the Free plan the App may show Google AdMob ads.</li>
        <li>No ads are shown to accounts under 13 or on kids canvases.</li>
      </ul>

      <h2>5. Your content</h2>
      <ul>
        <li>By default your drawings, notes, 3D shapes and images are stored only on your device; they are sent to the server with your account only when you use sync.</li>
        <li>You own all rights to the content you create. Backing up your device is your responsibility.</li>
        <li>You agree not to use the App to create content that is unlawful, offensive or infringes the rights of others.</li>
      </ul>

      <h2>6. Distributing and commercially using your comics</h2>
      <ul>
        <li>The comics, pictures, notes and other works you create with the App (&quot;Your Works&quot;) are yours. As the author, you hold the moral and economic rights in Your Works under Turkish Law No. 5846 on Intellectual and Artistic Works (FSEK). {DEVELOPER_NAME} claims no right, share or fee in them, and no rights are transferred to {DEVELOPER_NAME}.</li>
        <li>You may use Your Works as you wish: reproduce them digitally or in print as PDF, CBZ, PNG, JPEG or any other format; publish them online, on social media and on digital comic and e-book platforms; sell them or distribute them for free; give them to publishers and print services; and use them commercially in advertising, promotion and products. You do not need separate permission, to pay, or to credit {DEVELOPER_NAME}.</li>
        <li>You may assign or license the rights in Your Works (for example publishing rights) to publishers or others. Under FSEK art. 52 such contracts must be in writing and list each right separately; they are between you and the other party.</li>
        <li><strong>Ready-made content license:</strong> for the ready-made characters, objects, effects, location backgrounds, shapes, patterns and templates you use in Your Works, {DEVELOPER_NAME} grants you a worldwide, perpetual, royalty-free, non-exclusive and irrevocable license. It covers adaptation (FSEK art. 21), reproduction (art. 22), distribution (art. 23), performance (art. 24) and communication to the public (art. 25) of the ready-made content as part of Your Work, including commercial use. Publishers, distributors and print services to whom you assign or license Your Work may use the ready-made content in it to the same extent.</li>
        <li>This license continues after Your Work is created even if you uninstall the App, your Premium subscription ends, you delete your account, or the ready-made content is removed from the catalog in a later version. Content from Premium theme packs can be added to Your Works while your subscription is active; the license for Works it was added to continues after the subscription ends.</li>
        <li>This section is {DEVELOPER_NAME}&apos;s written license statement. If a publisher or distributor needs a signed license document, you can request one free of charge at the contact address below.</li>
        <li><strong>Limits:</strong> ready-made content may only be used as part of a Work that carries your own creative contribution. You may not redistribute, sell or sublicense the ready-made content on its own or in a form whose main value is the ready-made content (for example sticker packs, clip art, asset or template collections, libraries for other apps, or T-shirts, mugs and similar products showing a single unmodified character or object).</li>
        <li>You may not use or register ready-made content as a trademark, logo or corporate identity. Ready-made content is non-exclusive and may also appear in other users&apos; works. You may not claim exclusive rights in the ready-made content against others; your rights in the original parts of Your Work remain yours.</li>
        <li><strong>Your responsibility:</strong> obtaining any permissions needed for your own images, photos, fonts, texts and third-party characters you add to Your Works is up to you. You may not create or distribute Works that use well-known characters, brands or others&apos; works without permission; expose real people without their consent or unlawfully disclose their personal data (Law No. 6698, KVKK); contain insults, hate speech, incitement to violence or child abuse; or are otherwise unlawful. You are responsible for the content and distribution of Your Works.</li>
        <li><strong>Print publishing:</strong> if you distribute Your Work as a printed book or magazine, obligations such as ISBN/ISSN, banderole (FSEK art. 81) and legal deposit (Law No. 6279) lie with the publisher or with you.</li>
        <li><strong>Color and print:</strong> the App&apos;s CMYK conversion is an approximate conversion based on a general-purpose print profile. Before printing, check the color profile, bleed and resolution your printer requires and order a proof.</li>
        <li><strong>AI assistance:</strong> drawing suggestions and recognition features are assistive tools you control. In Turkish law and in many countries copyright protection depends on human creative contribution; the extent to which Your Work is protected in a country is determined by that country&apos;s law.</li>
        <li><strong>Credit and promotion:</strong> credit is not required; if you like, you can write &quot;Made with {APP_NAME_EN}&quot;. You may not use {DEVELOPER_NAME}&apos;s name or logo in a way that suggests {DEVELOPER_NAME} made or endorsed Your Work. {DEVELOPER_NAME} does not use Your Works for promotion without your explicit permission.</li>
      </ul>

      <h2>7. Ready-made content and licenses</h2>
      <ul>
        <li>The ready-made shapes, objects, characters, effects, location backgrounds and templates were created originally for the App. No well-known characters, brands or third-party drawings are used.</li>
        <li>The intellectual property rights in the ready-made content belong to {DEVELOPER_NAME}. You may use it freely in your own Works, for personal and commercial work, under the license in section 6.</li>
        <li>Fonts come with their own open-source licenses (see the license file in the App).</li>
      </ul>

      <h2>8. Rules of use</h2>
      <p>You agree not to:</p>
      <ul>
        <li>reverse engineer the App, access the server without authorization or try to disrupt the service,</li>
        <li>use someone else&apos;s account or impersonate anyone,</li>
        <li>use the App to spread malware or for illegal purposes,</li>
        <li>extract the ready-made content from the App in bulk and share it as a separate collection.</li>
      </ul>

      <h2>9. Limitation of liability</h2>
      <ul>
        <li>The App is provided &quot;as is&quot;. Uninterrupted or error-free operation is not guaranteed.</li>
        <li>To the extent permitted by law, {DEVELOPER_NAME} is not liable for data loss on your device or for indirect damages arising from use of the App.</li>
      </ul>

      <h2>10. Privacy</h2>
      <p>
        How your personal data is processed is explained in the <Link href={href("en", "privacy")}>privacy policy</Link>.
      </p>

      <h2>11. Changes and termination</h2>
      <ul>
        <li>These terms may be updated. For significant changes you will be informed in the App and asked to accept again where needed. Changes do not narrow the license granted for Works you have already created.</li>
        <li>Accounts used in breach of these terms may be suspended or deleted.</li>
      </ul>

      <h2>12. Governing law</h2>
      <p>
        These terms are governed by the laws of the Republic of Türkiye. For consumer transactions, Consumer Arbitration
        Committees and Consumer Courts have jurisdiction; mandatory consumer protection rules of your country of residence
        are not affected.
      </p>

      <h2>13. Contact</h2>
      <p>
        Questions: <Contact locale="en" />.
      </p>
    </article>
  );
}
