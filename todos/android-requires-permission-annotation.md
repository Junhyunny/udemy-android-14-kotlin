# `@RequiresPermission`은 어떤 용도로 사용하는가

## 질문이 나온 코드

- [`chapter171/app/src/main/java/com/example/chapter_171/LocationUtils.kt`](../chapter171/app/src/main/java/com/example/chapter_171/LocationUtils.kt)
- 질문: `@RequiresPermission`은 어떤 용도로 사용하는가? 해당 애너테이션이 없으면 에러가 발생하는 거 같아.

```kotlin
// @RequiresPermission(allOf = [permission.ACCESS_FINE_LOCATION, permission.ACCESS_COARSE_LOCATION])
@SuppressLint("MissingPermission") // 이렇게 린팅을 스킵하는 방법도 있음
fun requestLocationUpdate(viewModel: LocationViewModel) {
    ...
    fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
}
```

## 질문 전제 점검

- **"애너테이션이 없으면 에러가 발생한다"는 관찰은 맞지만, 원인은 반대 방향이다.** 에러를 내는 주체는 `@RequiresPermission`이 아니다. **`fusedLocationClient.requestLocationUpdates()` 쪽에 이미 `@RequiresPermission`이 붙어 있어서** Lint가 "호출하는 쪽에서 권한을 확인했는지"를 따지는 것이다.

  ```
  Play services 내부:
      @RequiresPermission(anyOf = [ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION])
      fun requestLocationUpdates(...)        ← 여기에 붙어 있다

  우리 코드:
      fun requestLocationUpdate(...) {
          requestLocationUpdates(...)        ← Lint: MissingPermission 경고
      }
  ```

  즉 `@RequiresPermission`은 **"내가 이 권한이 필요하다"고 선언하는 것**이지, 붙인다고 권한 검사가 생기는 게 아니다.

- **그래서 해결 방법이 세 가지가 되고, 코드에는 그중 두 개가 적혀 있다.**

  | 방법 | 의미 |
  | --- | --- |
  | **① 호출 전에 권한을 확인한다** | Lint가 확인 코드를 인식하면 경고가 사라진다 — **정석** |
  | **② `@RequiresPermission`을 내 함수에 붙인다** (주석 처리된 줄) | "책임을 호출자에게 넘긴다" — 경고가 내 호출자로 이동한다 |
  | **③ `@SuppressLint("MissingPermission")`** (현재 코드) | "확인했으니 잠자코 있어라" — 검사를 끈다 |

  **③은 가장 마지막 선택지다.** 실제로 권한 확인이 없다면 런타임에 `SecurityException`이 난다. 지금 코드는 호출하는 쪽(`MainActivity`)에서 `hasLocationPermission`을 확인하고 있어서 실제로는 동작하지만, **Lint는 그 사실을 알지 못한다.**

- **`@RequiresPermission`도 컴파일러 기능이 아니다.** `@DrawableRes`와 같은 계열의 **Lint용 메타데이터**다. 붙여도 컴파일은 통과하고, 안 붙여도 컴파일은 통과한다. → [`android-drawable-res-annotation.md`](android-drawable-res-annotation.md)

- **`allOf`와 `anyOf`를 혼동하면 안 된다.** 주석 처리된 줄은 `allOf`(둘 다 필요)인데, Play services의 실제 선언은 `anyOf`(둘 중 하나면 됨)다. FLP는 **COARSE만 있어도 동작**하기 때문이다. `allOf`로 선언하면 실제보다 강한 조건을 주장하는 셈이다. → [`android-location-permission-coarse-vs-fine.md`](android-location-permission-coarse-vs-fine.md)

## 공부할 내용

### 무엇을 위한 애너테이션인가

안드로이드의 런타임 권한은 **"안 물어보고 쓰면 앱이 죽는다"**는 특성이 있다.

```kotlin
fusedLocationClient.requestLocationUpdates(...)   // 권한 없으면 SecurityException
```

컴파일러는 이걸 못 잡는다. 권한은 **런타임 상태**이지 타입이 아니기 때문이다. `@RequiresPermission`은 이 간극을 메운다.

> "The `@RequiresPermission` annotation ... validate the permissions of the caller of a method."

### `anyOf`와 `allOf`

