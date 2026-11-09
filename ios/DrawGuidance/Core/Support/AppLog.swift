import Foundation
#if canImport(os)
import os
#endif

/// Uygulama günlüğü (Android `AppLog`): sistem günlüğüne yazar ve son kayıtları Tanılama ekranı için bellekte tutar.
enum AppLog {
    struct Entry: Equatable { let time: Date; let level: String; let tag: String; let message: String }

    static let MAX = 300
    private static let lock = NSLock()
    nonisolated(unsafe) private static var buffer: [Entry] = []
    #if canImport(os)
    private static let logger = Logger(subsystem: "com.karapp.drawguidance", category: "app")
    #endif

    static var entries: [Entry] { lock.lock(); defer { lock.unlock() }; return buffer }

    private static func add(_ level: String, _ tag: String, _ message: String) {
        lock.lock()
        buffer.append(Entry(time: Date(), level: level, tag: tag, message: message))
        if buffer.count > MAX { buffer.removeFirst(buffer.count - MAX) }
        lock.unlock()
        #if canImport(os)
        logger.log("\(level, privacy: .public) [\(tag, privacy: .public)] \(message, privacy: .public)")
        #endif
    }

    static func e(_ tag: String, _ message: String, _ error: Error? = nil) {
        add("E", tag, error.map { "\(message): \($0)" } ?? message)
    }

    static func w(_ tag: String, _ message: String, _ error: Error? = nil) {
        add("W", tag, error.map { "\(message): \($0)" } ?? message)
    }

    static func i(_ tag: String, _ message: String) { add("I", tag, message) }
}
