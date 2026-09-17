// ARCH-FIXME: 패키지 구성이 일관되지 않다.
//  데이터 계층 파일이 두 곳으로 흩어져 있다.
//      com.example.chapter_205.data  → Wish.kt (Entity)
//      com.example.chapter_205       → WishDao.kt, WishDatabase.kt, WishRepository.kt, Graph.kt
//  Entity 만 `data` 로 내려가 있고 나머지 데이터 계층은 루트에 그대로 있어서,
//  "data 패키지를 열면 데이터 계층이 다 보인다"는 기대가 깨진다.
//  고치기: 계층별로 모은다.
//      data/       Wish.kt, WishDao.kt, WishDatabase.kt, WishRepository.kt
//      ui/         HomeView.kt, AddEditDetailView.kt, AppBar.kt, Navigation.kt, Screen.kt
//      di/         Graph.kt
//      루트         MainActivity.kt, WishListApp.kt
//  이렇게 하면 import 문만 봐도 "UI 가 data 를 참조한다"는 방향이 드러나고,
//  반대 방향(data → ui)이 생기면 눈에 띈다.
package com.example.chapter_205.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

// FIXME: 테이블 이름에 하이픈이 들어 있다(`wish-table`).
//  SQL 에서 하이픈은 식별자에 쓸 수 없는 문자라 Room 이 쿼리를 만들 때 따옴표로 감싸야 한다.
//  직접 쿼리를 쓸 때 문제가 되고, 다른 DB 도구와도 잘 맞지 않는다.
//  고치기: `wish_table` 처럼 snake_case 를 쓴다. (이미 배포된 앱이라면 마이그레이션이 필요하다)
@Entity(tableName = "wish-table")
data class Wish(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "title")
    val title: String = "",
    @ColumnInfo(name = "description")
    val description: String = ""
)

// FIXME: `DummyWish` 는 아무 곳에서도 쓰이지 않는 테스트용 데이터다.
//  운영 코드에 남아 있으면 APK 에 그대로 포함된다.
//  고치기: 삭제하거나 `src/test` / `src/debug` 로 옮긴다.
object DummyWish {
    val wishList = listOf(
        Wish(title = "Google watch 2", description = "Android Watch"),
        Wish(title = "Oculus Quest 2", description = "Android Watch"),
        Wish(title = "A Sci-fi 2", description = "Android Watch"),
    )
}