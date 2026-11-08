// Site dilleri ve arayüz yazıları. Türkçe öntanımlı; yasal metinler src/content altında.

export const LOCALES = ["tr", "en"] as const;
export type Locale = (typeof LOCALES)[number];
export const DEFAULT_LOCALE: Locale = "tr";

export function isLocale(value: string): value is Locale {
  return (LOCALES as readonly string[]).includes(value);
}

/** Sitenin sayfaları (iki dilde de aynı yol). */
export const PAGES = ["", "about", "download", "contact", "support", "privacy", "terms", "delete-account"] as const;
export type PagePath = (typeof PAGES)[number];

export function href(locale: Locale, page: PagePath): string {
  return page ? `/${locale}/${page}` : `/${locale}`;
}

const tr = {
  appName: "Çizim Rehberi",
  langName: "Türkçe",
  otherLang: "English",
  skipToContent: "İçeriğe geç",
  nav: {
    home: "Ana sayfa",
    about: "Hakkımızda",
    download: "İndir",
    contact: "İletişim",
    openMenu: "Menüyü aç",
    closeMenu: "Menüyü kapat",
    privacy: "Gizlilik",
    terms: "Kullanım şartları",
    deleteAccount: "Hesap silme",
    support: "Destek",
    menu: "Site menüsü",
  },
  footer: {
    product: "Ürün",
    company: "Kurumsal",
    legal: "Yasal",
    about: "Uygulama",
    blurb: "Çizim Rehberi, her yaştan kullanıcı için yapay zekâ destekli bir çizim uygulamasıdır.",
    developer: "Geliştirici",
    rights: "Tüm hakları saklıdır.",
    noTracking: "Bu site çerez ya da izleme kodu kullanmaz.",
  },
  meta: {
    homeTitle: "Çizim Rehberi — yapay zekâ destekli çizim uygulaması",
    homeDescription:
      "Çizim Rehberi (DrawGuidance): resim, not, çizgi roman, çocuk modu ve 3B şekiller için cihazda çalışan yapay zekâ destekli Android çizim uygulaması.",
    privacyTitle: "Gizlilik politikası",
    termsTitle: "Kullanım şartları",
    deleteTitle: "Hesap ve veri silme",
    supportTitle: "Destek ve iletişim",
    aboutTitle: "Hakkımızda",
    aboutDescription: "Çizim Rehberi'ni geliştiren ekip, misyonumuz ve değerlerimiz.",
    downloadTitle: "İndir",
    downloadDescription: "Çizim Rehberi'ni Google Play'den indirin; Windows ve iOS sürümleri yakında.",
    contactTitle: "İletişim",
    contactDescription: "Çizim Rehberi ekibine iş birliği, basın ve genel sorularınız için yazın.",
    notFound: "Sayfa bulunamadı",
  },
  pages: {
    statsLabel: "Rakamlarla Çizim Rehberi",
    featuresLabel: "Özellikler",
    showcaseLabel: "Neler yapabilirsin",
    faqLabel: "Sorular",
    imagePlaceholder: "Görsel yakında",
    available: "Şimdi indir",
    soon: "Yakında",
    platform: "Platform",
    breadcrumbHome: "Ana sayfa",
    contactLead: "İş birliği, basın ya da genel sorular için formu doldur. Uygulama içi sorunlar için Destek sayfasını kullanabilirsin.",
    contactFormTitle: "Bize ulaş",
    contactName: "Adın",
    contactSubject: "Konu",
    contactSuccess: "Mesajın alındı. En kısa sürede e-posta ile döneceğiz.",
    contactUnavailable: "İletişim formu şu anda kullanılamıyor. Lütfen e-posta ile yaz.",
    contactSupportHint: "Uygulamayla ilgili teknik bir sorun mu var?",
    contactSupportLink: "Destek sayfasına git",
    emailUs: "E-posta",
    responseTime: "Genellikle 2 iş günü içinde yanıt veriyoruz.",
  },
  home: {
    tagline: "Çiz, not al, çizgi roman yap: yapay zekâ destekli çizim rehberin.",
    intro:
      "Çizim Rehberi; resim, not, çizgi roman, çocuk çizimi ve 3B şekil modlarıyla her yaştan kullanıcı için tasarlanmış bir Android çizim uygulamasıdır. Çizim önerileri, şekil tanıma ve el yazısı tanıma cihazınızda çalışan yapay zekâ modelleriyle yapılır.",
    features: [
      { title: "Beş mod", text: "Resim, not, çizgi roman, çocuk ve 3B şekil modları tek uygulamada." },
      { title: "Cihazda yapay zekâ", text: "Öneriler ve tanıma cihazınızda çalışır; çizimleriniz sizde kalır." },
      { title: "Çocuk modu", text: "13 yaş altı için reklamsız, sade ve güvenli bir çizim alanı." },
      { title: "Hazır içerik", text: "Özgün şekiller, karakterler, efektler, arka planlar ve sayfa şablonları." },
    ],
    getIt: "Google Play'den edinin",
    comingSoon: "Uygulama yakında Google Play'de.",
    legalLinks: "Yasal metinler ve hesap işlemleri",
  },
  form: {
    email: "E-posta adresi",
    emailHint: "Uygulamada kullandığınız Google hesabının e-posta adresi.",
    reason: "Silme nedeni (isteğe bağlı)",
    subject: "Konu",
    message: "Mesajınız",
    optional: "isteğe bağlı",
    send: "Gönder",
    sending: "Gönderiliyor…",
    chars: (n: number, max: number) => `${n}/${max} karakter`,
    errors: {
      invalid_email: "Lütfen geçerli bir e-posta adresi girin.",
      rate_limited: "Çok fazla istek gönderildi. Lütfen bir süre sonra yeniden deneyin.",
      too_long: "Metin çok uzun. Lütfen kısaltın.",
      validation: "Lütfen zorunlu alanları doldurun.",
      network: "Sunucuya ulaşılamadı. İnternet bağlantınızı kontrol edip yeniden deneyin.",
      generic: "Bir sorun oluştu. Lütfen biraz sonra yeniden deneyin.",
      consent: "Devam etmek için onay kutusunu işaretleyin.",
    },
  },
  deletionForm: {
    title: "Giriş yapmadan silme talebi gönderin",
    lead: "Google hesabınızla giriş yapamıyorsanız e-posta adresinizi bırakın. Talebiniz elle incelenir; hesabın size ait olduğunu doğrulamak için bu adrese yazabiliriz.",
    consent:
      "Bu e-posta adresine bağlı Çizim Rehberi hesabımın ve verilerimin kalıcı olarak silinmesini istiyorum. Silmenin geri alınamayacağını anlıyorum.",
    submit: "Silme talebini gönder",
    success:
      "Talebiniz alındı. Hesabınız ve verileriniz en geç 30 gün içinde silinir; işlem tamamlanınca bu adrese bilgi verilir. Aynı adres için 24 saat içinde yeniden talep göndermenize gerek yoktur.",
  },
  signedIn: {
    title: "Google ile giriş yapıp hemen silin",
    lead: "Uygulamada kullandığınız Google hesabıyla giriş yapın. Hesabınızı doğruladıktan sonra silme işlemini onaylamanız istenir.",
    signIn: "Google ile giriş yap",
    signingIn: "Giriş yapılıyor…",
    signedInAs: "Giriş yapılan hesap:",
    signOut: "Çıkış yap",
    confirmTitle: "Silmeyi onaylayın",
    confirmText:
      "Bu işlem hesabınızı ve sunucudaki verilerinizi kalıcı olarak siler. Geri alınamaz. Aktif bir Premium aboneliğiniz varsa önce Google Play'den iptal edin; hesap silmek aboneliği iptal etmez.",
    confirmCheck: "Hesabımın ve verilerimin kalıcı olarak silineceğini anlıyorum.",
    confirmButton: "Hesabımı kalıcı olarak sil",
    deleting: "Siliniyor…",
    cancel: "Vazgeç",
    success: "Hesabınız ve sunucudaki verileriniz silindi. Uygulamayı cihazınızdan kaldırarak cihazdaki çizimlerinizi de silebilirsiniz.",
    authAccountKept:
      "Sunucudaki verileriniz silindi, ancak Firebase oturum hesabınız bu tarayıcıdan silinemedi. Uygulamada Ayarlar > Hesap ayarları > Hesabı sil adımıyla tamamlayabilir ya da aşağıdaki formdan talep gönderebilirsiniz.",
    errors: {
      popupBlocked: "Giriş penceresi engellendi. Tarayıcınızda açılır pencerelere izin verip yeniden deneyin.",
      popupClosed: "Giriş penceresi kapatıldı. Yeniden deneyin.",
      unauthorizedDomain: "Bu site henüz Google girişi için yetkilendirilmemiş. Lütfen aşağıdaki talep formunu kullanın.",
      signIn: "Google ile giriş yapılamadı. Yeniden deneyin ya da aşağıdaki talep formunu kullanın.",
      session: "Oturumunuzun süresi doldu. Lütfen yeniden giriş yapın.",
      server: "Sunucu şu an silme işlemini yapamıyor. Lütfen biraz sonra yeniden deneyin ya da talep formunu kullanın.",
    },
  },
  support: {
    formTitle: "Bize yazın",
    success: "Mesajınız alındı. En kısa sürede e-posta ile yanıt vereceğiz.",
    emailAlt: "Doğrudan e-posta:",
  },
  notFound: {
    title: "Sayfa bulunamadı",
    text: "Aradığınız sayfa yok ya da taşınmış olabilir.",
    back: "Ana sayfaya dön",
  },
};

