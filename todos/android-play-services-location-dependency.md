# `com.google.android.gms` 의존성은 뭐지

## 질문이 나온 코드

- [`chapter171/app/src/main/java/com/example/chapter_171/LocationUtils.kt`](../chapter171/app/src/main/java/com/example/chapter_171/LocationUtils.kt)
- 질문: 해당 의존성은 뭐지? 언제 사용하는지 유즈 케이스들을 조사해서 정리해줘.

```kotlin
import android.location.Address
import android.location.Geocoder
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.LatLng
```

## 질문 전제 점검

- **`com.google.android.gms`는 안드로이드 프레임워크가 아니다.** 패키지 이름이 `android.*`가 아니라 `com.google.*`인 것이 결정적인 단서다. 이건 **Google Play services**라는 별도의 라이브러리 묶음이고, AOSP(안드로이드 오픈 소스)에 들어 있지 않다. 구글이 별도로 배포하고, **기기에 Play 스토어가 깔려 있어야 동작**한다.

- **위 import 블록은 사실 서로 다른 세 출처에서 온다.** 하나로 뭉뚱그리면 안 된다.

  | import | 출처 | 실제 의존성 |
  | --- | --- | --- |
  | `android.location.Geocoder`, `Address` | **안드로이드 프레임워크** | 없음 (기본 제공) |
  | `com.google.android.gms.location.*` | Play services | `play-services-location` |
  | `com.google.android.gms.maps.model.LatLng` | Play services | `play-services-maps` |

  `build.gradle.kts`를 보면 둘 다 선언돼 있다.

  ```kotlin
  implementation(libs.play.services.maps)                                 // 카탈로그 경유
  implementation("com.google.android.gms:play-services-location:21.4.0")  // 문자열 직접
  ```

- **그런데 `play-services-maps`는 이 코드에서 거의 필요 없다.** `LatLng`을 딱 한 곳에서 쓰는데, **만들자마자 다시 풀어서 쓰고 버린다.**

  ```kotlin
  val coordinate = LatLng(location.latitude, location.longitude)
  val addresses = geocoder.getFromLocation(coordinate.latitude, coordinate.longitude, 1)
  //                                       ↑ 결국 원래 값을 그대로 쓴다
  ```

  `LatLng`를 지우고 `location.latitude`, `location.longitude`를 바로 넘겨도 완전히 같다. **지도를 화면에 그리지 않는 앱에 지도 SDK 전체를 넣고 있는 셈**이다. 강의가 지도를 나중에 다룰 예정이라면 남겨 두는 것도 이해되지만, 지금 코드만 보면 불필요한 의존성이다.

- **두 의존성의 선언 방식이 다른 것도 정리 대상이다.** `play-services-maps`는 버전 카탈로그에 있고 `play-services-location`은 문자열로 박혀 있다. 이 저장소의 다른 챕터는 카탈로그로 통일돼 있다. → [`gradle-version-catalog-and-build-files.md`](gradle-version-catalog-and-build-files.md)

## 공부할 내용

### Google Play services란 무엇인가

안드로이드에는 두 겹의 API가 있다.

```
┌─────────────────────────────────────────┐
│ Google Play services (com.google.android.gms)  ← 구글이 배포, GMS 기기만
│  - 위치(FLP), 지도, 로그인, 결제, ML Kit, 광고 ID ...
├─────────────────────────────────────────┤
│ Android Framework (android.*)                  ← AOSP, 모든 기기
│  - LocationManager, Geocoder, Camera, ...
└─────────────────────────────────────────┘
```

Play services의 특징은 **앱과 따로 업데이트된다**는 점이다. 기기의 OS 버전이 낡아도 Play 스토어를 통해 GMS가 갱신되므로, 구글은 새 기능을 OS 업데이트 없이 배포할 수 있다. 이것이 구글이 많은 기능을 프레임워크 대신 Play services에 넣는 이유다.

**대가는 이식성이다.** Play services가 없는 기기에서는 동작하지 않는다.

| 상황 | Play services |
| --- | --- |
| 일반 Play 스토어 탑재 기기 | 있다 |
| 중국 내수용 ROM (화웨이 등) | **없다** |
| AOSP 기반 커스텀 ROM, 일부 에뮬레이터 이미지 | **없다** |
| Amazon Fire 태블릿 | **없다** |

### `play-services-location` — 무엇을 주는가

핵심은 **Fused Location Provider(FLP)**다. 이름 그대로 여러 위치 소스를 **융합**한다.

```
GPS 위성  ─┐
Wi-Fi     ─┼→ Fused Location Provider → 하나의 Location
기지국     ─┤
센서(가속도) ─┘
```

프레임워크의 `LocationManager`는 **개발자가 직접 소스를 고르고 관리**해야 한다. GPS를 켤지, 네트워크를 쓸지, 실내에서 GPS가 안 잡히면 어떻게 할지를 전부 손으로 처리한다. FLP는 **요구 정확도만 말하면 알아서 고른다.**

