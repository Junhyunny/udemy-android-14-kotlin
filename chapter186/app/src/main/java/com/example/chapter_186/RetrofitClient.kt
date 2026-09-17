package com.example.chapter_186

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

// ARCH-FIXME: 이 앱에는 조립 지점(composition root)이 없다.
//  `RetrofitClient` 는 전역 object 이고, `LocationUtils` 는 컴포저블이 만들고,
//  ViewModel 은 함수 안에서 전역을 호출한다. 의존성이 만들어지는 곳이 세 군데로 흩어져 있다.
//  chapter205 의 `Graph` 처럼(그것도 완벽하진 않지만) 최소한 한 곳에 모으는 편이 낫다.
//      object Graph {
//          private val retrofit by lazy { Retrofit.Builder()...build() }
//          val geocodingApi: GeocodingApiService by lazy { retrofit.create(...) }
//          val geocodingRepository by lazy { GeocodingRepository(geocodingApi) }
//          fun locationDataSource(context: Context) = LocationDataSource(context.applicationContext)
//      }
//  그러면 "무엇이 무엇에 의존하는가"를 파일 하나로 볼 수 있게 된다.
object RetrofitClient {
    private const val BASE_URL = "https://maps.googleapis.com/"

    // FIXME: 호출할 때마다 Retrofit / OkHttpClient / 프록시를 통째로 새로 만든다.
    //  `OkHttpClient` 는 커넥션 풀과 스레드 풀을 들고 있어서, 매번 새로 만들면
    //  연결 재사용이 안 되고 소켓·스레드가 낭비된다. 주소를 조회할 때마다 반복된다.
    //  고치기: 한 번만 만들어 재사용한다.
    //      val service: GeocodingApiService by lazy { Retrofit.Builder()...create(...) }
    //      호출부는 `RetrofitClient.service.getAddressFromCoordinate(...)`
    fun create(): GeocodingApiService {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        return retrofit.create(GeocodingApiService::class.java)
    }
}
