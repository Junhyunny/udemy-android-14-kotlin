# Gradle 빌드 문제를 디버깅하는 방법

## 이 문서가 나온 배경

`chapter143`의 `@Parcelize` 문제를 고칠 때, 답을 바로 안 것이 아니라 **몇 단계를 거쳐 좁혀 나갔다.** 그 과정에서 쓴 방법과 명령을 정리한다. 같은 방법이 다른 빌드 문제에도 그대로 쓰인다.

문제의 내용 자체는 [`android-agp-kgp-and-compiler-plugins.md`](android-agp-kgp-and-compiler-plugins.md)에, 빌드 파일 구조는 [`gradle-version-catalog-and-build-files.md`](gradle-version-catalog-and-build-files.md)에 있다.

## 원칙 다섯 개

1. **에러 메시지를 끝까지 읽는다.** 첫 줄만 보고 추측하지 않는다.
2. **어느 단계에서 실패했는지 먼저 가른다.** 설정 단계인가 컴파일 단계인가.
3. **한 번에 하나만 바꾼다.** 두 개를 동시에 바꾸면 무엇이 고쳤는지 모른다.
4. **고쳐졌으면 되돌려서 다시 깨뜨려 본다.** 그래야 원인을 확정할 수 있다.
5. **결과물을 직접 확인한다.** "빌드 성공"은 "의도대로 됐다"와 다르다.

## 단계별로 실제로 한 것

### 0단계 — 재현한다

가장 먼저 할 일은 **에러를 내 손으로 다시 보는 것**이다. IDE가 보여 주는 빨간 줄과 Gradle이 내는 에러는 다를 수 있다.

```bash
./gradlew compileDebugKotlin --console=plain
```

`--console=plain`은 진행률 애니메이션을 없애고 **로그를 그대로 출력**한다. 파이프로 넘기거나 기록할 때 필수다.

`assembleDebug`(전체 빌드)가 아니라 `compileDebugKotlin`(코틀린 컴파일만)을 쓴 이유는 **가장 빨리 실패하는 최소 작업**이기 때문이다. 리소스 처리, APK 패키징까지 갈 필요가 없다.

> 자주 쓰는 작업
> | 작업 | 범위 | 언제 |
> | --- | --- | --- |
> | `compileDebugKotlin` | 코틀린 컴파일만 | 컴파일 에러 반복 확인 — **가장 빠름** |
> | `assembleDebug` | APK까지 | 최종 확인 |
> | `:app:dependencies` | 의존성 트리 | 라이브러리가 실제로 들어왔나 |
> | `buildEnvironment` | 빌드 스크립트 클래스패스 | 플러그인이 어느 버전으로 올라왔나 |
> | `tasks` | 사용 가능한 작업 목록 | 작업 이름을 모를 때 |

### 1단계 — 에러를 분류한다

```
e: .../Category.kt:4:16 Unresolved reference 'parcelize'.
e: .../Category.kt:6:2  Unresolved reference 'Parcelize'.
e: .../Category.kt:7:6  Class 'Category' is not abstract and does not implement abstract members:
                        fun describeContents(): Int
                        fun writeToParcel(p0: Parcel, p1: Int): Unit
```

여기서 두 가지를 읽어 냈다.

**① 실패 단계가 컴파일이다.** `e:` 로 시작하는 코틀린 컴파일러 에러다. 설정 단계였다면 `* What went wrong:`과 함께 플러그인/스크립트 에러가 났을 것이다.

```
설정(configuration) 단계 실패    →  플러그인을 못 찾음, 스크립트 문법 오류, DSL 오류
컴파일(execution) 단계 실패      →  소스 코드 문제, 클래스패스 문제
```

이 구분이 중요한 이유는 **"플러그인이 적용조차 안 됐다"와 "적용은 됐는데 일을 안 했다"가 갈리기 때문이다.** 이번엔 컴파일 단계까지 왔으므로 **플러그인 요청 자체는 성공**했다는 뜻이다.

**② 에러가 두 종류다.** 이게 결정적인 단서였다.

```
Unresolved reference 'parcelize'      → 애노테이션 정의(런타임 JAR)가 클래스패스에 없다
does not implement abstract members    → 코드 생성(컴파일러 플러그인)이 안 돌았다
```

컴파일러 플러그인은 **런타임 조각 + 컴파일러 조각**이 짝이다. 둘 다 빠졌다는 건 한쪽만 잘못된 게 아니라 **플러그인 전체가 아무 일도 안 했다**는 뜻이다.

