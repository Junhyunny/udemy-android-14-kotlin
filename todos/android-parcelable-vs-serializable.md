# `Parcelable`과 `Serializable`, 굳이 `@Parcelize`가 필요한가

## 질문이 나온 코드

- [`chapter143/app/src/main/java/com/example/chapter_143/Category.kt`](../chapter143/app/src/main/java/com/example/chapter_143/Category.kt)
- 메모: `Serializable` 인터페이스를 상속받지 않으면 navigation State에 저장할 수 없다.
- 질문: `Parcelable`을 통한 직렬화/역직렬화는 어떤 개념인가? 위의 `Serializable`과 다른가? 근본적으로? `Serializable`을 써도 되는데 굳이 `Parcelize`를 쓸 필요가 있나?

```kotlin
@Parcelize
data class Category(
    val idCategory: String,
    val strCategory: String,
    val strCategoryThumb: String,
    val strCategoryDescription: String
) : Parcelable
```

이 값은 `RecipeApp.kt`에서 `savedStateHandle`을 통해 화면 사이로 건네진다.

```kotlin
navController.currentBackStackEntry?.savedStateHandle?.set("cat", it)
```

## 질문 전제 점검

- **"`Serializable`을 상속받지 않으면 navigation State에 저장할 수 없다"** → 방향은 맞지만 조건이 부정확하다. **`Serializable`이어야 하는 게 아니라 `Bundle`에 담길 수 있어야 한다.** 그 자격은 셋 중 하나다.

  ```
  ① 원시 타입과 String (Int, Boolean, String, 배열 …)
  ② android.os.Parcelable  구현
  ③ java.io.Serializable   구현
  ```

  공식 문서도 그렇게 적고 있다.

  > "모든 결과는 `Bundle`에 넣을 수 있는 타입이어야 합니다 (`Parcelable` 또는 `Serializable`)"

  즉 `Serializable`은 **선택지 중 하나**이지 필수 조건이 아니다. 지금 코드는 ②를 골랐다.

- **"`Parcelable`과 `Serializable`이 근본적으로 다른가"** → **완전히 다른 계보다.** 이름이 비슷해 사촌처럼 보이지만 만든 주체도, 목적도, 동작 방식도 다르다.

  | | `java.io.Serializable` | `android.os.Parcelable` |
  | --- | --- | --- |
  | 출신 | 자바 표준 (1997) | 안드로이드 전용 |
  | 목적 | **저장·전송**용 범용 직렬화 | **프로세스 간 통신(IPC)** 전용 |
  | 방식 | 리플렉션으로 런타임에 알아서 | 개발자가 쓰기/읽기를 명시 |
  | 결과물 | 디스크·네트워크에 써도 되는 바이트 | **오직 메모리 안에서만 유효** |
  | 속도 | 느리다 | 빠르다 |
  | 코드량 | `: Serializable` 한 줄 | 길다 → 그래서 `@Parcelize` |

  가장 중요한 차이는 **결과물의 수명**이다.

  > "Parcel is not a general-purpose serialization mechanism, and you should never store any Parcel data on disk or send it over the network."

  `Parcel`로 만든 바이트는 **지금 이 기기, 이 안드로이드 버전에서만** 읽을 수 있다. 포맷이 버전마다 바뀔 수 있기 때문이다. 반면 `Serializable`로 만든 바이트는 파일에 저장했다가 몇 년 뒤에 읽어도 된다.

  그래서 이렇게 갈린다.

  ```
  화면 간 전달, Intent, savedInstanceState   → Parcelable   (메모리 안, 짧은 수명)
  파일 저장, 네트워크 전송, 캐시              → Serializable 또는 JSON
  ```

