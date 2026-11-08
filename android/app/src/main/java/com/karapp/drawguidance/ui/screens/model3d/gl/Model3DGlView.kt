package com.karapp.drawguidance.ui.screens.model3d.gl

import android.annotation.SuppressLint
import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.SystemClock
import com.karapp.drawguidance.content.model3d.Model3D
import com.karapp.drawguidance.content.model3d.View3
import com.karapp.drawguidance.content.model3d.gl.GlOptions
import com.karapp.drawguidance.content.model3d.gl.GlRenderer3
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLDisplay
import javax.microedition.khronos.opengles.GL10

/**
 * v24 3B sahnenin ekran kartı görünümü (GLSurfaceView): yalnız istendiğinde çizer (sahne değişince), dokunuşlar ve
 * işaretler üstteki Compose katmanındadır. [samples] kenar yumuşatma örneği (0, 2, 4; desteklenmezse kapalı),
 * [renderScale] çizim çözünürlüğü (hızlı kalitede ¾; görüntü ekrana büyütülür). Hata olursa [onFailure] çağrılır ve
 * düzenleyici yazılım çiziciye geçer.
 */
@SuppressLint("ViewConstructor")
class Model3DGlView(
    context: Context,
    samples: Int,
    private val renderScale: Float,
    private val onFailure: (String) -> Unit,
    private val onInfo: (String) -> Unit,
) : GLSurfaceView(context) {

    /** Çizilecek sahne (UI iş parçacığından gönderilir, GL iş parçacığında okunur). */
    class Scene(val model: Model3D, val options: GlOptions)

    @Volatile private var scene: Scene? = null
    @Volatile private var failed = false
    @Volatile var lastFrameMs: Float = 0f
        private set

    init {
        setEGLContextClientVersion(2)
        setEGLConfigChooser(ConfigChooser(samples))
        preserveEGLContextOnPause = true
        setRenderer(SceneRenderer())
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    fun submit(s: Scene) {
        scene = s
        if (!failed) requestRender()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (renderScale < 0.999f && w > 0 && h > 0) holder.setFixedSize((w * renderScale).toInt().coerceAtLeast(1), (h * renderScale).toInt().coerceAtLeast(1))
    }

    private fun fail(reason: String) {
        if (failed) return
        failed = true
        post { onFailure(reason) }
    }

    private inner class SceneRenderer : Renderer {
        // v25 gölge dokusu cihaz sınıfına göre (giriş düzeyinde 128 px)
        private val renderer = GlRenderer3(AndroidGl(), com.karapp.drawguidance.ui.screens.model3d.Budget3D.shadowSize)
        private var width = 1
        private var height = 1

        override fun onSurfaceCreated(unused: GL10?, config: EGLConfig?) {
            renderer.contextLost()
            if (!renderer.init()) { fail(renderer.lastError ?: "OpenGL ES başlatılamadı"); return }
            val name = GLES20.glGetString(GLES20.GL_RENDERER) ?: ""
            post { onInfo(name) }
        }

        override fun onSurfaceChanged(unused: GL10?, w: Int, h: Int) {
            width = w.coerceAtLeast(1); height = h.coerceAtLeast(1)
            GLES20.glViewport(0, 0, width, height)
        }

        override fun onDrawFrame(unused: GL10?) {
            val s = scene
            if (s == null || failed) {
                GLES20.glClearColor(1f, 1f, 1f, 1f)
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
                return
            }
            val t0 = SystemClock.elapsedRealtimeNanos()
            try {
                renderer.render(s.model, View3.editor(s.model.camera, width, height), s.options, 0)
            } catch (t: Throwable) {
                fail(t.message ?: t.javaClass.simpleName)
            }
            lastFrameMs = (SystemClock.elapsedRealtimeNanos() - t0) / 1e6f
        }
    }

    /**
     * Yapılandırma seçimi: önce istenen kenar yumuşatmayla 24/16 bit derinlik, sonra yumuşatmasız, sonra 565 ve en
     * son herhangi bir OpenGL ES 2.0 yapılandırması.
     */
    private class ConfigChooser(private val samples: Int) : EGLConfigChooser {
        override fun chooseConfig(egl: EGL10, display: EGLDisplay): EGLConfig? {
            val specs = ArrayList<IntArray>()
            for (ms in listOf(samples, 0).distinct()) {
                specs += spec(8, 24, ms); specs += spec(8, 16, ms)
            }
            specs += spec(5, 16, 0)
            specs += intArrayOf(EGL10.EGL_RENDERABLE_TYPE, ES2_BIT, EGL10.EGL_NONE)
            for (s in specs) pick(egl, display, s)?.let { return it }
            return null
        }

        private fun spec(bits: Int, depth: Int, ms: Int): IntArray {
            val list = arrayListOf(
                EGL10.EGL_RED_SIZE, bits, EGL10.EGL_GREEN_SIZE, if (bits == 5) 6 else bits, EGL10.EGL_BLUE_SIZE, bits,
                EGL10.EGL_DEPTH_SIZE, depth, EGL10.EGL_RENDERABLE_TYPE, ES2_BIT,
            )
            if (ms > 1) { list += EGL10.EGL_SAMPLE_BUFFERS; list += 1; list += EGL10.EGL_SAMPLES; list += ms }
            list += EGL10.EGL_NONE
            return list.toIntArray()
        }

        private fun pick(egl: EGL10, display: EGLDisplay, spec: IntArray): EGLConfig? {
            val num = IntArray(1)
            if (!egl.eglChooseConfig(display, spec, null, 0, num) || num[0] <= 0) return null
            val configs = arrayOfNulls<EGLConfig>(num[0])
            if (!egl.eglChooseConfig(display, spec, configs, num[0], num)) return null
            val red = valueOf(spec, EGL10.EGL_RED_SIZE)
            // Tam eşleşen renk derinliği önce (ör. 8 bit isterken 10 bit değil)
            return configs.filterNotNull().firstOrNull { c -> red == null || attr(egl, display, c, EGL10.EGL_RED_SIZE) == red } ?: configs.firstOrNull()
        }

        private fun valueOf(spec: IntArray, key: Int): Int? {
            var i = 0
            while (i + 1 < spec.size) { if (spec[i] == key) return spec[i + 1]; i += 2 }
            return null
        }

        private fun attr(egl: EGL10, display: EGLDisplay, c: EGLConfig, key: Int): Int {
            val v = IntArray(1)
            return if (egl.eglGetConfigAttrib(display, c, key, v)) v[0] else -1
        }

        companion object {
            private const val ES2_BIT = 4
        }
    }
}
