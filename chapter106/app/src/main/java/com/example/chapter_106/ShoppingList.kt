// ARCH-FIXME: 이 챕터에는 ViewModel 이 없고 모든 상태와 로직이 컴포저블 안에 있다.
//  학습 단계(상태 기초)로는 의도된 구성이지만, 이 구조의 한계를 알아 두면 다음 챕터가 쉬워진다.
//   - 화면 회전 시 목록 전체가 사라진다
//   - "항목 추가/수정/삭제" 규칙을 다른 화면에서 재사용할 수 없다
//   - UI 없이는 테스트할 수 없다
//  chapter127 에서 ViewModel 이, chapter205 에서 Repository 가 들어오는데,
//  그때 이 파일의 어느 부분이 어디로 옮겨 가는지 비교해 보면 계층의 의미가 분명해진다.
//      sItems / 추가·수정·삭제        → ViewModel
//      ShoppingItem                  → domain 모델
//      실제 저장                      → Repository + DAO (chapter205 의 Wish 와 같은 자리)
//      Column/LazyColumn/Dialog      → 화면에 남는다
//
// ARCH-FIXME: 파일 하나에 모델(`ShoppingItem`)과 화면 셋(`ShoppingListApp`, `ShoppingListItem`,
//  `ShoppingItemEditor`)이 함께 있다. 지금 규모에서는 문제없지만, 모델은 별도 파일로 빼는 편이
//  나중에 계층을 나눌 때 옮기기 쉽다.
package com.example.chapter_106

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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

// TODO: [todos/006-kotlin-optin-experimental-api.md](../../../../../../../../todos/006-kotlin-optin-experimental-api.md)
// FIXME: 화면 상태 4개가 모두 `remember` 에 있어 화면 회전 시 목록이 통째로 사라진다.
//  이 챕터의 학습 범위(상태 기초)로는 맞지만, 실제 앱이라면
//  - 목록은 ViewModel 로 올리고
//  - 다이얼로그 표시 여부는 `rememberSaveable` 로 바꾼다.
// FIXME: `modifier: Modifier = Modifier` 파라미터가 없어 호출자가 레이아웃을 조절할 수 없다.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingListApp() {
    var sItems by remember { mutableStateOf(listOf<ShoppingItem>()) }
    var showDialog by remember { mutableStateOf(false) }
    var itemName by remember { mutableStateOf("") }
    var itemQuantity by remember { mutableStateOf("") }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 50.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Button(
            onClick = { showDialog = true },
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text("Add Item")
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxHeight()
                .padding(16.dp)
        ) {
            items(sItems) { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                ) {
                    if (item.isEditing) {
                        ShoppingItemEditor(item = item) { editedName, editedQuantity ->
                            // FIXME: 아래 3줄은 리스트에 담긴 객체의 내부를 직접 바꾼다(`it.name = ...`).
                            //  Compose 는 "리스트 참조가 바뀌었는가"로 재구성을 판단하므로
                            //  같은 객체의 필드만 바꾸면 화면이 갱신되지 않는다.
                            //  지금은 바로 윗줄에서 `sItems` 를 새 리스트로 바꾼 덕분에 우연히 다시 그려질 뿐이고,
                            //  그 재구성 시점에 이름/수량이 반영됐다는 보장이 없다.
                            //  고치기: `map` 한 번으로 새 객체를 만들어 대입한다.
                            //      sItems = sItems.map {
                            //          if (it.id == item.id) it.copy(name = editedName, quantity = editedQuantity, isEditing = false)
                            //          else it.copy(isEditing = false)
                            //      }
                            sItems = sItems.map { it.copy(isEditing = false) }
                            val editedItem = sItems.find { it.id == item.id }
                            editedItem?.let {
                                it.name = editedName
                                it.quantity = editedQuantity
                            }
                        }
                    } else {
                        ShoppingListItem(
                            item,
                            { sItems = sItems.map { it.copy(isEditing = it.id == item.id) } },
                            { sItems = sItems.filter { it.id != item.id } }
                        )
                    }
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
                        if (itemName.isNotEmpty()) {
                            val newItem = ShoppingItem(
                                // FIXME: `size + 1` 은 고유 id 가 아니다.
                                //  [1,2,3] 에서 2번을 지우면 size=2 → 다음 id 가 3 이 되어 기존 3번과 충돌한다.
                                //  이후 수정·삭제가 엉뚱한 항목에 적용된다.
                                //  고치기: 증가만 하는 카운터를 따로 두거나 `UUID.randomUUID().toString()` 을 쓴다.
                                id = sItems.size + 1,
                                name = itemName,
                                quantity = itemQuantity.toIntOrNull() ?: 0
                            )
                            // TODO: [todos/037-compose-state-list-reference-change.md](../../../../../../../../todos/037-compose-state-list-reference-change.md)
                            sItems = sItems + newItem
                            showDialog = false
                            itemName = ""
                            itemQuantity = ""
                        }
                    }) {
                        Text("Add")
                    }
                    Button(onClick = { showDialog = false }) {
                        Text("Cancel")
                    }
                }
            },
            title = {
                Text("Add Shopping Item")
            },
            text = {
                Column {
                    OutlinedTextField(
                        value = itemName,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        label = { Text("Name") },
                        onValueChange = { itemName = it }
                    )
                    OutlinedTextField(
                        value = itemQuantity,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        label = { Text("Quantity") },
                        onValueChange = { itemQuantity = it })
                }
            }
        )
    }
}