- **"`Serializable`을 써도 되는데 굳이 `Parcelize`를?"** → **이 코드에서는 둘 다 동작한다.** 성능 차이도 필드 4개짜리 클래스에서는 체감되지 않는다. 그럼에도 `@Parcelize`를 택하는 이유는 셋이다.

  1. **안드로이드가 그렇게 하라고 한다.** 공식 문서는 복잡한 객체에 `@Parcelize`를 권장한다.
  2. **`Serializable`은 리플렉션을 쓴다.** 런타임에 클래스 구조를 뒤져서 필드를 찾는다. 느릴 뿐 아니라 **난독화(R8)에 취약**하다. 필드 이름이 바뀌면 깨질 수 있어 `@Keep` 규칙이 필요해진다.
  3. **`@Parcelize`는 코드량 문제를 없앴다.** `Parcelable`을 피하던 유일한 이유가 "손으로 쓰기 귀찮다"였는데, 애노테이션 한 줄로 사라졌다.

  다만 솔직하게 말하면, **이 예제 규모에서는 `: Serializable` 한 줄이 더 간단하다.** 컴파일러 플러그인을 설정할 필요도 없었다(실제로 그것 때문에 빌드가 막혔다 → [`android-agp-kgp-and-compiler-plugins.md`](android-agp-kgp-and-compiler-plugins.md)). "무조건 Parcelable"이 아니라 **"안드로이드 안에서 주고받는 값이면 Parcelable이 정석"** 정도로 이해하는 편이 정확하다.

- **한 가지 더 짚을 것.** 애초에 `Category` 객체를 통째로 넘기는 설계가 최선인지는 별개 문제다. 공식 권고는 **객체가 아니라 ID를 넘기는 것**이다. → [`android-navigation-passing-many-arguments.md`](android-navigation-passing-many-arguments.md)

## 공부할 내용

### 직렬화란 무엇인가

```
객체 (메모리 위의 구조)              바이트 나열
┌──────────────────┐               ┌────────────────────────┐
│ Category         │  직렬화        │ 04 00 "Beef" 08 00 ... │
│  idCategory="1"  │ ──────────▶   │                        │
│  strCategory=... │ ◀──────────   │                        │
└──────────────────┘  역직렬화      └────────────────────────┘
```

**객체를 줄 세워 바이트로 만드는 것**이 직렬화, 되돌리는 것이 역직렬화다. 왜 필요한가 하면, 메모리 주소는 그 프로세스 안에서만 의미가 있기 때문이다. 다른 프로세스나 다음 실행에 값을 넘기려면 **주소가 아니라 내용 자체**를 보내야 한다.

안드로이드에서 이게 필요한 순간들이다.

- 액티비티 사이에 `Intent`로 값을 넘길 때 (시스템 프로세스를 경유한다)
- 앱이 백그라운드에서 종료됐다 복원될 때 (`savedInstanceState`)
- 화면을 회전할 때 (`rememberSaveable`)
- 내비게이션에서 화면 간 인자를 넘길 때 ← **지금 코드**

### `Parcelable`을 손으로 쓰면

`@Parcelize`가 없던 시절의 코드다.

```kotlin
data class Category(
    val idCategory: String,
    val strCategory: String,
    val strCategoryThumb: String,
    val strCategoryDescription: String
) : Parcelable {

    constructor(parcel: Parcel) : this(
        parcel.readString()!!,        // ← 쓴 순서와 정확히 같아야 한다
        parcel.readString()!!,
        parcel.readString()!!,
        parcel.readString()!!
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(idCategory)
        parcel.writeString(strCategory)
        parcel.writeString(strCategoryThumb)
        parcel.writeString(strCategoryDescription)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<Category> {
        override fun createFromParcel(parcel: Parcel): Category = Category(parcel)
        override fun newArray(size: Int): Array<Category?> = arrayOfNulls(size)
    }
}
```

**여기서 `Serializable`과의 근본적 차이가 드러난다.**

- 쓰는 순서와 읽는 순서가 **정확히 같아야 한다.** 어긋나면 컴파일은 되고 런타임에 값이 뒤섞인다.
- 필드를 추가하면 **두 곳을 모두 고쳐야 한다.** 한 곳만 고치면 조용히 깨진다.
- 그 대신 리플렉션이 필요 없다. **어디에 무엇이 있는지 코드에 이미 적혀 있으니 빠르다.**

`Serializable`은 이 모든 걸 리플렉션으로 런타임에 알아낸다. 그래서 코드는 한 줄이고, 대신 느리다.

### `@Parcelize`가 하는 일

