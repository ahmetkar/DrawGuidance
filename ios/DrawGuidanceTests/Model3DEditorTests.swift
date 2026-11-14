import XCTest
@testable import DrawGuidance

@MainActor
final class Model3DEditorTests: XCTestCase {
    private func makeVM() async throws -> Model3DEditorModel {
        let dir = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        let defaults = UserDefaults(suiteName: UUID().uuidString)!
        let repo = Model3DRepository(directory: dir, textures: TextureRepository(directory: dir.appendingPathComponent("tex")), defaults: defaults)
        let vm = Model3DEditorModel(repository: repo, prefs: Model3DPrefs(defaults: defaults), modelId: nil)
        for _ in 0..<100 where vm.model == nil { try await Task.sleep(nanoseconds: 20_000_000) }
        XCTAssertNotNil(vm.model)
        vm.setViewport(400, 600)
        return vm
    }

    func testAddUndoRedoGroup() async throws {
        let vm = try await makeVM()
        XCTAssertEqual(vm.model?.parts.count, 1)
        vm.addPart(.SPHERE)
        XCTAssertEqual(vm.model?.parts.count, 2)
        XCTAssertTrue(vm.canUndo)
        vm.undo()
        XCTAssertEqual(vm.model?.parts.count, 1)
        vm.redo()
        XCTAssertEqual(vm.model?.parts.count, 2)
        vm.selection = Selection3(vm.model!.parts.map(\.id))
        vm.groupSelected()
        XCTAssertEqual(vm.model?.groups.count, 1)
        XCTAssertTrue(vm.model!.parts.allSatisfy { $0.group != nil })
        // Tek üyesi kalan grup çözülür
        vm.selection = Selection3([vm.model!.parts[1].id])
        vm.deleteSelected()
        XCTAssertEqual(vm.model?.groups.count, 0)
    }

    func testMoveCoalesceAndBoolean() async throws {
        let vm = try await makeVM()
        let id = vm.model!.parts[0].id
        vm.selection = Selection3([id])
        vm.moveSelected(V3(0.1, 0, 0))
        vm.moveSelected(V3(0.1, 0, 0))
        XCTAssertEqual(vm.model!.parts[0].position.x, 0.2, accuracy: 1e-5)
        vm.undo()
        XCTAssertEqual(vm.model!.parts[0].position.x, 0, accuracy: 1e-5)
        vm.addPart(.SPHERE)
        vm.selection = Selection3(vm.model!.parts.map(\.id))
        vm.setTransform(vm.model!.parts[1].id, position: V3(0.4, 0.3, 0))
        vm.booleanSelected(.UNION)
        for _ in 0..<300 where vm.model!.parts.count != 1 { try await Task.sleep(nanoseconds: 20_000_000) }
        XCTAssertEqual(vm.model?.parts.count, 1)
        XCTAssertNil(vm.busy)
    }

    func testFaceExtrudeOpAndPaint() async throws {
        let vm = try await makeVM()
        let id = vm.model!.parts[0].id
        vm.setTool(.FACE)
        XCTAssertEqual(vm.mode, .EDIT)
        vm.selection = Selection3([id], faces: OrderedSet([0]))
        vm.startOp(.EXTRUDE)
        vm.setOpAmount(0.5)
        XCTAssertGreaterThan(vm.model!.parts[0].mesh.faceCount, 6)
        vm.applyOp()
        XCTAssertNil(vm.op)
        vm.undo()
        XCTAssertEqual(vm.model!.parts[0].mesh.faceCount, 6)
        vm.setTool(.PAINT)
        vm.setPaintScope(.PART)
        var red = Material(); red.color = Int32(bitPattern: 0xFFFF_0000)
        vm.setPaint(red)
        vm.paintAt(0, 0)
        XCTAssertEqual(vm.model!.parts[0].material.color, red.color)
    }

    func testRepositorySaveLoadAndSamples() async throws {
        let vm = try await makeVM()
        vm.addPart(.TORUS)
        vm.save()
        for _ in 0..<1500 where vm.repository.models.isEmpty { try await Task.sleep(nanoseconds: 20_000_000) }
        XCTAssertEqual(vm.repository.models.count, 1)
        let loaded = await vm.repository.load(vm.model!.id)
        XCTAssertEqual(loaded?.parts.count, 2)
        await vm.repository.warmUp()
        XCTAssertEqual(vm.repository.models.count, 1 + Samples3D.all.count)
    }

    func testI18nEnglish() {
        let t = I18n.load("en", bundle: Bundle(for: Model3DEditorModel.self))
        XCTAssertFalse(t.isEmpty)
        I18n.setLanguage("en")
        defer { I18n.setLanguage("tr") }
        XCTAssertNotEqual("Küp".tr, "Küp")
        XCTAssertEqual("Kullanıcının yazdığı metin 123".tr, "Kullanıcının yazdığı metin 123")
    }
}
