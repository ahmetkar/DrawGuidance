import XCTest
@testable import DrawGuidance

/// v55 iOS: geniş cihaz desteği (iOS 16, en az izin), çizgi roman ve not almada çok sayfa ve sayfa şeridi, örnek manga
/// şablonu.
final class V55Tests: XCTestCase {

    // MARK: Cihaz desteği

    func testNoCameraOrFullLibraryPermission() {
        let info = Bundle.main.infoDictionary ?? [:]
        XCTAssertNil(info["NSCameraUsageDescription"], "kamera kullanılmıyor")
        XCTAssertNil(info["NSPhotoLibraryUsageDescription"], "resim seçimi PhotosPicker ile izinsiz")
        XCTAssertNotNil(info["NSPhotoLibraryAddUsageDescription"], "galeriye kaydetme kalır")
        XCTAssertNil(info["UIRequiredDeviceCapabilities"], "zorunlu donanım yok")
    }

    func testVersion() {
        let info = Bundle.main.infoDictionary ?? [:]
        XCTAssertEqual(info["CFBundleShortVersionString"] as? String, "1.12.1")
        XCTAssertEqual(info["CFBundleVersion"] as? String, "55")
    }

    // MARK: Sayfalar

    @MainActor
    func testComicAndNotesAreCreatedWithChosenPageCount() {
        let dir = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        let store = CanvasStore(directory: dir)
        let notes = store.create(kind: .NOTES, name: "Not", width: 1240, height: 1754, pageCount: 4)
        XCTAssertEqual(notes.pageCount, 4)
        XCTAssertEqual(notes.currentPage, 0)
        // Çizgi roman: kapak + seçilen sayıda sayfa, kapaktan sonraki ilk sayfada açılır
        let comic = CanvasCreator.create(CanvasFormResult(name: "Roman", width: 1200, height: 1800, paperHex: "#FFFFFF",
                                                          aiEnabled: true, kind: .COMIC, pageCount: 8), store: store)
        XCTAssertEqual(comic.pageCount, 9)
        XCTAssertTrue(comic.hasCover)
        XCTAssertEqual(comic.currentPage, 1)
    }

    func testPageStripPositionText() {
        XCTAssertEqual(PageStripView.position(page: 0, count: 4, cover: false), L("page_of", 1, 4))
        XCTAssertEqual(PageStripView.position(page: 2, count: 4, cover: false), L("page_of", 3, 4))
        // Kapaklı çizgi romanda kapak "Kapak", numaralar kapaksız sayılır
        XCTAssertEqual(PageStripView.position(page: 0, count: 5, cover: true), L("v45_cover"))
        XCTAssertEqual(PageStripView.position(page: 1, count: 5, cover: true), L("page_of", 1, 4))
    }

    // MARK: Şablon

    func testMangaTemplateReplacesSchoolAdventure() {
        CanvasTemplates.reset()
        XCTAssertNil(CanvasTemplates.find("comic_story_school"))
        let manga = CanvasTemplates.find("comic_story_manga")
        XCTAssertNotNil(manga)
        XCTAssertEqual(manga?.names["tr"], "Manga: Avlu düellosu")
        XCTAssertEqual(manga?.pages, 2)
        XCTAssertNotNil(CanvasTemplates.coverBytes("comic_story_manga"))
        XCTAssertNotNil(CanvasTemplates.prepare("comic_story_manga", canvasName: "Manga"))
    }
}
