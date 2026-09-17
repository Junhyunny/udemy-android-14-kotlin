# `rememberAsyncImagePainter`는 왜 쓰는가, `AsyncImagePainter`는 뭔가

## 질문이 나온 코드

- [`chapter143/app/src/main/java/com/example/chapter_143/CategoryDetailScreen.kt`](../chapter143/app/src/main/java/com/example/chapter_143/CategoryDetailScreen.kt)
- 질문: `rememberAsyncImagePainter` 함수를 쓰는 이유는 뭔가? 실제 `AsyncImagePainter`라는 게 존재하는데?

```kotlin
Image(
    painter = rememberAsyncImagePainter(category.strCategoryThumb),
    contentDescription = "${category.strCategory}'s thumbnail",
    modifier = Modifier.wrapContentSize().aspectRatio(1f)
)
```

## 질문 전제 점검

- **"실제 `AsyncImagePainter`라는 게 존재하는데"** → 맞다. 존재한다. 그리고 `rememberAsyncImagePainter`가 **반환하는 것이 바로 그 `AsyncImagePainter`다.** 둘은 대안 관계가 아니라 **"만드는 함수"와 "만들어지는 물건"** 관계다.

  ```kotlin
  val painter: AsyncImagePainter = rememberAsyncImagePainter(url)
  //           ^^^^^^^^^^^^^^^^^   반환 타입이 곧 그 클래스다
  ```

  그러니 "왜 `AsyncImagePainter`를 직접 안 쓰고 `remember...`를 쓰나"라는 질문은, 정확히는 **"왜 `remember`로 감싸서 만드나"**가 된다. 답은 컴포즈의 기본 규칙이다.

- **`remember` 접두사는 코일이 만든 관례가 아니라 컴포즈 전체의 규칙이다.** `rememberScrollState`, `rememberNavController`, `rememberCoroutineScope`, `rememberSaveable` 전부 같은 뜻이다.

  > **"재구성이 일어나도 같은 객체를 다시 쓴다"**

  컴포저블 함수는 재구성될 때마다 **처음부터 다시 실행된다.** `remember` 없이 객체를 만들면 재구성 때마다 새 객체가 생긴다. 이미지의 경우 **재구성될 때마다 네트워크 요청을 새로 보내는** 참사가 된다. → [`034-compose-remember-mutablestate-and-by.md`](034-compose-remember-mutablestate-and-by.md)

- **그리고 더 중요한 사실이 있다.** 코일 공식 문서는 **이 함수 대신 `AsyncImage`를 쓰라고 권한다.**

  > "Prefer using `AsyncImage` in most cases. It correctly determines the size your image should be loaded at based on the constraints of the composable and the provided `ContentScale`."

  `rememberAsyncImagePainter`에는 알려진 단점이 있다.

  > "The main drawback of this function is it does not detect the size your image is loaded at on screen and always loads the image with its original dimensions."

  **화면에 그려질 크기를 모르기 때문에 원본 해상도 그대로 받아서 디코딩한다.** 2000×2000 이미지를 200dp 칸에 그리더라도 2000×2000으로 메모리에 올린다. 목록에서 이걸 여러 개 하면 메모리 사용량이 크게 늘어난다. 지금 코드처럼 격자로 여러 장 띄우는 화면에서는 실제로 영향이 있다.

- 정리하면 질문의 답은 두 겹이다.
  1. `remember`로 감싸는 이유 → **재구성 때 요청을 다시 보내지 않기 위해**
  2. 그런데 애초에 → **대부분의 경우 `AsyncImage`를 쓰는 게 낫다**

## 공부할 내용

### 코일이 제공하는 세 가지 방식

```kotlin
// ① AsyncImage — 권장. 컴포저블 하나로 끝
AsyncImage(
    model = category.strCategoryThumb,
    contentDescription = null
)

// ② rememberAsyncImagePainter — Painter 객체가 필요할 때
Image(
    painter = rememberAsyncImagePainter(category.strCategoryThumb),
    contentDescription = null
)

// ③ SubcomposeAsyncImage — 로딩/에러 상태별로 다른 컴포저블을 그릴 때
SubcomposeAsyncImage(
    model = category.strCategoryThumb,
    contentDescription = null,
    loading = { CircularProgressIndicator() },
    error = { Text("이미지를 불러올 수 없습니다") }
)
```

### `Painter`란 무엇인가

`Painter`는 컴포즈에서 **"무언가를 그릴 수 있는 것"**을 표현하는 추상 타입이다.

```kotlin
painterResource(R.drawable.icon)          // 리소스 → Painter
ColorPainter(Color.Red)                   // 단색 → Painter
BitmapPainter(bitmap)                     // 비트맵 → Painter
rememberAsyncImagePainter(url)            // 네트워크 URL → Painter (코일)
```

`Image(painter = ...)`는 어떤 `Painter`든 받는다. 그래서 `rememberAsyncImagePainter`는 **"네트워크 이미지를 `Painter`인 척하게 만들어 주는 어댑터"**인 셈이다.

이 점이 이 함수가 존재하는 이유다. `Painter`를 요구하는 API가 여럿 있다.

