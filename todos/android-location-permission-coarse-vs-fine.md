# `COARSE`와 `FINE` 위치 권한의 차이점은 뭐야

## 질문이 나온 코드

- [`chapter171/app/src/main/java/com/example/chapter_171/MainActivity.kt`](../chapter171/app/src/main/java/com/example/chapter_171/MainActivity.kt)
- 질문: 같은 LOCATION 권한인데, COARSE와 FINE의 차이점은 뭐야?

```kotlin
if (permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        && permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
    locationUtils.requestLocationUpdate(viewModel = viewModel)
} else {
    ...
}
```

## 질문 전제 점검

- **정확도의 차이다.** 숫자로 보면 확연하다.

  | 권한 | 사용자에게 보이는 이름 | 정확도 |
  | --- | --- | --- |
  | `ACCESS_COARSE_LOCATION` | **대략적인 위치**(Approximate) | 약 3km² 범위 |
  | `ACCESS_FINE_LOCATION` | **정확한 위치**(Precise) | 보통 50m 이내, 좋으면 수 m |

  > "`ACCESS_COARSE_LOCATION` provides a location estimate accurate to within about 3 square kilometers (about 1.2 square miles), while `ACCESS_FINE_LOCATION` usually provides accuracy within about 50 meters (160 feet) and is sometimes as accurate as within a few meters (10 feet) or better."

- **그런데 "`FINE`은 `COARSE`의 상위 호환이니 `FINE`만 요청하면 된다"는 생각은 틀렸다.** Android 12부터 그렇게 동작하지 않는다.

  > "If you try to request only `ACCESS_FINE_LOCATION`, the system ignores the request on some releases of Android 12. If your app targets Android 12 or higher, the system logs the following error message in Logcat: `ACCESS_FINE_LOCATION must be requested with ACCESS_COARSE_LOCATION`."

  **반드시 둘을 한 번에 요청해야 한다.** 지금 코드가 `arrayOf(COARSE, FINE)`으로 함께 요청하는 것은 이 규칙을 지킨 것이다.

- **그리고 이 코드의 진짜 문제는 조건이 `&&`라는 것이다.** Android 12부터 **사용자가 정확도를 고를 수 있다.**

  > "When your app requests both `ACCESS_FINE_LOCATION` and `ACCESS_COARSE_LOCATION`, the system permissions dialog includes the following options for the user: **Precise**: Allows your app to get precise location information. **Approximate**: Allows your app to get only approximate location information."

  사용자가 **"대략적인 위치"를 선택하면** `COARSE`만 `true`, `FINE`은 `false`가 온다. 그런데 이 코드는 `&&`라서 **그 경우를 거부와 똑같이 취급**한다. 사용자는 분명히 권한을 허용했는데 앱은 "권한이 필요합니다" Toast를 띄우는 것이다.

  **FLP는 `COARSE`만 있어도 잘 동작한다.** 정확도만 낮아질 뿐이다. 이 코드는 동작할 수 있는 경우를 스스로 막고 있다.

- **구글은 오히려 `COARSE`만 요청하기를 권장한다.**

  > "it's recommended that you only request `ACCESS_COARSE_LOCATION` since you can fulfill most use cases even with even approximate location information."

  "정밀 위치가 정말 필요한가"를 먼저 묻는 것이 권장 흐름이다.

## 공부할 내용

### 사용자 눈에는 어떻게 보이는가

Android 12+ 권한 다이얼로그는 이렇게 뜬다.

```
┌────────────────────────────────┐
│  이 앱이 기기 위치에 액세스하도록  │
│  허용하시겠습니까?                │
│                                │
│   ○ 정확한 위치   ● 대략적인 위치  │   ← 사용자가 고른다
│                                │
│   [ 앱 사용 중에만 허용 ]         │
│   [ 이번만 허용 ]                │
│   [ 허용 안 함 ]                 │
└────────────────────────────────┘
```

**두 축이 곱해진다.** "얼마나 정확하게"(정확/대략) × "언제까지"(사용 중/한 번만/거부). 그래서 결과 조합이 여러 가지다.

| 사용자 선택 | `COARSE` | `FINE` |
| --- | --- | --- |
| 정확한 위치 + 허용 | `true` | `true` |
| 대략적인 위치 + 허용 | `true` | **`false`** |
| 허용 안 함 | `false` | `false` |

**`FINE`이 `true`인데 `COARSE`가 `false`인 경우는 없다.** `FINE`은 `COARSE`를 포함하기 때문이다. 그래서 검사 순서는 **`FINE` → `COARSE` → 거부**가 되어야 한다.

