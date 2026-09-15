package com.example.chapter_205

import android.app.Application

// TODO: [todos/android-application-class-lifecycle.md](../../../../../../../../todos/android-application-class-lifecycle.md)
class WishListApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // TODO: [todos/android-context-types-and-application-context.md](../../../../../../../../todos/android-context-types-and-application-context.md)
        Graph.provide(this)
    }
}