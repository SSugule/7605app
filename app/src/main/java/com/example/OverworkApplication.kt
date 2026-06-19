package com.example

import android.app.Application
import com.example.ui.CrashReporter

class OverworkApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize the automatic crash/bug reporting system
        CrashReporter.init(this)
    }
}
