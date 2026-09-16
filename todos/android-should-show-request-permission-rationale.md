# `shouldShowRequestPermissionRationale`의 역할은 뭐야

## 질문이 나온 코드

- [`chapter171/app/src/main/java/com/example/chapter_171/MainActivity.kt`](../chapter171/app/src/main/java/com/example/chapter_171/MainActivity.kt)
- 질문: `shouldShowRequestPermissionRationale` 기능의 역할은 뭐야? 어떤 경우에 `true`, 어떤 경우에 `false`를 반환하지?

```kotlin
val rationalRequired = ActivityCompat.shouldShowRequestPermissionRationale(
    context as MainActivity, Manifest.permission.ACCESS_FINE_LOCATION
) || ActivityCompat.shouldShowRequestPermissionRationale(
    context, Manifest.permission.ACCESS_COARSE_LOCATION
)
if (rationalRequired) {
    Toast.makeText(context, "Location Permission is required for this feature to work", ...).show()
} else {
    Toast.makeText(context, "Location Permission is required. Please enable it in the Android settings", ...).show()
}
```

## 질문 전제 점검

- **이름이 직관을 배신하는 대표적인 API다.** "설명을 보여 줘야 하는가?"라고 읽히지만, 실제로는 **"사용자가 이 권한을 이미 한 번 거부한 적이 있는가?"**를 알려 준다. 시스템은 그 사실을 근거로 "설명이 필요할 것"이라고 판단한다.

- **반환값은 세 상태 중 둘을 구분하지 못한다.** 이게 핵심이자 함정이다.

  | 상황 | 반환값 |
  | --- | --- |
  | **아직 한 번도 요청한 적 없음** | `false` |
  | **거부한 적 있음** (아직 영구 거부는 아님) | **`true`** |
  | **영구 거부** ("다시 묻지 않음" 상태) | `false` |
  | 이미 허용됨 | `false` |

  **`false`가 "처음"과 "영구 거부"를 동시에 뜻한다.** 값만 보고는 구분할 수 없다.

  > "If the `ContextCompat.checkSelfPermission()` method returns `PERMISSION_DENIED`, call `shouldShowRequestPermissionRationale()`. If this method returns `true`, show an educational UI to the user."

  > "if the user taps Deny for a specific permission more than once during your app's lifetime of installation on a device, the user will no longer see the system permissions dialog if your app requests that permission again. The user's action implies 'don't ask again,' and is considered a permanent denial."

- **그래서 지금 코드의 `else` 분기는 "설정에서 켜 주세요"라고 안내하는데, 이 판단이 성립하는 이유가 있다.** 이 호출은 **권한 요청 결과 콜백 안**에 있다. 즉 이미 한 번 요청했고 거부당한 직후다. 그 시점에 `false`라면 "처음"일 수 없으므로 **영구 거부로 볼 수 있다.** 논리 자체는 맞다.

  **다만 이건 호출 위치 덕분에 성립하는 것**이고, 같은 코드를 요청 전에 두면 완전히 틀린 판단이 된다.

- **`context as MainActivity` 캐스팅은 위험하다.** `LocalContext.current`가 항상 `MainActivity`라는 보장이 없다. Compose 프리뷰나 다른 컨텍스트 래퍼에서는 `ClassCastException`이 난다. `as?`와 `?:`, 또는 `findActivity()` 확장을 쓰는 편이 안전하다. → [`kotlin-is-operator-and-smart-cast.md`](kotlin-is-operator-and-smart-cast.md)

  ```kotlin
  fun Context.findActivity(): Activity? = when (this) {
      is Activity -> this
      is ContextWrapper -> baseContext.findActivity()
      else -> null
  }
  ```

## 공부할 내용

### 왜 이런 API가 필요한가

권한 요청은 **사용자에게 한 번 물어보면 끝**이 아니다. 거부한 사용자에게 같은 다이얼로그를 반복해서 띄우면 앱이 불쾌해진다. 그래서 안드로이드는 **두 번 거부하면 다이얼로그 자체를 막아 버린다.**

```
1차 요청 → 거부
2차 요청 → 거부       ← 이 시점에 "영구 거부"
3차 요청 → 다이얼로그가 뜨지 않고 즉시 거부로 콜백
```

앱 입장에서는 3차 요청이 **아무 일도 일어나지 않은 것처럼 보인다.** 사용자는 다이얼로그를 못 보고, 앱은 거부 결과만 받는다. 이 상태를 감지해서 **"설정 화면에서 직접 바꿔 주세요"**로 안내하라는 것이 이 API의 존재 이유다.

### 공식 권장 흐름

