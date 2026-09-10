# 액티비티 생명주기 콜백과 `Bundle`

## 질문이 나온 코드

- [`chapter078/app/src/main/java/com/example/chapter_078/MainActivity.kt`](../chapter078/app/src/main/java/com/example/chapter_078/MainActivity.kt)
- 질문: `onCreate` 외에 다른 생명주기 메서드는 없는가? 파라미터로 받는 `Bundle`은 무엇이고 용도가 무엇인가?

## 공부할 내용

`onCreate`와 `super.onCreate` 자체는 [`activity-oncreate-and-super.md`](activity-oncreate-and-super.md)에 정리했다. 여기서는 나머지 콜백과 `Bundle`을 다룬다.

### 생명주기 콜백 전체

`Activity`는 상태 전이마다 콜백을 제공한다.

| 콜백 | 호출 시점 | 하는 일 |
| --- | --- | --- |
| `onCreate` | 시스템이 액티비티를 처음 생성할 때 | 액티비티 생애 동안 한 번만 하는 기본 시작 로직 |
| `onStart` | 액티비티가 Started 상태로 들어갈 때 | 사용자에게 보이기 시작하는 시점의 UI 초기화 |
| `onResume` | 포그라운드로 올라와 상호작용이 가능해질 때 | 카메라 프리뷰처럼 화면에 보이고 포커스가 있을 때만 필요한 기능 활성화 |
| `onPause` | 사용자가 액티비티를 떠나기 시작할 때 | 계속할 수 없는 동작 일시 중지, GPS 같은 자원 해제 |
| `onStop` | 액티비티가 더 이상 보이지 않을 때 | 보이지 않을 때 불필요한 자원 해제, 데이터 저장 |
| `onRestart` | 정지된 액티비티로 다시 돌아올 때 | `onStart` 직전에 호출 |
| `onDestroy` | 액티비티가 소멸되기 직전 | 앞선 콜백에서 해제하지 않은 자원 정리 |

주의할 점이 있다.

- `onPause`는 "매우 짧고 저장 작업을 수행할 만큼 충분한 시간을 보장하지 않는다." 그래서 "`onPause`에서 앱이나 사용자 데이터를 저장하거나, 네트워크 호출을 하거나, 데이터베이스 트랜잭션을 실행하지 말라"고 안내한다.
- `onStop`에서 액티비티 객체는 메모리에 남아 있고 모든 상태와 멤버 정보를 유지하지만, 윈도우 매니저에는 연결되어 있지 않다.
- `onDestroy`가 호출되는 이유는 두 가지다. 사용자가 액티비티를 끝냈거나, 화면 회전 같은 구성 변경으로 시스템이 일시적으로 소멸시키는 경우다. 이유를 구분하는 로직을 액티비티에 두기보다 `ViewModel`을 쓰라고 권장한다.
- 시스템은 메모리 확보를 위해 액티비티를 직접 죽이지 않고 액티비티가 속한 프로세스를 죽인다.

### `savedInstanceState: Bundle?`

`Bundle`은 키-값 쌍의 모음이다. 시스템이 이전 액티비티 인스턴스의 상태를 저장해 둔 것을 인스턴스 상태(instance state)라고 하며, 기본적으로 "텍스트 입력이나 스크롤 위치처럼 UI 레이아웃에 관한 기본 정보"가 담긴다.

- 값이 `null`인 경우: 액티비티가 이전에 존재한 적이 없을 때. 사용자가 명시적으로 액티비티를 닫거나 `finish()`가 호출된 경우에도 인스턴스 상태 저장 메커니즘은 동작하지 않는다.
- 값이 있는 경우: 화면 회전 같은 구성 변경이나 시스템에 의한 프로세스 종료 후 액티비티가 재생성될 때.
- 저장은 `onSaveInstanceState(outState: Bundle)`에서 이루어지고, 복원은 `onCreate`의 파라미터 또는 `onRestoreInstanceState`에서 한다.
- 인스턴스 상태는 "사소한 양 이상의 데이터를 보존하기에는 적절하지 않다." 큰 데이터는 로컬 저장소나 `ViewModel`을 쓴다.

Compose에서는 같은 메커니즘을 `rememberSaveable`이 감싸서 제공한다. `rememberSaveable`은 "구성 변경과 시스템에 의한 프로세스 종료를 모두 견디도록 내부적으로 상태를 `Bundle`에 담는다." 관련 내용은 [`compose-remember-mutablestate-and-by.md`](compose-remember-mutablestate-and-by.md)를 참고한다.

## 체크리스트

- [ ] 7개 생명주기 콜백의 호출 순서를 그릴 수 있다.
- [ ] `onPause`에서 데이터 저장을 하면 안 되는 이유를 설명할 수 있다.
- [ ] `onStop`과 `onDestroy`의 차이를 설명할 수 있다.
- [ ] `savedInstanceState`가 `null`인 경우를 구분할 수 있다.
- [ ] `onSaveInstanceState`와 `onCreate` 복원의 관계를 설명할 수 있다.
- [ ] 인스턴스 상태와 `ViewModel`, 로컬 저장소의 사용 기준을 구분할 수 있다.

## 공식 참고 자료

- [Android Developers: The activity lifecycle](https://developer.android.com/guide/components/activities/activity-lifecycle)
- [Android Developers: Save UI states](https://developer.android.com/topic/libraries/architecture/saving-states)
- [Android Developers API: Activity](https://developer.android.com/reference/android/app/Activity)
- [Android Developers API: Bundle](https://developer.android.com/reference/android/os/Bundle)
