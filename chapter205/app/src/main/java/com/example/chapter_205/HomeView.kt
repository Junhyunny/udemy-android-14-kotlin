package com.example.chapter_205

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.chapter_205.data.Wish
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun HomeView(
    navController: NavController,
    viewModel: WishViewModel,
) {
    // FIXME: `context` 를 선언만 하고 아무 데서도 쓰지 않는다. 죽은 코드다.
    //  `LocalContext.current` 는 컴포지션 로컬 조회 비용도 있으니 지우는 게 맞다.
    // TODO: [todos/compose-localcontext.md](../../../../../../../../todos/compose-localcontext.md)
    val context = LocalContext.current
    // TODO: [todos/compose-scaffold.md](../../../../../../../../todos/compose-scaffold.md)
    Scaffold(
        topBar = {
            AppBarView(title = "Wish List")
        },
        floatingActionButton = {
            FloatingActionButton(
                modifier = Modifier.padding(all = 20.dp),
                contentColor = Color.White,
                containerColor = Color.Black,
                onClick = {
                    navController.navigate(Screen.AddScreen.route + "/0")
                }
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null)
            }
        },
    ) { paddingValues ->
        // TODO: [todos/compose-collectasstate-flow-to-state.md](../../../../../../../../todos/compose-collectasstate-flow-to-state.md)
        // FIXME: `collectAsState` 는 화면이 백그라운드로 가도 수집을 멈추지 않는다.
        //  고치기: 라이프사이클을 인식하는 쪽을 쓴다.
        //      viewModel.getAllWishes.collectAsStateWithLifecycle(initialValue = emptyList())
        //      (androidx.lifecycle:lifecycle-runtime-compose 의존성 필요)
        val wishList = viewModel.getAllWishes.collectAsState(initial = listOf())
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            items(
                items = wishList.value,
                key = { it.id }
            ) { wish ->
                val dismissState = rememberSwipeToDismissBoxState(
                    positionalThreshold = { totalDistance ->
                        totalDistance * 0.5f
                    },
                )
                // TODO: [todos/compose-launchedeffect-and-snapshotflow.md](../../../../../../../../todos/compose-launchedeffect-and-snapshotflow.md)
                LaunchedEffect(dismissState) {
                    snapshotFlow { dismissState.currentValue }
                        .distinctUntilChanged()
                        .collect { value ->
                            if (value == SwipeToDismissBoxValue.EndToStart) {
                                viewModel.deleteWish(wish)
                            }
                        }
                }
                SwipeToDismissBox(
                    // TODO: [todos/compose-lazy-list-animate-item.md](../../../../../../../../todos/compose-lazy-list-animate-item.md)
                    //
                    // [고침 2] 카드에 있던 padding 을 여기(행 전체)로 올렸다.
                    // 이전에는 padding 이 WishItem 의 Card 에만 걸려 있어서
                    // 배경(backgroundContent)은 행 전체를, 카드는 8dp 안쪽만 차지했다.
                    // 즉 스와이프하지 않아도 카드 둘레로 배경색이 항상 삐져나왔다.
                    // 배경을 "항상 빨강"으로 바꾸는 [고침 1]을 적용하려면
                    // 배경과 카드의 경계가 정확히 같아야 평소에 빨강이 보이지 않는다.
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 72.dp)
                        .padding(top = 8.dp, start = 8.dp, end = 8.dp)
                        .animateItem(),
                    state = dismissState,
                    // 스와이프 시 뒤에 나타날 배경 (빨간색 삭제 배경)
                    //
                    // [고침 1] 빨간 배경이 안 보이던 진짜 원인.
                    //
                    // 이전 코드는 dismissState.targetValue == EndToStart 일 때만 빨강을 칠하고
                    // 그 전에는 Color.Transparent 를 칠했다. 그런데 실제로 로그를 찍어 보면
                    // targetValue 는 드래그하는 내내 Settled 로 남아 있다가
                    // 임계값을 넘는 "그 순간" currentValue 와 함께 EndToStart 로 바뀐다.
                    //   I/SWIPE: target=Settled   current=Settled
                    //   I/SWIPE: target=EndToStart current=EndToStart   ← 같은 시점
                    // 그리고 바로 그 시점에 위의 LaunchedEffect 가 currentValue 를 보고
                    // deleteWish() 를 호출해 항목을 목록에서 없앤다.
                    // 결국 "빨강을 칠할 조건이 참이 되는 순간 = 항목이 사라지는 순간" 이라
                    // 드래그 중에는 계속 Transparent(=화면 배경색) 만 보였던 것이다.
                    // animateColorAsState 의 애니메이션 시간까지 더해지면 더더욱 보이지 않는다.
                    //
                    // 해결: 조건 없이 항상 빨강을 칠한다.
                    // backgroundContent 는 원래 content 뒤에 깔리는 레이어라서,
                    // 카드가 밀려난 만큼만 드러난다. 따로 조건을 걸 필요가 없다.
                    backgroundContent = {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                // 카드와 같은 모서리 모양이라야 카드가 배경을 완전히 덮는다
                                .clip(CardDefaults.shape)
                                .background(Color.Red)
                                .padding(horizontal = 20.dp),
                            contentAlignment = Alignment.CenterEnd // 오른쪽에 쓰레기통 아이콘 배치
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Icon",
                                tint = Color.White
                            )
                        }
                    },
                    enableDismissFromStartToEnd = false,
                    enableDismissFromEndToStart = true,
                ) {
                    WishItem(wish = wish) {
                        val id = wish.id
                        navController.navigate(Screen.AddScreen.route + "/$id")
                    }
                }
            }
        }
    }
}

@Composable
fun WishItem(wish: Wish, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            // [고침 2] padding 을 SwipeToDismissBox 로 옮겼다.
            // 여기에 padding 이 있으면 카드가 배경보다 8dp 작아져서
            // 스와이프하지 않아도 카드 둘레로 빨간 배경이 새어 나온다.
            .clickable {
                onClick()
            },
        colors = CardColors(
            containerColor = colorResource(id = R.color.white),
            contentColor = colorResource(id = R.color.black),
            disabledContainerColor = colorResource(id = R.color.white),
            disabledContentColor = colorResource(id = R.color.black),
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(text = wish.title, fontWeight = FontWeight.ExtraBold)
            Text(text = wish.description)
        }
    }
}