import Foundation
import UIKit

/// v53 bellek düzeni (Android `DeviceCaps.cache` / `editorClosed`): çizim önbellekleri bellek uyarısında ve
/// düzenleyici kapanınca boşaltılır. Kapatılan tuvalin resimleri, çizim resimleri ve yazı düzenleri bellekte
/// tutulmaz; yalnız açık olan tuvalin verileri bellekte kalır. Küçük resim önbellekleri yalnız bellek uyarısında boşalır.
enum MemoryHygiene {
    private static let lock = NSLock()
    nonisolated(unsafe) private static var editorEvictors: [() -> Void] = []
    nonisolated(unsafe) private static var thumbEvictors: [() -> Void] = []
    nonisolated(unsafe) private static var openEditors = 0
    nonisolated(unsafe) private static var observer: NSObjectProtocol?

    /// [editor] true: çizim önbelleği (tuval kapanınca da boşalır); false: küçük resim önbelleği.
    static func register(editor: Bool, _ evict: @escaping () -> Void) {
        lock.lock(); defer { lock.unlock() }
        if editor { editorEvictors.append(evict) } else { thumbEvictors.append(evict) }
    }

    /// Uygulama açılışında bir kez: sistem bellek uyarısı gelince bütün önbellekler boşalır.
    static func install() {
        guard observer == nil else { return }
        _ = CanvasImageCache.registered
        observer = NotificationCenter.default.addObserver(forName: UIApplication.didReceiveMemoryWarningNotification, object: nil, queue: .main) { _ in
            trimAll()
        }
    }

    static func trimAll() {
        lock.lock(); let all = editorEvictors + thumbEvictors; lock.unlock()
        all.forEach { $0() }
        builtInEditorCaches()
    }

    static func editorOpened() { lock.lock(); openEditors += 1; lock.unlock() }

    /// Düzenleyici kapanınca: kayıt bitsin diye kısa bir süre sonra, o sırada başka tuval açık değilse boşaltılır.
    static func editorClosed() {
        lock.lock(); openEditors = max(openEditors - 1, 0); lock.unlock()
        DispatchQueue.main.asyncAfter(deadline: .now() + 2) {
            lock.lock(); let idle = openEditors == 0; let list = editorEvictors; lock.unlock()
            guard idle else { return }
            list.forEach { $0() }
            builtInEditorCaches()
        }
    }

    /// Kendi kilitli sözlüklerini tutan çiziciler.
    private static func builtInEditorCaches() {
        TextRenderer.trimCaches()
        FormulaRenderer.trimCaches()
        BalloonRenderer.trimCaches()
        DiagramPainter.trimCaches()
        ToneRenderer.trim()
    }
}