```kotlin
@Parcelize
data class Category(...) : Parcelable
```

위의 30줄을 **컴파일 시점에 자동 생성**한다. 실제로 생성됐는지 확인할 수 있다.

```bash
javap -p app/build/.../Category.class | grep -E "writeToParcel|describeContents|CREATOR"
```

```
public static final android.os.Parcelable$Creator<...Category> CREATOR;
public final int describeContents();
public final void writeToParcel(android.os.Parcel, int);
```

소스에 없는 멤버 세 개가 들어 있다. 즉 `@Parcelize`는 **"편하게 쓰는 `Serializable`"이 아니라, 손으로 쓸 `Parcelable` 코드를 기계가 대신 써 주는 것**이다. 실행 시점의 성격은 손으로 쓴 `Parcelable`과 완전히 같다.

주 생성자의 프로퍼티만 직렬화되므로, 본문에 선언한 프로퍼티는 복원되지 않는다.

```kotlin
@Parcelize
data class User(val name: String) : Parcelable {
    var nickname: String = ""    // ← 직렬화되지 않는다. 복원하면 ""
}
```

### 크기 제한 — 둘 다 해당된다

`Parcelable`이든 `Serializable`이든 `Bundle`에 들어가는 순간 같은 한계에 묶인다.

> "Binder 트랜잭션 버퍼는 프로세스 레벨에서 **1MB의 고정 크기 제한**이 있습니다. 이 제한을 초과하면 `TransactionTooLargeException`이 발생합니다. Intent를 통해 전송하는 데이터는 **수 KB 이내**로 제한하세요."

`rememberSaveable`은 더 엄격하다.

> "`rememberSaveable` 사용 시 저장되는 데이터는 **50KB 이하**로 유지할 것을 권장합니다."

**"Parcelable로 만들었으니 크기는 괜찮다"가 아니다.** 직렬화 방식과 무관하게 총량이 제한된다. 지금은 `Category` 하나라 문제없지만, 목록 전체를 넘기는 순간 터진다.

### 세 가지 직렬화를 구분하기

이름이 비슷한 것이 하나 더 있다. `kotlinx.serialization`이다.

| | `java.io.Serializable` | `android.os.Parcelable` | `kotlinx.serialization` |
| --- | --- | --- | --- |
| 표시 | `: Serializable` | `@Parcelize` + `: Parcelable` | `@Serializable` |
| 만든 곳 | 자바 표준 | 안드로이드 | 젯브레인즈 |
| 방식 | 리플렉션 | 컴파일러 생성 | 컴파일러 생성 |
| 주 용도 | 자바 생태계 저장·전송 | 안드로이드 IPC | JSON 등 포맷 변환 |
| `Bundle`에 담기 | ✅ | ✅ | ❌ (직접은 불가) |

**`@Serializable`(kotlinx)과 `: Serializable`(자바)은 전혀 다른 것이다.** 이름이 겹쳐 가장 많이 헷갈린다.

- 타입 안전 내비게이션 route → `kotlinx`의 `@Serializable`
- `savedStateHandle`에 담을 값 → `Parcelable` 또는 자바 `Serializable`

`chapter157`에서 route에 쓰는 `@Serializable`과 여기 `Category`의 `@Parcelize`는 목적이 다르다. → [`android-navigation-string-route-vs-type-safe.md`](android-navigation-string-route-vs-type-safe.md)

## 관련 아키텍처와 베스트 프랙티스

### 언제 무엇을 쓰나

| 상황 | 선택 |
| --- | --- |
| 액티비티/프래그먼트/화면 간 전달 | **`@Parcelize`** |
| `savedInstanceState`, `rememberSaveable` | **`@Parcelize`** |
| JSON으로 서버와 주고받기 | `kotlinx.serialization` 또는 Gson/Moshi |
| 파일에 저장 | `kotlinx.serialization` (JSON), Room, DataStore |
| 타입 안전 내비게이션 route | `kotlinx.serialization`의 `@Serializable` |
| 자바 라이브러리가 요구할 때 | `java.io.Serializable` |

### 데이터 모델을 겸용하지 않는다

지금 `Category`는 **두 가지 역할을 겸하고 있다.**