@Composable
fun ShoppingListItem(
    item: ShoppingItem,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .padding(12.dp)
            .fillMaxWidth()
            .border(
                border = BorderStroke(2.dp, Color.Gray),
                shape = RoundedCornerShape(20)
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(item.name, modifier = Modifier.padding(8.dp))
        Text("${item.quantity}", modifier = Modifier.padding(8.dp))
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

@Composable
fun ShoppingItemEditor(item: ShoppingItem, onEditComplete: (String, Int) -> Unit) {
    var editName by remember { mutableStateOf(item.name) }
    var editedQuantity by remember { mutableStateOf(item.quantity.toString()) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        Column {
            OutlinedTextField(
                value = editName,
                onValueChange = { editName = it },
                singleLine = true,
                modifier = Modifier
                    .wrapContentSize() // TODO: [todos/031-compose-wrapcontentsize-modifier.md](../../../../../../../../todos/031-compose-wrapcontentsize-modifier.md)
                    .padding(8.dp)
            )
            OutlinedTextField(
                value = editedQuantity,
                onValueChange = { editedQuantity = it },
                singleLine = true,
                modifier = Modifier
                    .wrapContentSize()
                    .padding(8.dp)
            )
        }
        Button(
            // FIXME: `toInt()` 는 숫자가 아닌 입력에서 `NumberFormatException` 으로 앱을 종료시킨다.
            //  수량 필드에 키보드 제한이 없어 문자를 입력할 수 있다.
            //  고치기: `editedQuantity.toIntOrNull() ?: 0` 으로 받고,
            //         OutlinedTextField 에 keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number) 를 준다.
            onClick = { onEditComplete(editName, editedQuantity.toInt()) },
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minWidth = 30.dp)
        ) {
            Text("Save")
        }
    }
}

// FIXME: `var` 프로퍼티를 가진 data class 를 Compose 상태로 쓰고 있다.
//  객체 내부를 바꿔도 Compose 가 알아채지 못해 위와 같은 갱신 누락이 생긴다.
//  고치기: 전부 `val` 로 바꾸고 변경은 항상 `copy()` 로 새 객체를 만든다.
//      data class ShoppingItem(val id: Int, val name: String, val quantity: Int, val isEditing: Boolean = false)
data class ShoppingItem(
    val id: Int, var name: String, var quantity: Int, var isEditing: Boolean = false
)