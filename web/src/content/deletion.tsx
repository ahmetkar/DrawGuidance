import Link from "next/link";
import { Contact } from "@/components/Contact";
import { APP_NAME_EN, APP_NAME_TR, DEVELOPER_NAME, PACKAGE_NAME, PLAY_SUBSCRIPTIONS_URL } from "@/lib/config";
import { href, type Locale } from "@/lib/i18n";

// Hesap ve veri silme sayfasının sabit metinleri (Google Play'in "hesap silme URL'si" gereksinimi: uygulama ve
// geliştirici adı, adımlar, silinen ve saklanan veriler ve süreleri).

export function DeletionIntro({ locale }: { locale: Locale }) {
  if (locale === "tr") {
    return (
      <>
        <h1>Hesap ve veri silme</h1>
        <p className="lead">
          Bu sayfa <strong>{APP_NAME_TR}</strong> ({APP_NAME_EN}) Android uygulamasının hesap ve veri silme sayfasıdır.
          Geliştirici: <strong>{DEVELOPER_NAME}</strong> · Paket adı: <code>{PACKAGE_NAME}</code>.
        </p>
        <div className="notice">
          <p>
            <strong>Aboneliğiniz varsa önce iptal edin.</strong> Hesap silmek Google Play aboneliğini iptal etmez ve
            ücretlendirme devam eder. İptal için: Google Play Store &gt; profil simgesi &gt; Ödemeler ve abonelikler &gt;
            Abonelikler &gt; {APP_NAME_TR} &gt; Aboneliği iptal et (
            <a href={PLAY_SUBSCRIPTIONS_URL} rel="noopener noreferrer">
              abonelikleri aç
            </a>
            ).
          </p>
        </div>
        <h2>Hesabınızı silmenin üç yolu</h2>
        <ol className="steps">
          <li>
            <strong>Uygulamada:</strong> {APP_NAME_TR}&apos;ni açın, <strong>Ayarlar &gt; Hesap ayarları &gt; Hesabı sil</strong>{" "}
            adımına dokunun ve onaylayın. Silme hemen yapılır.
          </li>
          <li>
            <strong>Bu sayfada Google ile giriş yaparak:</strong> uygulamada kullandığınız Google hesabıyla giriş yapın,
            silmeyi onaylayın. Silme hemen yapılır.
          </li>
          <li>
            <strong>Giriş yapmadan talep göndererek:</strong> aşağıdaki forma e-posta adresinizi yazın. Talebiniz elle
            işlenir ve en geç 30 gün içinde tamamlanır; tamamlanınca e-postayla bilgi verilir.
          </li>
        </ol>
      </>
    );
  }
  return (
    <>
      <h1>Account and data deletion</h1>
      <p className="lead">
        This is the account and data deletion page of the <strong>{APP_NAME_EN}</strong> ({APP_NAME_TR}) Android app.
        Developer: <strong>{DEVELOPER_NAME}</strong> · Package name: <code>{PACKAGE_NAME}</code>.
      </p>
      <div className="notice">
        <p>
          <strong>If you have a subscription, cancel it first.</strong> Deleting your account does not cancel a Google
          Play subscription, and you will keep being charged. To cancel: Google Play Store &gt; profile icon &gt; Payments
          &amp; subscriptions &gt; Subscriptions &gt; {APP_NAME_EN} &gt; Cancel subscription (
          <a href={PLAY_SUBSCRIPTIONS_URL} rel="noopener noreferrer">
            open subscriptions
          </a>
          ).
        </p>
      </div>
      <h2>Three ways to delete your account</h2>
      <ol className="steps">
        <li>
          <strong>In the app:</strong> open {APP_NAME_EN}, go to <strong>Settings &gt; Account settings &gt; Delete account</strong>{" "}
          and confirm. Deletion happens immediately.
        </li>
        <li>
          <strong>On this page, by signing in with Google:</strong> sign in with the Google account you use in the app and
          confirm. Deletion happens immediately.
        </li>
        <li>
          <strong>By sending a request without signing in:</strong> enter your email address in the form below. We process
          requests manually within 30 days and confirm by email when done.
        </li>
      </ol>
    </>
  );
}