```kotlin
when {
    ContextCompat.checkSelfPermission(CONTEXT, PERMISSION) == PackageManager.PERMISSION_GRANTED -> {
        // You can use the API that requires the permission.
    }
    ActivityCompat.shouldShowRequestPermissionRationale(this, PERMISSION) -> {
        // In an educational UI, explain to the user why your app requires this
        // permission for a specific feature to behave as expected, and what
        // features are disabled if it's declined. In this UI, include a
        // "cancel" or "no thanks" button that lets the user continue
        // using your app without granting the permission.
        showInContextUI(...)
    }
    else -> {
        // You can directly ask for the permission.
        requestPermissionLauncher.launch(PERMISSION)
    }
}
```

**호출 위치가 요청 "전"이라는 점이 중요하다.** 이 흐름에서 `false`는 "처음이니 그냥 물어보자"로 해석되고, 그래서 `else`가 바로 `launch()`다.

지금 코드는 요청 **후** 콜백에서 부르고 있어서 의미가 달라진다. 두 위치 모두 쓸 수 있지만 **해석이 정반대**라는 것을 알아야 한다.

| 호출 위치 | `true` | `false` |
| --- | --- | --- |
| **요청 전** | 거부 이력 있음 → 설명 UI | 처음 → 바로 요청 |
| **요청 후 콜백** | 거부했지만 재시도 가능 → 설명 | **영구 거부** → 설정 안내 |

### 설명 UI는 무엇을 담아야 하는가

공식 가이드가 요구하는 요소는 셋이다.

1. **왜 이 권한이 필요한지** — 기능과 연결해서
2. **거부하면 무엇이 안 되는지**
3. **"취소" 또는 "괜찮아요" 버튼** — 권한 없이도 앱을 계속 쓸 수 있게

```kotlin
AlertDialog(
    onDismissRequest = { showRationale = false },
    title = { Text("위치 권한이 필요합니다") },
    text = { Text("현재 위치의 주소를 보여 드리기 위해 위치 권한을 사용합니다. 허용하지 않으면 주소 조회 기능을 쓸 수 없습니다.") },
    confirmButton = {
        TextButton(onClick = { launcher.launch(permissions) }) { Text("권한 요청") }
    },
    dismissButton = {
        TextButton(onClick = { showRationale = false }) { Text("괜찮아요") }
    }
)
```

**Toast는 이 역할에 맞지 않는다.** 지금 코드가 Toast를 쓰는데, 사라져 버리고 행동으로 이어지는 버튼이 없다. 설명 UI의 목적은 "알리기"가 아니라 **"다시 요청할 기회를 만드는 것"**이다.

### 영구 거부일 때는 설정으로 보낸다

앱은 영구 거부를 코드로 풀 수 없다. **사용자가 직접 설정에서 바꿔야 한다.** 그 화면으로 보내 주는 것이 최선이다.

```kotlin
val settingsLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.StartActivityForResult()
) {
    // 돌아왔을 때 권한을 다시 확인한다
}

Button(onClick = {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null)
    )
    settingsLauncher.launch(intent)
}) {
    Text("설정 열기")
}
```

`StartActivityForResult`로 띄우면 **사용자가 설정에서 돌아왔을 때 콜백이 오므로** 권한 상태를 다시 확인할 수 있다. → [`android-activity-result-api-and-launcher.md`](android-activity-result-api-and-launcher.md)

### 상태를 직접 추적해서 구분하기

"처음"과 "영구 거부"를 요청 전에도 구분하고 싶다면, **한 번이라도 요청했는지를 앱이 직접 기억**해야 한다.

```kotlin
// SharedPreferences 나 DataStore 에 저장
val hasRequestedBefore = prefs.getBoolean("requested_location", false)

val state = when {
    hasPermission -> Granted
    !hasRequestedBefore -> NeverAsked
    shouldShowRationale -> Denied
    else -> PermanentlyDenied
}
```

시스템이 이 정보를 주지 않기 때문에 **앱이 직접 관리하는 것 외에 방법이 없다.** Accompanist Permissions 같은 라이브러리도 내부적으로 같은 일을 한다.

### 기기 정책에 의한 `false`

거부 이력과 무관하게 `false`가 나오는 경우도 있다.

- **기기 관리자(MDM) 정책으로 권한이 차단된 경우**
- **사용자가 "다시 묻지 않음"을 명시적으로 체크한 경우** (구버전 안드로이드)

이 경우에도 요청은 조용히 실패한다. 설정 안내가 같은 대응이 된다.

### 여러 권한을 함께 다룰 때

지금 코드처럼 `||`로 묶는 것은 **"둘 중 하나라도 설명이 필요하면 설명한다"**는 뜻이다. 위치 권한은 한 쌍으로 움직이므로 합리적이다.

```kotlin
val rationaleRequired = listOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION
).any { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }
```

`any`로 쓰면 권한이 늘어나도 그대로 확장된다.

