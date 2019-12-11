package com.example.surfaces.helpers

import android.content.Context
import android.graphics.Point
import android.view.WindowManager


object ContextHelper {
    lateinit var appContext: Context

    fun getDisplayWidth() = getDisplay().x

    private fun getDisplay(): Point {
        val wm = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val display = wm.defaultDisplay
        val size = Point()
        display.getSize(size)
        return size
    }
}