### 2단계 — 사실로 확인한다 (추측하지 않는다)

"런타임 JAR이 없는 것 같다"는 추측을 **확인 가능한 사실**로 바꿨다.

```bash
./gradlew :app:dependencies --configuration debugCompileClasspath | grep -i parcelize
```

결과: **아무것도 안 나온다.** `kotlin-parcelize-runtime`이 컴파일 클래스패스에 없다는 것이 확정됐다.

> `--configuration`을 지정하는 이유
>
> 의존성 트리는 용도별로 여러 개다. 지정하지 않으면 수천 줄이 쏟아진다.
> - `debugCompileClasspath` — 디버그 빌드를 **컴파일할 때** 보이는 것
> - `debugRuntimeClasspath` — 실행 시점에 들어가는 것
> - `releaseCompileClasspath` — 릴리스 컴파일용
>
> "컴파일이 안 된다"면 봐야 할 것은 `...CompileClasspath`다.

플러그인 쪽도 확인했다.

```bash
./gradlew buildEnvironment --console=plain -q | grep -i "kotlin-gradle-plugin"
# org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.10
```

`buildEnvironment`는 **빌드 스크립트 자신이 쓰는 클래스패스**를 보여 준다. 앱이 쓰는 의존성(`dependencies`)과 다르다. 여기서 **AGP가 KGP 2.2.10을 딸려 왔다**는 사실을 확인했다. 카탈로그의 `kotlin = "2.2.10"`과 우연히 같아서 더 헷갈릴 수 있던 부분이다.

### 3단계 — 가설을 하나씩 검증한다

**가설 A: 런타임 JAR만 없는 것이다.**

확인하려고 런타임 의존성을 직접 넣어 봤다.

```kotlin
implementation("org.jetbrains.kotlin:kotlin-parcelize-runtime:2.2.10")
```

결과:

```
e: Class 'Category' is not abstract and does not implement abstract members:
```

`Unresolved reference` 두 개는 사라지고 **세 번째 에러만 남았다.** 가설 A는 절반만 맞았다. 애노테이션은 찾았지만 **코드 생성이 여전히 안 된다.** 즉 컴파일러 플러그인이 안 돌고 있다는 것이 이 실험으로 증명됐다.

> 이 실험의 가치
>
> 이 시도는 **최종 해법이 아니었지만 버린 시간이 아니다.** "두 문제가 하나가 아니라 둘"이라는 것을 갈라 주었기 때문이다. 디버깅에서 실패한 시도는 **가능성을 지우는 방식으로 기여**한다.

**가설 B: built-in Kotlin을 끄고 옛날 방식으로 하면 된다.**

```properties
android.builtInKotlin=false
```
```kotlin
id("org.jetbrains.kotlin.android")
id("org.jetbrains.kotlin.plugin.parcelize")
```

결과:

```
* What went wrong:
> Failed to apply plugin 'org.jetbrains.kotlin.android'.
```

첫 줄만으로는 이유를 알 수 없다. **한 단계 더 파고들었다.**

```bash
./gradlew compileDebugKotlin --console=plain 2>&1 | grep -A6 "Failed to apply plugin"
```

```
> class com.android.build.gradle.internal.dsl.ApplicationExtensionImpl$AgpDecorated_Decorated
  cannot be cast to class com.android.build.gradle.BaseExtension
```

**들여쓰기된 `>` 줄이 진짜 원인이다.** Gradle은 에러를 중첩해서 보여 준다. 바깥은 "무엇이 실패했나", 안쪽은 "왜 실패했나"다. `grep -A6`(뒤 6줄 함께 출력)으로 안쪽까지 본 덕분에 `BaseExtension`이 사라졌다는 것을 알았고, 이 길이 막혀 있음을 확정했다.

**가설 C: KGP가 낡아서 그렇다. 올리면 된다.**

```toml
kotlin = "2.4.20"
```

```
BUILD SUCCESSFUL
```

### 4단계 — 되돌려서 원인을 확정한다

여기가 사람들이 자주 건너뛰는 단계다. 이번 수정에서는 여러 개를 바꿨다.

```
① 카탈로그에 kotlin-parcelize 플러그인 항목 추가
② 루트 build.gradle.kts 에 apply false 로 선언
③ app/build.gradle.kts 에서 id("kotlin-parcelize") → alias(...)
④ kotlin 버전 2.2.10 → 2.4.20
```

