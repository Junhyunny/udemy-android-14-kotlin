package com.example.chapter_205

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.example.chapter_205.data.Wish
import kotlinx.coroutines.launch

@Composable
fun AddEditDetailView(
    id: Long,
    navController: NavHostController,
    viewModel: WishViewModel,
) {
    val snackMessage = remember {
        mutableStateOf("")
    }
    // TODO: [todos/kotlin-coroutine-scope-concept.md](../../../../../../../../todos/kotlin-coroutine-scope-concept.md)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    if (id != 0L) {
        // TODO: [todos/android-viewmodel-encapsulation-and-async-timing.md](../../../../../../../../todos/android-viewmodel-encapsulation-and-async-timing.md)
        val wish = viewModel.getWishById(id).collectAsState(
            initial = Wish(0L, "", "")
        )
        viewModel.wishTitleState.value = wish.value.title
        viewModel.wishDescriptionState.value = wish.value.description
    } else {
        viewModel.wishTitleState.value = ""
        viewModel.wishDescriptionState.value = ""
    }
    Scaffold(
        topBar = {
            AppBarView(
                title = if (id != 0L) stringResource(R.string.update_wish) else stringResource(
                    R.string.add_wish
                )
            ) {
                // TODO: [todos/android-navigation-popbackstack-vs-navigateup.md](../../../../../../../../todos/android-navigation-popbackstack-vs-navigateup.md)
                // navController.popBackStack()
                navController.navigateUp()
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) {
        Column(
            modifier = Modifier
                .padding(it)
                .wrapContentSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.height(10.dp))
            WishTextField(label = "Title", value = viewModel.wishTitleState.value) {
                viewModel.onWishTitleChange(it)
            }
            Spacer(modifier = Modifier.height(10.dp))
            WishTextField(label = "Description", value = viewModel.wishDescriptionState.value) {
                viewModel.onWishDescriptionState(it)
            }
            Spacer(modifier = Modifier.height(10.dp))
            Button(onClick = {
                val title = viewModel.wishTitleState.value.trim()
                val description = viewModel.wishDescriptionState.value.trim()
                if (title.isNotEmpty() && description.isNotEmpty()) {
                    if (id != 0L) {
                        viewModel.updateWish(Wish(id, title, description))
                    } else {
                        viewModel.addWish(Wish(title = title, description = description))
                    }
                    snackMessage.value = "Success save wish list"
                } else {
                    snackMessage.value = "Enter fields to create a wish"
                }
                // TODO: [todos/android-viewmodel-encapsulation-and-async-timing.md](../../../../../../../../todos/android-viewmodel-encapsulation-and-async-timing.md)
                scope.launch {
                    snackbarHostState.showSnackbar(snackMessage.value)
                    navController.navigateUp()
                    snackMessage.value = ""
                }
            }) {
                Text(
                    text = if (id != 0L) stringResource(id = R.string.update_wish) else stringResource(
                        id = R.string.add_wish
                    ),
                    style = TextStyle(
                        fontSize = 18.sp
                    )

                )
            }
        }
    }
}

@Composable
fun WishTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(text = label, color = Color.Black) },
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = colorResource(R.color.black),
            unfocusedTextColor = colorResource(R.color.black),
            cursorColor = colorResource(R.color.black),
            focusedLabelColor = colorResource(R.color.black),
            unfocusedLabelColor = colorResource(R.color.black),
        )
    )
}

@Preview
@Composable
fun WishTextFieldPreview() {
    WishTextField(
        label = "Wish",
        value = "This is a wish",
        onValueChange = {}
    )
}