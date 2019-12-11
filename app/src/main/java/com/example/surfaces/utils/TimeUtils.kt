package com.example.surfaces.utils

import android.os.SystemClock

object TimeUtils {
    fun now() = SystemClock.elapsedRealtime()
}