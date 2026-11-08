# İçerik biçimi test dosyaları

Android `ContentCodec` (Kotlin) ile üretilen örnek belgeler. iOS testleri (`ContentCodecTests`) bunları çözer ve
Android ile aynı sonucu verdiğini doğrular. Yeniden üretmek için:

    gradle -p ios/tools/codec-fixtures run --args=$PWD/ios/DrawGuidanceTests/Fixtures
