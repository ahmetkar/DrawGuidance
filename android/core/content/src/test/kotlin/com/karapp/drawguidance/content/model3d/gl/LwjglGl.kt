package com.karapp.drawguidance.content.model3d.gl

import org.lwjgl.PointerBuffer
import org.lwjgl.egl.EGL10
import org.lwjgl.opengles.GLES
import org.lwjgl.opengles.GLES20
import org.lwjgl.system.Configuration
import org.lwjgl.system.MemoryStack
import java.nio.ByteBuffer
import java.nio.FloatBuffer

/** Testlerde [GlApi]: masaüstü OpenGL ES 2.0 (LWJGL, Mesa). */
class LwjglGl : GlApi {
    override fun createShader(type: Int) = GLES20.glCreateShader(type)
    override fun shaderSource(shader: Int, source: String) = GLES20.glShaderSource(shader, source)
    override fun compileShader(shader: Int) = GLES20.glCompileShader(shader)
    override fun shaderCompiled(shader: Int) = GLES20.glGetShaderi(shader, 0x8B81) != 0
    override fun shaderInfoLog(shader: Int): String = GLES20.glGetShaderInfoLog(shader)
    override fun deleteShader(shader: Int) = GLES20.glDeleteShader(shader)
    override fun createProgram() = GLES20.glCreateProgram()
    override fun attachShader(program: Int, shader: Int) = GLES20.glAttachShader(program, shader)
    override fun bindAttribLocation(program: Int, index: Int, name: String) = GLES20.glBindAttribLocation(program, index, name)
    override fun linkProgram(program: Int) = GLES20.glLinkProgram(program)
    override fun programLinked(program: Int) = GLES20.glGetProgrami(program, 0x8B82) != 0
    override fun programInfoLog(program: Int): String = GLES20.glGetProgramInfoLog(program)
    override fun deleteProgram(program: Int) = GLES20.glDeleteProgram(program)
    override fun useProgram(program: Int) = GLES20.glUseProgram(program)
    override fun uniformLocation(program: Int, name: String) = GLES20.glGetUniformLocation(program, name)
    override fun uniform1i(location: Int, v: Int) = GLES20.glUniform1i(location, v)
    override fun uniform1f(location: Int, v: Float) = GLES20.glUniform1f(location, v)
    override fun uniform2f(location: Int, a: Float, b: Float) = GLES20.glUniform2f(location, a, b)
    override fun uniform3f(location: Int, a: Float, b: Float, c: Float) = GLES20.glUniform3f(location, a, b, c)
    override fun uniform4f(location: Int, a: Float, b: Float, c: Float, d: Float) = GLES20.glUniform4f(location, a, b, c, d)
    override fun uniformMatrix4(location: Int, m: FloatArray) = GLES20.glUniformMatrix4fv(location, false, m)
    override fun uniformMatrix3(location: Int, m: FloatArray) = GLES20.glUniformMatrix3fv(location, false, m)
    override fun genBuffer() = GLES20.glGenBuffers()
    override fun deleteBuffer(id: Int) = GLES20.glDeleteBuffers(id)
    override fun bindBuffer(target: Int, id: Int) = GLES20.glBindBuffer(target, id)
    override fun bufferData(target: Int, data: FloatBuffer, usage: Int) = GLES20.glBufferData(target, data, usage)
    override fun vertexAttribPointer(index: Int, size: Int, stride: Int, offset: Int) =
        GLES20.glVertexAttribPointer(index, size, GLES20.GL_FLOAT, false, stride, offset.toLong())
    override fun enableVertexAttribArray(index: Int) = GLES20.glEnableVertexAttribArray(index)
    override fun disableVertexAttribArray(index: Int) = GLES20.glDisableVertexAttribArray(index)
    override fun drawArrays(mode: Int, first: Int, count: Int) = GLES20.glDrawArrays(mode, first, count)
    override fun enable(cap: Int) = GLES20.glEnable(cap)
    override fun disable(cap: Int) = GLES20.glDisable(cap)
    override fun depthFunc(func: Int) = GLES20.glDepthFunc(func)
    override fun depthMask(flag: Boolean) = GLES20.glDepthMask(flag)
    override fun blendFuncSeparate(srcRgb: Int, dstRgb: Int, srcAlpha: Int, dstAlpha: Int) = GLES20.glBlendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha)
    override fun colorMask(r: Boolean, g: Boolean, b: Boolean, a: Boolean) = GLES20.glColorMask(r, g, b, a)
    override fun cullFace(mode: Int) = GLES20.glCullFace(mode)
    override fun frontFace(mode: Int) = GLES20.glFrontFace(mode)
    override fun polygonOffset(factor: Float, units: Float) = GLES20.glPolygonOffset(factor, units)
    override fun clearColor(r: Float, g: Float, b: Float, a: Float) = GLES20.glClearColor(r, g, b, a)
    override fun clear(mask: Int) = GLES20.glClear(mask)
    override fun viewport(x: Int, y: Int, w: Int, h: Int) = GLES20.glViewport(x, y, w, h)
    override fun genTexture() = GLES20.glGenTextures()
    override fun deleteTexture(id: Int) = GLES20.glDeleteTextures(id)
    override fun bindTexture(target: Int, id: Int) = GLES20.glBindTexture(target, id)
    override fun activeTexture(unit: Int) = GLES20.glActiveTexture(unit)
    override fun texParameteri(target: Int, name: Int, value: Int) = GLES20.glTexParameteri(target, name, value)
    override fun texImage2D(target: Int, level: Int, w: Int, h: Int, data: ByteBuffer?) =
        GLES20.glTexImage2D(target, level, GLES20.GL_RGBA, w, h, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, data)
    override fun generateMipmap(target: Int) = GLES20.glGenerateMipmap(target)
    override fun genFramebuffer() = GLES20.glGenFramebuffers()
    override fun deleteFramebuffer(id: Int) = GLES20.glDeleteFramebuffers(id)
    override fun bindFramebuffer(target: Int, id: Int) = GLES20.glBindFramebuffer(target, id)
    override fun framebufferTexture2D(target: Int, attachment: Int, texTarget: Int, texture: Int, level: Int) =
        GLES20.glFramebufferTexture2D(target, attachment, texTarget, texture, level)
    override fun checkFramebufferStatus(target: Int) = GLES20.glCheckFramebufferStatus(target)
    override fun genRenderbuffer() = GLES20.glGenRenderbuffers()
    override fun deleteRenderbuffer(id: Int) = GLES20.glDeleteRenderbuffers(id)
    override fun bindRenderbuffer(target: Int, id: Int) = GLES20.glBindRenderbuffer(target, id)
    override fun renderbufferStorage(target: Int, format: Int, w: Int, h: Int) = GLES20.glRenderbufferStorage(target, format, w, h)
    override fun framebufferRenderbuffer(target: Int, attachment: Int, rbTarget: Int, renderbuffer: Int) =
        GLES20.glFramebufferRenderbuffer(target, attachment, rbTarget, renderbuffer)
    override fun readPixels(x: Int, y: Int, w: Int, h: Int, out: ByteBuffer) =
        GLES20.glReadPixels(x, y, w, h, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, out)
    override fun getError() = GLES20.glGetError()
    override fun getString(name: Int): String? = GLES20.glGetString(name)
    override fun maxTextureSize() = GLES20.glGetInteger(0x0D33)

    companion object {
        private var created: Boolean? = null

        /**
         * Ekransız EGL bağlamı (Mesa "surfaceless"; ortam değişkeni EGL_PLATFORM=surfaceless). Yoksa false: test
         * atlanır (ör. Windows'ta).
         */
        @Synchronized
        fun ensureContext(): Boolean {
            created?.let { return it }
            val ok = runCatching {
                Configuration.DISABLE_CHECKS.set(true)
                Configuration.EGL_LIBRARY_NAME.set("libEGL.so.1")
                Configuration.OPENGLES_LIBRARY_NAME.set("libGLESv2.so.2")
                MemoryStack.stackPush().use { st ->
                    val dpy = EGL10.eglGetDisplay(0L)
                    require(dpy != 0L) { "EGL ekranı yok" }
                    require(EGL10.eglInitialize(dpy, st.mallocInt(1), st.mallocInt(1))) { "EGL başlatılamadı" }
                    org.lwjgl.egl.EGL12.eglBindAPI(0x30A0)
                    val configs: PointerBuffer = st.mallocPointer(1)
                    val num = st.mallocInt(1)
                    EGL10.eglChooseConfig(dpy, st.ints(0x3040, 4, 0x3038), configs, num)
                    val cfg = if (num[0] > 0) configs[0] else 0L
                    val ctx = EGL10.eglCreateContext(dpy, cfg, 0L, st.ints(0x3098, 2, 0x3038))
                    require(ctx != 0L) { "EGL bağlamı kurulamadı" }
                    require(EGL10.eglMakeCurrent(dpy, 0L, 0L, ctx)) { "EGL bağlamı etkinleşmedi" }
                    GLES.createCapabilities()
                }
                true
            }.onFailure { println("GL testi atlandı: $it"); it.printStackTrace(System.out) }.getOrDefault(false)
            created = ok
            return ok
        }
    }
}

