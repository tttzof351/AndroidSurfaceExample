package com.example.surfaces.utils

import android.graphics.Bitmap
import android.opengl.GLException
import java.nio.IntBuffer
import javax.microedition.khronos.opengles.GL10


object SurfaceUtils {
    @Throws(OutOfMemoryError::class)
    fun createBitmapFromGLSurface(
        x: Int,
        y: Int,
        sceneWidth: Int,
        sceneHeight: Int,
        gl: GL10
    ): Bitmap? {
        val bitmapBuffer = IntArray(sceneWidth * sceneHeight)
        val bitmapSource = IntArray(sceneWidth * sceneHeight)
        val intBuffer = IntBuffer.wrap(bitmapBuffer)
        intBuffer.position(0)

        try {
            gl.glReadPixels(x, y, sceneWidth, sceneHeight, GL10.GL_RGBA, GL10.GL_UNSIGNED_BYTE, intBuffer)
            var offset1: Int
            var offset2: Int
            for (i in 0 until sceneHeight) {
                offset1 = i * sceneWidth
                offset2 = (sceneHeight - i - 1) * sceneWidth
                for (j in 0 until sceneWidth) {
                    val texturePixel = bitmapBuffer[offset1 + j]
                    val blue = texturePixel shr 16 and 0xff
                    val red = texturePixel shl 16 and 0x00ff0000
                    val pixel = texturePixel and -0xff0100 or red or blue
                    bitmapSource[offset2 + j] = pixel
                }
            }
        } catch (e: GLException) {
            return null
        }

        return Bitmap.createBitmap(bitmapSource, sceneWidth, sceneHeight, Bitmap.Config.ARGB_8888)
    }

}