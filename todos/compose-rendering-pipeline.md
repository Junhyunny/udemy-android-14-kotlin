# 컴포저블 함수가 화면에 그려지기까지

## 질문이 나온 코드

- [`chapter078/app/src/main/java/com/example/chapter_078/MainActivity.kt`](../chapter078/app/src/main/java/com/example/chapter_078/MainActivity.kt)
- 질문: 컴포저블 함수가 어떤 메커니즘으로 화면에 그려지는가? 이 코틀린 코드가 컴파일되면 XML로 바뀌는가? Compose를 썼을 때 사용자 화면에 보이기까지의 과정이 궁금하다.

## 공부할 내용

### XML로 바뀌지 않는다

먼저 오해부터 정리한다. 컴포저블 함수는 XML 레이아웃으로 변환되지 않는다. XML 레이아웃과 `findViewById`, `setText` 같은 뷰 조작은 뷰 시스템의 방식이고, Compose는 그 경로를 쓰지 않는다. 문서는 "Compose의 선언적 접근에서 위젯은 상대적으로 상태가 없고 setter나 getter를 노출하지 않는다. 사실 위젯이 객체로 노출되지 않는다"라고 설명한다.

### 전체 흐름

1. **컴파일**: `@Composable` 애너테이션은 "이 함수가 데이터를 UI로 변환하기 위한 것임을 Compose 컴파일러에 알린다." Compose 컴파일러는 코틀린 컴파일러 플러그인으로 동작하며, 컴포저블 함수를 일반 함수와 다르게 변환해 런타임이 호출 위치와 입력, 읽은 상태를 추적할 수 있게 만든다. 산출물은 여전히 JVM 바이트코드이고, 최종적으로 DEX로 변환되어 APK에 담긴다.

2. **호스팅**: 액티비티에서 `setContent { }`를 호출하면 컴포저블 콘텐츠가 액티비티의 루트 뷰가 된다. 문서는 이것이 "`ComposeView`를 `setContentView`로 지정하는 것과 대략 동등"하다고 밝힌다. 즉 안드로이드 창 입장에서 Compose UI 전체는 뷰 하나다. 자세한 내용은 [`setcontent-and-compose-entry-point.md`](setcontent-and-compose-entry-point.md)를 참고한다.

3. **컴포지션(Composition)**: 무엇을 보여줄지 결정한다. "컴포지션 단계에서 Compose 런타임은 컴포저블 함수를 실행하고 UI를 나타내는 트리 구조를 출력한다." 이 트리의 노드는 안드로이드 `View` 객체가 아니라 Compose UI의 레이아웃 노드다. 런타임은 결과를 `Composition`에 보관하고, 각 컴포저블을 호출 위치로 식별한다.

4. **레이아웃(Layout)**: 어디에 놓을지 결정한다. 각 노드가 자식을 측정하고, 자신의 크기를 정하고, 자식을 배치한다. "각 노드는 한 번만 방문된다. Compose 런타임은 모든 노드를 측정하고 배치하는 데 UI 트리를 한 번만 순회하면 되며, 이는 성능을 향상시킨다." 이 단계가 끝나면 모든 노드가 크기와 x, y 좌표를 갖는다.

5. **드로잉(Drawing)**: 어떻게 그릴지 수행한다. "트리를 위에서 아래로 다시 순회하며 각 노드가 차례로 화면에 자신을 그린다." 그리기 대상은 `Canvas`이며, 이후는 안드로이드의 일반적인 렌더링 경로(RenderNode, RenderThread, SurfaceFlinger)를 탄다.

이 세 단계는 "컴포지션에서 레이아웃, 드로잉으로 한 방향으로 데이터가 흐르며" 한 프레임을 만든다.

### 갱신은 어떻게 일어나는가

상태를 읽은 지점을 런타임이 추적한다. "앞선 단계들 중 하나에서 스냅샷 상태의 `value`를 읽으면, Compose는 그 값을 읽었을 때 무엇을 하고 있었는지 자동으로 추적한다." 그래서 상태가 바뀌면 해당 단계만 무효화된다.

- 컴포지션에서 읽은 상태가 바뀌면 재구성이 일어난다.
- `Modifier.offset { }`처럼 레이아웃 단계에서 읽은 상태가 바뀌면 재구성 없이 레이아웃과 드로잉만 다시 한다.
- `Canvas`나 `Modifier.drawBehind`처럼 드로잉 단계에서 읽었다면 드로잉만 다시 한다.

재구성 시 "Compose는 변경되었을 수 있는 함수나 람다만 호출하고 나머지는 건너뛴다." 이 때문에 컴포저블 함수는 빠르고 멱등적이며 부수 효과가 없어야 한다. 관련 내용은 [`composable-annotation-and-recomposition.md`](composable-annotation-and-recomposition.md)에 정리했다.

### 정리

```
Kotlin 소스 (@Composable)
  └ Compose 컴파일러 플러그인 → 바이트코드 → DEX        (XML 아님)
        └ setContent → ComposeView가 액티비티 루트 뷰
              └ 컴포지션: 무엇을 (레이아웃 노드 트리)
                    └ 레이아웃: 어디에 (크기와 좌표)
                          └ 드로잉: 어떻게 (Canvas)
```

## 체크리스트

- [ ] 컴포저블이 XML로 변환되지 않는다는 점과 그 이유를 설명할 수 있다.
- [ ] Compose UI가 안드로이드 뷰 계층에서 차지하는 위치를 설명할 수 있다.
- [ ] 컴포지션, 레이아웃, 드로잉 각 단계의 산출물을 말할 수 있다.
- [ ] 레이아웃 단계가 트리를 한 번만 순회한다는 점의 의미를 설명할 수 있다.
- [ ] 상태를 어느 단계에서 읽느냐에 따라 무효화 범위가 달라지는 이유를 설명할 수 있다.
- [ ] 재구성에서 건너뛰기가 가능한 조건을 설명할 수 있다.

## 공식 참고 자료

- [Android Developers: Jetpack Compose phases](https://developer.android.com/develop/ui/compose/phases)
- [Android Developers: Thinking in Compose](https://developer.android.com/develop/ui/compose/mental-model)
- [Android Developers: Lifecycle of composables](https://developer.android.com/develop/ui/compose/lifecycle)
- [Android Developers: Compose compiler](https://developer.android.com/develop/ui/compose/compiler)
- [Android Developers API: `androidx.activity.compose` 패키지 (`ComponentActivity.setContent`)](https://developer.android.com/reference/kotlin/androidx/activity/compose/package-summary)