/** Ekran dışı çizim yüzeyi: renk dokusu + derinlik. */
class OffscreenTarget(private val gl: GlApi, val w: Int, val h: Int) {
    val fbo = gl.genFramebuffer()
    private val tex = gl.genTexture()
    private val depth = gl.genRenderbuffer()

    init {
        gl.bindTexture(GL.TEXTURE_2D, tex)
        gl.texParameteri(GL.TEXTURE_2D, GL.TEXTURE_MIN_FILTER, GL.NEAREST)
        gl.texParameteri(GL.TEXTURE_2D, GL.TEXTURE_MAG_FILTER, GL.NEAREST)
        gl.texImage2D(GL.TEXTURE_2D, 0, w, h, null)
        gl.bindRenderbuffer(GL.RENDERBUFFER, depth)
        gl.renderbufferStorage(GL.RENDERBUFFER, GL.DEPTH_COMPONENT16, w, h)
        gl.bindFramebuffer(GL.FRAMEBUFFER, fbo)
        gl.framebufferTexture2D(GL.FRAMEBUFFER, GL.COLOR_ATTACHMENT0, GL.TEXTURE_2D, tex, 0)
        gl.framebufferRenderbuffer(GL.FRAMEBUFFER, GL.DEPTH_ATTACHMENT, GL.RENDERBUFFER, depth)
        check(gl.checkFramebufferStatus(GL.FRAMEBUFFER) == GL.FRAMEBUFFER_COMPLETE) { "FBO eksik" }
        gl.bindFramebuffer(GL.FRAMEBUFFER, 0)
    }

    /** Pikseller ARGB, üst satırdan (önçarpımsız). */
    fun read(): IntArray {
        gl.bindFramebuffer(GL.FRAMEBUFFER, fbo)
        val buf = ByteBuffer.allocateDirect(w * h * 4)
        gl.readPixels(0, 0, w, h, buf)
        val out = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            val i = ((h - 1 - y) * w + x) * 4
            val r = buf.get(i).toInt() and 0xFF; val g = buf.get(i + 1).toInt() and 0xFF
            val b = buf.get(i + 2).toInt() and 0xFF; val a = buf.get(i + 3).toInt() and 0xFF
            out[y * w + x] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }
        gl.bindFramebuffer(GL.FRAMEBUFFER, 0)
        return out
    }

    fun release() {
        gl.deleteFramebuffer(fbo); gl.deleteTexture(tex); gl.deleteRenderbuffer(depth)
    }
}
