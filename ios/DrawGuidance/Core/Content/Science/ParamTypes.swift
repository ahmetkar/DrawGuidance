import Foundation

/// v45 parametreli şekil tutamağı (Android `ParamHandle`): kutudaki yeri (0..1, y aşağı).
struct ParamHandle: Equatable { let key: String; let x: Float; let y: Float }

/// Formül uygulamasının hatası (Android `ParamError`).
struct ParamError: Equatable {
    enum Code { case SYNTAX, UNKNOWN_SYMBOL, OUT_OF_RANGE, NO_MATCH, NOT_NUMBER, CHEMISTRY }
    var code: Code
    var detail: String = ""
    var math: MathError? = nil
}

/// Uygulama sonucu (Android `ParamApply`).
enum ParamApply: Equatable {
    case ok([String: String])
    case failed(ParamError)
}

/// Parametreli şekil modelinin düzenleyicinin kullandığı yüzü (Android `ParamModel`).
protocol ParamShapeModel: AnyObject {
    func apply(_ raw: [String: String]?, _ text: String) -> ParamApply
    func setValue(_ raw: [String: String]?, _ key: String, _ value: Double) -> [String: String]?
    func withText(_ raw: [String: String]?, _ key: String, _ text: String) -> [String: String]
    func unitHandles(_ raw: [String: String]?) -> [ParamHandle]
    func dragUnit(_ raw: [String: String]?, _ key: String, _ ux: Float, _ uy: Float) -> [String: String]?
}
