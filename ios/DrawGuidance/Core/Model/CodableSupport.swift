import Foundation

/// kotlinx.serialization uyumu: eksik alan varsayılanla dolar, bilinmeyen alan yok sayılır; yazarken varsayılan
/// değerdeki alanlar atlanır (Android'deki `encodeDefaults = false` ile aynı JSON).
struct AnyKey: CodingKey {
    var stringValue: String
    var intValue: Int? { nil }
    init(_ s: String) { stringValue = s }
    init?(stringValue: String) { self.stringValue = stringValue }
    init?(intValue: Int) { return nil }
}

extension KeyedDecodingContainer where K == AnyKey {
    func v<T: Decodable>(_ key: String, _ def: T) -> T {
        ((try? decodeIfPresent(T.self, forKey: AnyKey(key))) ?? nil) ?? def
    }
    func o<T: Decodable>(_ key: String) -> T? {
        (try? decodeIfPresent(T.self, forKey: AnyKey(key))) ?? nil
    }
    func req<T: Decodable>(_ key: String) throws -> T {
        try decode(T.self, forKey: AnyKey(key))
    }
}

extension KeyedEncodingContainer where K == AnyKey {
    /// Varsayılandan farklıysa yazar.
    mutating func e<T: Encodable & Equatable>(_ value: T, _ key: String, _ def: T) throws {
        if value != def { try encode(value, forKey: AnyKey(key)) }
    }
    /// Her zaman yazar (varsayılanı olmayan alan).
    mutating func r<T: Encodable>(_ value: T, _ key: String) throws {
        try encode(value, forKey: AnyKey(key))
    }
    /// null değilse yazar.
    mutating func o<T: Encodable>(_ value: T?, _ key: String) throws {
        if let value { try encode(value, forKey: AnyKey(key)) }
    }
}

/// Kotlin enum'ları ada göre serileştirilir; bilinmeyen ad varsayılana döner.
protocol KotlinEnum: RawRepresentable, Codable, CaseIterable, Equatable where RawValue == String {
    static var fallback: Self { get }
}

extension KotlinEnum {
    init(from decoder: Decoder) throws {
        let s = try decoder.singleValueContainer().decode(String.self)
        self = Self(rawValue: s) ?? Self.fallback
    }
    func encode(to encoder: Encoder) throws {
        var c = encoder.singleValueContainer()
        try c.encode(rawValue)
    }
    /// Kotlin `ordinal`.
    var ordinal: Int { Array(Self.allCases).firstIndex(of: self) ?? 0 }
}

/// Kotlin `Map<Int, String>`: JSON'da anahtarlar metindir.
struct IntKeyedStrings: Codable, Equatable {
    var map: [Int: String]
    init(_ map: [Int: String] = [:]) { self.map = map }
    init(from decoder: Decoder) throws {
        let raw = try decoder.singleValueContainer().decode([String: String].self)
        var m: [Int: String] = [:]
        for (k, v) in raw { if let i = Int(k) { m[i] = v } }
        map = m
    }
    func encode(to encoder: Encoder) throws {
        var c = encoder.singleValueContainer()
        try c.encode(Dictionary(uniqueKeysWithValues: map.map { (String($0.key), $0.value) }))
    }
}
