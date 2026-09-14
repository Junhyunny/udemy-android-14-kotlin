package com.example.chapter_157

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SecondScreen(name: String, age: Int, navigateToSecondScreen: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("This is the second screen", fontSize = 24.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Welcome $name ($age)", fontSize = 20.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = navigateToSecondScreen) {
            Text("Go to the first screen")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SecondScreenPreview() {
    SecondScreen("Jun", 0, {})
}