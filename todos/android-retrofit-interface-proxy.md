# 인터페이스만 있어도 함수를 호출할 수 있나 — Retrofit의 프록시

## 질문이 나온 코드

- [`chapter186/app/src/main/java/com/example/chapter_186/GeocodingApiService.kt`](../chapter186/app/src/main/java/com/example/chapter_186/GeocodingApiService.kt)
- 질문: 이렇게 인터페이스만 있어도 함수를 호출할 수 있나? 프록시 객체가 자동으로 셋업되나?

```kotlin
interface GeocodingApiService {
    @GET("maps/api/geocode/json")
    suspend fun getAddressFromCoordinate(
        @Query("latlng") latlng: String,
        @Query("key") apiKey: String
    ): GeocodingResponse
}

// 사용하는 쪽
return retrofit.create(GeocodingApiService::class.java)
```

## 질문 전제 점검

- **"프록시 객체가 자동으로 셋업되나?" → 그렇다. 정확히 짚었다.** 다만 **자동으로 되는 것이 아니라 `retrofit.create()`를 부르는 순간** 만들어진다. 인터페이스만 선언해 두고 `create()`를 안 부르면 아무 일도 일어나지 않는다.

- **"인터페이스만 있어도 호출할 수 있나"는 반은 맞고 반은 틀리다.** 인터페이스 타입만으로는 호출할 수 없다. **인스턴스가 있어야** 한다. 그 인스턴스를 **우리가 쓰지 않고 Retrofit이 런타임에 만들어 준다**는 것이 핵심이다.

  ```kotlin
  // 이건 불가능하다 — 인터페이스는 인스턴스화할 수 없다
  val service = GeocodingApiService()

  // Retrofit 이 구현체를 만들어 준다
  val service: GeocodingApiService = retrofit.create(GeocodingApiService::class.java)
  ```

- **Room의 `@Dao`와 헷갈리기 쉬운데 방식이 완전히 다르다.** 둘 다 "인터페이스만 쓰면 동작한다"는 경험은 같지만 원리가 정반대다.

  | | 시점 | 방식 |
  | --- | --- | --- |
  | **Room `@Dao`** | **컴파일 시점** | 애너테이션 프로세서가 `XxxDao_Impl.java` **소스를 생성** |
  | **Retrofit** | **런타임** | `java.lang.reflect.Proxy`로 **동적 프록시 생성** |

  Room은 `build/generated`에 실제 파일이 생긴다. Retrofit은 **파일이 생기지 않는다.** 클래스가 메모리에서 만들어진다.

  → [`android-room-dao-code-generation.md`](android-room-dao-code-generation.md)

- **그래서 오타는 컴파일 시점에 안 잡힌다.** 이 프로젝트에서 실제로 겪은 문제다.

  ```kotlin
  @GET("maps/api/geocode")     // /json 누락 — 컴파일 통과, 런타임 404
  ```

  애너테이션 문자열은 **런타임에야 해석된다.** Room이라면 컴파일 에러가 났을 상황이 Retrofit에서는 조용히 통과한다.

## 공부할 내용

### 동적 프록시란 무엇인가

자바의 표준 기능이다. Retrofit이 발명한 게 아니다.

> "A dynamic proxy class is a class that implements a list of interfaces specified at runtime when the class is created."

```java
Proxy.newProxyInstance(
    classLoader,                        // 어느 로더에 만들 것인가
    new Class<?>[] { MyApi.class },     // 어떤 인터페이스를 구현할 것인가
    invocationHandler                   // 호출되면 무엇을 할 것인가
);
```

**세 번째 인자가 전부다.** 프록시의 어떤 메서드를 부르든 `InvocationHandler.invoke()` 하나로 모인다.

> "A method invocation on a proxy instance through one of its proxy interfaces will be dispatched to the `invoke` method of the instance's invocation handler, passing the proxy instance, a `java.lang.reflect.Method` object identifying the method that was invoked, and an array of type `Object` containing the arguments."

```java
Object invoke(Object proxy, Method method, Object[] args)
//                          ↑ 어떤 함수가 불렸나   ↑ 무슨 인자로
```

### Retrofit이 이걸 어떻게 쓰는가

```
retrofit.create(GeocodingApiService::class.java)
   ↓
Proxy.newProxyInstance(..., GeocodingApiService.class, handler)
   ↓
service.getAddressFromCoordinate("37.5,127.0", "AIza...")  호출
   ↓
handler.invoke(proxy, Method(getAddressFromCoordinate), ["37.5,127.0", "AIza..."])
   ↓
1. Method 의 애너테이션을 리플렉션으로 읽는다  → @GET("maps/api/geocode/json")
2. 파라미터 애너테이션을 읽는다              → @Query("latlng"), @Query("key")
3. URL 을 조립한다
     https://maps.googleapis.com/maps/api/geocode/json?latlng=37.5,127.0&key=AIza...
4. OkHttp 로 요청을 보낸다
5. 응답 본문을 GsonConverterFactory 로 GeocodingResponse 로 변환한다
6. 반환한다
```

