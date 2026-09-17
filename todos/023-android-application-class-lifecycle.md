# `Application` 클래스의 `onCreate`는 언제 실행되는가

## 질문이 나온 코드

- [`chapter205/app/src/main/java/com/example/chapter_205/WishListApp.kt`](../chapter205/app/src/main/java/com/example/chapter_205/WishListApp.kt)
- 질문: `Application` 클래스를 상속받으면 `onCreate` 메서드는 언제 실행되는가? 생명주기가 어떻게 되는 것이고 언제 필요한지, 어떤 용도로 쓰는지 정리해 달라. 앱이 실행될 때 그냥 실행되나?

```kotlin
class WishListApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Graph.provide(this)
    }
}
```

## 질문 전제 점검

- **"앱이 실행될 때 그냥 실행되나?"** → **"그냥"은 아니다. 매니페스트에 등록해야만 실행된다.**

  ```xml
  <application
      android:name=".WishListApp"        ← 이 한 줄이 없으면 이 클래스는 무시된다
      ... >
  ```

  이 속성이 없으면 안드로이드는 기본 `Application` 객체를 쓰고, `WishListApp`은 만들어지지도 않는다. **컴파일 에러도 경고도 없다.** `Graph.database`가 `lateinit`이라 첫 DB 접근에서 `UninitializedPropertyAccessException`으로 죽는다. `chapter205`는 등록되어 있어서 정상 동작한다.

- **"앱이 실행될 때"** → 표현을 정확히 하면 **"앱 프로세스가 만들어질 때"**다. 이 둘은 다르다.
  - 사용자가 앱을 껐다가 금방 다시 켜면 프로세스가 살아 있어서 **`onCreate`가 다시 호출되지 않는다.**
  - 반대로 사용자가 앱을 건드리지 않아도, 브로드캐스트 수신이나 `WorkManager` 작업 때문에 프로세스가 뜨면 **`onCreate`가 호출된다.** 화면은 하나도 안 뜨는데도 그렇다.

  즉 **"화면이 보이는 것"과 무관하다.** 프로세스 단위 사건이다.

- **"생명주기가 어떻게 되나"** → 여기서 가장 중요한 사실은 **끝을 알려 주는 콜백이 없다**는 것이다. `onTerminate()`가 있긴 하지만 실제 기기에서는 호출되지 않는다.

  > 문서에도 에뮬레이터 환경에서만 호출되며 실제 기기에서는 프로세스가 그냥 종료된다고 적혀 있다.

  그래서 "앱이 종료될 때 정리하기" 같은 코드를 여기에 쓰면 안 된다. **`Application`은 시작 지점만 제공하고 끝 지점은 제공하지 않는다.**

## 공부할 내용

### 실행 순서

앱 프로세스가 뜰 때 순서가 정해져 있다.

```
1. 프로세스 생성 (Zygote fork)
2. Application 인스턴스 생성
3. ContentProvider.onCreate()          ← Application.onCreate 보다 먼저!
4. Application.onCreate()
5. Activity / Service / BroadcastReceiver 시작
```

**3번이 4번보다 먼저**라는 점이 의외의 지점이다. Firebase나 WorkManager 같은 라이브러리가 `ContentProvider`를 몰래 등록해 자동 초기화하는 것이 이 순서를 이용한 것이다. 요즘은 이 용도로 **App Startup 라이브러리**를 쓴다.

핵심은 **`Application.onCreate()`가 모든 액티비티보다 먼저 끝난다**는 보장이다. 그래서 여기서 초기화한 것은 어느 화면에서든 준비되어 있다.

```kotlin
// WishListApp.onCreate() 에서 Graph.provide(this) 를 했으므로
// MainActivity 가 뜰 때는 Graph.database 가 이미 준비되어 있다
class WishViewModel(private val wishRepository: WishRepository = Graph.wishRepository)
```

### `Application` 인스턴스는 하나인가

**프로세스마다 하나다.** 보통 앱은 프로세스가 하나이므로 사실상 싱글톤이다.

다만 매니페스트에서 `android:process`로 별도 프로세스를 지정한 컴포넌트가 있으면 **그 프로세스에서 `Application.onCreate()`가 또 실행된다.** 초기화 코드가 두 번 도는 셈이다. 흔한 경우는 아니지만, 알아 두면 "왜 초기화가 두 번 되지?"에 답할 수 있다.

