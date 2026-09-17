package com.example.chapter_246

// FIXME: 프로젝트의 `R` 이 아니라 안드로이드 프레임워크의 `android.R` 을 임포트하고 있다.
//  그래서 아래에서 `R.drawable.ic_menu_add`(시스템 기본 아이콘)를 쓰게 됐다.
//  같은 파일에서 프로젝트 리소스를 쓰려 해도 이름이 가려져 쓸 수 없다.
//  고치기: 이 import 를 지우고 프로젝트 리소스(`com.example.chapter_246.R`)를 쓴다.
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
    // FIXME: 재구성될 때마다 리스트 생성과 `groupBy` 가 다시 실행된다.
    //  고치기: 최소한 `remember { ... }` 로 감싼다. 원칙적으로는 ViewModel 에서 계산해 내려 준다.
    // FIXME: 세 항목의 첫 글자가 모두 달라 그룹마다 원소가 1개뿐이다. 그룹화가 아무 일도 하지 않는다.
    //  게다가 머리글 자리에 `key`(첫 글자)가 아니라 `values[0]`(원소 자체)을 넣고 있다.
    val grouped = listOf(
        "New Released", "Favorites", "Top Rated"
    ).groupBy { it[0] }
    LazyColumn {
        grouped.forEach { (key, values) ->
            // TODO: [todos/043-compose-lazy-list-sticky-header.md](../../../../../../../../todos/043-compose-lazy-list-sticky-header.md)
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