package com.example.chapter_246

import android.R
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

@Composable
fun HomeView() {
    val categories = listOf(
        "Hits", "Happy", "Workout", "Running", "TGIF", "YOGA"
    )
    val grouped = listOf(
        "New Released", "Favorites", "Top Rated"
    ).groupBy { it[0] }
    LazyColumn {
        grouped.forEach { (key, values) ->
            // TODO: [todos/compose-lazy-list-sticky-header.md](../../../../../../../../todos/compose-lazy-list-sticky-header.md)
            stickyHeader {
                Text(text = values[0], modifier = Modifier.padding(16.dp))
                LazyRow {
                    items(categories) {
                        BrowseItem(it, drawable = R.drawable.ic_menu_add)
                    }
                }
            }
        }
    }
}

@Composable
fun BrowseItem(
    category: String, drawable: Int
) {
    Card(
        modifier = Modifier
            .padding(16.dp)
            .size(200.dp),
        border = BorderStroke(3.dp, color = Color.DarkGray),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(category)
            Image(painter = painterResource(id = drawable), contentDescription = null)
        }
    }
}