export function DeletionDetails({ locale }: { locale: Locale }) {
  if (locale === "tr") {
    return (
      <>
        <h2 id="silinenler">Silinen veriler</h2>
        <ul>
          <li>Hesap bilgileriniz: ad, soyad, görünen ad, e-posta adresi, profil fotoğrafı bağlantısı, doğum tarihi, hesap kimliği.</li>
          <li>Kullanım Şartları ve KVKK metni onay kayıtları.</li>
          <li>Google ile giriş (Firebase Authentication) hesabınız.</li>
          <li>Sunucudaki eşitlenmiş içerik ve çocuk modu &quot;Çizim Görevi&quot; ilerlemesi.</li>
          <li>Hesabınıza bağlı olarak gönderilmiş hata raporları.</li>
        </ul>
        <p>
          Uygulama ve oturumlu web silmesinde bu veriler <strong>hemen</strong>, giriş yapmadan gönderilen taleplerde{" "}
          <strong>en geç 30 gün içinde</strong> silinir. Silinen veriler geri getirilemez.
        </p>

        <h2 id="saklananlar">Saklanan veriler ve süreleri</h2>
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th scope="col">Veri</th>
                <th scope="col">Neden</th>
                <th scope="col">Süre</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td>Satın alma ve abonelik kayıtları (plan, sipariş numarası, tarihler)</td>
                <td>Vergi ve ticaret mevzuatı; iade ve uyuşmazlıklar. Hesabınızla bağı koparılır.</td>
                <td>Yasal süre boyunca (10 yıla kadar)</td>
              </tr>
              <tr>
                <td>Google Play abonelik bildirimleri (hesap kimliği içermez)</td>
                <td>Abonelik durumunun doğru işlenmesi</td>
                <td>180 gün</td>
              </tr>
              <tr>
                <td>Hesap silme talebi kaydı (e-posta, tarih)</td>
                <td>Talebin işlendiğinin kanıtı</td>
                <td>1 yıl</td>
              </tr>
              <tr>
                <td>Destek mesajlarınız</td>
                <td>Yazışmanın sürdürülmesi ve kayıt</td>
                <td>2 yıl</td>
              </tr>
              <tr>
                <td>Hesaba bağlı olmayan hata raporları</td>
                <td>Hata giderme (kimliğinizi içermez)</td>
                <td>En çok 2 yıl</td>
              </tr>
            </tbody>
          </table>
        </div>
        <p>
          Google&apos;ın kendi kayıtları (Google hesabınız, Google Play satın alma geçmişi, AdMob verileri) Google&apos;ın
          gizlilik politikasına tabidir. Reklam kimliğinizi Android Ayarlar &gt; Google &gt; Reklamlar bölümünden
          sıfırlayabilir ya da silebilirsiniz.
        </p>
        <p>
          <strong>Cihazınızdaki veriler:</strong> çizimleriniz ve ayarlarınız cihazınızda kalır. Uygulamayı kaldırarak ya
          da Android Ayarlar &gt; Uygulamalar &gt; {APP_NAME_TR} &gt; Depolama &gt; Verileri temizle adımıyla silebilirsiniz.
        </p>
        <p>
          Ayrıntılar için <Link href={href("tr", "privacy")}>gizlilik politikası</Link>. Sorularınız için:{" "}
          <Contact locale="tr" />.
        </p>
      </>
    );
  }
  return (
    <>
      <h2 id="deleted">Data that is deleted</h2>
      <ul>
        <li>Your account data: first and last name, display name, email address, profile photo link, birth date, account ID.</li>
        <li>Your acceptance records for the Terms of Use and the privacy notice.</li>
        <li>Your Sign in with Google (Firebase Authentication) account.</li>
        <li>Synced content on the server and kids mode &quot;Drawing Challenge&quot; progress.</li>
        <li>Error reports sent while signed in to your account.</li>
      </ul>
      <p>
        With in-app or signed-in web deletion this data is deleted <strong>immediately</strong>; for requests sent
        without signing in, <strong>within 30 days</strong>. Deleted data cannot be recovered.
      </p>

      <h2 id="kept">Data that is kept, and for how long</h2>
      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th scope="col">Data</th>
              <th scope="col">Why</th>
              <th scope="col">How long</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td>Purchase and subscription records (plan, order ID, dates)</td>
              <td>Tax and commercial law; refunds and disputes. Unlinked from your account.</td>
              <td>As required by law (up to 10 years)</td>
            </tr>
            <tr>
              <td>Google Play subscription notifications (no account ID)</td>
              <td>Correct processing of subscription status</td>
              <td>180 days</td>
            </tr>
            <tr>
              <td>Deletion request record (email, date)</td>
              <td>Proof that the request was handled</td>
              <td>1 year</td>
            </tr>
            <tr>
              <td>Your support messages</td>
              <td>Continuing the conversation and record keeping</td>
              <td>2 years</td>
            </tr>
            <tr>
              <td>Error reports not linked to an account</td>
              <td>Fixing bugs (they do not identify you)</td>
              <td>Up to 2 years</td>
            </tr>
          </tbody>
        </table>
      </div>
      <p>
        Google&apos;s own records (your Google account, Google Play purchase history, AdMob data) are governed by
        Google&apos;s privacy policy. You can reset or delete your advertising ID in Android Settings &gt; Google &gt; Ads.
      </p>
      <p>
        <strong>Data on your device:</strong> your drawings and settings stay on your device. Remove them by uninstalling
        the app or via Android Settings &gt; Apps &gt; {APP_NAME_EN} &gt; Storage &gt; Clear data.
      </p>
      <p>
        See the <Link href={href("en", "privacy")}>privacy policy</Link> for details. Questions: <Contact locale="en" />.
      </p>
    </>
  );
}