**넷 중 무엇이 진짜 고친 것인가?** 확인 방법은 하나를 되돌려 보는 것이다.

```bash
sed -i '' 's/^kotlin = "2.4.20"/kotlin = "2.2.10"/' gradle/libs.versions.toml
./gradlew compileDebugKotlin --console=plain
```

```
e: Unresolved reference 'parcelize'.
e: Class 'Category' is not abstract and does not implement abstract members:
```

**다시 깨졌다.** ①②③을 다 해 둔 상태에서도 버전만 되돌리면 실패하므로, **④가 본질적인 수정**이라는 것이 확정됐다. ①②③은 정리와 일관성을 위한 것이다.

이 기법을 **ablation(제거 실험)** 이라고 부른다. "고쳤다"에서 멈추지 않고 **"무엇이 고쳤는지"**를 아는 유일한 방법이다. 이걸 하지 않으면 나중에 "카탈로그에 넣어야 parcelize가 된다" 같은 잘못된 지식이 남는다.

### 5단계 — 결과물을 직접 확인한다

`BUILD SUCCESSFUL`은 "컴파일 에러가 없다"는 뜻이지 "원하는 코드가 생성됐다"는 뜻이 아니다. 생성된 바이트코드를 직접 열어 봤다.

```bash
./gradlew assembleDebug
find app/build -name "Category.class"
# app/build/intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes/.../Category.class

javap -p .../Category.class | grep -E "writeToParcel|describeContents|CREATOR"
```

```
public static final android.os.Parcelable$Creator<...Category> CREATOR;
public final int describeContents();
public final void writeToParcel(android.os.Parcel, int);
```

**소스에 없는 멤버 세 개가 실제로 생겼다.** 컴파일러 플러그인이 돌았다는 직접 증거다.

`javap`은 JDK에 포함된 도구로 클래스 파일의 구조를 보여 준다. `-p`는 private 멤버까지 포함한다. 컴파일러 플러그인이 무엇을 만드는지 확인할 때 가장 확실한 방법이다.

> 경로에 있는 `built_in_kotlinc`도 정보다. AGP의 **built-in Kotlin 컴파일러**가 썼다는 뜻이다. 예전 방식이었다면 `kotlinc` 경로였을 것이다.

## 실험할 때의 안전장치

빌드 파일을 여러 번 고쳐야 하므로 **원본을 먼저 백업**했다.

```bash
cp app/build.gradle.kts /tmp/bg.bak
cp gradle.properties /tmp/gp.bak
cp gradle/libs.versions.toml /tmp/toml.bak
```

깔끔하게 되돌릴 때는 git을 쓴다.

```bash
git checkout build.gradle.kts       # 이 파일만 되돌린다
git diff -- app/build.gradle.kts    # 지금까지 뭘 바꿨는지 본다
git stash                           # 전부 잠깐 치워 둔다
```

**이미 커밋된 파일이면 git이 가장 안전하고, 커밋 안 된 실험이면 `cp` 백업이 편하다.**

## 로그에서 신호만 골라내기

Gradle 출력은 길다. 필요한 것만 추리는 패턴이다.

```bash
# 에러와 결과만
./gradlew compileDebugKotlin --console=plain 2>&1 | grep -E "^e: |BUILD|What went wrong"

# 중첩된 원인까지 (Gradle은 들여쓴 > 줄에 진짜 이유를 쓴다)
./gradlew build --console=plain 2>&1 | grep -A6 "What went wrong"

# 마지막 부분만
./gradlew build --console=plain 2>&1 | tail -30
```

`2>&1`은 **표준 에러를 표준 출력으로 합치는 것**이다. Gradle은 에러를 표준 에러로 내보내므로, 이걸 빼면 `grep`에 아무것도 안 걸린다.

더 많은 정보가 필요할 때 쓰는 옵션이다.

| 옵션 | 언제 |
| --- | --- |
| `--stacktrace` | 예외가 어디서 났는지 |
| `--info` | 작업이 왜 실행/생략됐는지 |
| `--debug` | 최후의 수단. 출력이 매우 많다 |
| `--scan` | 웹에서 보는 상세 리포트 |
| `--rerun-tasks` | 캐시 무시하고 전부 다시 |

## 캐시에 속지 않기

