package com.example.chapter_205

import com.example.chapter_205.data.Wish
import kotlinx.coroutines.flow.Flow

// ARCH-FIXME: repository 가 DAO 를 그대로 감싸기만 하는 통과 계층(pass-through)이다.
//  다섯 함수 모두 DAO 호출 한 줄을 위임할 뿐이라, 지금은 "계층이 하나 더 있다"는 비용만 내고 있다.
//  repository 가 값을 하는 자리는 이런 것들이다.
//    - 여러 데이터 소스를 합친다 (로컬 DB + 원격 API + 캐시)
//    - 데이터 계층의 타입(Entity)을 도메인 모델로 바꾼다
//    - 스레드 전환과 에러 변환을 여기서 책임진다
//  지금은 `Wish`(Room Entity)가 화면까지 그대로 올라간다. 즉 화면이 DB 스키마에 묶여 있어,
//  컬럼을 바꾸면 UI 코드까지 영향을 받는다.
//  고치기(이 규모에서 과하지 않은 선):
//    - DAO 를 suspend 로 바꾸고 repository 가 main-safe 를 보장한다
//    - 앱이 커지면 `Wish`(Entity)와 `WishUi`(화면용 모델)를 분리하고 여기서 매핑한다
//
// ARCH-FIXME: 인터페이스가 없어 구현을 바꿔 끼울 수 없다.
//  테스트에서 가짜 repository 를 쓰려면 `interface WishRepository` 와
//  `class DefaultWishRepository(dao) : WishRepository` 로 나눈다.
//  (다만 구현이 하나뿐이고 학습용이라면 지금 상태도 과하지 않다. 우선순위는 낮다.)
// TODO: [todos/kotlin-flow-concepts-and-suspend.md](../../../../../../../../todos/kotlin-flow-concepts-and-suspend.md)
class WishRepository(private val wishDao: WishDao) {

    suspend fun addWish(wish: Wish) {
        wishDao.addWish(wish)
    }

    fun getAllWishes(): Flow<List<Wish>> = wishDao.getAll()

    fun getWishById(id: Long): Flow<Wish> {
        return wishDao.getWishById(id)
    }

    suspend fun updateWish(wish: Wish) {
        wishDao.updateWish(wish)
    }

    suspend fun deleteWish(wish: Wish) {
        wishDao.deleteWish(wish)
    }
}