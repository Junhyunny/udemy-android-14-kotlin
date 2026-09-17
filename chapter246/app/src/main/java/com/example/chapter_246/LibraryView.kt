package com.example.chapter_246

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
// FIXME: `Divider` 는 Material3 에서 deprecated 다(AccountView, Subscription 도 같다).
//  고치기: `HorizontalDivider` 로 바꾼다. (세로 구분선은 `VerticalDivider`)
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

@Composable
fun LibraryView() {
    LazyColumn {
        items(libraries) { lib ->
            LibItem(lib = lib)
        }
    }
}

@Composable
fun LibItem(lib: Lib) {
    Column {
        // FIXME: 바깥 Row 안에 아무 속성도 없는 Row 가 한 겹 더 들어 있다.
        //  중첩이 아무 역할을 하지 않으면서 레이아웃 단계만 늘린다.
        //  고치기: 안쪽 Row 를 없애고 바깥 Row 에 verticalAlignment = Alignment.CenterVertically 를 준다.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.Absolute.Left
        ) {
            Row {
                Icon(
                    painter = painterResource(id = lib.icon),
                    modifier = Modifier.padding(horizontal = 8.dp),
                    contentDescription = lib.name
                )
                Text(lib.name)
            }
        }
        Divider(color = Color.LightGray)
    }
}