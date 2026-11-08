import Foundation

/// Android metin kaynağı (`R.string.<ad>`): `Android.strings` tablosundan, uygulamanın dilinde.
func L(_ key: String) -> String {
    NSLocalizedString(key, tableName: "Android", bundle: .main, value: key, comment: "")
}

/// Biçimli Android metni (`stringResource(id, args)`).
func L(_ key: String, _ args: CVarArg...) -> String {
    String(format: L(key), locale: Locale.current, arguments: args)
}
