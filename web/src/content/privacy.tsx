import Link from "next/link";
import { Contact } from "@/components/Contact";
import { APP_NAME_EN, APP_NAME_TR, DEVELOPER_NAME, LEGAL_EFFECTIVE, PACKAGE_NAME, PLAY_SUBSCRIPTIONS_URL } from "@/lib/config";
import { href } from "@/lib/i18n";

// Gizlilik politikası. Uygulamadaki KVKK Aydınlatma Metni (android/app/src/main/assets/legal/kvkk_*.txt) temel
// alınarak v40'ta abonelik, reklam, hata raporu, çocuk modu ve web formlarını kapsayacak şekilde genişletildi.

export function PrivacyTr() {
  return (
    <article>
      <h1>Gizlilik politikası</h1>
      <p className="meta">
        {APP_NAME_TR} ({APP_NAME_EN}) · Android paket adı <code>{PACKAGE_NAME}</code> · Yürürlük: {LEGAL_EFFECTIVE.tr}
      </p>
      <p className="lead">
        Bu politika, {DEVELOPER_NAME} tarafından geliştirilen {APP_NAME_TR} uygulamasının ve bu web sitesinin hangi
        kişisel verileri neden işlediğini, kimlerle paylaştığını, ne kadar sakladığını ve haklarınızı açıklar. 6698 sayılı
        Kişisel Verilerin Korunması Kanunu (KVKK) 10. maddesi kapsamındaki aydınlatma yükümlülüğünü de karşılar.
      </p>

      <nav className="toc" aria-label="İçindekiler">
        <ol>
          <li><a href="#sorumlu">Veri sorumlusu ve iletişim</a></li>
          <li><a href="#veriler">Toplanan veriler</a></li>
          <li><a href="#amaclar">İşleme amaçları ve hukuki sebepler</a></li>
          <li><a href="#cocuklar">Çocuklar ve çocuk modu</a></li>
          <li><a href="#paylasim">Üçüncü taraflar ve aktarım</a></li>
          <li><a href="#saklama">Saklama süreleri</a></li>
          <li><a href="#silme">Hesap ve veri silme</a></li>
          <li><a href="#guvenlik">Güvenlik</a></li>
          <li><a href="#haklar">Haklarınız</a></li>
          <li><a href="#degisiklik">Değişiklikler</a></li>
        </ol>
      </nav>

      <h2 id="sorumlu">1. Veri sorumlusu ve iletişim</h2>
      <p>
        Veri sorumlusu uygulamanın geliştiricisi <strong>{DEVELOPER_NAME}</strong>&apos;tır. Gizlilikle ilgili her soru ve
        talep için: <Contact locale="tr" />.
      </p>

      <h2 id="veriler">2. Toplanan veriler</h2>
      <p>
        Uygulamanın temel özellikleri hesap açmadan kullanılabilir. Çizimleriniz, notlarınız, 3B şekilleriniz,
        resimleriniz ve cihazdaki yapay zekânın öğrendikleri öntanımlı olarak yalnızca cihazınızda kalır.
      </p>
      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th scope="col">Veri</th>
              <th scope="col">Ne zaman ve nereden</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td>
                <strong>Hesap bilgileri:</strong> Google hesabınızın e-posta adresi ve doğrulanma durumu, adınız,
                soyadınız, görünen adınız, profil fotoğrafının bağlantısı, Google/Firebase hesap kimliği, Google
                hesabının açılış tarihi; girdiğiniz doğum tarihi; Kullanım Şartları ve KVKK metninin onay zamanı ve sürümü.
              </td>
              <td>Google ile giriş yapıp hesap açtığınızda (Firebase Authentication) ve hesap ayarlarında.</td>
            </tr>
            <tr>
              <td>
                <strong>Eşitlenen içerik:</strong> hesabınızla eşitlediğiniz çizimler ve ilgili bilgiler (ör. ad,
                tarih), profil ayarları ve çocuk modundaki &quot;Çizim Görevi&quot; ilerlemesi.
              </td>
              <td>
                Oturum açıkken eşitleme yapıldığında. Eşitlemediğiniz çizimler sunucuya gönderilmez.
              </td>
            </tr>
            <tr>
              <td>
                <strong>Satın alma durumu:</strong> abonelik planı (Ücretsiz/Premium), durumu, başlangıç ve bitiş
                tarihleri, Google Play sipariş numarası ve satın alma jetonu. Kart ya da ödeme bilgilerinizi görmeyiz.
              </td>
              <td>Premium satın aldığınızda; durum Google Play Faturalandırma&apos;dan doğrulanır.</td>
            </tr>
            <tr>
              <td>
                <strong>Reklam verileri:</strong> Google AdMob, reklam göstermek ve ölçmek için reklam kimliğini
                (Android advertising ID), IP adresini ve cihaz bilgilerini işleyebilir. Avrupa Ekonomik Alanı ve
                Birleşik Krallık&apos;ta kişiselleştirilmiş reklam yalnız onayınızla gösterilir.
              </td>
              <td>Ücretsiz planda, 13 yaş ve üstü hesaplarda reklam gösterilirken.</td>
            </tr>
            <tr>
              <td>
                <strong>Hata raporları:</strong> hata iletisi ve yığın dökümü, ilgili ekran, uygulama ve Android sürümü,
                cihaz modeli, uygulama dili, kuruluma özgü rastgele bir kimlik, son birkaç işlem ve isterseniz
                yazdığınız not; oturum açıksa hesap kimliğiniz.
              </td>
              <td>Uygulama bir hata yakaladığında ya da çöktüğünde ve &quot;Sorun bildir&quot; ile. Öntanımlı olarak her seferinde onayınız sorulur; Ayarlar &gt; Hata raporları bölümünden &quot;Sormadan gönder&quot; ya da &quot;Gönderme&quot; seçebilirsiniz.</td>
            </tr>
            <tr>
              <td>
                <strong>Geri bildirim ve destek:</strong> yazdığınız mesaj ve e-posta adresiniz; uygulama içi geri
                bildirimde cihaz bilgileri (cihaz modeli, Android sürümü, uygulama sürümü, dil). Bu sitedeki formlarda
                ayrıca tarayıcı bilgisi (User-Agent) ve IP adresinin geri döndürülemez özeti saklanır; ham IP adresi
                saklanmaz.
              </td>
              <td>Uygulamadan geri bildirim gönderdiğinizde ya da bu sitedeki destek veya silme formunu kullandığınızda.</td>
            </tr>
          </tbody>
        </table>
      </div>
      <p>
        Bu web sitesi çerez, analiz ya da izleme kodu kullanmaz. Barındırma sağlayıcısı (Vercel) güvenlik ve işletim için
        standart erişim kayıtları tutabilir.
      </p>

      <h2 id="amaclar">3. İşleme amaçları ve hukuki sebepler</h2>
      <ul>
        <li>Hesabınızı oluşturmak, kimliğinizi doğrulamak, oturumunuzu yönetmek ve verilerinizi cihazlar arasında eşitlemek (sözleşmenin kurulması ve ifası; KVKK 5/2-c, GDPR 6/1-b).</li>
        <li>Yaşa uygun kullanım: doğum tarihine göre çocuk korumalarını uygulamak (hukuki yükümlülük ve meşru menfaat; KVKK 5/2-ç ve 5/2-f).</li>
        <li>Premium aboneliği doğrulamak ve ilgili hakları vermek; satın alma kayıtlarını mevzuat gereği saklamak (sözleşmenin ifası ve hukuki yükümlülük).</li>
        <li>Ücretsiz planı reklamlarla sunmak (meşru menfaat; gereken bölgelerde açık rıza).</li>
        <li>Hataları gidermek ve uygulamayı iyileştirmek (onayınızla gönderilen raporlar; meşru menfaat).</li>
        <li>Destek taleplerini ve silme taleplerini yanıtlamak; kötüye kullanımı önlemek (meşru menfaat ve hukuki yükümlülük).</li>
        <li>Onay kayıtlarını olası uyuşmazlıklarda delil olarak saklamak (bir hakkın tesisi, kullanılması veya korunması; KVKK 5/2-e).</li>
      </ul>
      <p>Verileriniz satılmaz ve yukarıdaki amaçlar dışında kullanılmaz.</p>

      <h2 id="cocuklar">4. Çocuklar ve çocuk modu</h2>
      <ul>
        <li>Uygulamada 13 yaşından küçükler için sade bir <strong>çocuk modu</strong> vardır.</li>
        <li>13 yaşından küçükler hesabı yalnız veli ya da vasinin izniyle ve gözetiminde kullanabilir.</li>
        <li>
          Doğum tarihine göre 13 yaşından küçük hesaplarda ve çocuk tuvallerinde <strong>reklam gösterilmez</strong>.
          Reklam istekleri Google&apos;a çocuklara yönelik içerik olarak işaretlenerek gönderilir.
        </li>
        <li>Çocuklardan hizmet için gerekenden fazla veri istemeyiz.</li>
        <li>
          Veli ya da vasiler çocuklarına ait hesabın silinmesini <Link href={href("tr", "delete-account")}>hesap silme sayfasından</Link> isteyebilir.
        </li>
      </ul>

      <h2 id="paylasim">5. Üçüncü taraflar ve aktarım</h2>
      <ul>
        <li><strong>Google (Firebase Authentication, Google ile giriş):</strong> kimlik doğrulama.</li>
        <li><strong>Google AdMob ve Google&apos;ın kullanıcı mesajlaşma platformu (UMP):</strong> reklam ve reklam onayı. Ayrıntılar: <a href="https://policies.google.com/technologies/partner-sites" rel="noopener noreferrer">Google&apos;ın iş ortaklarının uygulamalarındaki veri kullanımı</a>.</li>
        <li><strong>Google Play Faturalandırma:</strong> abonelik satın alma ve doğrulama. Ödemeyi Google işler.</li>
        <li><strong>Uygulamanın sunucusu:</strong> profil, eşitlenen içerik, abonelik durumu, hata raporları ve destek kayıtları kendi sunucumuzdaki veritabanında saklanır.</li>
        <li><strong>Vercel:</strong> bu web sitesinin barındırılması.</li>
        <li><strong>Slack:</strong> yeni destek ve silme taleplerinin ekibe bildirilmesi; yalnız kısmen gizlenmiş e-posta (ör. a***@gmail.com) ve konu gönderilir.</li>
        <li>Yasal olarak yetkili kamu kurum ve kuruluşlarının talepleri saklıdır.</li>
      </ul>
      <p>
        Bu hizmet sağlayıcıların sunucuları yurt dışında olabilir; aktarım KVKK 9. maddesine ve uygulanabilir veri koruma
        mevzuatına uygun olarak yapılır.
      </p>

      <h2 id="saklama">6. Saklama süreleri</h2>
      <ul>
        <li><strong>Hesap bilgileri ve eşitlenen içerik:</strong> hesabınız açık kaldığı sürece; hesabı sildiğinizde hemen silinir.</li>
        <li><strong>Hata raporları:</strong> hesabınıza bağlı olanlar hesapla birlikte silinir; diğerleri en çok 2 yıl saklanır.</li>
        <li><strong>Satın alma ve abonelik kayıtları:</strong> vergi ve ticaret mevzuatının öngördüğü süre boyunca (10 yıla kadar); hesap silinince hesabınızla bağı koparılır.</li>
        <li><strong>Google Play abonelik bildirimleri:</strong> 180 gün.</li>
        <li><strong>Destek mesajları:</strong> 2 yıl. <strong>Silme talepleri:</strong> işlendiğinin kanıtı olarak 1 yıl.</li>
        <li><strong>Cihazınızdaki veriler:</strong> uygulamayı kaldırana ya da uygulama verilerini temizleyene kadar cihazınızda kalır.</li>
      </ul>

      <h2 id="silme">7. Hesap ve veri silme</h2>
      <p>
        Hesabınızı uygulamada <strong>Ayarlar &gt; Hesap ayarları &gt; Hesabı sil</strong> adımıyla ya da{" "}
        <Link href={href("tr", "delete-account")}>hesap ve veri silme sayfasından</Link> silebilirsiniz. Hangi verilerin
        silinip hangilerinin saklandığı o sayfada ayrıntılı olarak yazar. Hesap silmek Google Play aboneliğini iptal etmez;
        aboneliği <a href={PLAY_SUBSCRIPTIONS_URL} rel="noopener noreferrer">Google Play &gt; Ödemeler ve abonelikler</a> bölümünden iptal edin.
      </p>

      <h2 id="guvenlik">8. Güvenlik</h2>
      <p>
        Uygulama ile sunucu arasındaki bütün trafik HTTPS ile şifrelenir. Sunucu yalnız Google&apos;ın imzaladığı geçerli
        kimlik jetonlarını kabul eder; veritabanına erişim yetkili kişilerle sınırlıdır. Web formlarında ham IP adresi
        saklanmaz ve istekler kötüye kullanıma karşı sınırlandırılır.
      </p>

      <h2 id="haklar">9. Haklarınız</h2>
      <p>KVKK 11. madde ve uygulanabilir diğer mevzuat (ör. GDPR) kapsamında şu haklara sahipsiniz:</p>
      <ul>
        <li>Kişisel verilerinizin işlenip işlenmediğini öğrenme ve bilgi talep etme,</li>
        <li>İşlenme amacını ve amaca uygun kullanılıp kullanılmadığını öğrenme,</li>
        <li>Yurt içinde ya da yurt dışında aktarıldığı üçüncü kişileri bilme,</li>
        <li>Eksik ya da yanlış işlenmişse düzeltilmesini isteme,</li>
        <li>Silinmesini ya da yok edilmesini isteme ve bunun aktarılan üçüncü kişilere bildirilmesini isteme,</li>
        <li>Verilerinizin bir kopyasını isteme ve işlemeye itiraz etme; onaya dayalı işlemede onayı geri alma,</li>
        <li>Otomatik sistemlerle analiz sonucu aleyhinize bir sonuç çıkmasına itiraz etme,</li>
        <li>Kanuna aykırı işleme nedeniyle zarara uğrarsanız zararın giderilmesini talep etme,</li>
        <li>Kişisel Verileri Koruma Kurumu&apos;na ya da bulunduğunuz ülkenin veri koruma otoritesine şikâyette bulunma.</li>
      </ul>
      <p>
        Taleplerinizi <Contact locale="tr" /> üzerinden iletebilirsiniz. Başvurular en geç 30 gün içinde ücretsiz
        sonuçlandırılır. Reklam kimliğinizi Android ayarlarından sıfırlayabilir ya da silebilirsiniz.
      </p>

      <h2 id="degisiklik">10. Değişiklikler</h2>
      <p>
        Bu politika güncellenebilir. Önemli değişikliklerde uygulama içinde bilgilendirilir, gerektiğinde onayınız
        yeniden istenir. Güncel sürüm her zaman bu sayfadadır.
      </p>
    </article>
  );
}

