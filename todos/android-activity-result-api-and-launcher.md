# `rememberLauncherForActivityResult`는 왜 사용하는가

## 질문이 나온 코드

- [`chapter171/app/src/main/java/com/example/chapter_171/MainActivity.kt`](../chapter171/app/src/main/java/com/example/chapter_171/MainActivity.kt)
- 질문: `rememberLauncherForActivityResult` 이 메서드는 왜 사용하는 거야? 어떤 케이스에 사용하는 거야? Activity의 result를 가져온다는 게 무슨 말이지?

```kotlin
val requestPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestMultiplePermissions(),
) { permissions ->
    ...
}

// 버튼에서
requestPermissionLauncher.launch(
    arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
)
```

## 질문 전제 점검

- **"Activity의 result를 가져온다"는 표현이 헷갈리는 게 정상이다.** 이름은 `ActivityResult`인데 지금 코드는 **액티비티를 띄우지 않고 권한 다이얼로그를 띄운다.** 이름과 용도가 어긋나 보인다.

  이유는 역사에 있다. 이 API는 원래 **"다른 액티비티를 띄우고 결과를 받아 오는" 용도**로 만들어졌다.

  > "You can also start an activity and receive a result back. For example, your app can start a camera app and receive the captured photo as a result. Or you might start the Contacts app for the user to select a contact, and then receive the contact details as a result."

  그런데 **"우리 앱을 잠시 벗어났다가 결과를 들고 돌아오는" 구조**가 권한 요청과 똑같았다. 권한 다이얼로그도 시스템이 띄우고, 사용자가 선택하면 결과가 우리 앱으로 돌아온다. 그래서 같은 틀에 `RequestMultiplePermissions`라는 **계약(contract)**으로 얹은 것이다.

  **"result"는 "다른 화면에 갔다 온 결과"로 이해하면 된다.** 그게 사진이든, 선택한 연락처든, 권한 허용 여부든 상관없다.

- **`rememberLauncherForActivityResult`는 Compose용 래퍼다.** 액티비티/프래그먼트의 `registerForActivityResult`와 하는 일이 같고, **`remember`로 컴포지션에 묶어 줄 뿐**이다.

  ```kotlin
  // 액티비티/프래그먼트
  val launcher = registerForActivityResult(contract) { result -> }

  // 컴포저블
  val launcher = rememberLauncherForActivityResult(contract) { result -> }
  ```

- **"왜 굳이 이런 구조인가"에 대한 답은 프로세스 종료에 있다.** 이것이 이 API의 존재 이유다. 카메라처럼 메모리를 많이 쓰는 앱을 띄우면 **우리 앱 프로세스가 죽을 수 있다.**

  > "When starting an activity for a result, it is possible—and, in cases of memory-intensive operations such as camera usage, almost certain—that your process and your activity will be destroyed due to low memory."

  살아 돌아왔을 때 결과를 받으려면, **콜백이 앱이 다시 만들어질 때 이미 등록돼 있어야 한다.** 그래서 "등록"과 "실행"이 분리돼 있다.

- **그래서 등록을 조건문 안에 넣으면 안 된다.**

  > "the callback must be unconditionally registered every time your activity is created, even if the logic of launching the other activity only happens based on user input or other business logic."

  지금 코드는 컴포저블 본문 최상위에서 등록하고 있어서 이 규칙을 지키고 있다. 다만 **`if` 안에서 `rememberLauncherForActivityResult`를 부르면** 조건이 바뀔 때 등록이 사라진다.

## 공부할 내용

### 옛날 방식과 무엇이 다른가

```kotlin
// 과거: startActivityForResult + onActivityResult
private const val REQUEST_CODE_PICK_CONTACT = 1001   // 숫자를 직접 관리

startActivityForResult(intent, REQUEST_CODE_PICK_CONTACT)

override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)
    when (requestCode) {                              // 모든 결과가 한 곳으로 모인다
        REQUEST_CODE_PICK_CONTACT -> { ... }
        REQUEST_CODE_TAKE_PHOTO -> { ... }
    }
}
```

문제가 뚜렷했다.

| 문제 | 설명 |
| --- | --- |
| **요청 코드를 사람이 관리** | 상수 충돌, 의미 없는 숫자 |
| **처리 코드가 멀리 떨어짐** | 띄우는 곳과 받는 곳이 분리돼 읽기 어렵다 |
| **타입이 없다** | `Intent?`에서 직접 꺼내야 하고, 키를 틀려도 컴파일이 된다 |
| **거대한 `when`** | 한 액티비티의 모든 결과가 한 함수로 모인다 |

> "Google strongly recommends using the Activity Result APIs introduced in AndroidX `Activity` and `Fragment` classes."

새 방식은 **요청 코드가 없고, 콜백이 등록 지점에 붙어 있고, 타입이 있다.**

