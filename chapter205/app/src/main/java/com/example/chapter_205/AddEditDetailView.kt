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

// FIXME: 화면 컴포저블이 `NavHostController` 를 통째로 받고 있다(HomeView 도 같다).
//  - 미리보기(@Preview)를 만들 수 없다
//  - 이 화면이 앱의 내비게이션 그래프를 알아야만 동작한다
//  고치기: 필요한 동작만 콜백으로 받는다.
//      fun AddEditDetailView(id: Long, onBack: () -> Unit, viewModel: WishViewModel = viewModel())
@Composable
fun AddEditDetailView(
    id: Long,
    navController: NavHostController,
    viewModel: WishViewModel,
) {
    // ARCH-FIXME: 이 화면 하나가 네 가지 책임을 동시에 지고 있다.
    //   (1) 화면 그리기            — 원래 역할
    //   (2) 데이터 로딩 트리거      — getWishById 를 직접 호출
    //   (3) ViewModel 상태 쓰기    — wishTitleState 에 직접 대입
    //   (4) 내비게이션 결정        — navigateUp 시점 판단
    //  MVVM 에서 (2)(3)(4)는 화면 밖(ViewModel)의 일이다.
    //  결과적으로 "저장에 성공했는가"를 화면이 판단하고 있어(아래 if 문), 같은 규칙을 다른 화면에서
    //  재사용할 수 없고 테스트도 UI 테스트로만 가능하다.
    //  고치기: ViewModel 이 결과를 이벤트로 내보내고 화면은 그것을 따르기만 한다.
    //      // ViewModel
    //      sealed interface SaveEvent { data object Saved : SaveEvent; data class Invalid(val msg: String) : SaveEvent }
    //      val events = Channel<SaveEvent>()
    //      fun save(id: Long) { ...검증과 저장은 여기서... }
    //      // 화면
    //      LaunchedEffect(Unit) { viewModel.events.receiveAsFlow().collect { when (it) { ... } } }
    val snackMessage = remember {
        mutableStateOf("")
    }
    // TODO: [todos/053-kotlin-coroutine-scope-concept.md](../../../../../../../../todos/053-kotlin-coroutine-scope-concept.md)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    // FIXME: 이 프로젝트에서 가장 큰 문제. 컴포지션 "도중에" ViewModel 상태를 쓰고 있다.
    //  컴포저블 본문은 언제, 몇 번 실행될지 보장되지 않으며 부수 효과를 넣으면 안 되는 자리다.
    //  실제로 생기는 증상:
    //    - 재구성될 때마다 입력 필드가 DB 값으로 되돌아간다 → 사용자가 타이핑한 내용이 덮어써진다
    //    - `getWishById(id)` 가 재구성마다 새 Flow 를 만들어 구독이 계속 갈아엎힌다
    //  고치기: 부수 효과는 이펙트 안에서 한 번만 실행한다.
    //      LaunchedEffect(id) {
    //          if (id != 0L) {
    //              val wish = viewModel.getWishById(id).first()
    //              viewModel.onWishTitleChange(wish.title)
    //              viewModel.onWishDescriptionState(wish.description)
    //          } else {
    //              viewModel.onWishTitleChange("")
    //              viewModel.onWishDescriptionState("")
    //          }
    //      }
    //  더 나은 구조는 화면이 아니라 ViewModel 이 id 를 받아 스스로 불러오게 하는 것이다.
    if (id != 0L) {
        // TODO: [todos/048-android-viewmodel-encapsulation-and-async-timing.md](../../../../../../../../todos/048-android-viewmodel-encapsulation-and-async-timing.md)
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
                // TODO: [todos/071-android-navigation-popbackstack-vs-navigateup.md](../../../../../../../../todos/071-android-navigation-popbackstack-vs-navigateup.md)
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
                // TODO: [todos/048-android-viewmodel-encapsulation-and-async-timing.md](../../../../../../../../todos/048-android-viewmodel-encapsulation-and-async-timing.md)
                // FIXME: `showSnackbar` 는 스낵바가 사라질 때까지 중단되는 suspend 함수다.
                //  그 다음 줄의 `navigateUp()` 은 스낵바가 닫힌 뒤에야 실행되므로
                //  저장 후 화면이 한참 뒤에 닫힌다(사용자에게는 멈춘 것처럼 보인다).
                //  또 입력값이 비어 실패한 경우에도 똑같이 화면을 닫아 버린다.
                //  고치기: 성공일 때만 즉시 이동하고, 스낵바는 기다리지 않는다.
                //      if (성공) { navController.navigateUp() }
                //      scope.launch { snackbarHostState.showSnackbar(message) }
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