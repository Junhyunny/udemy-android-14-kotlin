package com.example.chapter_143

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET

interface ApiService {
    @GET("categories.php")
    // TODO: [todos/kotlin-coroutines-suspend-and-event-loop.md](../../../../../../../../todos/kotlin-coroutines-suspend-and-event-loop.md)
    // TODO: [todos/kotlin-coroutines-continuation-state-machine.md](../../../../../../../../todos/kotlin-coroutines-continuation-state-machine.md)
    suspend fun getCategories(): CategoriesResponse
}

private val retrofit = Retrofit.Builder()
    .baseUrl("https://themealdb.com/api/json/v1/1/")
    .addConverterFactory(GsonConverterFactory.create())
    .build()

val recipieService = retrofit.create(ApiService::class.java)
