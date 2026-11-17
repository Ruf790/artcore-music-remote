package com.ruf790.artcore.remote

import android.app.Application
import android.content.Context

class App : Application() {
    lateinit var remote: Remote
        private set

    override fun onCreate() {
        super.onCreate()
        remote = Remote(this)
    }
}

val Context.remote: Remote get() = (applicationContext as App).remote
