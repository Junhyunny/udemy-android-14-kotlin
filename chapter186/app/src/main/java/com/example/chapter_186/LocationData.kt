// ARCH-FIXME: 파일 하나에 성격이 다른 모델 셋이 섞여 있다.
//      LocationData      — 앱 내부에서 쓰는 도메인 모델
//      GeocodingResult   — 서버 응답 DTO
//      GeocodingResponse — 서버 응답 DTO
//  DTO 와 도메인 모델을 같은 파일에 두면 경계가 흐려지고, 실제로 이 앱은
//  DTO(`GeocodingResult`)를 화면까지 그대로 올려 쓰고 있다.
//  고치기: 패키지로 나눈다.
//      data/remote/dto/GeocodingDto.kt   (GeocodingResponse, GeocodingResult)
//      domain/model/LocationData.kt      (LocationData, Address)
//  그리고 Repository 경계에서 DTO → 도메인 모델로 변환한다.
//
// ARCH-FIXME: 챕터 전체가 단일 패키지(`com.example.chapter_186`)에 11개 파일이 평평하게 쌓여 있다.
//  화면·ViewModel·네트워크·모델이 한 폴더에 섞여 있어 계층을 눈으로 확인할 수 없다.
//  고치기(이 규모에 맞는 최소 구성):
//      ui/        MainActivity.kt, ShoppingList.kt, LocationSelectionScreen.kt
//      ui/model/  LocationViewModel.kt
//      data/      GeocodingApiService.kt, RetrofitClient.kt, LocationUtils.kt, GeocodingRepository.kt
//      domain/    LocationData.kt
package com.example.chapter_186

data class LocationData(
    val latitude: Double,
    val longitude: Double
)

// FIXME: 프로퍼티 이름이 JSON 키(snake_case)를 그대로 따르고 있다.
//  - 코틀린 명명 규칙에 어긋난다
//  - R8/난독화가 적용되면 Gson 이 필드명을 못 찾아 파싱이 깨질 수 있다
//  고치기: `@SerializedName` 으로 JSON 키를 고정하고 프로퍼티는 camelCase 로 둔다.
//      data class GeocodingResult(@SerializedName("formatted_address") val formattedAddress: String)
// FIXME: `status` 를 문자열로 두면 오타를 잡을 수 없다. enum 이나 sealed 로 표현하는 편이 낫다.
data class GeocodingResult(
    val formatted_address: String
)

data class GeocodingResponse(
    val results: List<GeocodingResult>,
    val status: String
)
