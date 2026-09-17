package com.example.chapter_186

import android.Manifest
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.navigation.NavController
// FIXME: IDE 자동 임포트로 들어온 사용되지 않는 import 다.
//  `java.nio.file` 은 API 26 미만에서 사용할 수 없어 minSdk 24 인 이 프로젝트에서는 위험하기도 하다.
//  고치기: 삭제한다.
import java.nio.file.WatchEvent

data class ShoppingItem(
    val id: Int,
    var name: String,
    var quantity: Int,
    var isEditing: Boolean = false,
    var address: String = ""
)

// ARCH-FIXME: 컴포저블 하나에 책임이 과하게 몰려 있다. 파라미터 5개가 그 증거다.
//   locationUtils  → 데이터 소스를 화면이 직접 들고 있다
//   viewModel      → 상태 보유자
//   navController  → 내비게이션 전권
//   context        → 안드로이드 프레임워크 (미리보기·테스트 불가의 주범)
//   address        → 이미 가공된 표시용 문자열
//  이 화면이 지금 하는 일을 세어 보면: 목록 상태 관리 + 항목 추가/수정/삭제 + 권한 요청 +
//  rationale 판단 + Toast 안내 + 위치 구독 시작 + 화면 이동. 일곱 가지다.
//
//  고치기(방향): 화면은 "상태를 받고 이벤트를 올린다"만 한다.
//      @Composable
//      fun ShoppingListScreen(
//          uiState: ShoppingUiState,
//          onAddItem: (String, Int) -> Unit,
//          onEditItem: (ShoppingItem) -> Unit,
//          onDeleteItem: (ShoppingItem) -> Unit,
//          onRequestAddress: () -> Unit,
//          modifier: Modifier = Modifier,
//      )
//  권한 흐름은 화면에 남더라도(런처는 컴포저블에 묶이므로) "허용됨/거부됨"만 ViewModel 에 알리고
//  그 다음 판단은 ViewModel 이 하게 한다.
@Composable
fun ShoppingListApp(
    locationUtils: LocationUtils,
    viewModel: LocationViewModel,
    navController: NavController,
    context: Context,
    address: String
) {
    // ARCH-FIXME: 이 앱의 핵심 데이터(쇼핑 목록)가 ViewModel 이 아니라 화면의 `remember` 에 있다.
    //  화면 회전, 프로세스 종료, 다른 화면 이동 후 복귀에서 전부 사라진다.
    //  ViewModel 은 이미 있는데 위치/주소만 담고 있어 역할 분담이 어긋나 있다.
    //  고치기: 목록과 다이얼로그 입력 상태를 ViewModel 의 UiState 로 올린다.
    var sItems by remember { mutableStateOf(listOf<ShoppingItem>()) }
    var showDialog by remember { mutableStateOf(false) }
    var itemName by remember { mutableStateOf("") }
    var itemQuantity by remember { mutableStateOf("") }

    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            if (permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true && permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
                locationUtils.requestLocationUpdate(viewModel = viewModel)
            } else {
                val rationaleRequired = ActivityCompat.shouldShowRequestPermissionRationale(
                    context as MainActivity,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) || ActivityCompat.shouldShowRequestPermissionRationale(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
                if (rationaleRequired) {
                    Toast.makeText(
                        context,
                        "Location Permission is required for this feature to work",
                        Toast.LENGTH_LONG
                    )
                        .show()
                } else {
                    Toast.makeText(
                        context,
                        "Location Permission is required. Please enable it in the Android Settings",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        })
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(modifier = Modifier.height(72.dp))
        Button(
            onClick = { showDialog = true },
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text("Add Item")
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            items(sItems) { item ->
                if (item.isEditing) {
                    ShoppingItemEditor(
                        item = item,
                        onEditComplete = { editedName, editedQuantity ->
                            sItems = sItems.map { it.copy(isEditing = false) }
                            val editedItem = sItems.find { it.id == item.id }
                            editedItem?.let {
                                it.name = editedName
                                it.quantity = editedQuantity
                                it.address = address
                            }
                        },
                    )
                } else {
                    ShoppingListItem(
                        item = item,
                        onEditClick = {
                            sItems = sItems.map { it.copy(isEditing = it.id == item.id) }
                        },
                        onDeleteClick = {
                            sItems = sItems - item
                        }
                    )
                }
            }
        }
    }
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Button(onClick = {
                        if (itemName.isNotBlank()) {
                            val newItem = ShoppingItem(
                                id = sItems.size + 1,
                                name = itemName,
                                quantity = itemQuantity.toInt(),
                                address = address
                            )
                            sItems = sItems + newItem
                            showDialog = false
                            itemName = ""
                        }
                    }) {
                        Text("Add")
                    }
                    Button(onClick = { showDialog = false }) {
                        Text("Cancel")
                    }
                }
            },
            title = { Text("Add Shopping Item") },
            text = {
                Column(modifier = Modifier.fillMaxSize()) {
                    OutlinedTextField(
                        value = itemName,
                        onValueChange = { itemName = it },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                    )
                    OutlinedTextField(
                        value = itemQuantity,
                        onValueChange = { itemQuantity = it },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                    )
                    Button(onClick = {
                        if (locationUtils.hasLocationPermission(context)) {
                            locationUtils.requestLocationUpdate(viewModel)
                            // TODO: [todos/android-navigation-navoptions-builder.md](../../../../../../../../todos/android-navigation-navoptions-builder.md)
                            // FIXME: `this.launchSingleTop` 은 프로퍼티 값을 읽고 버리는 표현식이라
                            //  아무 효과가 없다(컴파일 에러도 경고도 없다).
                            //  의도한 "중복 쌓임 방지"가 동작하지 않아 버튼을 연타하면 다이얼로그가 겹쳐 쌓인다.
                            //  고치기: 대입해야 한다. → `launchSingleTop = true`
                            navController.navigate("locationscreen") {
                                this.launchSingleTop
                            }
                        } else {
                            requestPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                ),
                            )
                        }
                    }) {
                        Text("Address")
                    }
                }
            }
        )
    }
}

