package com.karapp.drawguidance.ui.screens.model3d.gl

import android.opengl.GLES20
import com.karapp.drawguidance.content.model3d.gl.GlApi
import java.nio.ByteBuffer
import java.nio.FloatBuffer

/** v24 [GlApi]'nin Android gerçeklemesi (OpenGL ES 2.0, GL iş parçacığında). */
class AndroidGl : GlApi {
    private val tmp = IntArray(1)

    override fun createShader(type: Int) = GLES20.glCreateShader(type)
    override fun shaderSource(shader: Int, source: String) = GLES20.glShaderSource(shader, source)
    override fun compileShader(shader: Int) = GLES20.glCompileShader(shader)
    override fun shaderCompiled(shader: Int): Boolean { GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, tmp, 0); return tmp[0] != 0 }
    override fun shaderInfoLog(shader: Int): String = GLES20.glGetShaderInfoLog(shader) ?: ""
    override fun deleteShader(shader: Int) = GLES20.glDeleteShader(shader)
    override fun createProgram() = GLES20.glCreateProgram()
    override fun attachShader(program: Int, shader: Int) = GLES20.glAttachShader(program, shader)
    override fun bindAttribLocation(program: Int, index: Int, name: String) = GLES20.glBindAttribLocation(program, index, name)
    override fun linkProgram(program: Int) = GLES20.glLinkProgram(program)
    override fun programLinked(program: Int): Boolean { GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, tmp, 0); return tmp[0] != 0 }
    override fun programInfoLog(program: Int): String = GLES20.glGetProgramInfoLog(program) ?: ""
    override fun deleteProgram(program: Int) = GLES20.glDeleteProgram(program)
    override fun useProgram(program: Int) = GLES20.glUseProgram(program)
    override fun uniformLocation(program: Int, name: String) = GLES20.glGetUniformLocation(program, name)
    override fun uniform1i(location: Int, v: Int) = GLES20.glUniform1i(location, v)
    override fun uniform1f(location: Int, v: Float) = GLES20.glUniform1f(location, v)
    override fun uniform2f(location: Int, a: Float, b: Float) = GLES20.glUniform2f(location, a, b)
    override fun uniform3f(location: Int, a: Float, b: Float, c: Float) = GLES20.glUniform3f(location, a, b, c)
    override fun uniform4f(location: Int, a: Float, b: Float, c: Float, d: Float) = GLES20.glUniform4f(location, a, b, c, d)
    override fun uniformMatrix4(location: Int, m: FloatArray) = GLES20.glUniformMatrix4fv(location, 1, false, m, 0)
    override fun uniformMatrix3(location: Int, m: FloatArray) = GLES20.glUniformMatrix3fv(location, 1, false, m, 0)
    override fun genBuffer(): Int { GLES20.glGenBuffers(1, tmp, 0); return tmp[0] }
    override fun deleteBuffer(id: Int) { tmp[0] = id; GLES20.glDeleteBuffers(1, tmp, 0) }
    override fun bindBuffer(target: Int, id: Int) = GLES20.glBindBuffer(target, id)
    override fun bufferData(target: Int, data: FloatBuffer, usage: Int) = GLES20.glBufferData(target, data.remaining() * 4, data, usage)
    override fun vertexAttribPointer(index: Int, size: Int, stride: Int, offset: Int) =
        GLES20.glVertexAttribPointer(index, size, GLES20.GL_FLOAT, false, stride, offset)
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
    override fun genTexture(): Int { GLES20.glGenTextures(1, tmp, 0); return tmp[0] }
    override fun deleteTexture(id: Int) { tmp[0] = id; GLES20.glDeleteTextures(1, tmp, 0) }
    override fun bindTexture(target: Int, id: Int) = GLES20.glBindTexture(target, id)
    override fun activeTexture(unit: Int) = GLES20.glActiveTexture(unit)
    override fun texParameteri(target: Int, name: Int, value: Int) = GLES20.glTexParameteri(target, name, value)
    override fun texImage2D(target: Int, level: Int, w: Int, h: Int, data: ByteBuffer?) =
        GLES20.glTexImage2D(target, level, GLES20.GL_RGBA, w, h, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, data)
    override fun generateMipmap(target: Int) = GLES20.glGenerateMipmap(target)
    override fun genFramebuffer(): Int { GLES20.glGenFramebuffers(1, tmp, 0); return tmp[0] }
    override fun deleteFramebuffer(id: Int) { tmp[0] = id; GLES20.glDeleteFramebuffers(1, tmp, 0) }
    override fun bindFramebuffer(target: Int, id: Int) = GLES20.glBindFramebuffer(target, id)
    override fun framebufferTexture2D(target: Int, attachment: Int, texTarget: Int, texture: Int, level: Int) =
        GLES20.glFramebufferTexture2D(target, attachment, texTarget, texture, level)
    override fun checkFramebufferStatus(target: Int) = GLES20.glCheckFramebufferStatus(target)
    override fun genRenderbuffer(): Int { GLES20.glGenRenderbuffers(1, tmp, 0); return tmp[0] }
    override fun deleteRenderbuffer(id: Int) { tmp[0] = id; GLES20.glDeleteRenderbuffers(1, tmp, 0) }
    override fun bindRenderbuffer(target: Int, id: Int) = GLES20.glBindRenderbuffer(target, id)
    override fun renderbufferStorage(target: Int, format: Int, w: Int, h: Int) = GLES20.glRenderbufferStorage(target, format, w, h)
    override fun framebufferRenderbuffer(target: Int, attachment: Int, rbTarget: Int, renderbuffer: Int) =
        GLES20.glFramebufferRenderbuffer(target, attachment, rbTarget, renderbuffer)
    override fun readPixels(x: Int, y: Int, w: Int, h: Int, out: ByteBuffer) =
        GLES20.glReadPixels(x, y, w, h, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, out)
    override fun getError() = GLES20.glGetError()
    override fun getString(name: Int): String? = GLES20.glGetString(name)
    override fun maxTextureSize(): Int { GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, tmp, 0); return tmp[0] }
}