```kotlin
@Parcelize
data class Category(
    val idCategory: String,       // 서버 JSON 필드 이름 그대로
    val strCategory: String,      // "str" 접두사는 TheMealDB API의 관례
    ...
) : Parcelable
```

- Retrofit이 **JSON을 파싱할 DTO**
- 화면 사이로 넘기는 **UI 모델**

학습 예제에서는 괜찮다. 하지만 규모가 커지면 서버 API가 바뀔 때마다 UI 코드까지 흔들린다. 실무에서는 나누는 편이다.

```kotlin
// 네트워크 응답 전용 (data/remote)
data class CategoryDto(
    val idCategory: String,
    val strCategory: String,
    val strCategoryThumb: String,
    val strCategoryDescription: String
)

// 앱 안에서 쓰는 모델 (domain)
@Parcelize
data class Category(
    val id: String,
    val name: String,
    val thumbnailUrl: String,
    val description: String
) : Parcelable

fun CategoryDto.toDomain() = Category(idCategory, strCategory, strCategoryThumb, strCategoryDescription)
```

이름도 읽을 수 있게 바뀌고, 서버 필드가 바뀌어도 매핑 함수 한 곳만 고치면 된다. → [`android-repository-single-source-of-truth.md`](android-repository-single-source-of-truth.md)

### 난독화에 주의한다

`Serializable`은 리플렉션으로 필드를 찾으므로, R8이 이름을 바꾸면 복원이 깨질 수 있다.

```proguard
-keepnames class com.example.chapter_143.** implements java.io.Serializable
```

`@Parcelize`는 생성된 코드가 필드를 직접 참조하므로 이 문제가 없다. **난독화 안정성도 `Parcelable`을 택하는 실질적 이유 중 하나다.**

### 다른 앱에 커스텀 `Parcelable`을 보내지 않는다

> "다른 앱으로 custom Parcelable을 전송하면 안 됩니다. 시스템이 해당 클래스를 알 수 없어 unmarshal 실패가 발생할 수 있기 때문입니다."

앱 밖으로 나가는 `Intent`에는 원시 타입이나 문자열만 담는다.

## 체크리스트

- [ ] 직렬화가 왜 필요한지 메모리 주소 관점에서 설명할 수 있다.
- [ ] `Bundle`에 담길 수 있는 세 가지 자격을 말할 수 있다.
- [ ] `Parcelable`과 `Serializable`의 출신과 목적 차이를 설명할 수 있다.
- [ ] `Parcel` 데이터를 디스크에 저장하면 안 되는 이유를 설명할 수 있다.
- [ ] 손으로 쓴 `Parcelable`에서 쓰기/읽기 순서가 중요한 이유를 안다.
- [ ] `@Parcelize`가 컴파일 시점에 무엇을 생성하는지 확인할 수 있다.
- [ ] `@Parcelize`가 주 생성자 프로퍼티만 직렬화한다는 것을 안다.
- [ ] `Bundle` 크기 제한이 직렬화 방식과 무관하게 적용된다는 것을 안다.
- [ ] `kotlinx`의 `@Serializable`과 자바의 `: Serializable`을 구분할 수 있다.
- [ ] 상황별로 세 가지 직렬화 중 무엇을 쓸지 고를 수 있다.
- [ ] `Serializable`이 난독화에 취약한 이유를 설명할 수 있다.

## 공식 참고 자료

- [Android Developers: Parcelables and bundles](https://developer.android.com/guide/components/activities/parcelables-and-bundles)
- [Android Developers: Parcelable implementation generator (`kotlin-parcelize`)](https://developer.android.com/kotlin/parcelize)
- [Android Developers: `Parcelable` reference](https://developer.android.com/reference/android/os/Parcelable)
- [Android Developers: Save UI states](https://developer.android.com/topic/libraries/architecture/saving-states)
- [Android Developers: Pass data between destinations](https://developer.android.com/guide/navigation/use-graph/pass-data)
- [Kotlin Docs: Serialization](https://kotlinlang.org/docs/serialization.html)
- [Java Docs: `java.io.Serializable`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/Serializable.html)
