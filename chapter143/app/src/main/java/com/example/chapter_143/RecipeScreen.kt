package com.example.chapter_143

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter

// TODO: [todos/070-compose-navigation-prop-drilling.md](../../../../../../../../todos/070-compose-navigation-prop-drilling.md)
@Composable
fun RecipeScreen(
    modifier: Modifier = Modifier,
    viewState: MainViewModel.RecipeState,
    navigateToDetail: (Category) -> Unit
) {
    // FIXME: 파라미터로 받은 `modifier` 를 최상위 `Box` 에 쓰지 않고 무시한 뒤,
    //  안쪽 `CircularProgressIndicator` 에만 붙이고 있다. 관례와 반대다.
    //  호출자가 `RecipeScreen(modifier = Modifier.padding(16.dp))` 을 줘도 화면 여백이 생기지 않고
    //  엉뚱하게 스피너에만 적용된다.
    //  고치기: Box(modifier = modifier.fillMaxSize()) 로 받고,
    //         스피너에는 Modifier.align(Alignment.Center) 를 새로 만들어 준다.
    Box(modifier = Modifier.fillMaxSize()) {
        when {
            viewState.loading -> CircularProgressIndicator(modifier.align(Alignment.Center))
            viewState.error != null -> Text("Error Occurred")
            else -> CategoryScreen(viewState.list, navigateToDetail)
        }
    }
}

@Composable
fun CategoryScreen(categories: List<Category>, navigateToDetail: (Category) -> Unit) {
    LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.fillMaxSize()) {
        // FIXME: `items(categories)` 에 `key` 가 없다.
        //  목록이 바뀌면 항목의 정체성이 유지되지 않아 스크롤 위치와 항목별 상태가 흐트러진다.
        //  고치기: items(categories, key = { it.idCategory }) { ... }
        items(categories) { category ->
            CategoryItem(category = category, navigateToDetail)
        }
    }
}

@Composable
fun CategoryItem(category: Category, navigateToDetail: (Category) -> Unit) {
    Column(
        modifier = Modifier
            .padding(8.dp)
            .fillMaxSize()
            .clickable {
                navigateToDetail(category)
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = rememberAsyncImagePainter(category.strCategoryThumb),
            // TODO: [todos/039-compose-image-content-description-null.md](../../../../../../../../todos/039-compose-image-content-description-null.md)
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .aspectRatio(1f)
        )
        Text(
            text = category.strCategory,
            // FIXME: `Color.Black` 하드코딩. 다크 모드에서 검은 배경에 검은 글씨가 된다.
            //  고치기: `MaterialTheme.colorScheme.onSurface` 처럼 테마의 색 역할을 쓴다.
            color = Color.Black,
            style = TextStyle(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}