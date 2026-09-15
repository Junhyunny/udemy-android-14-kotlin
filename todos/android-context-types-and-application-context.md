# `Context`의 종류 — `Application`도 `Context`가 되는 이유

## 질문이 나온 코드

- [`chapter205/app/src/main/java/com/example/chapter_205/Graph.kt`](../chapter205/app/src/main/java/com/example/chapter_205/Graph.kt)
- [`chapter205/app/src/main/java/com/example/chapter_205/WishListApp.kt`](../chapter205/app/src/main/java/com/example/chapter_205/WishListApp.kt)
- 질문: `context`는 뭔가? 패키지가 `android.content.Context`인데, 해당 애플리케이션의 특정 정보에 접근하는 건가? 어떤 정보들이 있는 컨텍스트인지 알려 달라. 그리고 `Context`가 애플리케이션이 되네?

```kotlin
// WishListApp.kt
class WishListApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Graph.provide(this)          // ← this 가 Context 로 넘어간다
    }
}

// Graph.kt
fun provide(context: Context) {
    database = Room.databaseBuilder(context, WishDatabase::class.java, "wishlist.db").build()
}
```

## 질문 전제 점검

- **"`Context`가 애플리케이션이 되네?"** → 관찰이 정확하다. 그리고 이것이 `Context`를 이해하는 핵심이다. **`Context`는 클래스 계층의 밑바탕이지 특정 화면을 가리키는 핸들이 아니다.**

  ```
  Context (추상 클래스)
    └─ ContextWrapper
         ├─ Application       ← 앱 전체. 화면이 없다
         ├─ Service           ← 화면이 없다
         └─ ContextThemeWrapper
              └─ Activity     ← 화면이 있다
  ```

  `Application`, `Service`, `Activity`가 **모두 `Context`를 상속한다.** 그래서 `Application.onCreate()` 안의 `this`를 `Context` 자리에 그대로 넘길 수 있다. 셋 다 "앱 환경에 접근하는 통로"라는 공통 역할을 갖기 때문이다.

- **"애플리케이션의 특정 정보에 접근하는 건가"** → 방향은 맞지만 "정보"보다 넓다. `Context`는 **읽기만 하는 것이 아니라 동작도 시킨다.** 액티비티 시작, 브로드캐스트 전송, 시스템 서비스 획득 같은 것이 전부 `Context`를 통한다. `Context`가 무엇인지에 대한 기본 설명은 앞서 정리했다. → [`android-context-and-toast.md`](android-context-and-toast.md)

- **그래서 진짜 물어야 할 질문은 "`Context`가 뭐냐"가 아니라 "어떤 `Context`를 넘겨야 하느냐"다.** 여기서 메모리 누수가 갈린다. Room 데이터베이스처럼 **앱 전체 수명을 사는 객체에 액티비티 컨텍스트를 넘기면 그 액티비티가 GC되지 못한다.**

  `chapter205`는 `Application`에서 `this`를 넘기고 있으므로 **이 점에서는 올바르다.** 다만 `provide(context)`의 시그니처가 아무 `Context`나 받게 되어 있어서, 나중에 액티비티에서 호출해도 컴파일이 통과한다. 그 위험을 아래에서 다룬다.

## 공부할 내용

### `Context`가 제공하는 것

크게 네 갈래다.

```kotlin
// ① 리소스 접근
context.getString(R.string.app_name)
context.resources.getDimension(R.dimen.padding)
context.assets.open("data.json")

// ② 시스템 서비스
context.getSystemService(Context.CONNECTIVITY_SERVICE)
ContextCompat.getSystemService(context, NotificationManager::class.java)

// ③ 앱 전용 저장 공간
context.filesDir            // /data/data/<패키지>/files
context.cacheDir            // 캐시
context.getDatabasePath("wishlist.db")
context.getSharedPreferences("prefs", MODE_PRIVATE)

// ④ 앱 수준 동작
context.startActivity(intent)
context.sendBroadcast(intent)
context.checkSelfPermission(...)
```

`Room.databaseBuilder(context, ...)`가 `Context`를 요구하는 이유는 **③** 때문이다. `wishlist.db` 파일을 앱 전용 디렉토리 어딘가에 만들어야 하는데, 그 경로를 아는 것이 `Context`다.

### 두 종류의 `Context`

실무에서 구분해야 하는 것은 사실상 둘이다.