### 무엇을 넣어야 하나

**넣어도 되는 것**

```kotlin
class WishListApp : Application() {
    override fun onCreate() {
        super.onCreate()

        Graph.provide(this)                              // DI 컨테이너 준비
        if (BuildConfig.DEBUG) Timber.plant(DebugTree()) // 로깅
        AppCompatDelegate.setDefaultNightMode(...)       // 앱 전역 설정
    }
}
```

**넣으면 안 되는 것**

| 하지 말 것 | 이유 | 대신 |
| --- | --- | --- |
| 네트워크 요청 | 앱 시작이 느려진다 | 화면에서 필요할 때 |
| 파일/DB 읽기 | 메인 스레드 I/O | 지연 초기화 |
| 무거운 라이브러리 초기화 | 콜드 스타트 지연 | App Startup, `by lazy` |
| 액티비티 참조 저장 | 누수 | 저장하지 않는다 |
| `Context`를 전역 변수에 담기 | 설계 냄새 | DI로 주입 |

**`Application.onCreate()`에서 보내는 1초는 사용자가 앱을 열 때마다 기다리는 1초다.** 여기 들어가는 코드는 가볍고 빨라야 한다.

`chapter205`가 `Room.databaseBuilder(...).build()`를 여기서 호출하는 것은 괜찮다. `build()`는 **빌더 객체만 만들고 실제 DB 파일은 처음 쿼리할 때 연다.** 즉 여기서 디스크를 건드리지 않는다.

그리고 `Graph.wishRepository`가 `by lazy`인 것도 같은 맥락이다. 실제로 쓸 때까지 만들지 않는다. → [`004-kotlin-by-lazy-delegate.md`](004-kotlin-by-lazy-delegate.md)

### `Application`을 상속할 필요가 있을 때만 한다

안드로이드 문서는 대부분의 경우 서브클래싱이 필요 없다고 안내한다. 싱글톤이 필요하면 그냥 싱글톤을 만들면 되고, 초기화가 필요하면 App Startup을 쓸 수 있다.

**그럼에도 커스텀 `Application`이 흔히 쓰이는 이유**는 두 가지다.

1. **앱 수명 `Context`가 필요한 초기화** — Room, DataStore 등
2. **DI 프레임워크가 요구** — Hilt의 `@HiltAndroidApp`은 반드시 `Application`에 붙는다

`chapter205`는 1번에 해당한다.

### 액티비티 생명주기를 앱 전역에서 관찰하기

`Application`은 앱 안의 모든 액티비티 생명주기를 한곳에서 볼 수 있는 유일한 자리다.

```kotlin
class WishListApp : Application() {
    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) { /* 화면 전환 추적 */ }
            // 나머지 콜백 생략
        })
    }
}
```

"앱이 포그라운드로 돌아왔는가"를 알고 싶다면 요즘은 더 나은 API가 있다.

```kotlin
ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
    override fun onStart(owner: LifecycleOwner) { /* 앱이 포그라운드로 */ }
    override fun onStop(owner: LifecycleOwner) { /* 앱이 백그라운드로 */ }
})
```

이쪽이 **앱 전체를 하나의 생명주기로** 다루므로 회전 같은 액티비티 재생성에 흔들리지 않는다.

### 그 밖의 콜백

```kotlin
class WishListApp : Application() {
    override fun onCreate() { }

    // 메모리가 부족할 때. 캐시를 비울 기회
    override fun onTrimMemory(level: Int) { super.onTrimMemory(level) }

    // 언어, 다크모드, 화면 크기 등 기기 설정 변경
    override fun onConfigurationChanged(newConfig: Configuration) { super.onConfigurationChanged(newConfig) }

    // 실제 기기에서 호출되지 않는다. 쓰지 않는다
    override fun onTerminate() { super.onTerminate() }
}
```

**`onTerminate()`에 의존하지 않는 것**이 중요하다. 저장해야 할 것이 있으면 그때그때 저장한다.

## 관련 아키텍처와 베스트 프랙티스

### 초기화를 미루는 습관

