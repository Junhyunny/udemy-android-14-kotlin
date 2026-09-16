package com.example.chapter_246


import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable

@Composable
fun BrowseView() {
    val categories = listOf("Hits", "Happy", "Workout", "Running", "TGIF", "Yoga")
    LazyVerticalGrid(
        columns = GridCells.Fixed(2)
    ) {
        items(categories) {
            BrowseItem(it, drawable = R.drawable.ic_apps_browse)
        }
    }
}