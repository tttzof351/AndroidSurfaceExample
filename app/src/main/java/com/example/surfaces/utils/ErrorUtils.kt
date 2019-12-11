package com.example.surfaces.utils

import android.opengl.GLES20
import android.util.Log

object ErrorUtils {
    private val tag = ErrorUtils::class.java.simpleName

    fun checkGlError(op: String) {
        val error = GLES20.glGetError()
        if (error != GLES20.GL_NO_ERROR) {
            Log.e(tag, "$op: glError $error")
            throw RuntimeException("$op: glError $error")
        }
    }
}