```kotlin
// 나쁨: 앱 시작 때마다 전부 만든다
class MyApp : Application() {
    val analytics = Analytics()      // 안 쓸 수도 있는데 무조건 생성
    val imageLoader = ImageLoader()
    override fun onCreate() { super.onCreate(); heavyInit() }
}

// 좋음: 실제로 쓸 때 만든다
class MyApp : Application() {
    val analytics by lazy { Analytics() }
    val imageLoader by lazy { ImageLoader() }
}
```

`chapter205`의 `Graph`가 `wishRepository`를 `by lazy`로 둔 것이 이 패턴이다.

### 콜드 스타트 시간을 재 본다

```bash
adb shell am start -W -n com.example.chapter_205/.MainActivity
# TotalTime: 380   ← 이 숫자가 커지면 Application.onCreate 를 의심한다
```

`Application.onCreate()`에 코드를 추가할 때는 이 숫자가 어떻게 변하는지 확인하는 습관이 좋다.

### 전역 상태 저장소로 쓰지 않는다

```kotlin
// 안티패턴: Application 을 전역 변수 창고로
class MyApp : Application() {
    var currentUser: User? = null      // 어디서든 바꿀 수 있다
    var isLoggedIn = false
}
```

**프로세스가 죽었다 살아나면 이 값들은 전부 사라진다.** 사용자는 앱을 계속 쓰고 있다고 생각하는데 로그인 상태가 풀린다. 영속이 필요하면 DataStore나 Room에, 화면 상태는 `ViewModel`에 둔다.

### App Startup 라이브러리

초기화 항목이 늘어나면 순서와 의존성을 관리하기 어려워진다.

```kotlin
class DatabaseInitializer : Initializer<WishDatabase> {
    override fun create(context: Context): WishDatabase =
        Room.databaseBuilder(context, WishDatabase::class.java, "wishlist.db").build()

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
```

초기화 사이의 의존 관계를 선언적으로 쓸 수 있고, 라이브러리들이 각자 `ContentProvider`를 만드는 것보다 가볍다.

### Hilt를 쓴다면

```kotlin
@HiltAndroidApp
class WishListApp : Application()
```

`Graph` 같은 수동 컨테이너가 사라진다. → [`050-android-manual-di-graph-object.md`](050-android-manual-di-graph-object.md)

## 체크리스트

- [ ] 매니페스트의 `android:name` 없이는 커스텀 `Application`이 실행되지 않는다는 것을 안다.
- [ ] `Application.onCreate()`가 프로세스 생성 시점 사건임을 설명할 수 있다.
- [ ] 앱을 다시 열어도 프로세스가 살아 있으면 호출되지 않는 이유를 안다.
- [ ] 화면 없이도 `onCreate`가 호출되는 상황을 예로 들 수 있다.
- [ ] `ContentProvider.onCreate()`가 `Application.onCreate()`보다 먼저임을 안다.
- [ ] `onTerminate()`에 의존하면 안 되는 이유를 설명할 수 있다.
- [ ] `Application.onCreate()`에 넣어도 되는 것과 안 되는 것을 구분할 수 있다.
- [ ] `Room.databaseBuilder(...).build()`가 디스크를 건드리지 않는다는 것을 안다.
- [ ] `by lazy`로 초기화를 미루는 이유를 설명할 수 있다.
- [ ] `Application`을 전역 상태 저장소로 쓰면 안 되는 이유를 안다.
- [ ] 콜드 스타트 시간을 `adb`로 측정할 수 있다.

## 공식 참고 자료

- [Android Developers: `Application` reference](https://developer.android.com/reference/android/app/Application)
- [Android Developers: App startup time](https://developer.android.com/topic/performance/vitals/launch-time)
- [Android Developers: App Startup library](https://developer.android.com/topic/libraries/app-startup)
- [Android Developers: Processes and app lifecycle](https://developer.android.com/guide/components/activities/process-lifecycle)
- [Android Developers: Lifecycle-aware components (`ProcessLifecycleOwner`)](https://developer.android.com/topic/libraries/architecture/lifecycle)
- [Android Developers: The app manifest file](https://developer.android.com/guide/topics/manifest/manifest-intro)
- [Android Developers: Manual dependency injection](https://developer.android.com/training/dependency-injection/manual)