`UP-TO-DATE`가 보이면 **그 작업은 실행되지 않았다**는 뜻이다.

```
> Task :app:compileDebugKotlin UP-TO-DATE
```

파일을 고쳤는데 이게 뜬다면 Gradle이 변경을 감지하지 못한 것이다. 주석만 바꿨을 때도 실제로 재컴파일된다(소스가 입력이므로). 그래도 의심스러우면 강제한다.

```bash
./gradlew compileDebugKotlin --rerun-tasks
./gradlew clean                 # 최후의 수단. 느리다
```

이 프로젝트는 `org.gradle.configuration-cache=true`가 켜져 있어서 이런 줄도 나온다.

```
Calculating task graph as configuration cache cannot be reused because JVM has changed.
```

**설정 캐시가 무효화되어 다시 계산했다는 정보 메시지지 에러가 아니다.** 빌드 스크립트를 고치면 자연스럽게 나온다.

## 문제 유형별 첫 수

| 증상 | 먼저 볼 것 |
| --- | --- |
| `Plugin [id: '...'] was not found` | `settings.gradle.kts`의 `pluginManagement` 저장소, 플러그인 ID 오타 |
| `already on the classpath with an unknown version` | 버전을 지정하는 `alias()`/`version`을 버전 없는 `id()`로 |
| `Unresolved reference` (라이브러리) | `:app:dependencies --configuration debugCompileClasspath` |
| `Unresolved reference` (애노테이션) | 컴파일러 플러그인이 적용됐는지 |
| 생성됐어야 할 코드가 없음 | `javap`으로 클래스 파일 확인 |
| `Failed to apply plugin` | `grep -A6`으로 중첩된 원인 확인, 버전 호환성 |
| `Duplicate class` | `:app:dependencies`에서 같은 라이브러리 중복 |
| 버전을 올렸는데 안 바뀜 | `:app:dependencies`에서 `->` 표시(강제 해석) 확인 |

마지막 항목의 `->`는 이런 모양이다.

```
+--- org.jetbrains.kotlin:kotlin-stdlib:1.8.0 -> 2.2.10
```

**"1.8.0을 요청했지만 2.2.10으로 해석됐다"**는 뜻이다. Gradle은 같은 라이브러리를 요청한 것 중 가장 높은 버전을 고른다. "왜 내가 지정한 버전이 아니지?"의 답이 대개 여기 있다.

## 체크리스트

- [ ] `--console=plain`을 쓰는 이유를 안다.
- [ ] 설정 단계 실패와 컴파일 단계 실패를 에러 모양으로 구분할 수 있다.
- [ ] 에러가 여러 개일 때 그것이 단서가 될 수 있다는 것을 안다.
- [ ] `:app:dependencies --configuration debugCompileClasspath`로 라이브러리 유무를 확인할 수 있다.
- [ ] `dependencies`와 `buildEnvironment`가 다른 클래스패스를 본다는 것을 안다.
- [ ] Gradle 에러의 중첩 구조(들여쓴 `>`)를 알고 `grep -A`로 끝까지 읽을 수 있다.
- [ ] 한 번에 하나만 바꾸는 이유를 설명할 수 있다.
- [ ] 되돌려서 다시 깨뜨리는 검증(ablation)을 할 수 있다.
- [ ] `javap -p`로 생성된 멤버를 확인할 수 있다.
- [ ] `UP-TO-DATE`가 무슨 뜻인지 알고 `--rerun-tasks`를 쓸 수 있다.
- [ ] 의존성 트리의 `->` 표시가 무엇을 뜻하는지 안다.
- [ ] 실험 전에 백업하거나 git으로 되돌릴 준비를 한다.

## 공식 참고 자료

- [Gradle Docs: Troubleshooting builds](https://docs.gradle.org/current/userguide/troubleshooting.html)
- [Gradle Docs: Viewing and debugging dependencies](https://docs.gradle.org/current/userguide/viewing_debugging_dependencies.html)
- [Gradle Docs: Understanding dependency resolution](https://docs.gradle.org/current/userguide/dependency_resolution.html)
- [Gradle Docs: Command-line interface](https://docs.gradle.org/current/userguide/command_line_interface.html)
- [Gradle Docs: Configuration cache](https://docs.gradle.org/current/userguide/configuration_cache.html)
- [Android Developers: Configure your build](https://developer.android.com/build)
- [Android Developers: Optimize your build speed](https://developer.android.com/build/optimize-your-build)