```kotlin
Icon(painter = ..., contentDescription = null)
Modifier.paint(painter)
Modifier.background(...)   // 일부 상황
```

이런 자리에 네트워크 이미지를 넣어야 하면 `AsyncImage`(컴포저블)로는 안 되고 `Painter`가 필요하다.

### 코일이 직접 밝힌 사용 기준

> "Useful if you need a `Painter` instead of a composable - or if you need to observe the `AsyncImagePainter.state` and draw a different composable based on it - or if you need to manually restart the image request."

세 가지로 정리된다.

| 상황 | 쓴다 |
| --- | --- |
| 컴포저블이 아니라 `Painter`가 필요하다 | `rememberAsyncImagePainter` |
| 로딩 상태를 관찰해 다른 UI를 그려야 한다 | `rememberAsyncImagePainter` 또는 `SubcomposeAsyncImage` |
| 요청을 수동으로 다시 시작해야 한다 | `rememberAsyncImagePainter` |
| **그 밖의 모든 경우** | **`AsyncImage`** |

### 상태를 관찰하는 예제

`AsyncImagePainter`를 직접 들고 있으면 `state`를 읽을 수 있다.

```kotlin
val painter = rememberAsyncImagePainter(category.strCategoryThumb)
val state = painter.state

Box(contentAlignment = Alignment.Center) {
    Image(painter = painter, contentDescription = null)
    when (state) {
        is AsyncImagePainter.State.Loading -> CircularProgressIndicator()
        is AsyncImagePainter.State.Error   -> Icon(Icons.Filled.BrokenImage, null)
        else -> Unit
    }
}
```

다만 여기에도 함정이 있다.

> "`AsyncImagePainter.state` will always be `AsyncImagePainter.State.Empty` for the first composition"

**첫 컴포지션에서는 항상 `Empty`다.** 요청이 아직 시작되지 않았기 때문이다. `when`에서 `Empty`를 빠뜨리면 한 프레임 동안 아무것도 안 그려진다.

### 이 코드를 개선한다면

```kotlin
// 지금
Image(
    painter = rememberAsyncImagePainter(category.strCategoryThumb),
    contentDescription = "${category.strCategory}'s thumbnail",
    modifier = Modifier.wrapContentSize().aspectRatio(1f)
)

// 권장
AsyncImage(
    model = category.strCategoryThumb,
    contentDescription = "${category.strCategory}'s thumbnail",
    contentScale = ContentScale.Crop,
    placeholder = painterResource(R.drawable.placeholder),
    error = painterResource(R.drawable.image_error),
    modifier = Modifier.fillMaxWidth().aspectRatio(1f)
)
```

바뀐 점이 넷이다.

1. **`AsyncImage`로 교체** — 화면 크기에 맞춰 디코딩한다
2. **`contentScale = ContentScale.Crop`** — 비율이 다른 이미지가 찌그러지지 않는다
3. **`placeholder` / `error`** — 로딩 중과 실패 시 빈 칸이 아니라 무언가가 보인다
4. **`wrapContentSize()` → `fillMaxWidth()`** — 아래에서 설명

`wrapContentSize()`와 `aspectRatio(1f)`를 같이 쓰면 의도가 모호해진다. `wrapContentSize()`는 "내용물 크기만큼만 차지하라"인데, 네트워크 이미지는 **받아 보기 전까지 크기를 모른다.** 그래서 로딩 중과 완료 후에 레이아웃이 튀는(layout shift) 현상이 생긴다. `fillMaxWidth().aspectRatio(1f)`처럼 **크기를 먼저 확정**하면 그 자리가 처음부터 예약되어 화면이 흔들리지 않는다. → [`031-compose-wrapcontentsize-modifier.md`](031-compose-wrapcontentsize-modifier.md)

### `remember`를 빼면 무슨 일이 생기나

```kotlin
// 위험한 코드
Image(painter = AsyncImagePainter(...), contentDescription = null)
```

컴포저블은 재구성될 때마다 본문이 다시 실행된다. `remember` 없이 만들면 **재구성마다 새 painter가 만들어지고 요청이 다시 나간다.** 화면의 다른 상태가 바뀔 때마다 이미지가 깜빡이거나 네트워크가 반복 호출된다.

`rememberAsyncImagePainter`는 내부에서 `remember`를 쓰고, **`model`(URL)이 바뀔 때만** 새 요청을 보낸다. `remember(key)`의 키 개념과 같다.

```kotlin
// 개념적으로
@Composable
fun rememberAsyncImagePainter(model: Any?): AsyncImagePainter {
    return remember(model) { AsyncImagePainter(model) }   // model이 바뀌면 새로 만든다
}
```

→ [`036-compose-recomposition-timing-and-scope.md`](036-compose-recomposition-timing-and-scope.md)

## 관련 아키텍처와 베스트 프랙티스

### 컴포즈의 `remember` 계열 함수 읽는 법