```kotlin
val getContent = registerForActivityResult(GetContent()) { uri: Uri? ->
    // Uri 로 바로 받는다. Intent 파싱이 없다
}
```

### 계약(contract)이 핵심이다

`ActivityResultContract`는 **"무엇을 넣으면 무엇이 나오는가"**를 타입으로 정의한다.

```
ActivityResultContract<입력 타입, 출력 타입>
```

| 계약 | 입력 | 출력 | 쓰는 때 |
| --- | --- | --- | --- |
| `RequestPermission` | `String` | `Boolean` | 권한 하나 |
| `RequestMultiplePermissions` | `Array<String>` | `Map<String, Boolean>` | 권한 여러 개 |
| `TakePicture` | `Uri` (저장 위치) | `Boolean` | 카메라로 사진 촬영 |
| `TakePicturePreview` | `Void?` | `Bitmap?` | 썸네일만 필요할 때 |
| `PickVisualMedia` | `PickVisualMediaRequest` | `Uri?` | 사진 피커 (권한 불필요) |
| `GetContent` | `String` (MIME) | `Uri?` | 파일 선택 |
| `OpenDocument` | `Array<String>` | `Uri?` | 영속 접근 가능한 문서 선택 |
| `CreateDocument` | `String` | `Uri?` | 저장 위치 선택 |
| `StartActivityForResult` | `Intent` | `ActivityResult` | 계약이 없는 임의의 액티비티 |

지금 코드가 쓰는 `RequestMultiplePermissions`의 출력이 `Map<String, Boolean>`이라서 콜백 파라미터가 `permissions`이고 `permissions[...] == true`로 꺼내는 것이다.

**직접 만들 수도 있다.** `createIntent()`와 `parseResult()`를 구현하면 된다.

```kotlin
class PickUser : ActivityResultContract<Unit, String?>() {
    override fun createIntent(context: Context, input: Unit) =
        Intent(context, UserPickerActivity::class.java)

    override fun parseResult(resultCode: Int, intent: Intent?): String? =
        if (resultCode == Activity.RESULT_OK) intent?.getStringExtra("userId") else null
}
```

**결과 파싱이 계약 안에 갇힌다**는 것이 이 설계의 이점이다. 호출하는 쪽은 `String?`만 본다.

### Compose에서의 주의점

**컴포지션 도중에 `launch()`를 부르면 안 된다.**

```kotlin
@Composable
fun Screen() {
    val launcher = rememberLauncherForActivityResult(...) { }
    launcher.launch(...)    // 런타임 에러 — 아직 등록이 끝나지 않았다
}
```

> "If you attempt to launch a request from inside the composable, you'll get a runtime error because the `ActivityResultLauncher` has not been initialized at that point"

컴포지션 직후에 자동으로 띄워야 한다면 **이펙트 안에서** 부른다.

```kotlin
LaunchedEffect(Unit) {
    launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
}
```

지금 코드는 **버튼 `onClick`에서** 부르고 있어서 문제가 없다. 클릭 람다는 컴포지션이 아니라 **이벤트 시점**에 실행되기 때문이다.

→ [`compose-launchedeffect-and-snapshotflow.md`](compose-launchedeffect-and-snapshotflow.md)

### 어떤 케이스에 쓰는가

| 하고 싶은 일 | 계약 |
| --- | --- |
| 런타임 권한 요청 | `RequestPermission` / `RequestMultiplePermissions` |
| 사진 촬영 | `TakePicture` |
| 갤러리에서 사진 고르기 | `PickVisualMedia` (권장) |
| 파일 열기/저장 | `OpenDocument` / `CreateDocument` |
| 연락처 선택 | `PickContact` |
| 다른 앱 화면 띄우고 결과 받기 | `StartActivityForResult` |
| 앱 설정 화면 열기 | `StartActivityForResult` + `ACTION_APPLICATION_DETAILS_SETTINGS` |

마지막 항목은 **권한이 영구 거부됐을 때** 자주 쓴다. → [`android-should-show-request-permission-rationale.md`](android-should-show-request-permission-rationale.md)

```kotlin
val settingsLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.StartActivityForResult()
) { /* 돌아왔을 때 권한을 다시 확인한다 */ }

settingsLauncher.launch(
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
)
```

### `PickVisualMedia`를 알아 둘 값어치

사진을 고르는 데 **`READ_EXTERNAL_STORAGE` 권한이 필요 없다.** 시스템 피커가 사용자가 고른 항목만 넘겨주기 때문이다. 권한 요청 자체를 없앨 수 있다면 그게 가장 좋은 권한 처리다.

```kotlin
val picker = rememberLauncherForActivityResult(
    ActivityResultContracts.PickVisualMedia()
) { uri -> /* 권한 요청 없이 Uri 획득 */ }
```

## 관련 아키텍처와 베스트 프랙티스

### 등록은 무조건, 실행은 조건부