## 관련 아키텍처와 베스트 프랙티스

### 권한 상태를 네 갈래로 모델링한다

`Boolean` 하나로는 표현이 안 된다.

```kotlin
sealed interface PermissionState {
    data object Granted : PermissionState
    data object NeverAsked : PermissionState
    data object Denied : PermissionState            // 재요청 가능
    data object PermanentlyDenied : PermissionState // 설정으로 보내야 한다
}
```

각 상태마다 **해야 할 행동이 다르다.**

| 상태 | 행동 |
| --- | --- |
| `Granted` | 기능 실행 |
| `NeverAsked` | 바로 요청 |
| `Denied` | 설명 UI → 재요청 |
| `PermanentlyDenied` | 설정 화면 안내 |

→ [`kotlin-sealed-class-and-interface.md`](kotlin-sealed-class-and-interface.md)

### 거부를 실패로 취급하지 않는다

권한 거부는 **버그가 아니라 사용자의 정당한 선택**이다. 앱은 그 상태에서도 쓸 만해야 한다.

```
위치 권한 거부
   → 지역을 직접 검색해서 선택하는 대안 제공
   → "위치 권한을 허용하면 자동으로 찾아 드려요" 정도의 안내만
```

반복해서 요청하거나, 기능을 인질로 잡거나, 앱을 못 쓰게 막는 것은 정책 위반이 될 수 있다.

### 요청 시점을 맥락에 맞춘다

| 나쁨 | 좋음 |
| --- | --- |
| 앱 첫 실행 시 모든 권한 요청 | 해당 기능을 처음 쓸 때 요청 |
| 설명 없이 바로 시스템 다이얼로그 | 필요하면 사전 설명 후 요청 |
| 거부 후 즉시 재요청 | 다음에 그 기능에 다시 들어올 때 |

### Compose에서는 액티비티 접근을 조심한다

`shouldShowRequestPermissionRationale`은 **`Activity`가 필요하다.** 컴포저블은 액티비티를 직접 알지 못하는 것이 원칙이므로, 이 판단을 화면 코드에 두면 결합이 생긴다.

```kotlin
// 권장: 상태를 위로 끌어올리고, 컴포저블은 상태만 본다
@Composable
fun LocationScreen(
    permissionState: PermissionState,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
)
```

→ [`compose-navigation-prop-drilling.md`](compose-navigation-prop-drilling.md)

### 라이브러리를 쓰는 선택지

권한 상태 관리는 경계 조건이 많아 직접 구현하면 실수하기 쉽다. Compose에서는 `accompanist-permissions`가 널리 쓰였고, 그 기능 상당수가 이후 공식 API로 흡수되는 흐름이다. 학습 단계에서는 **직접 구현해 보고 원리를 익힌 뒤** 라이브러리로 옮기는 편이 좋다.

## 체크리스트

- [ ] 이 API가 "거부 이력이 있는가"를 알려 준다는 것을 설명할 수 있다.
- [ ] `true`가 나오는 조건을 말할 수 있다.
- [ ] `false`가 두 가지 상황을 동시에 뜻한다는 것을 안다.
- [ ] 영구 거부가 어떻게 발생하는지 설명할 수 있다.
- [ ] 요청 전과 요청 후 콜백에서 해석이 달라지는 이유를 안다.
- [ ] 지금 코드의 `else` 분기가 왜 성립하는지 설명할 수 있다.
- [ ] 설명 UI가 담아야 할 세 요소를 안다.
- [ ] Toast가 설명 UI로 부적절한 이유를 설명할 수 있다.
- [ ] 영구 거부 시 설정 화면으로 보내는 방법을 안다.
- [ ] "처음"과 "영구 거부"를 구분하려면 앱이 직접 기록해야 함을 안다.
- [ ] 권한 상태를 네 갈래로 모델링할 수 있다.
- [ ] `context as Activity` 캐스팅의 위험을 안다.

## 공식 참고 자료

- [Android Developers: Request runtime permissions](https://developer.android.com/training/permissions/requesting)
- [Android Developers: Explain access to more sensitive information](https://developer.android.com/training/permissions/explaining-access)
- [Android Developers: `ActivityCompat.shouldShowRequestPermissionRationale`](https://developer.android.com/reference/androidx/core/app/ActivityCompat)
- [Android Developers: App permissions best practices](https://developer.android.com/training/permissions/usage-notes)
- [Android Developers: Permissions on Android](https://developer.android.com/guide/topics/permissions/overview)
- [Android Developers: Auto-reset permissions from unused apps](https://developer.android.com/topic/performance/app-hibernation)
- [Android Developers: Request location access at runtime](https://developer.android.com/develop/sensors-and-location/location/permissions/runtime)
- [Android Developers: Get a result from an activity](https://developer.android.com/training/basics/intents/result)
