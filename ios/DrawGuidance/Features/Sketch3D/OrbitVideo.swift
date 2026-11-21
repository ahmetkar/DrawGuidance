import AVFoundation
import CoreGraphics
import Foundation
import Photos

/// v56 aşama 13 dönen videonun MP4 yazıcısı (Android `OrbitMp4`): AVAssetWriter + piksel arabelleği bağdaştırıcısı,
/// H.264. [save] dosyayı Fotoğraflar'a ekler (izin istenir).
enum OrbitVideo {

    /// [frame] i. karenin ARGB pikselleri (boyu w·h). Başarılıysa true.
    static func writeMp4(_ url: URL, _ w: Int, _ h: Int, _ fps: Int, _ frames: Int, _ frame: (Int) -> [UInt32]) -> Bool {
        try? FileManager.default.removeItem(at: url)
        guard let writer = try? AVAssetWriter(outputURL: url, fileType: .mp4) else { return false }
        let settings: [String: Any] = [
            AVVideoCodecKey: AVVideoCodecType.h264, AVVideoWidthKey: w, AVVideoHeightKey: h,
            AVVideoCompressionPropertiesKey: [AVVideoAverageBitRateKey: w * h * 4],
        ]
        let input = AVAssetWriterInput(mediaType: .video, outputSettings: settings)
        input.expectsMediaDataInRealTime = false
        let adaptor = AVAssetWriterInputPixelBufferAdaptor(assetWriterInput: input, sourcePixelBufferAttributes: [
            kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32BGRA, kCVPixelBufferWidthKey as String: w, kCVPixelBufferHeightKey as String: h,
        ])
        guard writer.canAdd(input) else { return false }
        writer.add(input)
        guard writer.startWriting() else { return false }
        writer.startSession(atSourceTime: .zero)
        for i in 0..<frames {
            while !input.isReadyForMoreMediaData { Thread.sleep(forTimeInterval: 0.005) }
            guard let pool = adaptor.pixelBufferPool else { return false }
            var pb: CVPixelBuffer?
            CVPixelBufferPoolCreatePixelBuffer(nil, pool, &pb)
            guard let buf = pb else { return false }
            let px = frame(i)
            CVPixelBufferLockBaseAddress(buf, [])
            if let base = CVPixelBufferGetBaseAddress(buf) {
                let row = CVPixelBufferGetBytesPerRow(buf)
                let dst = base.assumingMemoryBound(to: UInt8.self)
                // ARGB (UInt32) → BGRA bayt sırası
                for y in 0..<h {
                    let o = y * row
                    for x in 0..<w {
                        let c = px[y * w + x]
                        let d = o + x * 4
                        dst[d] = UInt8(c & 0xFF); dst[d + 1] = UInt8(c >> 8 & 0xFF); dst[d + 2] = UInt8(c >> 16 & 0xFF); dst[d + 3] = 0xFF
                    }
                }
            }
            CVPixelBufferUnlockBaseAddress(buf, [])
            if !adaptor.append(buf, withPresentationTime: CMTime(value: CMTimeValue(i), timescale: CMTimeScale(fps))) { return false }
        }
        input.markAsFinished()
        let done = DispatchSemaphore(value: 0)
        writer.finishWriting { done.signal() }
        done.wait()
        return writer.status == .completed
    }

    /// Videoyu ya da GIF'i Fotoğraflar'a ekler.
    static func save(_ url: URL, completion: @escaping (Bool) -> Void) {
        PHPhotoLibrary.requestAuthorization(for: .addOnly) { status in
            guard status == .authorized || status == .limited else { completion(false); return }
            PHPhotoLibrary.shared().performChanges({
                let req = PHAssetCreationRequest.forAsset()
                req.addResource(with: url.pathExtension == "gif" ? .photo : .video, fileURL: url, options: nil)
            }) { ok, _ in completion(ok) }
        }
    }
}