**애너테이션이 "설정"이고, 프록시가 그 설정을 읽어 실행하는 엔진이다.** 우리가 쓰는 것은 선언뿐이고, 동작은 전부 Retrofit이 만든다.

### `suspend`는 어떻게 지원되는가

코틀린의 `suspend` 함수는 컴파일되면 **마지막 파라미터로 `Continuation`이 추가된 일반 함수**가 된다.

```kotlin
suspend fun getAddressFromCoordinate(latlng: String, key: String): GeocodingResponse
```

```java
// 바이트코드 수준
Object getAddressFromCoordinate(String latlng, String key, Continuation<? super GeocodingResponse> $completion)
```

Retrofit은 `invoke()`에서 **마지막 인자가 `Continuation`인지 확인**해서 `suspend` 함수임을 알아낸다. 그리고 OkHttp의 콜백을 `Continuation`에 연결한다.

**즉 Retrofit은 코루틴을 특별히 아는 게 아니라, 컴파일된 형태를 알아보고 다리를 놓는 것이다.**

→ [`kotlin-coroutines-continuation-state-machine.md`](kotlin-coroutines-continuation-state-machine.md)

반환 타입에 따라 동작이 달라진다.

| 선언 | 동작 |
| --- | --- |
| `suspend fun f(): Foo` | 중단하고 결과를 직접 반환. **실패 시 예외를 던진다** |
| `suspend fun f(): Response<Foo>` | HTTP 상태 코드까지 받는다. 4xx/5xx도 예외가 아니다 |
| `fun f(): Call<Foo>` | 콜백/동기 실행. 예전 방식 |

**첫 번째 형태(지금 코드)는 비-2xx 응답에서 `HttpException`을 던진다.** 그래서 `try-catch`가 필수다.

→ [`kotlin-coroutines-exception-handling-try-catch.md`](kotlin-coroutines-exception-handling-try-catch.md)

### `create()`를 매번 부르면 안 된다

지금 코드의 실질적인 문제다.

```kotlin
object RetrofitClient {
    fun create(): GeocodingApiService {
        val retrofit = Retrofit.Builder()        // ← 호출할 때마다 새로 만든다
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        return retrofit.create(GeocodingApiService::class.java)
    }
}

// 사용하는 쪽 — 주소를 조회할 때마다
RetrofitClient.create().getAddressFromCoordinate(...)
```

**매 호출마다 `Retrofit`, `OkHttpClient`, 프록시가 전부 새로 만들어진다.**

`OkHttpClient`가 특히 문제다. 커넥션 풀과 스레드 풀을 들고 있어서, 새로 만들면 **연결 재사용이 안 되고 소켓과 스레드가 낭비된다.**

```kotlin
object RetrofitClient {
    private const val BASE_URL = "https://maps.googleapis.com/"

    val service: GeocodingApiService by lazy {      // 한 번만 만든다
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GeocodingApiService::class.java)
    }
}

// 사용하는 쪽
RetrofitClient.service.getAddressFromCoordinate(...)
```

→ [`kotlin-by-lazy-delegate.md`](kotlin-by-lazy-delegate.md)

**프록시 생성 자체는 비싸지 않다.** 진짜 비용은 `OkHttpClient`다. 하지만 어차피 함께 만들어지므로 통째로 재사용하는 것이 맞다.

### 리플렉션 비용과 R8

동적 프록시는 리플렉션을 쓴다. 두 가지를 알아 둔다.

- **첫 호출이 조금 느리다.** Retrofit은 메서드별 파싱 결과를 캐시하므로 두 번째부터는 빠르다.
- **R8/ProGuard가 애너테이션을 지우면 깨진다.** Retrofit은 자체 `consumer-rules.pro`를 포함하고 있어 보통은 문제없지만, 응답 데이터 클래스는 **Gson이 리플렉션으로 필드명을 읽기 때문에** 난독화되면 깨질 수 있다.

```kotlin
// 방법 1: @SerializedName 으로 이름을 고정한다
data class GeocodingResult(
    @SerializedName("formatted_address") val formattedAddress: String
)

// 방법 2: keep 규칙을 둔다
// -keep class com.example.chapter_186.** { *; }
```

**방법 1이 낫다.** 부수 효과로 `formatted_address`라는 코틀린 답지 않은 이름도 사라진다. 지금 코드는 JSON 키와 프로퍼티 이름을 똑같이 맞춰서 동작하게 한 형태다.

### 인터페이스를 쓰는 다른 이유 — 테스트

프록시 이야기와 별개로, **인터페이스라서 얻는 이점**이 있다.

```kotlin
class FakeGeocodingApi : GeocodingApiService {
    override suspend fun getAddressFromCoordinate(latlng: String, apiKey: String) =
        GeocodingResponse(listOf(GeocodingResult("서울특별시 중구")), "OK")
}
```

