package com.example.chapter_205

import android.app.Application

// TODO: [todos/023-android-application-class-lifecycle.md](../../../../../../../../todos/023-android-application-class-lifecycle.md)
class WishListApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // TODO: [todos/020-android-context-types-and-application-context.md](../../../../../../../../todos/020-android-context-types-and-application-context.md)
        Graph.provide(this)
    }
}