| | Application Context | Activity Context |
| --- | --- | --- |
| 얻는 법 | `applicationContext`, `Application` 자신 | `this`(액티비티 안), `LocalContext.current` |
| 수명 | **앱 프로세스 전체** | 그 액티비티가 살아 있는 동안 |
| 테마 | ❌ 없음 | ✅ 있음 |
| UI(다이얼로그, 인플레이트) | ❌ 부적합 | ✅ |
| 오래 사는 객체에 보관 | ✅ 안전 | ❌ **누수** |

**핵심 규칙 한 줄.**

> **오래 사는 객체가 붙잡을 `Context`는 Application Context. 화면을 그리는 데 쓸 `Context`는 Activity Context.**

### 왜 액티비티 컨텍스트를 붙잡으면 안 되는가

```kotlin
// 위험한 코드
object Graph {
    lateinit var database: WishDatabase
    fun provide(context: Context) {
        database = Room.databaseBuilder(context, ...).build()   // context 를 계속 들고 있다
    }
}

// 액티비티에서 호출했다면
class MainActivity : ComponentActivity() {
    override fun onCreate(...) {
        Graph.provide(this)      // ← MainActivity 를 object 가 영원히 참조한다
    }
}
```

`object Graph`는 앱이 죽을 때까지 살아 있다. 그 안의 `database`가 `MainActivity`를 참조하면, **화면을 나가거나 회전해도 `MainActivity`가 메모리에서 해제되지 않는다.** 액티비티는 뷰 트리 전체를 들고 있으므로 누수 규모가 크다.

회전할 때마다 액티비티가 새로 만들어지므로, 회전을 반복하면 누수가 쌓인다.

### `chapter205`가 맞게 한 부분

```kotlin
class WishListApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Graph.provide(this)      // this = Application. 어차피 앱 수명만큼 산다
    }
}
```

`Application` 자신을 넘기므로 누수가 없다. **이미 영원히 사는 객체라 붙잡아도 잃을 것이 없다.**

다만 시그니처를 더 안전하게 만들 수 있다.

```kotlin
// 지금: 아무 Context 나 받는다
fun provide(context: Context) { ... }

// 더 안전: 실수로 액티비티를 넘겨도 애플리케이션 컨텍스트로 바꾼다
fun provide(context: Context) {
    database = Room.databaseBuilder(
        context.applicationContext,      // ← 한 줄로 방어
        WishDatabase::class.java,
        "wishlist.db"
    ).build()
}

// 또는 타입으로 강제
fun provide(application: Application) { ... }
```

`context.applicationContext`는 **어떤 `Context`에서 호출해도 애플리케이션 컨텍스트를 돌려준다.** 라이브러리성 코드에서 흔히 쓰는 방어 패턴이다.

### Compose에서 얻는 `Context`

```kotlin
val context = LocalContext.current
```

`setContent`가 액티비티 안에서 호출되므로, 이 값은 실질적으로 **액티비티 컨텍스트**다. 그래서 다이얼로그나 토스트에는 알맞지만, 오래 사는 객체에 보관하면 안 된다.

```kotlin
// 나쁨: ViewModel 이 액티비티를 붙잡는다
class MyViewModel(private val context: Context) : ViewModel()

// 좋음: 필요하면 AndroidViewModel 이 주는 Application 을 쓴다
class MyViewModel(application: Application) : AndroidViewModel(application)

// 더 좋음: ViewModel 이 Context 를 아예 모르게 한다
class MyViewModel(private val repository: WishRepository) : ViewModel()
```

**세 번째가 가장 낫다.** `ViewModel`은 UI 프레임워크를 몰라야 테스트하기 쉽다. `chapter205`의 `WishViewModel`이 `Context`를 받지 않는 것은 잘한 설계다.

`HomeView`에서 `val context = LocalContext.current`를 선언해 두고 실제로는 쓰지 않는 점은 정리 대상이다. → [`compose-localcontext.md`](compose-localcontext.md)

### `Application.onCreate`에서 초기화하는 이유

```kotlin
class WishListApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Graph.provide(this)
    }
}
```

`Application.onCreate()`는 **어떤 액티비티, 서비스, 브로드캐스트 리시버보다 먼저** 실행된다. 그래서 여기서 DB를 준비해 두면, 어느 진입점으로 앱이 시작되든 `Graph.database`가 이미 준비되어 있다. → [`android-application-class-lifecycle.md`](android-application-class-lifecycle.md)