네트워크 없이 ViewModel을 테스트할 수 있다. **Retrofit이 인터페이스를 요구하는 것이 제약이 아니라 이점으로 돌아온다.**

## 관련 아키텍처와 베스트 프랙티스

### 서비스 인터페이스는 얇게 유지한다

```kotlin
// 좋음: HTTP 계약만 표현한다
interface GeocodingApiService {
    @GET("maps/api/geocode/json")
    suspend fun getAddressFromCoordinate(@Query("latlng") latlng: String, @Query("key") key: String): GeocodingResponse
}
```

비즈니스 로직, 캐싱, 에러 변환은 **Repository에서** 한다. 서비스 인터페이스는 "서버와의 약속"만 담는다.

→ [`android-repository-single-source-of-truth.md`](android-repository-single-source-of-truth.md)

### 공통 파라미터는 Interceptor로 뺀다

```kotlin
// 모든 함수에 apiKey 가 반복된다
suspend fun getAddressFromCoordinate(@Query("latlng") latlng: String, @Query("key") key: String)
```

API 키처럼 **모든 요청에 붙는 값**은 Interceptor로 옮긴다.

```kotlin
class ApiKeyInterceptor(private val apiKey: String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val url = chain.request().url.newBuilder().addQueryParameter("key", apiKey).build()
        return chain.proceed(chain.request().newBuilder().url(url).build())
    }
}
```

→ [`android-api-key-secure-management.md`](android-api-key-secure-management.md)

### 응답을 도메인 모델로 변환한다

```kotlin
// API 응답 (서버 구조에 종속)
data class GeocodingResponse(val results: List<GeocodingResult>, val status: String)

// 도메인 모델 (우리 구조)
data class Address(val text: String)
```

서버 응답 구조가 앱 전체에 퍼지면 **API가 바뀔 때 화면까지 고쳐야 한다.** Repository 경계에서 변환한다.

지금 코드는 `viewModel.address.value.firstOrNull()?.formatted_address ?: "No Address"`처럼 **화면이 API 응답 구조를 직접 알고 있다.** 정리 대상이다.

### `status` 필드를 확인한다

Google Geocoding API는 **HTTP 200을 주면서도 본문에서 실패를 알린다.**

```json
{ "results": [], "status": "REQUEST_DENIED", "error_message": "The provided API key is invalid." }
```

`suspend fun`이 예외를 던지지 않으므로 `try-catch`로는 이걸 못 잡는다. **`status`를 반드시 확인해야 한다.**

```kotlin
when (result.status) {
    "OK" -> _address.value = result.results
    "ZERO_RESULTS" -> /* 주소 없음 */
    else -> Log.e("fetchAddress", "API 오류: ${result.status}")
}
```

키가 잘못됐을 때 "아무 일도 안 일어나는" 증상이 여기서 나온다.

## 체크리스트

- [ ] 인터페이스 타입만으로는 호출할 수 없고 인스턴스가 필요함을 안다.
- [ ] `retrofit.create()`가 동적 프록시를 만든다는 것을 설명할 수 있다.
- [ ] `InvocationHandler.invoke()`로 모든 호출이 모인다는 것을 안다.
- [ ] Room의 코드 생성과 Retrofit의 동적 프록시 차이를 설명할 수 있다.
- [ ] 애너테이션 오타가 컴파일 시점에 안 잡히는 이유를 안다.
- [ ] `suspend` 함수가 `Continuation`으로 컴파일된다는 것을 안다.
- [ ] 반환 타입에 따른 동작 차이를 구분할 수 있다.
- [ ] `create()`를 매번 부르면 안 되는 이유를 설명할 수 있다.
- [ ] `OkHttpClient` 재사용이 중요한 이유를 안다.
- [ ] R8과 Gson 리플렉션의 충돌을 설명할 수 있다.
- [ ] `@SerializedName`의 두 가지 이점을 안다.
- [ ] HTTP 200인데 실패인 응답을 처리해야 함을 안다.

## 공식 참고 자료

- [Square: Retrofit](https://square.github.io/retrofit/)
- [Square: `Retrofit` API reference](https://square.github.io/retrofit/2.x/retrofit/retrofit2/Retrofit.html)
- [Oracle: Dynamic Proxy Classes](https://docs.oracle.com/javase/8/docs/technotes/guides/reflection/proxy.html)
- [Oracle: `java.lang.reflect.Proxy`](https://docs.oracle.com/javase/8/docs/api/java/lang/reflect/Proxy.html)
- [Square: OkHttp](https://square.github.io/okhttp/)
- [Android Developers: Connect to the network](https://developer.android.com/develop/connectivity/network-ops/connecting)
- [Android Developers: Data layer](https://developer.android.com/topic/architecture/data-layer)
- [Google for Developers: Geocoding API status codes](https://developers.google.com/maps/documentation/geocoding/requests-geocoding)
- [Kotlin Docs: Composing suspending functions](https://kotlinlang.org/docs/composing-suspending-functions.html)
