import Foundation

/// v39 reklamın gösterildiği geçişler.
enum AdTrigger { case APP_ENTRY, CANVAS_CLOSED }

/// v39 reklam kararı ve nedeni.
enum AdDecision { case SHOW, NOT_LOADED_YET, PREMIUM, KIDS_CANVAS, CHILD_USER, FIRST_LAUNCH, NO_CONSENT, FREQUENCY_CAP, UNAVAILABLE }

/// v39 reklam kuralları (saf; Android `AdsPolicy` ile aynı):
/// - Yalnız Free planda; çocuk tuvalinden çıkarken ve 13 yaşından küçük kullanıcıda asla.
/// - İlk açılışta yok; UMP onayı olmadan yok; iki reklam arasında en az 3 dk.
struct AdsPolicy {
    var minIntervalMs: Int64 = 3 * 60_000

    func decide(_ trigger: AdTrigger, premium: Bool?, child: Bool, canvasKind: CanvasKind? = nil, firstLaunch: Bool, consent: Bool,
                lastShownAt: Int64?, now: Int64, available: Bool = true) -> AdDecision {
        if !available { return .UNAVAILABLE }
        guard let premium else { return .NOT_LOADED_YET }
        if premium { return .PREMIUM }
        if canvasKind == .KIDS { return .KIDS_CANVAS }
        if child { return .CHILD_USER }
        if trigger == .APP_ENTRY && firstLaunch { return .FIRST_LAUNCH }
        if !consent { return .NO_CONSENT }
        if let last = lastShownAt, now - last < minIntervalMs { return .FREQUENCY_CAP }
        return .SHOW
    }
}