```kotlin
// 나열된 권한 중 하나라도 있으면 된다
@RequiresPermission(anyOf = [
    Manifest.permission.ACCESS_COARSE_LOCATION,
    Manifest.permission.ACCESS_FINE_LOCATION
])
fun getLocation() { ... }

// 나열된 권한이 전부 있어야 한다
@RequiresPermission(allOf = [
    Manifest.permission.READ_CONTACTS,
    Manifest.permission.WRITE_CONTACTS
])
fun syncContacts() { ... }

// 권한이 하나뿐이면 value 로 바로 쓴다
@RequiresPermission(Manifest.permission.CAMERA)
fun openCamera() { ... }
```

> "To check for a single permission from a list of valid permissions, use the `anyOf` attribute, and to check for a set of permissions, use the `allOf` attribute."

### Lint가 경고를 거두는 조건

가장 중요한 부분이다. Lint는 **호출 직전에 권한 확인이 보이면** 경고를 내리지 않는다.

```kotlin
// Lint 가 인식하는 형태
if (ContextCompat.checkSelfPermission(context, permission.ACCESS_FINE_LOCATION)
        == PackageManager.PERMISSION_GRANTED) {
    fusedLocationClient.requestLocationUpdates(...)   // 경고 없음
}
```

반면 **확인 로직이 다른 함수로 빠지면 추적이 끊긴다.**

```kotlin
// Lint 가 못 따라가는 형태 — 지금 코드가 이렇다
fun hasLocationPermission(context: Context): Boolean { ... }

fun requestLocationUpdate(...) {
    // 호출자가 hasLocationPermission() 으로 확인했지만 Lint 는 모른다
    fusedLocationClient.requestLocationUpdates(...)   // MissingPermission 경고
}
```

**정적 분석의 한계**다. 그래서 `@RequiresPermission`이나 `@SuppressLint`로 "내가 보증한다"고 알려 주는 우회가 필요해진다.

### 세 가지 대응을 언제 쓰나

**① 함수 안에서 직접 확인한다 — 가장 안전하다**

```kotlin
fun requestLocationUpdate(viewModel: LocationViewModel) {
    if (!hasLocationPermission(context)) return          // 방어
    if (ContextCompat.checkSelfPermission(context, permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) return
    fusedLocationClient.requestLocationUpdates(...)
}
```

**② `@RequiresPermission`으로 계약을 위로 넘긴다 — 라이브러리성 코드에 맞다**

```kotlin
@RequiresPermission(anyOf = [permission.ACCESS_COARSE_LOCATION, permission.ACCESS_FINE_LOCATION])
fun requestLocationUpdate(viewModel: LocationViewModel) { ... }
```

이러면 **이 함수를 부르는 쪽에서 Lint 경고가 뜬다.** 책임이 위로 올라갔을 뿐 사라진 게 아니다. `LocationUtils`처럼 재사용되는 유틸리티에는 이 방식이 적절하다. **문서 역할도 한다** — IDE에서 함수 시그니처만 봐도 어떤 권한이 필요한지 보인다.

**③ `@SuppressLint("MissingPermission")` — 확인이 끝났음이 명백할 때만**

```kotlin
@SuppressLint("MissingPermission")   // 호출자가 hasLocationPermission() 으로 확인함
fun requestLocationUpdate(...) { ... }
```

**반드시 이유를 주석으로 남긴다.** 억제는 "검사를 껐다"는 뜻이고, 나중에 코드가 바뀌어도 아무도 경고해 주지 않는다.

### 같은 계열의 애너테이션들

`androidx.annotation`에는 **"타입으로 표현할 수 없는 계약"**을 적는 애너테이션이 모여 있다.

| 애너테이션 | 계약 |
| --- | --- |
| `@RequiresPermission` | 이 권한이 필요하다 |
| `@RequiresApi(26)` | 이 API 레벨 이상에서만 부를 수 있다 |
| `@RequiresFeature` | 특정 기능이 활성화돼야 한다 |
| `@MainThread` / `@WorkerThread` | 이 스레드에서 불러야 한다 |
| `@CallSuper` | 오버라이드하면 `super`를 불러야 한다 |
| `@CheckResult` | 반환값을 무시하면 안 된다 |

읽는 주체는 전부 **Lint와 IDE**다. 공통 목적은 "문서에 적으면 안 읽히지만 애너테이션으로 적으면 도구가 읽어 준다"는 것이다.

### 읽기 전용·쓰기 전용 권한

