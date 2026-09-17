# API Key를 안전하게 관리하는 방법

## 질문이 나온 코드

- [`chapter186/app/src/main/AndroidManifest.xml`](../chapter186/app/src/main/AndroidManifest.xml)
- [`chapter186/app/src/main/java/com/example/chapter_186/LocationViewModel.kt`](../chapter186/app/src/main/java/com/example/chapter_186/LocationViewModel.kt)
- 질문: API Key 같은 값은 환경 변수로 안전하게 사용하는 방법이 없을까? 이렇게 하드코딩 하는 방식 말고 환경 변수를 통해 주입할 수 있는 방법도 있을까?

```xml
<meta-data
    android:name="com.google.android.geo.API_KEY"
    android:value="${MAPS_API_KEY}" />
```

```kotlin
val result = RetrofitClient.create().getAddressFromCoordinate(
    latLng,
    BuildConfig.MAPS_API_KEY
)
```

## 질문 전제 점검

- **사실 이 프로젝트는 이미 하드코딩하고 있지 않다.** 질문의 전제가 현재 코드와 어긋난다. `build.gradle.kts`에 이런 설정이 들어 있다.

  ```kotlin
  val localProperties = Properties().apply {
      val localPropertiesFile = rootProject.file("local.properties")
      if (localPropertiesFile.exists()) {
          localPropertiesFile.inputStream().use { load(it) }
      }
  }

  defaultConfig {
      buildConfigField("String", "MAPS_API_KEY", "\"${localProperties.getProperty("MAPS_API_KEY", "")}\"")
  }
  ```

  즉 **키는 `local.properties`에 있고, 빌드 시점에 `BuildConfig`로 주입**된다. `local.properties`는 `.gitignore`에 들어 있어 저장소에 올라가지 않는다. 이미 권장 방식을 쓰고 있는 것이다.

- **그런데 같은 일을 하는 장치가 두 개 겹쳐 있다.** `build.gradle.kts`에 `secrets-gradle-plugin`도 적용돼 있다.

  ```kotlin
  plugins {
      id("com.google.android.libraries.mapsplatform.secrets-gradle-plugin")
  }
  ```

  이 플러그인이 매니페스트의 `${MAPS_API_KEY}`를 채우고, 수동으로 짠 `buildConfigField` 코드가 `BuildConfig.MAPS_API_KEY`를 만든다. **플러그인만으로 둘 다 할 수 있으므로 수동 코드는 지워도 된다.**

- **가장 중요한 정정 — "숨기면 안전하다"가 아니다.** 이 방식이 막아 주는 것은 **저장소 유출**뿐이다. 빌드된 APK 안에는 키가 문자열로 들어 있고, 디컴파일하면 나온다.

  > "This plugin is primarily for hiding your keys from version control. Since your key is part of the static binary, your API keys are still recoverable by decompiling an APK. So, securing your key using other measures like adding restrictions (if possible) are recommended."

  **클라이언트에 넣는 키는 언제나 공개된 것으로 간주해야 한다.** 진짜 방어는 키 제한(restriction)과 서버 경유다.

- **"환경 변수"라는 표현도 정확히 하면 두 층이 있다.** 안드로이드 앱은 실행 시점에 OS 환경 변수를 읽는 구조가 아니다.

  | 층 | 수단 |
  | --- | --- |
  | **빌드 시점** (개발자 PC / CI) | `local.properties`, Gradle property, OS 환경 변수 |
  | **앱 실행 시점** | `BuildConfig` 상수, 매니페스트 `meta-data`, 원격 설정 |

  빌드 시점에 어디서 읽어 오든, 결국 **앱 안에는 상수로 박힌다.**

## 공부할 내용

### 방법 1 — `secrets-gradle-plugin` (Google Maps 계열의 표준)

Google이 공식 제공하는 플러그인이다.

```kotlin
// 루트 build.gradle.kts
plugins {
    id("com.google.android.libraries.mapsplatform.secrets-gradle-plugin") version "2.0.1" apply false
}

// app/build.gradle.kts
plugins {
    id("com.google.android.libraries.mapsplatform.secrets-gradle-plugin")
}

secrets {
    propertiesFileName = "secrets.properties"          // 기본값은 local.properties
    defaultPropertiesFileName = "local.defaults.properties"  // VCS 에 올려도 되는 기본값
}
```

```properties
# local.properties (.gitignore 대상)
MAPS_API_KEY=AIza...
```

플러그인이 자동으로 두 가지를 해 준다.

- **매니페스트 플레이스홀더** — `${MAPS_API_KEY}`를 치환
- **`BuildConfig` 필드** — `BuildConfig.MAPS_API_KEY` 생성

> "Google strongly recommends that you not check an API key into your version control system. Instead, you should store it in a local `secrets.properties` file, which is located in the root directory of your project but excluded from version control."

