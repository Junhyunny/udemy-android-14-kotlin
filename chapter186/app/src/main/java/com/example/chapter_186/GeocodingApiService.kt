package com.example.chapter_186

import retrofit2.http.GET
import retrofit2.http.Query

// TODO: [todos/android-retrofit-interface-proxy.md](../../../../../../../../todos/android-retrofit-interface-proxy.md)
interface GeocodingApiService {

    // FIXME: API 키를 모든 함수의 파라미터로 받고 있다. 함수가 늘어날수록 반복된다.
    //  고치기: OkHttp Interceptor 에서 공통으로 붙인다.
    //      class ApiKeyInterceptor(key) : Interceptor { url.newBuilder().addQueryParameter("key", key) }
    //  그러면 이 인터페이스에서 `apiKey` 파라미터가 사라진다.
    @GET("maps/api/geocode/json")
    suspend fun getAddressFromCoordinate(
        @Query("latlng") latlng: String,
        @Query("key") apiKey: String
    ): GeocodingResponse
}