@Composable
fun ShoppingItemEditor(item: ShoppingItem, onEditComplete: (String, Int) -> Unit) {
    var editedName by remember { mutableStateOf(item.name) }
    var editedQuantity by remember { mutableStateOf(item.quantity.toString()) }
    var isEditing by remember { mutableStateOf(item.isEditing) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        Column {
            BasicTextField(
                value = editedName,
                onValueChange = { editedName = it },
                singleLine = true,
                modifier = Modifier
                    .wrapContentSize()
                    .padding(8.dp)
            )
            BasicTextField(
                value = editedQuantity,
                onValueChange = { editedQuantity = it },
                singleLine = true,
                modifier = Modifier
                    .wrapContentSize()
                    .padding(8.dp)
            )
        }
        Button(onClick = {
            isEditing = false
            onEditComplete(editedName, editedQuantity.toIntOrNull() ?: 1)
        }) {
            Text("Save")
        }
    }
}

@Composable
fun ShoppingListItem(
    item: ShoppingItem,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .padding(8.dp)
            .fillMaxSize()
            .border(
                border = BorderStroke(1.dp, Color(0XFF018786)),
                shape = RoundedCornerShape(20)
            ),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(8.dp)
        ) {
            Row {
                Text(item.name, modifier = Modifier.padding(8.dp))
                Text("Qty: ${item.quantity}", modifier = Modifier.padding(8.dp))
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                Icon(imageVector = Icons.Default.LocationOn, contentDescription = null)
                Text(item.address)
            }
        }
        Row(modifier = Modifier.padding(8.dp)) {
            IconButton(onClick = onEditClick) {
                Icon(imageVector = Icons.Default.Edit, contentDescription = null)
            }
            IconButton(onClick = onDeleteClick) {
                Icon(imageVector = Icons.Default.Delete, contentDescription = null)
            }
        }
    }
}