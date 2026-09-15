package com.example.chapter_205

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBarView(
    title: String,
    onBackNavClick: () -> Unit = {}
) {
    val navigationIcon: @Composable (() -> Unit) = @Composable {
        if (!title.contains("Wish List")) {
            IconButton(onClick = onBackNavClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Default.ArrowBack,
                    contentDescription = null,
                    tint = Color.White,
                )
            }
        }
    }
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = colorResource(id = R.color.app_bar_color)
        ),
        navigationIcon = navigationIcon,
        title = {
            // TODO: [todos/android-resources-r-class-and-compose-theme.md](../../../../../../../../todos/android-resources-r-class-and-compose-theme.md)
            Text(
                title,
                color = colorResource(id = R.color.white),
                modifier = Modifier.padding(start = 4.dp)
            )
        },
    )
}