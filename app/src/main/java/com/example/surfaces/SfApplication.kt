package com.example.surfaces

import android.app.Application
import com.example.surfaces.helpers.ContextHelper

class SfApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ContextHelper.appContext = applicationContext
    }
}