### 공식 권장 형태

```kotlin
val locationPermissionRequest = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions()
) { permissions ->
    when {
        permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) -> {
            // Precise location access granted.
        }
        permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {
            // Only approximate location access granted.
        }
        else -> {
            // No location access granted.
        }
    }
}

locationPermissionRequest.launch(
    arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )
)
```

**`&&`가 아니라 `when`의 순차 분기**라는 점이 핵심이다. 세 상태를 각각 다루면 "대략적 위치만 허용" 사용자도 앱을 쓸 수 있다.

### 이 코드에 적용하면

```kotlin
val requestPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestMultiplePermissions(),
) { permissions ->
    when {
        permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
        permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true -> {
            locationUtils.requestLocationUpdate(viewModel = viewModel)
        }
        else -> {
            // 거부 처리
        }
    }
}
```

`hasLocationPermission`도 같은 이유로 `&&`를 `||`로 바꿔야 일관된다.

```kotlin
// 현재: 둘 다 있어야 true
fun hasLocationPermission(context: Context): Boolean =
    checkSelfPermission(context, ACCESS_FINE_LOCATION) == GRANTED &&
    checkSelfPermission(context, ACCESS_COARSE_LOCATION) == GRANTED

// 권장: 하나라도 있으면 위치를 얻을 수 있다
fun hasLocationPermission(context: Context): Boolean =
    checkSelfPermission(context, ACCESS_FINE_LOCATION) == GRANTED ||
    checkSelfPermission(context, ACCESS_COARSE_LOCATION) == GRANTED
```

Play services가 `@RequiresPermission(anyOf = [...])`로 선언한 것과 같은 논리다. → [`android-requires-permission-annotation.md`](android-requires-permission-annotation.md)

### 대략적 위치에서 정밀 위치로 승급하기

한 번 "대략적"을 고른 사용자에게 다시 물을 수 있다.

> "To request that the user upgrade your app's location access from approximate to precise, do the following: 1. If necessary, explain why your app needs the permission. 2. Request the `ACCESS_FINE_LOCATION` and `ACCESS_COARSE_LOCATION` permissions together again."

같은 배열로 다시 요청하면 **시스템이 다른 다이얼로그**를 보여 준다. 이미 대략적 위치가 허용돼 있음을 알기 때문이다. 다만 **정밀 위치가 정말 필요한 기능에 진입할 때만** 물어야 한다. 아무 때나 반복하면 사용자가 영구 거부로 답한다.

### 매니페스트에는 둘 다 선언한다

```xml
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
```

이 프로젝트는 이미 둘 다 선언돼 있다. **런타임 요청과 별개로 매니페스트 선언은 필수**다. 선언하지 않은 권한은 요청해도 즉시 거부된다.

`FINE`이 선택적 기능이라면 **필수가 아님을 밝힐 수도 있다.**

```xml
<uses-feature android:name="android.hardware.location.gps" android:required="false" />
```

### 위치 권한의 전체 지형

| 권한 | 범위 | 비고 |
| --- | --- | --- |
| `ACCESS_COARSE_LOCATION` | 대략적, 포그라운드 | 대부분의 경우 충분 |
| `ACCESS_FINE_LOCATION` | 정밀, 포그라운드 | `COARSE`와 함께 요청 |
| `ACCESS_BACKGROUND_LOCATION` | 앱이 꺼져 있을 때도 | **별도로, 나중에** 요청. 심사 까다로움 |

**`ACCESS_BACKGROUND_LOCATION`은 포그라운드 권한과 같이 요청할 수 없다.** 먼저 포그라운드 권한을 받고, 실제로 백그라운드 기능을 쓰는 시점에 따로 요청해야 한다. 이 순서를 어기면 요청이 무시된다.

### 정확도별로 무엇이 가능한가

| 기능 | 필요한 정확도 |
| --- | --- |
| 날씨, 지역 뉴스, 시간대 | `COARSE`로 충분 |
| 근처 매장·맛집 목록 | `COARSE`로 충분한 경우가 많다 |
| 지도에 내 위치 점 찍기 | `COARSE`면 부정확하지만 동작은 한다 |
| 턴바이턴 내비게이션 | `FINE` 필요 |
| 운동 경로 기록, 거리 측정 | `FINE` 필요 |
| 배달 기사 실시간 추적 | `FINE` 필요 |

이 챕터의 앱은 **좌표를 표시하고 주소로 변환**한다. 주소 수준이라면 `COARSE`로도 의미 있는 결과가 나온다.

