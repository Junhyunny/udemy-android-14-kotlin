package com.example.chapter_186

import retrofit2.http.GET
import retrofit2.http.Query

// TODO: [todos/android-retrofit-interface-proxy.md](../../../../../../../../todos/android-retrofit-interface-proxy.md)
interface GeocodingApiService {

    @GET("maps/api/geocode/json")
    suspend fun getAddressFromCoordinate(
        @Query("latlng") latlng: String,
        @Query("key") apiKey: String
    ): GeocodingResponse
}