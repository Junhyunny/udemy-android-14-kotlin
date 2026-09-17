# 학습 TODO

## 1. Kotlin 언어 기초

- [ ] [`is` 키워드 용도가 뭐야](001-kotlin-is-operator-and-smart-cast.md)
- [ ] [코틀린 후행 람다와 Compose의 `content` 슬롯](002-kotlin-trailing-lambda-and-content-slot.md)
- [ ] [`sealed` 키워드는 왜 사용하는가](003-kotlin-sealed-class-and-interface.md)
- [ ] [`by lazy`는 무엇이고 언제 쓰는가](004-kotlin-by-lazy-delegate.md)
- [ ] [`BigDecimal` 나눗셈과 출력 형식](005-kotlin-bigdecimal-division-and-formatting.md)
- [ ] [`@OptIn`과 실험적 API](006-kotlin-optin-experimental-api.md)

## 2. Gradle과 Android 프로젝트 구조

- [ ] [빌드 파일 4형제: `settings`, 루트 `build`, 모듈 `build`, `libs.versions.toml`](007-gradle-version-catalog-and-build-files.md)
- [ ] [안드로이드 프로젝트의 `build.gradle.kts` 파일 구성](008-gradle-build-files-in-android-project.md)
- [ ] [`com.android.application` 플러그인의 역할](009-android-application-plugin-role.md)
- [ ] [AGP, KGP, 컴파일러 플러그인 — `@Parcelize`가 안 되던 진짜 이유](010-android-agp-kgp-and-compiler-plugins.md)
- [ ] [안드로이드 모듈에서 `main` 함수가 실행되지 않는 이유](011-android-module-main-function-run.md)
- [ ] [Gradle 빌드 문제를 디버깅하는 방법](012-gradle-build-problem-debugging.md)
- [ ] [`AndroidManifest.xml`의 용도](013-android-manifest-purpose.md)
- [ ] [`R` 객체와 `res/values` xml, `ui/theme` 중 무엇을 쓰나](014-android-resources-r-class-and-compose-theme.md)
- [ ] [`@DrawableRes` 애너테이션 용도가 뭐야](015-android-drawable-res-annotation.md)
- [ ] [안드로이드의 `dp` 단위](016-android-dp-unit.md)

## 3. Android 애플리케이션 기본 구조

- [ ] [액티비티와 화면의 관계](017-android-activity-and-screen.md)
- [ ] [`onCreate`와 `super.onCreate`의 역할](018-activity-oncreate-and-super.md)
- [ ] [액티비티 생명주기 콜백과 `Bundle`](019-activity-lifecycle-callbacks-and-bundle.md)
- [ ] [`Context`의 종류 — `Application`도 `Context`가 되는 이유](020-android-context-types-and-application-context.md)
- [ ] [`LocalContext`의 역할](021-compose-localcontext.md)
- [ ] [`Toast`에 컨텍스트를 전달하는 이유](022-android-context-and-toast.md)
- [ ] [`Application` 클래스의 `onCreate`는 언제 실행되는가](023-android-application-class-lifecycle.md)
- [ ] [`Parcelable`과 `Serializable`, 굳이 `@Parcelize`가 필요한가](024-android-parcelable-vs-serializable.md)

## 4. Compose UI 기초

- [ ] [`@Composable`과 재구성](025-composable-annotation-and-recomposition.md)
- [ ] [`setContent`와 Compose 진입점](026-setcontent-and-compose-entry-point.md)
- [ ] [컴포저블 함수가 화면에 그려지기까지](027-compose-rendering-pipeline.md)
- [ ] [`Column`과 `Row` 레이아웃](028-compose-column-and-row-layout.md)
- [ ] [`Box` 컴포저블은 언제 사용하는가](029-compose-box-usage.md)
- [ ] [간격 주기: `padding`과 `Spacer`](030-compose-padding-vs-spacer.md)
- [ ] [`Modifier.wrapContentSize()`는 무엇을 하는가](031-compose-wrapcontentsize-modifier.md)
- [ ] [`Scaffold`는 무엇을 해 주는가](032-compose-scaffold.md)
- [ ] [`rememberScrollState`에는 어떤 기능이 숨어 있는가](033-compose-scroll-state-and-vertical-scroll.md)

## 5. Compose 상태와 재구성

- [ ] [`remember`, `mutableStateOf`, 그리고 `by` 위임](034-compose-remember-mutablestate-and-by.md)
- [ ] [`remember`가 만든 상태는 어디에 저장되는가](035-compose-state-storage-and-snapshot.md)
- [ ] [재구성은 언제, 어디까지 일어나는가](036-compose-recomposition-timing-and-scope.md)
- [ ] [새 리스트를 대입했는데 재구성이 되는 이유](037-compose-state-list-reference-change.md)
- [ ] [Compose는 반복 렌더링에 키가 필요 없는가](038-compose-list-item-key.md)

## 6. Compose 컴포넌트 활용

- [ ] [`contentDescription`은 왜 필수 값이고 `null`은 무슨 뜻인가](039-compose-image-content-description-null.md)
- [ ] [`rememberAsyncImagePainter`는 왜 쓰는가](040-compose-remember-async-image-painter.md)
- [ ] [아이콘 색은 `tint`로 바꾸나 `colors`로 지정하나](041-compose-icon-tint-vs-component-colors.md)
- [ ] [`animateItem()`은 무엇을 하고, 왜 잔상 문제가 해결됐나](042-compose-lazy-list-animate-item.md)
- [ ] [`stickyHeader` 기능은 뭘까](043-compose-lazy-list-sticky-header.md)
- [ ] [바텀 시트는 상태 플래그로 여나 `SheetState`로 여나](044-compose-modal-bottom-sheet-state-and-sheetstate.md)