## 관련 아키텍처와 베스트 프랙티스

### 필요한 최소 권한만 요청한다

권한 요청은 **사용자가 앱을 거부할 수 있는 지점**이다. 요청이 많고 이르면 이탈률이 올라간다.

```
질문 순서:
1. 이 기능에 위치가 정말 필요한가?        → 아니면 요청하지 않는다
2. COARSE 로 충분한가?                   → 충분하면 FINE 을 요청하지 않는다
3. 포그라운드로 충분한가?                  → 충분하면 BACKGROUND 를 요청하지 않는다
```

### 기능을 쓰는 시점에 요청한다

```kotlin
// 나쁨: 앱 시작하자마자
override fun onCreate(...) { requestLocationPermission() }

// 좋음: 위치 기능 버튼을 눌렀을 때
Button(onClick = { if (!hasPermission) launcher.launch(...) else getLocation() })
```

지금 코드는 **버튼 클릭 시 요청**하고 있어서 이 부분은 잘 되어 있다. 맥락이 분명할 때 물으면 허용률이 높다.

### 권한이 없어도 앱이 동작해야 한다

권한 거부는 **정상적인 선택지**다. 거부당했을 때 기능 전체가 멈추는 대신 대체 경로를 준다.

| 거부 시 | 대체 |
| --- | --- |
| 위치 권한 없음 | 도시를 직접 검색·선택하게 한다 |
| 정밀 위치 없음 | 대략적 위치로 동작, 정밀이 필요한 기능만 안내 |

### 권한 상태를 UI 상태로 모델링한다

```kotlin
sealed interface LocationPermissionState {
    data object Granted : LocationPermissionState          // 정밀
    data object ApproximateOnly : LocationPermissionState  // 대략
    data object Denied : LocationPermissionState
    data object PermanentlyDenied : LocationPermissionState
}
```

`Boolean` 하나로 표현하면 **"대략만 허용"이라는 중간 상태가 사라진다.** 지금 코드가 그 상태를 놓친 것도 결국 이 모델링 문제다. → [`kotlin-sealed-class-and-interface.md`](kotlin-sealed-class-and-interface.md)

### 권한은 언제든 취소될 수 있다

사용자가 설정에서 권한을 끄거나, 시스템이 **오래 안 쓴 앱의 권한을 자동 회수**할 수 있다(Android 11+). 따라서 권한 상태를 앱 시작 시 한 번만 확인하고 캐시하면 안 된다. **기능을 실행하는 시점마다 확인**한다.

## 체크리스트

- [ ] `COARSE`와 `FINE`의 정확도 차이를 숫자로 말할 수 있다.
- [ ] Android 12부터 `FINE`만 요청하면 안 되는 이유를 안다.
- [ ] 사용자가 "정확/대략"을 고를 수 있다는 것을 안다.
- [ ] "대략만 허용" 시 `COARSE=true, FINE=false`가 됨을 안다.
- [ ] `&&` 검사가 왜 문제인지 설명할 수 있다.
- [ ] 검사 순서가 `FINE` → `COARSE` → 거부여야 하는 이유를 안다.
- [ ] FLP가 `COARSE`만으로도 동작한다는 것을 안다.
- [ ] 정밀 위치로 승급 요청하는 방법을 안다.
- [ ] `ACCESS_BACKGROUND_LOCATION`을 따로 요청해야 하는 이유를 안다.
- [ ] 기능별로 필요한 정확도를 판단할 수 있다.
- [ ] 권한 상태를 세 가지 이상으로 모델링할 필요를 안다.
- [ ] 권한이 런타임에 회수될 수 있음을 안다.

## 공식 참고 자료

- [Android Developers: Request location permissions](https://developer.android.com/develop/sensors-and-location/location/permissions)
- [Android Developers: Request location access at runtime](https://developer.android.com/develop/sensors-and-location/location/permissions/runtime)
- [Android Developers: Approximate location codelab](https://developer.android.com/codelabs/approximate-location)
- [Android Developers: Access location in the background](https://developer.android.com/training/location/background)
- [Android Developers: Request runtime permissions](https://developer.android.com/training/permissions/requesting)
- [Android Developers: Explain access to more sensitive information](https://developer.android.com/training/permissions/explaining-access)
- [Android Developers: Privacy changes in Android 10](https://developer.android.com/about/versions/10/privacy/changes)
- [Android Developers: `Manifest.permission`](https://developer.android.com/reference/android/Manifest.permission)
