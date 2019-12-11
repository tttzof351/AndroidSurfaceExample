package com.example.surfacemeetup

import android.app.Application
import com.example.surfacemeetup.helpers.ContextHelper

class SfApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ContextHelper.appContext = applicationContext
    }
}