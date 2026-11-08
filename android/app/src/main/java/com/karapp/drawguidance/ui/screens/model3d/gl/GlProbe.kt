package com.karapp.drawguidance.ui.screens.model3d.gl

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES20
import com.karapp.drawguidance.content.model3d.Catalog3D
import com.karapp.drawguidance.content.model3d.View3
import com.karapp.drawguidance.content.model3d.gl.GL
import com.karapp.drawguidance.content.model3d.gl.GlOptions
import com.karapp.drawguidance.content.model3d.gl.GlRenderer3

/**
 * v24 ekran kartı sınaması: görünmez küçük bir yüzeyde (pbuffer) OpenGL ES 2.0 bağlamı kurulur, çizicinin
 * gölgelendiricileri derlenir ve bir hazır şekil çizilir. Başarısızsa 3B düzenleyici yazılım çiziciyle çalışır (ekran
 * kartı penceresi hiç açılmaz; böylece sürücü hatası uygulamayı kapatamaz). Arka plan iş parçacığında çağrılır.
 */
object GlProbe {

    class Result(val ok: Boolean, val info: String)

    fun run(): Result {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (display == EGL14.EGL_NO_DISPLAY) return Result(false, "EGL yok")
        val version = IntArray(2)
        if (!EGL14.eglInitialize(display, version, 0, version, 1)) return Result(false, "EGL başlatılamadı")
        var context = EGL14.EGL_NO_CONTEXT
        var surface = EGL14.EGL_NO_SURFACE
        try {
            val attribs = intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_RED_SIZE, 5, EGL14.EGL_GREEN_SIZE, 6, EGL14.EGL_BLUE_SIZE, 5,
                EGL14.EGL_DEPTH_SIZE, 16,
                EGL14.EGL_NONE,
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val num = IntArray(1)
            if (!EGL14.eglChooseConfig(display, attribs, 0, configs, 0, 1, num, 0) || num[0] == 0) return Result(false, "Uygun EGL yapılandırması yok")
            val config = configs[0]
            context = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
            if (context == null || context == EGL14.EGL_NO_CONTEXT) return Result(false, "OpenGL ES 2.0 bağlamı kurulamadı")
            surface = EGL14.eglCreatePbufferSurface(display, config, intArrayOf(EGL14.EGL_WIDTH, SIZE, EGL14.EGL_HEIGHT, SIZE, EGL14.EGL_NONE), 0)
            if (surface == null || surface == EGL14.EGL_NO_SURFACE) return Result(false, "Görünmez yüzey kurulamadı")
            if (!EGL14.eglMakeCurrent(display, surface, surface, context)) return Result(false, "Bağlam etkinleştirilemedi")
            val renderer = GlProbeRenderer()
            return renderer.check()
        } catch (t: Throwable) {
            return Result(false, t.message ?: t.javaClass.simpleName)
        } finally {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            if (surface != null && surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
            if (context != null && context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
            EGL14.eglReleaseThread()
        }
    }

    private class GlProbeRenderer {
        fun check(): Result {
            val gl = AndroidGl()
            val r = GlRenderer3(gl)
            val name = listOfNotNull(GLES20.glGetString(GLES20.GL_RENDERER), GLES20.glGetString(GLES20.GL_VERSION)).joinToString(" · ")
            if (!r.init()) return Result(false, r.lastError ?: "Gölgelendirici derlenemedi")
            val m = Catalog3D.byId("geo.cube_diag")?.build() ?: return Result(true, name)
            r.render(m, View3.fit(m, m.camera, SIZE, SIZE), GlOptions(clearColor = -1))
            val err = gl.getError()
            r.release()
            return if (err == GL.NO_ERROR) Result(true, name) else Result(false, "GL hatası $err")
        }
    }

    private const val SIZE = 64
}