export type Dictionary = typeof tr;

const en: Dictionary = {
  appName: "DrawGuidance",
  langName: "English",
  otherLang: "Türkçe",
  skipToContent: "Skip to content",
  nav: {
    home: "Home",
    about: "About",
    download: "Download",
    contact: "Contact",
    openMenu: "Open menu",
    closeMenu: "Close menu",
    privacy: "Privacy",
    terms: "Terms of use",
    deleteAccount: "Delete account",
    support: "Support",
    menu: "Site menu",
  },
  footer: {
    product: "Product",
    company: "Company",
    legal: "Legal",
    about: "App",
    blurb: "DrawGuidance is an AI-assisted drawing app for users of all ages.",
    developer: "Developer",
    rights: "All rights reserved.",
    noTracking: "This site uses no cookies or tracking scripts.",
  },
  meta: {
    homeTitle: "DrawGuidance — AI-assisted drawing app",
    homeDescription:
      "DrawGuidance (Çizim Rehberi): an Android drawing app for pictures, notes, comics, kids and 3D shapes, with AI assistance that runs on your device.",
    privacyTitle: "Privacy policy",
    termsTitle: "Terms of use",
    deleteTitle: "Account and data deletion",
    supportTitle: "Support and contact",
    aboutTitle: "About us",
    aboutDescription: "The team behind DrawGuidance, our mission and our values.",
    downloadTitle: "Download",
    downloadDescription: "Get DrawGuidance on Google Play; Windows and iOS versions are coming soon.",
    contactTitle: "Contact",
    contactDescription: "Write to the DrawGuidance team about partnerships, press and general questions.",
    notFound: "Page not found",
  },
  pages: {
    statsLabel: "DrawGuidance in numbers",
    featuresLabel: "Features",
    showcaseLabel: "What you can do",
    faqLabel: "Questions",
    imagePlaceholder: "Image coming soon",
    available: "Download now",
    soon: "Coming soon",
    platform: "Platform",
    breadcrumbHome: "Home",
    contactLead: "Fill in the form for partnerships, press or general questions. For in-app problems, please use the Support page.",
    contactFormTitle: "Get in touch",
    contactName: "Your name",
    contactSubject: "Subject",
    contactSuccess: "Your message has been received. We will reply by email as soon as possible.",
    contactUnavailable: "The contact form is unavailable right now. Please write to us by email.",
    contactSupportHint: "Having a technical problem with the app?",
    contactSupportLink: "Go to Support",
    emailUs: "Email",
    responseTime: "We usually reply within 2 business days.",
  },
  home: {
    tagline: "Draw, take notes, make comics: your AI-assisted drawing guide.",
    intro:
      "DrawGuidance is an Android drawing app for all ages, with modes for pictures, notes, comics, kids' drawings and 3D shapes. Drawing suggestions, shape recognition and handwriting recognition run on AI models on your own device.",
    features: [
      { title: "Five modes", text: "Pictures, notes, comics, kids and 3D shapes in one app." },
      { title: "On-device AI", text: "Suggestions and recognition run on your device; your drawings stay with you." },
      { title: "Kids mode", text: "An ad-free, simple and safe drawing space for children under 13." },
      { title: "Ready-made content", text: "Original shapes, characters, effects, backgrounds and page templates." },
    ],
    getIt: "Get it on Google Play",
    comingSoon: "Coming soon to Google Play.",
    legalLinks: "Legal and account",
  },
  form: {
    email: "Email address",
    emailHint: "The email address of the Google account you use in the app.",
    reason: "Reason for deletion (optional)",
    subject: "Subject",
    message: "Your message",
    optional: "optional",
    send: "Send",
    sending: "Sending…",
    chars: (n: number, max: number) => `${n}/${max} characters`,
    errors: {
      invalid_email: "Please enter a valid email address.",
      rate_limited: "Too many requests. Please try again later.",
      too_long: "The text is too long. Please shorten it.",
      validation: "Please fill in the required fields.",
      network: "Could not reach the server. Check your connection and try again.",
      generic: "Something went wrong. Please try again later.",
      consent: "Please tick the confirmation box to continue.",
    },
  },
  deletionForm: {
    title: "Request deletion without signing in",
    lead: "If you cannot sign in with your Google account, leave your email address. We review requests manually and may write to this address to confirm that the account is yours.",
    consent:
      "I want my DrawGuidance account and data linked to this email address to be permanently deleted. I understand that deletion cannot be undone.",
    submit: "Send deletion request",
    success:
      "Your request has been received. Your account and data will be deleted within 30 days, and we will confirm by email when it is done. There is no need to send another request for the same address within 24 hours.",
  },
  signedIn: {
    title: "Sign in with Google and delete now",
    lead: "Sign in with the Google account you use in the app. After we verify your account, you will be asked to confirm the deletion.",
    signIn: "Sign in with Google",
    signingIn: "Signing in…",
    signedInAs: "Signed in as:",
    signOut: "Sign out",
    confirmTitle: "Confirm deletion",
    confirmText:
      "This permanently deletes your account and your data on our server. It cannot be undone. If you have an active Premium subscription, cancel it in Google Play first; deleting your account does not cancel the subscription.",
    confirmCheck: "I understand that my account and data will be permanently deleted.",
    confirmButton: "Permanently delete my account",
    deleting: "Deleting…",
    cancel: "Cancel",
    success: "Your account and your data on our server have been deleted. Uninstall the app to also remove the drawings stored on your device.",
    authAccountKept:
      "Your data on our server has been deleted, but your Firebase sign-in account could not be deleted from this browser. Finish in the app under Settings > Account settings > Delete account, or send a request with the form below.",
    errors: {
      popupBlocked: "The sign-in window was blocked. Allow pop-ups in your browser and try again.",
      popupClosed: "The sign-in window was closed. Please try again.",
      unauthorizedDomain: "This site is not yet authorized for Google sign-in. Please use the request form below.",
      signIn: "Could not sign in with Google. Try again or use the request form below.",
      session: "Your session has expired. Please sign in again.",
      server: "The server cannot delete the account right now. Please try again later or use the request form.",
    },
  },
  support: {
    formTitle: "Write to us",
    success: "Your message has been received. We will reply by email as soon as possible.",
    emailAlt: "Direct email:",
  },
  notFound: {
    title: "Page not found",
    text: "The page you are looking for does not exist or may have moved.",
    back: "Back to home",
  },
};

const dictionaries: Record<Locale, Dictionary> = { tr, en };

export function getDictionary(locale: Locale): Dictionary {
  return dictionaries[locale];
}