`defaultPropertiesFileName`은 **팀원이 키 없이도 빌드는 되게** 하는 장치다. 빈 문자열을 기본값으로 올려 두면 클론 직후 빌드가 깨지지 않는다.

### 방법 2 — `buildConfigField` 직접 작성 (지금 코드의 방식)

플러그인 없이 같은 일을 한다.

```kotlin
android {
    buildFeatures { buildConfig = true }   // 필수 — AGP 8부터 기본 off

    defaultConfig {
        buildConfigField("String", "MAPS_API_KEY", "\"${localProperties.getProperty("MAPS_API_KEY", "")}\"")
    }
}
```

**`buildConfig = true`를 켜야 한다**는 점이 자주 걸리는 함정이다. AGP 8부터 `BuildConfig` 생성이 기본으로 꺼져 있다.

값을 **따옴표로 감싸는 것**도 주의점이다. `buildConfigField`의 세 번째 인자는 **생성될 자바 코드 조각**이라서, 문자열이면 따옴표까지 포함해야 한다.

```kotlin
buildConfigField("String", "KEY", "\"abc\"")   // → public static final String KEY = "abc";
buildConfigField("String", "KEY", "abc")       // → 컴파일 에러 (식별자로 해석)
```

### 방법 3 — CI를 위한 환경 변수 경유

CI 서버에는 `local.properties`가 없다. Gradle은 **환경 변수와 Gradle property를 함께** 읽을 수 있다.

```kotlin
val mapsApiKey: String =
    localProperties.getProperty("MAPS_API_KEY")          // 로컬 개발
        ?: System.getenv("MAPS_API_KEY")                 // CI 환경 변수
        ?: providers.gradleProperty("MAPS_API_KEY").orNull  // -PMAPS_API_KEY=...
        ?: ""
```

GitHub Actions라면 이렇게 넣는다.

```yaml
- name: Build
  env:
    MAPS_API_KEY: ${{ secrets.MAPS_API_KEY }}
  run: ./gradlew assembleRelease
```

**우선순위를 로컬 → CI 순으로 두는 것**이 관례다. 개발자 PC에서는 `local.properties`가 이기고, CI에서는 환경 변수만 존재한다.

### 방법 4 — 아예 앱에 넣지 않는다 (가장 안전하다)

키가 앱 안에 있는 한 추출은 가능하다. **민감한 키는 서버에 두고, 앱은 우리 서버를 부른다.**

```
[현재]  앱 ──(key 포함)──> Google Geocoding API
[권장]  앱 ──(우리 토큰)──> 우리 백엔드 ──(key 포함)──> Google Geocoding API
```

| 키 성격 | 앱에 넣어도 되는가 |
| --- | --- |
| Maps SDK 키 (제한 설정 가능) | 넣는다. 다른 방법이 없다 |
| Geocoding 등 **웹 서비스** 키 | **서버에 두는 것이 원칙** |
| 결제, 관리자, 쓰기 권한 키 | **절대 넣지 않는다** |

**이 프로젝트의 Geocoding 호출이 바로 두 번째 경우다.** 학습 목적이라 앱에서 직접 부르고 있지만, 실제 서비스라면 백엔드를 거치는 것이 맞다. 웹 서비스 키는 **애플리케이션 제한(패키지명 + SHA-1)을 걸 수 없어** 유출되면 그대로 도용된다.

### 키 제한 걸기 — 실질적인 방어선

Google Cloud Console에서 키마다 제한을 건다.

| 제한 종류 | 설명 |
| --- | --- |
| **애플리케이션 제한** | 안드로이드 앱의 **패키지명 + SHA-1 인증서 지문**으로 한정 |
| **API 제한** | 이 키로 부를 수 있는 API를 Maps SDK만으로 한정 |
| **할당량** | 일일 호출 상한을 걸어 피해 규모를 제한 |

**두 제한을 함께 걸어야 한다.** 애플리케이션 제한만 걸면 그 앱 안에서 다른 API를 부를 수 있고, API 제한만 걸면 누구나 그 API를 부를 수 있다.

용도가 다르면 **키를 분리**하는 것이 원칙이다. Maps SDK용 키와 Geocoding용 키를 같이 쓰면 제한을 제대로 걸 수 없다.

### 난독화는 해결책이 아니다

```kotlin
// 이런 것들은 시간 벌기일 뿐이다
val key = "QUl6YVN5..."  .let { String(Base64.decode(it, 0)) }   // 디코드 코드가 같이 들어 있다
```

R8/ProGuard도 **문자열 상수는 그대로 남긴다.** NDK에 넣는 방법도 `strings` 명령 한 번이면 나온다. **"추출을 어렵게 만드는 것"과 "안전한 것"은 다르다.**

