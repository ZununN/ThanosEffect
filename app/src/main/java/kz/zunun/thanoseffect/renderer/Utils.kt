package kz.zunun.thanoseffect.renderer

import android.content.Context
import android.content.res.Resources
import android.opengl.GLES20
import android.util.Log
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader


fun readTextFromRaw(context: Context, resourceId: Int): String {
    val stringBuilder = StringBuilder()
    try {
        var bufferedReader: BufferedReader? = null
        try {
            val inputStream = context.resources.openRawResource(resourceId)
            bufferedReader = BufferedReader(InputStreamReader(inputStream))
            var line: String?
            while (bufferedReader.readLine().also { line = it } != null) {
                stringBuilder.append(line)
                stringBuilder.append("\r\n")
            }
        } finally {
            bufferedReader?.close()
        }
    } catch (ex: IOException) {
        ex.printStackTrace()
    } catch (ex: Resources.NotFoundException) {
        ex.printStackTrace()
    }
    return stringBuilder.toString()
}

fun createProgram(vertexShaderId: Int, fragmentShaderId: Int): Int {
    val programId = GLES20.glCreateProgram()
    if (programId == 0) {
        return 0
    }
    GLES20.glAttachShader(programId, vertexShaderId)
    GLES20.glAttachShader(programId, fragmentShaderId)
    GLES20.glLinkProgram(programId)
    val linkStatus = IntArray(1)
    GLES20.glGetProgramiv(programId, GLES20.GL_LINK_STATUS, linkStatus, 0)
    if (linkStatus[0] == 0) {
        GLES20.glDeleteProgram(programId)
        return 0
    }
    return programId
}

fun createShader(context: Context, type: Int, shaderRawId: Int): Int {
    val shaderText = readTextFromRaw(
        context, shaderRawId
    )
    return createShader(type, shaderText)
}

private fun createShader(type: Int, shaderText: String?): Int {
    val shaderId = GLES20.glCreateShader(type)
    if (shaderId == 0) {
        return 0
    }
    GLES20.glShaderSource(shaderId, shaderText)
    GLES20.glCompileShader(shaderId)
    val compileStatus = IntArray(1)
    GLES20.glGetShaderiv(shaderId, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
    if (compileStatus[0] == 0) {
        Log.e(
            "ShaderUtils",
            "Shader compilation failure. Reason: " + GLES20.glGetShaderInfoLog(shaderId)
        )
        GLES20.glDeleteShader(shaderId)
        return 0
    }
    return shaderId
}