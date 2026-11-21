package com.karapp.drawguidance.ui.screens.sketch3d

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/**
 * v56 aşama 13 dönen videonun MP4 yazıcısı: H.264 kodlayıcının giriş yüzeyine her kare donanım tuvaliyle çizilir,
 * MediaMuxer ile MP4'e konur. [publish] dosyayı galeriye (Movies/DrawGuidance ya da GIF için Pictures/DrawGuidance)
 * kopyalar.
 */
object OrbitMp4 {

    private const val MIME = MediaFormat.MIMETYPE_VIDEO_AVC
    private const val TIMEOUT_US = 10_000L

    /** [frame] i. karenin ARGB pikselleri (boyu w·h). */
    fun encode(out: File, w: Int, h: Int, fps: Int, frames: Int, frame: (Int) -> IntArray) {
        val format = MediaFormat.createVideoFormat(MIME, w, h).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, w * h * 4)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        val codec = MediaCodec.createEncoderByType(MIME)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val surface = codec.createInputSurface()
        codec.start()
        val muxer = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val info = MediaCodec.BufferInfo()
        var track = -1
        var muxing = false
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        fun drain(end: Boolean) {
            while (true) {
                val idx = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                when {
                    idx == MediaCodec.INFO_TRY_AGAIN_LATER -> if (!end) return
                    idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> { track = muxer.addTrack(codec.outputFormat); muxer.start(); muxing = true }
                    idx >= 0 -> {
                        val buf = codec.getOutputBuffer(idx)
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                        if (info.size > 0 && muxing && buf != null) {
                            buf.position(info.offset); buf.limit(info.offset + info.size)
                            muxer.writeSampleData(track, buf, info)
                        }
                        codec.releaseOutputBuffer(idx, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                    }
                }
            }
        }
        try {
            for (i in 0 until frames) {
                bmp.setPixels(frame(i), 0, w, 0, 0, w, h)
                val c = surface.lockHardwareCanvas()
                try { c.drawBitmap(bmp, 0f, 0f, null) } finally { surface.unlockCanvasAndPost(c) }
                drain(false)
            }
            codec.signalEndOfInputStream()
            drain(true)
        } finally {
            bmp.recycle()
            runCatching { codec.stop() }; codec.release(); surface.release()
            if (muxing) runCatching { muxer.stop() }
            muxer.release()
        }
    }

    /** Dosyayı galeriye kopyalar; içerik adresi (eski sürümlerde dosya yolu) döner. */
    fun publish(context: Context, file: File, mime: String): String? = runCatching {
        val video = mime.startsWith("video/")
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, (if (video) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES) + "/DrawGuidance")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val coll = if (video) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) else MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri = context.contentResolver.insert(coll, values) ?: return@runCatching null
            context.contentResolver.openOutputStream(uri)?.use { o -> file.inputStream().use { it.copyTo(o) } }
            values.clear(); values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
            uri.toString()
        } else {
            @Suppress("DEPRECATION")
            val dir = File(Environment.getExternalStoragePublicDirectory(if (video) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES), "DrawGuidance")
            dir.mkdirs()
            val dst = File(dir, file.name)
            file.copyTo(dst, overwrite = true)
            dst.absolutePath
        }
    }.getOrNull()
}