## 7. ViewModel과 앱 아키텍처

- [ ] [`ViewModel`의 역할과 `remember`가 필요 없는 이유](045-android-viewmodel-role-and-remember.md)
- [ ] [`viewModel()` 함수와 직접 생성의 차이](046-compose-viewmodel-function-vs-manual.md)
- [ ] [ViewModel에서 state를 따로 관리하는 이유 — 베스트 프랙티스인가](047-android-viewmodel-state-exposure-patterns.md)
- [ ] [화면에서 상태를 직접 바꾸는 게 맞나 — 비동기 실행 시점](048-android-viewmodel-encapsulation-and-async-timing.md)
- [ ] [`Repository` 호출이 없어도 동작하는데 왜 필요한가](049-android-repository-single-source-of-truth.md)
- [ ] [`Graph` object로 의존성을 주입하는 방식은 베스트 프랙티스인가](050-android-manual-di-graph-object.md)

## 8. 코루틴과 반응형 상태

- [ ] [`suspend` 키워드와 코루틴은 이벤트 루프인가](051-kotlin-coroutines-suspend-and-event-loop.md)
- [ ] [중단된 코루틴의 상태는 어디에 저장되는가](052-kotlin-coroutines-continuation-state-machine.md)
- [ ] [코루틴 스코프란 무엇인가](053-kotlin-coroutine-scope-concept.md)
- [ ] [`Dispatchers.IO`는 무엇이고 다른 값들과 어떻게 다른가](054-kotlin-coroutine-dispatchers.md)
- [ ] [코루틴 안의 `try-catch`는 왜 필요한가](055-kotlin-coroutines-exception-handling-try-catch.md)
- [ ] [`viewModelScope.launch`로 감싸야 하는 이유](056-android-viewmodelscope-launch-necessity.md)
- [ ] [`Flow`란 무엇인가 — 왜 `Flow` 반환에는 `suspend`가 없나](057-kotlin-flow-concepts-and-suspend.md)
- [ ] [`collectAsState`로 `Flow`를 받으면 상태처럼 관리되는가](058-compose-collectasstate-flow-to-state.md)
- [ ] [`LaunchedEffect`와 `snapshotFlow`는 언제 실행되는가](059-compose-launchedeffect-and-snapshotflow.md)

## 9. 데이터 저장과 네트워크

- [ ] [Room은 무엇인가 — JPA 같은 ORM인가](060-android-room-architecture.md)
- [ ] [`@Dao`는 프록시를 만드는가](061-android-room-dao-code-generation.md)
- [ ] [인터페이스만 있어도 함수를 호출할 수 있나 — Retrofit의 프록시](062-android-retrofit-interface-proxy.md)
- [ ] [API Key를 안전하게 관리하는 방법](063-android-api-key-secure-management.md)

## 10. Navigation

- [ ] [`androidx.navigation`과 `androidx.navigation.compose`의 차이](064-android-navigation-package-compose-vs-runtime.md)
- [ ] [`NavHost`, `NavController`, `composable`, `dialog`의 역할](065-android-navigation-navhost-composable-dialog.md)
- [ ] [문자열 route 방식은 최신·권장 방식인가](066-android-navigation-string-route-vs-type-safe.md)
- [ ] [`navArgument`로 인자를 선언하는 방식이 베스트 프랙티스인가](067-android-navigation-navargument-setup.md)
- [ ] [라우팅 파라미터가 많아지면 어떻게 전달하는가](068-android-navigation-passing-many-arguments.md)
- [ ] [`currentBackStackEntry`와 `savedStateHandle`은 무엇인가](069-android-navigation-backstackentry-savedstatehandle.md)
- [ ] [내비게이션 콜백 프롭 드릴링, 개선할 수 있나](070-compose-navigation-prop-drilling.md)
- [ ] [`popBackStack`과 `navigateUp`은 무엇이 다른가](071-android-navigation-popbackstack-vs-navigateup.md)
- [ ] [`navigate` 뒤에 오는 람다는 무엇인가 — `NavOptionsBuilder`](072-android-navigation-navoptions-builder.md)
- [ ] [`navigation-compose`와 `navigation3`은 무엇이 다른가](073-android-navigation-compose-vs-navigation3.md)

## 11. 권한, 위치 및 지도 API

- [ ] [`rememberLauncherForActivityResult`는 왜 사용하는가](074-android-activity-result-api-and-launcher.md)
- [ ] [`@RequiresPermission`은 어떤 용도로 사용하는가](075-android-requires-permission-annotation.md)
- [ ] [`COARSE`와 `FINE` 위치 권한의 차이점은 뭐야](076-android-location-permission-coarse-vs-fine.md)
- [ ] [`shouldShowRequestPermissionRationale`의 역할은 뭐야](077-android-should-show-request-permission-rationale.md)
- [ ] [`com.google.android.gms` 의존성은 뭐지](078-android-play-services-location-dependency.md)
- [ ] [`Looper`와 `fusedLocationClient`는 무엇을 하는가](079-android-fused-location-provider-and-looper.md)
- [ ] [`MarkerState`는 왜 `remember` 해서 써야 하나](080-compose-maps-marker-state-remember.md)
- [ ] [`rememberCameraPositionState`와 블록 안의 `position`](081-compose-maps-camera-position-state.md)