export function PrivacyEn() {
  return (
    <article>
      <h1>Privacy policy</h1>
      <p className="meta">
        {APP_NAME_EN} ({APP_NAME_TR}) · Android package <code>{PACKAGE_NAME}</code> · Effective: {LEGAL_EFFECTIVE.en}
      </p>
      <p className="lead">
        This policy explains which personal data the {APP_NAME_EN} app, developed by {DEVELOPER_NAME}, and this website
        process, why, who we share it with, how long we keep it and what your rights are. It also serves as the privacy
        notice required by the Turkish Personal Data Protection Law No. 6698 (KVKK).
      </p>

      <nav className="toc" aria-label="Contents">
        <ol>
          <li><a href="#controller">Data controller and contact</a></li>
          <li><a href="#data">Data we collect</a></li>
          <li><a href="#purposes">Purposes and legal bases</a></li>
          <li><a href="#children">Children and kids mode</a></li>
          <li><a href="#sharing">Third parties and transfers</a></li>
          <li><a href="#retention">Retention</a></li>
          <li><a href="#deletion">Account and data deletion</a></li>
          <li><a href="#security">Security</a></li>
          <li><a href="#rights">Your rights</a></li>
          <li><a href="#changes">Changes</a></li>
        </ol>
      </nav>

      <h2 id="controller">1. Data controller and contact</h2>
      <p>
        The data controller is the app&apos;s developer, <strong>{DEVELOPER_NAME}</strong>. For any privacy question or
        request: <Contact locale="en" />.
      </p>

      <h2 id="data">2. Data we collect</h2>
      <p>
        The app&apos;s core features work without an account. By default your drawings, notes, 3D shapes, images and
        what the on-device AI learns stay only on your device.
      </p>
      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th scope="col">Data</th>
              <th scope="col">When and from where</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td>
                <strong>Account data:</strong> your Google account email address and whether it is verified, first
                name, last name, display name, profile photo link, Google/Firebase account ID, the Google account
                creation date; the birth date you enter; the time and version of your acceptance of the Terms of Use
                and the privacy notice.
              </td>
              <td>When you sign in with Google and create an account (Firebase Authentication), and in account settings.</td>
            </tr>
            <tr>
              <td>
                <strong>Synced content:</strong> drawings you sync with your account and related details (such as name
                and date), profile settings, and &quot;Drawing Challenge&quot; progress in kids mode.
              </td>
              <td>When syncing while signed in. Drawings you do not sync are never sent to the server.</td>
            </tr>
            <tr>
              <td>
                <strong>Purchase status:</strong> subscription plan (Free/Premium), status, start and end dates, Google
                Play order ID and purchase token. We never see your card or payment details.
              </td>
              <td>When you buy Premium; the status is verified with Google Play Billing.</td>
            </tr>
            <tr>
              <td>
                <strong>Advertising data:</strong> Google AdMob may process the advertising ID (Android advertising ID),
                IP address and device information to serve and measure ads. In the EEA and the UK, personalized ads are
                shown only with your consent.
              </td>
              <td>On the Free plan, when ads are shown to accounts aged 13 or over.</td>
            </tr>
            <tr>
              <td>
                <strong>Error reports:</strong> the error message and stack trace, the screen involved, app and Android
                versions, device model, app language, a random installation ID, the last few actions and an optional note
                you write; your account ID if you are signed in.
              </td>
              <td>When the app catches an error or crashes, and when you use &quot;Report a problem&quot;. By default you are asked every time; in Settings &gt; Error reports you can choose &quot;Send automatically&quot; or &quot;Don&apos;t send&quot;.</td>
            </tr>
            <tr>
              <td>
                <strong>Feedback and support:</strong> your message and email address; for in-app feedback, device
                information (device model, Android version, app version, language). The forms on this website also store
                your browser information (User-Agent) and an irreversible hash of your IP address; the raw IP address is
                not stored.
              </td>
              <td>When you send feedback from the app or use the support or deletion form on this website.</td>
            </tr>
          </tbody>
        </table>
      </div>
      <p>
        This website uses no cookies, analytics or tracking scripts. The hosting provider (Vercel) may keep standard
        access logs for security and operations.
      </p>

      <h2 id="purposes">3. Purposes and legal bases</h2>
      <ul>
        <li>Creating your account, verifying your identity, managing your session and syncing your data across devices (performance of a contract; KVKK 5/2-c, GDPR 6(1)(b)).</li>
        <li>Age-appropriate use: applying child protections based on birth date (legal obligation and legitimate interest).</li>
        <li>Verifying Premium subscriptions and granting their benefits; keeping purchase records as required by law (contract and legal obligation).</li>
        <li>Offering the Free plan with ads (legitimate interest; consent where required).</li>
        <li>Fixing errors and improving the app (reports you choose to send; legitimate interest).</li>
        <li>Answering support and deletion requests and preventing abuse (legitimate interest and legal obligation).</li>
        <li>Keeping consent records as evidence in possible disputes (establishment, exercise or defense of legal claims).</li>
      </ul>
      <p>We do not sell your data or use it for any purpose other than those above.</p>

      <h2 id="children">4. Children and kids mode</h2>
      <ul>
        <li>The app has a simple <strong>kids mode</strong> for children under 13.</li>
        <li>Children under 13 may use an account only with the permission and under the supervision of a parent or guardian.</li>
        <li>
          <strong>No ads are shown</strong> to accounts under 13 (based on birth date) or on kids canvases. Ad requests
          are sent to Google tagged as child-directed.
        </li>
        <li>We do not ask children for more data than the service needs.</li>
        <li>
          Parents or guardians can request deletion of their child&apos;s account on the{" "}
          <Link href={href("en", "delete-account")}>account deletion page</Link>.
        </li>
      </ul>

      <h2 id="sharing">5. Third parties and transfers</h2>
      <ul>
        <li><strong>Google (Firebase Authentication, Sign in with Google):</strong> authentication.</li>
        <li><strong>Google AdMob and Google&apos;s User Messaging Platform (UMP):</strong> ads and ad consent. Details: <a href="https://policies.google.com/technologies/partner-sites" rel="noopener noreferrer">How Google uses information from sites or apps that use its services</a>.</li>
        <li><strong>Google Play Billing:</strong> subscription purchases and verification. Google processes the payment.</li>
        <li><strong>The app&apos;s server:</strong> profile, synced content, subscription status, error reports and support records are stored in our own server&apos;s database.</li>
        <li><strong>Vercel:</strong> hosting of this website.</li>
        <li><strong>Slack:</strong> notifying the team about new support and deletion requests; only a partially masked email (e.g. a***@gmail.com) and the subject are sent.</li>
        <li>Requests from legally authorized public authorities are reserved.</li>
      </ul>
      <p>
        These providers may operate servers outside your country; transfers are made in accordance with KVKK article 9
        and applicable data protection law.
      </p>

      <h2 id="retention">6. Retention</h2>
      <ul>
        <li><strong>Account data and synced content:</strong> while your account exists; deleted immediately when you delete the account.</li>
        <li><strong>Error reports:</strong> those linked to your account are deleted with it; others are kept for up to 2 years.</li>
        <li><strong>Purchase and subscription records:</strong> for the period required by tax and commercial law (up to 10 years); unlinked from you when the account is deleted.</li>
        <li><strong>Google Play subscription notifications:</strong> 180 days.</li>
        <li><strong>Support messages:</strong> 2 years. <strong>Deletion requests:</strong> 1 year, as proof that they were handled.</li>
        <li><strong>Data on your device:</strong> stays on your device until you uninstall the app or clear its data.</li>
      </ul>

      <h2 id="deletion">7. Account and data deletion</h2>
      <p>
        You can delete your account in the app under <strong>Settings &gt; Account settings &gt; Delete account</strong>{" "}
        or on the <Link href={href("en", "delete-account")}>account and data deletion page</Link>, which lists exactly
        what is deleted and what is kept. Deleting your account does not cancel a Google Play subscription; cancel it in{" "}
        <a href={PLAY_SUBSCRIPTIONS_URL} rel="noopener noreferrer">Google Play &gt; Payments &amp; subscriptions</a>.
      </p>

      <h2 id="security">8. Security</h2>
      <p>
        All traffic between the app and the server is encrypted with HTTPS. The server only accepts valid identity tokens
        signed by Google, and database access is limited to authorized people. The website forms do not store raw IP
        addresses and are rate-limited against abuse.
      </p>

      <h2 id="rights">9. Your rights</h2>
      <p>Under KVKK article 11 and other applicable law (such as the GDPR) you have the right to:</p>
      <ul>
        <li>learn whether your personal data is processed and request information about it,</li>
        <li>learn the purpose of processing and whether it is used accordingly,</li>
        <li>know the third parties in your country or abroad to whom it is transferred,</li>
        <li>request correction of incomplete or inaccurate data,</li>
        <li>request erasure or destruction and notification of this to third parties who received the data,</li>
        <li>request a copy of your data and object to processing; withdraw consent where processing is based on consent,</li>
        <li>object to an outcome against you arising from analysis by automated systems,</li>
        <li>claim compensation for damage caused by unlawful processing,</li>
        <li>lodge a complaint with the Turkish Personal Data Protection Authority or your local data protection authority.</li>
      </ul>
      <p>
        Send your requests via the <Contact locale="en" />. Requests are answered free of charge within 30 days at the
        latest. You can reset or delete your advertising ID in Android settings.
      </p>

      <h2 id="changes">10. Changes</h2>
      <p>
        We may update this policy. For significant changes we will inform you in the app and ask for your consent again
        where needed. The current version is always on this page.
      </p>
    </article>
  );
}