이 클래스를 만들었으면 **매니페스트에 등록해야 한다.** 안 하면 실행되지 않고, `lateinit var database`가 초기화되지 않아 크래시한다.

```xml
<application
    android:name=".WishListApp"
    ... >
```

## 관련 아키텍처와 베스트 프랙티스

### `Context`를 넘길 때의 판단표

| 넘길 대상 | 어떤 `Context` |
| --- | --- |
| Room, DataStore, SharedPreferences | **Application** |
| WorkManager, 싱글톤 매니저 | **Application** |
| `Toast`, `Dialog`, 뷰 인플레이트 | **Activity** |
| `startActivity` (액티비티 안에서) | **Activity** |
| `startActivity` (그 밖에서) | Application + `FLAG_ACTIVITY_NEW_TASK` |
| 테마가 적용된 리소스 읽기 | **Activity** |

### `Context`를 필드에 저장할 때의 점검

```kotlin
class SomeManager(private val context: Context)   // 이 객체는 얼마나 오래 사는가?
```

**"이 객체가 액티비티보다 오래 사는가?"**를 물어본다. 그렇다면 `applicationContext`를 받거나, 생성자에서 변환한다.

```kotlin
class SomeManager(context: Context) {
    private val appContext = context.applicationContext
}
```

### `ViewModel`에 `Context`를 넣지 않는다

`ViewModel`은 화면 회전을 견디므로 액티비티보다 오래 산다. 액티비티 컨텍스트를 들고 있으면 정확히 누수 조건이다. 문자열이 필요하면 리소스 ID를 상태에 담아 화면에서 해석한다. → [`android-resources-r-class-and-compose-theme.md`](android-resources-r-class-and-compose-theme.md)

### 누수를 확인하는 도구

```kotlin
// build.gradle.kts
debugImplementation("com.squareup.leakcanary:leakcanary-android:2.14")
```

LeakCanary는 디버그 빌드에서 액티비티 누수를 자동으로 잡아 알려 준다. `Context`를 다루는 코드를 쓸 때 붙여 두면 실수를 빨리 발견한다.

### `object` 싱글톤에 `Context`를 담을 때

`chapter205`의 `object Graph`처럼 전역 싱글톤에 `Context` 기반 객체를 담는 패턴은 **앱 수명과 일치하므로 그 자체로는 문제없다.** 다만 테스트에서 갈아 끼우기 어렵다는 별개의 문제가 있다. → [`android-manual-di-graph-object.md`](android-manual-di-graph-object.md)

## 체크리스트

- [ ] `Application`, `Service`, `Activity`가 모두 `Context`의 하위 타입임을 안다.
- [ ] `Context`가 화면 핸들이 아니라 앱 환경 접근 통로라는 것을 설명할 수 있다.
- [ ] `Context`가 제공하는 네 갈래 기능을 말할 수 있다.
- [ ] Room이 `Context`를 요구하는 이유를 설명할 수 있다.
- [ ] Application Context와 Activity Context의 수명 차이를 안다.
- [ ] 오래 사는 객체가 액티비티 컨텍스트를 붙잡으면 생기는 누수를 설명할 수 있다.
- [ ] `context.applicationContext`로 방어하는 패턴을 쓸 수 있다.
- [ ] `LocalContext.current`가 어떤 컨텍스트인지 안다.
- [ ] `ViewModel`에 `Context`를 넣지 않아야 하는 이유를 설명할 수 있다.
- [ ] 커스텀 `Application`을 매니페스트에 등록해야 한다는 것을 안다.
- [ ] 상황별로 어떤 `Context`를 넘길지 판단할 수 있다.

## 공식 참고 자료

- [Android Developers: `Context` reference](https://developer.android.com/reference/android/content/Context)
- [Android Developers: `Application` reference](https://developer.android.com/reference/android/app/Application)
- [Android Developers: `ContextWrapper` reference](https://developer.android.com/reference/android/content/ContextWrapper)
- [Android Developers: Save data in a local database using Room](https://developer.android.com/training/data-storage/room)
- [Android Developers: ViewModel overview](https://developer.android.com/topic/libraries/architecture/viewmodel)
- [Android Developers: Manage your app's memory](https://developer.android.com/topic/performance/memory)
- [Android Developers: Data and file storage overview](https://developer.android.com/training/data-storage)