```kotlin
// 프레임워크 방식 — 소스를 직접 고른다
locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, listener)

// FLP 방식 — 원하는 것만 말한다
val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 100).build()
fusedLocationClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
```

→ [`android-fused-location-provider-and-looper.md`](android-fused-location-provider-and-looper.md)

`play-services-location`에는 FLP 외에도 이런 것들이 들어 있다.

| API | 하는 일 | 유즈 케이스 |
| --- | --- | --- |
| **FusedLocationProviderClient** | 현재 위치, 위치 업데이트 | 지도 앱의 내 위치, 배달 추적, 운동 기록 |
| **Geofencing** | 특정 반경 진입·이탈 감지 | "집에 도착하면 알림", 매장 근처 쿠폰 |
| **Activity Recognition** | 걷기·달리기·운전 중 판별 | 만보기, 운전 중 알림 차단 |
| **SettingsClient** | 위치 설정이 꺼져 있으면 켜 달라는 시스템 다이얼로그 | 위치 기능 진입 전 사전 점검 |
| **Geocoding(FLP 아님)** | 좌표 ↔ 주소 변환 | 이 코드의 `reverseGeocodeLocation` — 단, **프레임워크 `Geocoder`를 쓰고 있다** |

### 언제 FLP를 쓰고 언제 프레임워크를 쓰나

| 기준 | 선택 |
| --- | --- |
| 일반 앱, Play 스토어 배포 | **FLP** — 배터리·정확도가 앞선다 |
| GMS 없는 기기도 지원해야 한다 | `LocationManager` 또는 분기 처리 |
| 실내 정확도가 중요하다 | **FLP** — Wi-Fi 융합의 이점이 크다 |
| 의존성을 최소화해야 한다 | `LocationManager` |

구글의 권장은 명확하다. **일반적인 앱은 FLP를 쓴다.** 프레임워크 API를 직접 쓰는 것은 GMS를 쓸 수 없는 경우로 한정한다.

### 다른 Play services 아티팩트들

`play-services-*`는 **기능별로 쪼개져 있다.** 필요한 것만 넣는 것이 원칙이다.

| 아티팩트 | 쓰는 때 |
| --- | --- |
| `play-services-location` | 위치, 지오펜싱, 활동 인식 |
| `play-services-maps` | 지도를 **화면에 그릴 때** |
| `play-services-auth` | 구글 계정 로그인 |
| `play-services-base` | 다른 아티팩트의 공통 기반 (직접 넣을 일은 드물다) |
| `play-services-ads-identifier` | 광고 ID |

**`play-services-all` 같은 통짜 의존성은 존재하지 않는다.** 예전에 `play-services` 단일 아티팩트가 있었지만 메서드 수가 폭증해 폐기됐다.

### 기기에 Play services가 있는지 확인하기

FLP를 쓴다면 방어 코드가 필요할 수 있다.

```kotlin
val availability = GoogleApiAvailability.getInstance()
when (availability.isGooglePlayServicesAvailable(context)) {
    ConnectionResult.SUCCESS -> { /* 정상 */ }
    else -> { /* 대체 경로 또는 안내 */ }
}
```

`isGooglePlayServicesAvailable`은 **버전이 낡은 경우**도 잡아 준다. `getErrorDialog()`로 업데이트를 유도하는 다이얼로그를 띄울 수 있다.

### `Geocoder`는 Play services가 아니다

혼동하기 쉬운 부분이다. 이 코드의 주소 변환은 **프레임워크 API**를 쓴다.

```kotlin
import android.location.Geocoder   // ← android.*  프레임워크다
val geocoder = Geocoder(context, Locale.getDefault())
```

`Geocoder`는 **네트워크를 타고 백엔드에 물어본다.** 그래서 두 가지를 조심해야 한다.

- **동기 버전은 API 33부터 deprecated다.** `getFromLocation(lat, lon, max)`를 메인 스레드에서 부르면 ANR 위험이 있다. 이 챕터를 빌드하면 실제로 경고가 뜬다.

  ```
  w: LocationUtils.kt:60:22 'fun getFromLocation(p0: Double, p1: Double, p2: Int):
     (Mutable)List<Address!>?' is deprecated. Deprecated in Java.
  ```

  API 33+에서는 콜백 버전을 쓴다.

  ```kotlin
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      geocoder.getFromLocation(lat, lon, 1) { addresses -> /* 콜백 */ }
  } else {
      @Suppress("DEPRECATION")
      geocoder.getFromLocation(lat, lon, 1)
  }
  ```

- **`isPresent()`로 지원 여부를 확인해야 한다.** 백엔드가 없는 기기에서는 항상 빈 결과가 온다.

이 코드는 `reverseGeocodeLocation`을 **컴포저블 본문에서 직접 호출**하고 있어서, 재구성될 때마다 동기 네트워크 호출이 일어난다. 실기기에서 체감되는 문제다.

```kotlin
val address = location.value?.let {
    locationUtils.reverseGeocodeLocation(location = it)   // 재구성마다 실행된다
}
```