`@RequiresPermission.Read`, `@RequiresPermission.Write`는 **ContentProvider처럼 읽기와 쓰기 권한이 다른 경우**에 쓴다.

```kotlin
@RequiresPermission.Read(RequiresPermission(Manifest.permission.READ_CONTACTS))
@RequiresPermission.Write(RequiresPermission(Manifest.permission.WRITE_CONTACTS))
val CONTENT_URI: Uri = ...
```

## 관련 아키텍처와 베스트 프랙티스

### 권한 확인은 "경계"에서 한 번, 방어는 "실행 지점"에서 한 번

```
사용자 버튼 클릭
   ↓
[경계] hasLocationPermission() → 없으면 요청 다이얼로그
   ↓
[실행] requestLocationUpdate() → 여기서도 한 번 더 확인 (방어)
```

**경계에서만 확인하면 깨지기 쉽다.** 사용자가 앱을 백그라운드로 보낸 뒤 설정에서 권한을 취소하고 돌아오면, 경계를 통과한 상태로 실행 지점에 도달한다. 권한은 **언제든 취소될 수 있는 상태**라는 점이 핵심이다.

### `@SuppressLint`를 쓸 때의 규칙

- **범위를 최소화한다.** 클래스가 아니라 함수에, 가능하면 문장에 붙인다.
- **이유를 주석으로 남긴다.** 지금 코드의 `// 이렇게 린팅을 스킵하는 방법도 있음`은 "방법 소개"이지 "억제 근거"가 아니다.
- **억제 대신 확인을 넣을 수 있는지 먼저 본다.**

```kotlin
// 권장 형태
@SuppressLint("MissingPermission")  // hasLocationPermission() 으로 호출 전 확인 보장
fun requestLocationUpdate(...) { ... }
```

### 런타임 예외를 잡을 것인가

권한이 취소된 상태에서 호출하면 `SecurityException`이 난다. 방어적으로 감쌀 수 있다.

```kotlin
try {
    fusedLocationClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
} catch (e: SecurityException) {
    // 권한이 도중에 취소된 경우
}
```

다만 **`try-catch`가 권한 확인을 대신하지는 않는다.** 확인은 확인대로 하고, `catch`는 경합 상황에 대한 안전망으로 둔다.

### Lint를 CI에서 돌린다

애너테이션은 **읽어 주는 도구가 실제로 돌아야** 값어치가 생긴다.

```bash
./gradlew :app:lint
```

`MissingPermission`은 기본적으로 **에러 수준**이라 릴리스 빌드에서 막힐 수 있다. 그래서 `@SuppressLint`가 흔히 쓰이는데, 그만큼 **진짜 권한 버그를 가릴 위험**도 커진다.

## 체크리스트

- [ ] `@RequiresPermission`이 Lint용 메타데이터임을 안다.
- [ ] 경고의 원인이 Play services 쪽 선언임을 설명할 수 있다.
- [ ] `anyOf`와 `allOf`의 차이를 안다.
- [ ] FLP가 `anyOf`인 이유를 설명할 수 있다.
- [ ] Lint가 권한 확인을 인식하는 조건과 한계를 안다.
- [ ] 세 가지 대응(확인/애너테이션/억제)의 우선순위를 판단할 수 있다.
- [ ] `@RequiresPermission`을 붙이면 책임이 호출자로 이동한다는 것을 안다.
- [ ] `@SuppressLint` 사용 시의 규칙을 말할 수 있다.
- [ ] 권한이 런타임에 취소될 수 있음을 고려한 설계를 할 수 있다.
- [ ] `@RequiresApi`, `@MainThread` 등 같은 계열 애너테이션을 안다.

## 공식 참고 자료

- [Android Developers: Improve code inspection with annotations](https://developer.android.com/studio/write/annotations)
- [Android Developers: `RequiresPermission`](https://developer.android.com/reference/androidx/annotation/RequiresPermission)
- [Android Developers: `SuppressLint`](https://developer.android.com/reference/android/annotation/SuppressLint)
- [Android Developers: Improve your code with lint checks](https://developer.android.com/studio/write/lint)
- [Android Developers: Request runtime permissions](https://developer.android.com/training/permissions/requesting)
- [Android Developers: `androidx.annotation` package summary](https://developer.android.com/reference/androidx/annotation/package-summary)
- [Google for Developers: `FusedLocationProviderClient`](https://developers.google.com/android/reference/com/google/android/gms/location/FusedLocationProviderClient)