## 관련 아키텍처와 베스트 프랙티스

### `.gitignore` 확인이 먼저다

```gitignore
local.properties
secrets.properties
*.jks
*.keystore
key.properties
```

이 저장소 루트의 `.gitignore`에는 이미 들어 있다. **새 챕터를 추가할 때마다 실제로 무시되는지 확인**하는 습관이 필요하다.

```bash
git check-ignore -v chapter186/local.properties   # 규칙에 걸리는지 확인
git ls-files | grep -i 'local.properties'         # 이미 추적 중인 게 있는지
```

### 이미 커밋해 버렸다면

**파일을 지우는 커밋만으로는 부족하다.** 히스토리에 남아 있다.

```
1. 즉시 키를 폐기(revoke)하고 새로 발급한다   ← 이게 가장 먼저다
2. 히스토리에서 제거한다 (git filter-repo 등)
3. 원격에 강제 푸시하고 팀에 공지한다
```

**1번을 건너뛰면 나머지는 의미가 없다.** 이미 공개된 키는 크롤러가 수집했다고 가정해야 한다.

### 키를 코드 전체에 퍼뜨리지 않는다

```kotlin
// 나쁨: 호출하는 곳마다 BuildConfig 를 참조한다
service.getAddress(latLng, BuildConfig.MAPS_API_KEY)

// 좋음: Interceptor 가 자동으로 붙인다
class ApiKeyInterceptor(private val apiKey: String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val url = chain.request().url.newBuilder()
            .addQueryParameter("key", apiKey)
            .build()
        return chain.proceed(chain.request().newBuilder().url(url).build())
    }
}
```

이러면 **`GeocodingApiService`에서 `apiKey` 파라미터가 사라진다.** 키를 바꾸거나 주입 방식을 바꿀 때 고칠 곳이 한 군데다.

### 로그에 키를 남기지 않는다

```kotlin
HttpLoggingInterceptor().apply {
    level = if (BuildConfig.DEBUG) Level.BODY else Level.NONE   // 릴리스에서는 끈다
    redactQueryParams("key")                                    // 쿼리 파라미터 가리기
}
```

디버깅용 로깅이 켜진 채 배포되면 **사용자 기기 로그에 키가 남는다.**

### 빌드 타입별로 다른 키를 쓴다

```kotlin
buildTypes {
    debug   { buildConfigField("String", "MAPS_API_KEY", "\"${debugKey}\"") }
    release { buildConfigField("String", "MAPS_API_KEY", "\"${releaseKey}\"") }
}
```

디버그 키가 유출돼도 프로덕션 할당량에 영향이 없다. 디버그 키에는 **디버그 인증서의 SHA-1**을 제한으로 걸어 둔다.

## 체크리스트

- [ ] 지금 프로젝트가 이미 `local.properties` 방식을 쓰고 있음을 안다.
- [ ] `secrets-gradle-plugin`과 수동 `buildConfigField`가 중복임을 안다.
- [ ] 이 방식이 막는 것이 "저장소 유출"뿐임을 설명할 수 있다.
- [ ] APK에서 키를 추출할 수 있다는 것을 안다.
- [ ] `buildConfig = true`가 필요한 이유를 안다.
- [ ] `buildConfigField`에서 따옴표를 감싸야 하는 이유를 안다.
- [ ] CI에서 환경 변수로 키를 주입하는 방법을 안다.
- [ ] 웹 서비스 키를 앱에 넣으면 안 되는 이유를 설명할 수 있다.
- [ ] 애플리케이션 제한과 API 제한을 둘 다 걸어야 하는 이유를 안다.
- [ ] 난독화가 해결책이 아닌 이유를 설명할 수 있다.
- [ ] 키가 커밋됐을 때 가장 먼저 할 일을 안다.
- [ ] Interceptor로 키 주입을 한곳에 모을 수 있다.

## 공식 참고 자료

- [Google for Developers: Secrets Gradle plugin (Maps SDK for Android)](https://developers.google.com/maps/documentation/android-sdk/secrets-gradle-plugin)
- [GitHub: google/secrets-gradle-plugin](https://github.com/google/secrets-gradle-plugin)
- [Google for Developers: Google Maps Platform security guidance](https://developers.google.com/maps/api-security-best-practices)
- [Google for Developers: Using API keys (Maps SDK for Android)](https://developers.google.com/maps/documentation/android-sdk/get-api-key)
- [Android Developers: Configure your build](https://developer.android.com/build/build-variants)
- [Android Developers: Shrink, obfuscate, and optimize your app](https://developer.android.com/build/shrink-code)
- [Android Developers: App security best practices](https://developer.android.com/privacy-and-security/security-best-practices)
- [Gradle Docs: Build environment](https://docs.gradle.org/current/userguide/build_environment.html)