| 함수 | 만드는 것 | 다시 만드는 조건 |
| --- | --- | --- |
| `remember { }` | 아무 객체 | 키가 바뀔 때 |
| `rememberSaveable { }` | 아무 객체 | 키가 바뀔 때 (+ 회전·프로세스 종료에도 복원) |
| `rememberScrollState()` | `ScrollState` | 없음 |
| `rememberNavController()` | `NavHostController` | 없음 |
| `rememberCoroutineScope()` | `CoroutineScope` | 없음 (컴포지션을 떠나면 취소) |
| `rememberAsyncImagePainter(model)` | `AsyncImagePainter` | `model`이 바뀔 때 |

**`remember`가 붙어 있으면 "컴포저블 안에서 만들어도 안전한 함수"**라고 읽으면 된다. 반대로 `remember`가 없는 생성자를 컴포저블 본문에서 직접 호출하고 있다면 의심해야 한다.

### 이미지 로딩에서 흔히 놓치는 것들

```kotlin
AsyncImage(
    model = ImageRequest.Builder(LocalContext.current)
        .data(url)
        .crossfade(true)                    // 부드러운 전환
        .build(),
    contentDescription = null,
    contentScale = ContentScale.Crop,
    placeholder = painterResource(R.drawable.placeholder),
    error = painterResource(R.drawable.image_error),
    modifier = Modifier.fillMaxWidth().aspectRatio(1f)
)
```

| 놓치기 쉬운 것 | 안 하면 |
| --- | --- |
| `contentScale` | 이미지가 늘어나거나 찌그러진다 |
| `placeholder` | 로딩 중 빈 칸이 보인다 |
| `error` | 실패 시 아무것도 안 보이고 원인을 모른다 |
| 크기 확정 | 로딩 완료 시 레이아웃이 튄다 |
| `contentDescription` | 접근성 결함 → [`039-compose-image-content-description-null.md`](039-compose-image-content-description-null.md) |

### 목록에서는 특히 주의한다

`RecipeScreen`처럼 `LazyVerticalGrid`로 수십 장을 띄우는 화면에서는 이미지 크기 결정이 메모리에 직접 영향을 준다. `rememberAsyncImagePainter`가 원본 해상도로 디코딩한다는 점이 여기서 실제 문제가 된다.

```kotlin
items(categories) { category ->
    AsyncImage(                            // ← 목록에서는 특히 AsyncImage
        model = category.strCategoryThumb,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxWidth().aspectRatio(1f)
    )
}
```

### 코일 버전 확인

이 프로젝트는 `io.coil-kt:coil-compose:2.4.0`을 쓴다. 코일 3.x부터는 좌표와 패키지가 바뀌었다.

```kotlin
// Coil 2.x (현재)
implementation("io.coil-kt:coil-compose:2.4.0")
import coil.compose.AsyncImage

// Coil 3.x
implementation("io.coil-kt.coil3:coil-compose:3.x.x")
import coil3.compose.AsyncImage
```

3.x는 코틀린 멀티플랫폼을 지원하고 API가 조금 다르다. 강의를 따라가는 중이라면 2.x 그대로 두는 편이 혼선이 적다. 다만 의존성이 버전 카탈로그 밖에 하드코딩되어 있는 점은 정리할 여지가 있다. → [`007-gradle-version-catalog-and-build-files.md`](007-gradle-version-catalog-and-build-files.md)

## 체크리스트

- [ ] `rememberAsyncImagePainter`가 `AsyncImagePainter`를 반환한다는 관계를 설명할 수 있다.
- [ ] 컴포즈에서 `remember` 접두사가 무엇을 뜻하는지 설명할 수 있다.
- [ ] `remember` 없이 painter를 만들면 무슨 일이 생기는지 설명할 수 있다.
- [ ] `rememberAsyncImagePainter`가 원본 해상도로 디코딩한다는 단점을 안다.
- [ ] 코일이 `AsyncImage`를 권장하는 이유를 말할 수 있다.
- [ ] `Painter`가 무엇을 추상화한 타입인지 설명할 수 있다.
- [ ] `rememberAsyncImagePainter`를 써야 하는 세 가지 상황을 말할 수 있다.
- [ ] `AsyncImagePainter.state`가 첫 컴포지션에서 `Empty`인 이유를 안다.
- [ ] `wrapContentSize()`와 네트워크 이미지를 같이 쓸 때의 레이아웃 문제를 설명할 수 있다.
- [ ] `contentScale`, `placeholder`, `error`를 왜 지정해야 하는지 안다.

## 공식 참고 자료

- [Coil: Compose](https://coil-kt.github.io/coil/compose/)
- [Coil: Getting started](https://coil-kt.github.io/coil/getting_started/)
- [Coil: Image requests](https://coil-kt.github.io/coil/image_requests/)
- [Android Developers: Loading images with Coil](https://developer.android.com/develop/ui/compose/graphics/images/loading)
- [Android Developers: Customize an image in Compose](https://developer.android.com/develop/ui/compose/graphics/images/customize)
- [Android Developers: `Painter` reference](https://developer.android.com/reference/kotlin/androidx/compose/ui/graphics/painter/Painter)
- [Android Developers: State and Jetpack Compose](https://developer.android.com/develop/ui/compose/state)