→ [`compose-recomposition-timing-and-scope.md`](compose-recomposition-timing-and-scope.md), [`kotlin-coroutine-dispatchers.md`](kotlin-coroutine-dispatchers.md)

## 관련 아키텍처와 베스트 프랙티스

### 의존성은 필요한 것만, 카탈로그로

```kotlin
// libs.versions.toml
[versions]
playServicesLocation = "21.4.0"

[libraries]
play-services-location = { group = "com.google.android.gms", name = "play-services-location", version.ref = "playServicesLocation" }
```

```kotlin
// app/build.gradle.kts
implementation(libs.play.services.location)
// implementation(libs.play.services.maps)   ← 지도를 그리기 전까지는 불필요
```

**"이 의존성이 없으면 무엇이 안 되는가"**를 답할 수 없으면 지우는 것이 맞다.

### Play services API를 직접 노출하지 않는다

`LocationUtils`가 `FusedLocationProviderClient`를 감싸고 있는 구조는 좋다. **화면과 ViewModel은 `LocationData`만 알고 GMS 타입을 모른다.**

```kotlin
data class LocationData(val latitude: Double, val longitude: Double)   // 우리 타입
```

이렇게 해 두면 나중에 GMS 없는 기기 대응이나 테스트 대역 교체가 **한 클래스 수정으로 끝난다.** 반대로 화면 코드에 `LocationResult`나 `LatLng`이 퍼지면 교체가 불가능해진다.

다만 지금 구조에는 개선할 점이 있다.

```kotlin
// 현재: Utils 가 ViewModel 을 알고 있다 — 의존 방향이 거꾸로다
fun requestLocationUpdate(viewModel: LocationViewModel)

// 권장: 결과를 흘려보내고, 구독은 위에서 한다
fun locationUpdates(): Flow<LocationData>
```

→ [`android-repository-single-source-of-truth.md`](android-repository-single-source-of-truth.md), [`kotlin-flow-concepts-and-suspend.md`](kotlin-flow-concepts-and-suspend.md)

### 위치 기능은 권한·설정·가용성 세 가지를 모두 확인한다

| 확인 대상 | 방법 |
| --- | --- |
| 런타임 권한 | `ContextCompat.checkSelfPermission` |
| 기기 위치 설정이 켜져 있는가 | `SettingsClient.checkLocationSettings` |
| Play services가 있는가 | `GoogleApiAvailability` |

지금 코드는 **권한만 확인**한다. 기기의 위치 설정이 꺼져 있으면 콜백이 영영 오지 않고, 사용자는 이유를 모른 채 기다린다.

### 버전을 임의로 올리지 않는다

Play services 아티팩트는 **각자 독립적으로 버전이 매겨진다.** `play-services-maps:20.0.0`과 `play-services-location:21.4.0`처럼 숫자가 달라도 정상이다. 다만 여러 아티팩트를 함께 쓸 때는 **서로 가까운 시기의 버전**으로 맞추는 편이 충돌이 적다.

## 체크리스트

- [ ] `com.google.android.gms`가 프레임워크가 아니라 Play services임을 안다.
- [ ] Play services가 앱과 별도로 업데이트되는 구조를 설명할 수 있다.
- [ ] GMS가 없는 기기가 존재한다는 것과 그 영향을 안다.
- [ ] `play-services-location`과 `play-services-maps`를 구분할 수 있다.
- [ ] 이 코드에서 `play-services-maps`가 왜 불필요한지 설명할 수 있다.
- [ ] FLP가 `LocationManager`와 무엇이 다른지 설명할 수 있다.
- [ ] `play-services-location`의 주요 유즈 케이스를 셋 이상 말할 수 있다.
- [ ] `Geocoder`가 프레임워크 API임을 안다.
- [ ] `Geocoder` 동기 호출의 문제를 설명할 수 있다.
- [ ] GMS 타입을 앱 전체에 퍼뜨리면 안 되는 이유를 안다.
- [ ] 권한·기기 설정·가용성 세 가지를 모두 확인해야 하는 이유를 안다.

## 공식 참고 자료

- [Google for Developers: Google Play services overview](https://developers.google.com/android/guides/overview)
- [Google for Developers: Set up Google Play services](https://developers.google.com/android/guides/setup)
- [Google for Developers: Fused Location Provider API](https://developers.google.com/location-context/fused-location-provider)
- [Google for Developers: `FusedLocationProviderClient`](https://developers.google.com/android/reference/com/google/android/gms/location/FusedLocationProviderClient)
- [Google for Developers: `GoogleApiAvailability`](https://developers.google.com/android/reference/com/google/android/gms/common/GoogleApiAvailability)
- [Android Developers: Build location-aware apps](https://developer.android.com/develop/sensors-and-location/location)
- [Android Developers: `Geocoder`](https://developer.android.com/reference/android/location/Geocoder)
- [Android Developers: `LocationManager`](https://developer.android.com/reference/android/location/LocationManager)
