# DrawGuidance v51 devir notu (Android + iOS)

_2026-11-15. This note is for a new Claude conversation that has no memory. Read it first, then `v50-devir-notu.md` (v50 and the general setup)._

## Summary

- **Version:** Android versionCode **51** / versionName **1.9.0**; iOS **1.9.0 (51)**. Room **v23** (unchanged). Server **50.0.0** (no server change in v51; sharing runs on the local network only).
- **Source:** GitHub `github.com/ahmetkar/DrawGuidance`, branch **`ios/v51-entegrasyon`** (built on v50 `ios/v49-port` @ f621fff). Every v51 thread was merged into this branch.
- **PC:** `C:\Users\ahmet\Documents\DrawGuidance` is the master copy. The v51 changed files are in `DrawGuidance-v51-degisen-dosyalar.zip`; it is extracted over the PC root (Ahmet's approval needed). No files are deleted.
- **Deliverables** (`/mnt/project-files/drawguidance/v51/`): `DrawGuidance-v51.apk` and `.aab` (upload key, R8 + freeRASP), `DrawGuidance-v51-mapping.txt.gz`, `DrawGuidance-v51-ios-unsigned.ipa` (Device Farm).

## What's new in v51 (Ahmet's 9-item list)

1. **Device-to-device sharing and live canvas broadcast.** Wi-Fi LAN only (Ahmet's decision): Bonjour/NSD `_dgshare._tcp`, automatic discovery, and the connection opens by itself when you send or start a broadcast.
   - Entry points: the share page in Settings ("Bağlantı") and the top-right button in My Canvases.
   - Sending: a canvas or a saved image. On the receiving side it is saved to the database or files, and the canvas is marked "Paylaşılan".
   - Broadcast: the device that starts it picks a canvas; the other side sees the changes live.
   - Android: `data/share/*` and `ui/screens/share/*`. iOS: `Data/Share/*` and `Features/Share/ShareView.swift`. Info.plist has `NSLocalNetworkUsageDescription` and `NSBonjourServices`.
   - The shared flag is kept in SharedPreferences/UserDefaults (Room did not change).
2. **A single note-taking mode.** `SIMPLE_NOTES` was merged into `NOTES`, and NOTES now sits in the Simple drawing group with the single top menu.
   - Old records are read as NOTES (`CanvasKind.parse`, iOS `init(from:)`).
   - Removed from notes: eyedropper, guide tool, vector editing (point/line), vectorize, and the pen tool.
   - Editing an image in a note opens a temporary SIMPLE_ART canvas with auto-save off. "Kaydet" copies the image back into the note and deletes the temporary canvas (Android `data/notes/TempImageEdit.kt`, iOS `Data/Notes/TempImageEdit.swift`).
3. **Full-screen add windows in portrait** (characters, effects, practices, shapes), via `DockedSheets.fullEditorSheet`. On tablets the menu sits below the title area.
4. **Tutorials grouped by function** instead of by version (`TutorialScripts` `merge(...)`). NOTES uses the simple-notes groups. Kids: `kids_v40` plus `v50_trace_kids`.
5. **Practices:** 90 practice levels and better scoring. The banner above the drawing area is smaller in portrait and narrower in landscape. Nicer result window.
6. **Moving to a new practice:** the old canvas is saved and a new canvas of the same type, size and paper opens (Android `practiceInNewCanvas` + NavGraph `onSwitchCanvas`; iOS `CanvasEditHost` + `switchToCanvas`).
7. **Tablet:** the menu sits below the title area and the top menu window can be dragged.
8. **Practices unlocked with a rewarded ad.**
   - Real ad units:
     - Android rewarded: `ca-app-pub-4962587185426397/7207499530`
     - iOS app ID: `~5716509800`
     - iOS interstitial: `/8360799106`
     - iOS rewarded: `/2072778264`
   - Release builds only. iOS sets them in `project.yml` `DG_ADMOB_*`; Debug uses Google's test IDs.
9. **New brushes and settings (advanced mode):**
   - Helper brushes: Blur, Sharpen, Dodge, Burn, Sponge, Clone, Healing, Mixer.
   - Paint brushes: Acrylic, Oil Paint, Cloud/Smoke.
   - Tip, paint and pen settings: size min/max, hardness, aspect, angle, spacing, flow, wetness, pressure/tilt/velocity and so on. Names are translated in 7 languages.
   - `BrushType` has 11 new values; `ContentCodec` VERSION_V10 uses type codes 50–60. `BrushCategory` gained PAINT and HELPER.

## Merge decisions

- NavGraph: `onOpenCanvas` is a push, used by the note's temporary image canvas. `onSwitchCanvas` replaces the current editor, used for a new practice.
- `TutorialScripts` (Android + iOS): `NOTES, SIMPLE_NOTES` play the simple-notes function groups. The advanced `notes_*` groups and `notesAi` are no longer used.
- iOS `Android.strings` were regenerated after every merge with `python3 ios/tools/strings-export/convert.py android/app/src/main/res ios/DrawGuidance/Resources`.
- The ART-mode shape net was retrained to include the practice templates (`mode_art.bin`, Android + iOS).

## Fixes made during integration

- **iOS: cancelling a tutorial could leave the demo's last stroke on the user's canvas and undo history.** `TutorialController.cancel()` now closes the sandbox only after the cancelled demo ends.
- Tests: the impact-star density is measured by spike count, the suggestion-template count is 16, and the tutorial step counts are dynamic.

## Tests (ios/v51-entegrasyon)

- **iOS** (CI, 575b85f): unit tests 197/197, UI tests 8/8.
- **Android** `:app:testDebugUnitTest`: 988 tests. 18 fail and are not v51 regressions:
  - Environment (no network access in the cloud, no local server): Robolectric SDK 21/24/29 jars, `CanvasFlowTest`, `V33BackendTest`, `V46NewCharactersTest`.
  - Also fail on v50: `V17VectorizeTest`, `V25CanvasAdd3DTest`, `V37KidsMascotTest.suggestions…`.
  - Image/3D timeouts: `V23Model3DCanvasTest`, `V30ImageToolsTest.removeBackground…`.
- `core:content` 323/323 and `core:model` 34/34.

## Open items

- **Device Farm:** the unsigned .ipa is left to Device Farm's re-signing. Apple sign-in, StoreKit and local network sharing need an entitlement or permission there.
- **App Store:** the local network permission text is in InfoPlist.strings (7 languages). For AdMob, only Google's own `cstr6suwn9.skadnetwork` is in SKAdNetworkItems; add Google's full list of third-party buyers before release.
- **Sharing over Wi-Fi Direct/Bluetooth:** v51 is LAN only. Two devices on different networks (or with no router) can't see each other.
- iOS `TutorialScripts.VERSION` is still 50 (the regrouping didn't change the tutorial version).
