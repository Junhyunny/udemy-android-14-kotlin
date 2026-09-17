package com.example.chapter_143

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET

interface ApiService {
    @GET("categories.php")
    // TODO: [todos/051-kotlin-coroutines-suspend-and-event-loop.md](../../../../../../../../todos/051-kotlin-coroutines-suspend-and-event-loop.md)
    // TODO: [todos/052-kotlin-coroutines-continuation-state-machine.md](../../../../../../../../todos/052-kotlin-coroutines-continuation-state-machine.md)
    suspend fun getCategories(): CategoriesResponse
}

// FIXME: 최상위 전역 변수로 Retrofit 인스턴스를 만들고 있다.
//  - 앱 어디서나 접근 가능해서 의존 관계가 코드에 드러나지 않는다
//  - 테스트에서 가짜 구현으로 바꿔 끼울 수 없다
//  - 클래스 로딩 시점에 무조건 생성된다
//  고치기: ViewModel 이 생성자로 받게 하고, 생성은 DI 컨테이너나 Graph 같은 한 곳에서 한다.
//      class MainViewModel(private val service: ApiService = Graph.recipeService) : ViewModel()
// FIXME: 변수 이름에 오타가 있다. `recipieService` → `recipeService`.
//  참조하는 곳이 한 군데뿐이라 지금 고치는 비용이 가장 싸다.
private val retrofit = Retrofit.Builder()
    .baseUrl("https://themealdb.com/api/json/v1/1/")
    .addConverterFactory(GsonConverterFactory.create())
    .build()

val recipieService = retrofit.create(ApiService::class.java)