```kotlin
// 좋음
@Composable
fun LocationDisplay(...) {
    val launcher = rememberLauncherForActivityResult(...) { }   // 항상 등록
    Button(onClick = {
        if (hasPermission) doWork() else launcher.launch(...)    // 실행만 조건부
    })
}

// 나쁨
if (needsPermission) {
    val launcher = rememberLauncherForActivityResult(...) { }    // 조건부 등록
}
```

### 권한 요청 흐름의 정석

공식 문서가 제시하는 순서는 셋으로 갈린다.

```kotlin
when {
    ContextCompat.checkSelfPermission(context, PERMISSION) == PackageManager.PERMISSION_GRANTED -> {
        // 이미 있다 — 바로 쓴다
    }
    ActivityCompat.shouldShowRequestPermissionRationale(activity, PERMISSION) -> {
        // 왜 필요한지 설명하는 UI를 먼저 보여 준다
        showInContextUI(...)
    }
    else -> {
        // 바로 요청한다
        requestPermissionLauncher.launch(PERMISSION)
    }
}
```

지금 코드는 **①과 ③만 있고 ②가 빠져 있다.** 이유 설명을 요청 **후**에 Toast로 하고 있는데, 권장 흐름은 요청 **전**에 설명하는 것이다.

```kotlin
// 현재: 거부당한 다음에 설명한다
requestPermissionLauncher.launch(...)  → 거부 → Toast("이 기능에는 위치 권한이 필요합니다")

// 권장: 요청하기 전에 설명한다
rationale 필요? → 설명 UI → 사용자가 동의 → launch()
```

### 결과 처리는 계약이 주는 타입에 맞춘다

```kotlin
// RequestMultiplePermissions 의 출력은 Map<String, Boolean>
permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false)
```

공식 예제는 `getOrDefault`를 쓴다. 지금 코드의 `permissions[...] == true`도 `null`을 안전하게 처리하므로 동작은 같다. 다만 **`when`으로 세 갈래를 나누는 공식 형태**가 의도를 더 잘 드러낸다.

```kotlin
when {
    permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true -> { /* 정밀 */ }
    permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true -> { /* 대략 */ }
    else -> { /* 거부 */ }
}
```

지금 코드는 `&&`로 **둘 다 허용된 경우만** 처리해서, 사용자가 "대략적 위치"를 골랐을 때 **거부와 똑같이 취급**한다. → [`android-location-permission-coarse-vs-fine.md`](android-location-permission-coarse-vs-fine.md)

### 디버그 로그를 남기지 않는다

```kotlin
Log.i("testing", "here can you see?")
```

강의 중 확인용 로그다. 태그가 `"testing"`이고 내용이 의미 없으므로 정리 대상이다. 실제 앱이라면 릴리스 빌드에서 제거되도록 처리한다.

## 체크리스트

- [ ] "Activity result"가 무엇을 뜻하는지 설명할 수 있다.
- [ ] 권한 요청이 왜 같은 API를 쓰는지 설명할 수 있다.
- [ ] `startActivityForResult` 방식의 문제 넷을 말할 수 있다.
- [ ] 프로세스 종료 때문에 등록과 실행이 분리됐다는 것을 안다.
- [ ] 등록을 조건부로 하면 안 되는 이유를 설명할 수 있다.
- [ ] `ActivityResultContract`의 입력·출력 타입 개념을 안다.
- [ ] 주요 내장 계약을 다섯 개 이상 말할 수 있다.
- [ ] 커스텀 계약을 만드는 방법을 안다.
- [ ] 컴포지션 도중 `launch()`를 부르면 안 되는 이유를 안다.
- [ ] `LaunchedEffect` 안에서 부르는 경우를 판단할 수 있다.
- [ ] 권한 요청 흐름 세 갈래를 설명할 수 있다.
- [ ] `PickVisualMedia`처럼 권한 자체를 없애는 선택지를 안다.

## 공식 참고 자료

- [Android Developers: Get a result from an activity](https://developer.android.com/training/basics/intents/result)
- [Android Developers: Request runtime permissions](https://developer.android.com/training/permissions/requesting)
- [Android Developers: Compose and other libraries](https://developer.android.com/develop/ui/compose/libraries)
- [Android Developers: `androidx.activity.compose` package summary](https://developer.android.com/reference/kotlin/androidx/activity/compose/package-summary)
- [Android Developers: `ActivityResultContracts`](https://developer.android.com/reference/androidx/activity/result/contract/ActivityResultContracts)
- [Android Developers: `ActivityResultLauncher`](https://developer.android.com/reference/androidx/activity/result/ActivityResultLauncher)
- [Android Developers: Photo picker](https://developer.android.com/training/data-storage/shared/photopicker)
- [Android Developers: Side-effects in Compose](https://developer.android.com/develop/ui/compose/side-effects)
