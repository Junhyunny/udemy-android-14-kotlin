package com.example.chapter_246

import androidx.annotation.DrawableRes

sealed class Screen(val title: String, val route: String) {
    // TODO: [todos/android-drawable-res-annotation.md](../../../../../../../../todos/android-drawable-res-annotation.md)
    sealed class DrawerScreen(val dTitle: String, val dRoute: String, @DrawableRes val icon: Int) :
        Screen(dTitle, dRoute) {
        object Account : DrawerScreen(
            "Account",
            "account",
            R.drawable.ic_account
        )

        object Subscription : DrawerScreen(
            "Subscription",
            "subscription",
            R.drawable.ic_subscription
        )

        object AddAccount : DrawerScreen(
            "Add Account",
            "add_account",
            R.drawable.ic_baseline_person_add_alt_1_24
        )
    }

    sealed class BottomBarScreen(
        val bTitle: String, val bRoute: String, @DrawableRes val icon: Int
    ) : Screen(bTitle, bRoute) {
        object Home : BottomBarScreen(
            "Home",
            "home",
            R.drawable.ic_baseline_music_video_24
        )

        object Library : BottomBarScreen(
            "Library",
            "library",
            R.drawable.ic_baseline_play_button
        )

        object Browse : BottomBarScreen(
            "Browse",
            "browse",
            R.drawable.ic_apps_browse
        )
    }
}