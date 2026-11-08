package com.karapp.drawguidance.content.model3d.gl

import java.nio.ByteBuffer
import java.nio.FloatBuffer

/**
 * v24 OpenGL ES 2.0 çağrılarının ince arayüzü. Ekran kartı çizicisi ([GlRenderer3]) yalnız bunu kullanır: telefonda
 * android.opengl.GLES20 ile, testlerde masaüstü EGL/GLES (Mesa) ile gerçeklenir; böylece aynı çizim kodu hem cihazda
 * hem test makinesinde çalışır. Tüm yöntemler GL iş parçacığında çağrılmalıdır.
 */
interface GlApi {
    fun createShader(type: Int): Int
    fun shaderSource(shader: Int, source: String)
    fun compileShader(shader: Int)
    fun shaderCompiled(shader: Int): Boolean
    fun shaderInfoLog(shader: Int): String
    fun deleteShader(shader: Int)

    fun createProgram(): Int
    fun attachShader(program: Int, shader: Int)
    fun bindAttribLocation(program: Int, index: Int, name: String)
    fun linkProgram(program: Int)
    fun programLinked(program: Int): Boolean
    fun programInfoLog(program: Int): String
    fun deleteProgram(program: Int)
    fun useProgram(program: Int)
    fun uniformLocation(program: Int, name: String): Int

    fun uniform1i(location: Int, v: Int)
    fun uniform1f(location: Int, v: Float)
    fun uniform2f(location: Int, a: Float, b: Float)
    fun uniform3f(location: Int, a: Float, b: Float, c: Float)
    fun uniform4f(location: Int, a: Float, b: Float, c: Float, d: Float)
    /** Sütun düzeninde 4×4 matris. */
    fun uniformMatrix4(location: Int, m: FloatArray)
    /** Sütun düzeninde 3×3 matris. */
    fun uniformMatrix3(location: Int, m: FloatArray)

    fun genBuffer(): Int
    fun deleteBuffer(id: Int)
    fun bindBuffer(target: Int, id: Int)
    /** [data]'nın konumundan sonuna kadarki değerleri yükler. */
    fun bufferData(target: Int, data: FloatBuffer, usage: Int)
    /** Ondalık öznitelik; [stride] ve [offset] bayt. */
    fun vertexAttribPointer(index: Int, size: Int, stride: Int, offset: Int)
    fun enableVertexAttribArray(index: Int)
    fun disableVertexAttribArray(index: Int)
    fun drawArrays(mode: Int, first: Int, count: Int)

    fun enable(cap: Int)
    fun disable(cap: Int)
    fun depthFunc(func: Int)
    fun depthMask(flag: Boolean)
    fun blendFuncSeparate(srcRgb: Int, dstRgb: Int, srcAlpha: Int, dstAlpha: Int)
    fun colorMask(r: Boolean, g: Boolean, b: Boolean, a: Boolean)
    fun cullFace(mode: Int)
    fun frontFace(mode: Int)
    fun polygonOffset(factor: Float, units: Float)
    fun clearColor(r: Float, g: Float, b: Float, a: Float)
    fun clear(mask: Int)
    fun viewport(x: Int, y: Int, w: Int, h: Int)

    fun genTexture(): Int
    fun deleteTexture(id: Int)
    fun bindTexture(target: Int, id: Int)
    fun activeTexture(unit: Int)
    fun texParameteri(target: Int, name: Int, value: Int)
    /** RGBA, bayt başına kanal; [data] null ise boş doku. */
    fun texImage2D(target: Int, level: Int, w: Int, h: Int, data: ByteBuffer?)
    fun generateMipmap(target: Int)

    fun genFramebuffer(): Int
    fun deleteFramebuffer(id: Int)
    fun bindFramebuffer(target: Int, id: Int)
    fun framebufferTexture2D(target: Int, attachment: Int, texTarget: Int, texture: Int, level: Int)
    fun checkFramebufferStatus(target: Int): Int
    fun genRenderbuffer(): Int
    fun deleteRenderbuffer(id: Int)
    fun bindRenderbuffer(target: Int, id: Int)
    fun renderbufferStorage(target: Int, format: Int, w: Int, h: Int)
    fun framebufferRenderbuffer(target: Int, attachment: Int, rbTarget: Int, renderbuffer: Int)

    /** RGBA bayt olarak okur (alt satırdan başlayarak). */
    fun readPixels(x: Int, y: Int, w: Int, h: Int, out: ByteBuffer)
    fun getError(): Int
    fun getString(name: Int): String?
    fun maxTextureSize(): Int
}

/** OpenGL ES 2.0 sabitleri. */
object GL {
    const val NO_ERROR = 0
    const val LINES = 0x0001
    const val TRIANGLES = 0x0004
    const val DEPTH_BUFFER_BIT = 0x0100
    const val COLOR_BUFFER_BIT = 0x4000
    const val ARRAY_BUFFER = 0x8892
    const val STATIC_DRAW = 0x88E4
    const val DYNAMIC_DRAW = 0x88E8
    const val VERTEX_SHADER = 0x8B31
    const val FRAGMENT_SHADER = 0x8B30
    const val DEPTH_TEST = 0x0B71
    const val BLEND = 0x0BE2
    const val CULL_FACE = 0x0B44
    const val POLYGON_OFFSET_FILL = 0x8037
    const val LESS = 0x0201
    const val LEQUAL = 0x0203
    const val ALWAYS = 0x0207
    const val ZERO = 0
    const val ONE = 1
    const val SRC_ALPHA = 0x0302
    const val ONE_MINUS_SRC_ALPHA = 0x0303
    const val FRONT = 0x0404
    const val BACK = 0x0405
    const val CW = 0x0900
    const val CCW = 0x0901
    const val TEXTURE_2D = 0x0DE1
    const val TEXTURE0 = 0x84C0
    const val TEXTURE_MAG_FILTER = 0x2800
    const val TEXTURE_MIN_FILTER = 0x2801
    const val TEXTURE_WRAP_S = 0x2802
    const val TEXTURE_WRAP_T = 0x2803
    const val NEAREST = 0x2600
    const val LINEAR = 0x2601
    const val LINEAR_MIPMAP_LINEAR = 0x2703
    const val REPEAT = 0x2901
    const val CLAMP_TO_EDGE = 0x812F
    const val FRAMEBUFFER = 0x8D40
    const val RENDERBUFFER = 0x8D41
    const val COLOR_ATTACHMENT0 = 0x8CE0
    const val DEPTH_ATTACHMENT = 0x8D00
    const val FRAMEBUFFER_COMPLETE = 0x8CD5
    const val DEPTH_COMPONENT16 = 0x81A5
    const val RGBA4 = 0x8056
    const val VENDOR = 0x1F00
    const val RENDERER = 0x1F01
    const val VERSION = 0x1F02
}
