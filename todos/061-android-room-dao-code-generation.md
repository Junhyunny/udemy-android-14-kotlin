# `@Dao`는 프록시를 만드는가 — 구현체가 없는데 어떻게 동작하나

## 질문이 나온 코드

- [`chapter205/app/src/main/java/com/example/chapter_205/WishDao.kt`](../chapter205/app/src/main/java/com/example/chapter_205/WishDao.kt)
- 질문: `@Dao` 애너테이션을 쓰면 자동으로 프록시 객체가 셋업되는 건가? 실제 구현체가 없는데 자동으로 프레임워크에 의해 설정되는 건가?

```kotlin
@Dao
abstract class WishDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract fun addWish(wish: Wish)      // 본문이 없다
    // ...
}
```

## 질문 전제 점검

- **"프록시 객체"** → **아니다. 이 단어가 핵심 오해다.** 프록시는 **런타임에** 동적으로 만들어지는 대리 객체를 뜻한다. 스프링의 `@Transactional`이나 JPA의 지연 로딩이 그런 방식이다. Room은 그러지 않는다.

  Room은 **컴파일 시점에 소스 코드를 생성한다.**

  > "At compile time, Room automatically generates implementations of the DAOs that you define."

  즉 `WishDao_Impl.kt`라는 **진짜 파일이 빌드 폴더에 만들어지고**, 그것이 컴파일되어 APK에 들어간다. 런타임에 마법이 일어나는 것이 아니라, 우리가 손으로 쓸 코드를 기계가 대신 써 둔 것이다.

  ```
  프록시 방식(스프링/하이버네이트):  런타임에 바이트코드 조작 → 대리 객체 생성
  Room:                          컴파일 타임에 .kt 파일 생성 → 그냥 평범한 클래스
  ```

- **"프레임워크에 의해 자동으로 설정되는 건가"** → 설정 주체가 프레임워크가 아니라 **빌드 도구(KSP 또는 KAPT)**다. `@Dao`는 그 도구에게 주는 표시일 뿐, 애노테이션 자체가 무언가를 하지는 않는다.

- **그리고 생성된 코드를 직접 볼 수 있다.** 이게 프록시와 가장 다른 점이다. 프록시는 런타임에만 존재해서 들여다보기 어렵지만, Room이 만든 코드는 파일로 남아 있다. 아래에서 실제로 열어 본다.

- **덧붙여, 이 코드가 `interface`가 아니라 `abstract class`인 것도 짚어 둘 만하다.** 공식 문서는 기본적으로 인터페이스를 권한다.

  > "You can define each DAO as either an interface or an abstract class. For basic use cases, you usually use an interface."

  추상 클래스는 **직접 구현한 헬퍼 메서드가 필요할 때** 쓴다. 지금 `WishDao`에는 그런 메서드가 없으므로 `interface`가 더 알맞다.

## 공부할 내용

### 생성된 코드를 직접 확인하기

빌드하면 이런 파일이 생긴다.

```bash
find app/build -name "WishDao_Impl.kt"
# app/build/generated/ksp/debug/kotlin/com/example/chapter_205/WishDao_Impl.kt
```

열어 보면 우리가 손으로 썼어야 할 코드가 그대로 들어 있다. 아래는 이 프로젝트(Room 2.8.5)에서 실제로 생성된 내용이다.

```kotlin
@Generated(value = ["androidx.room.RoomProcessor"])
public class WishDao_Impl(
  __db: RoomDatabase,
) : WishDao() {                                    // ← 우리가 쓴 추상 클래스를 상속한다
  private val __db: RoomDatabase
  private val __insertAdapterOfWish: EntityInsertAdapter<Wish>
  private val __deleteAdapterOfWish: EntityDeleteOrUpdateAdapter<Wish>
  private val __updateAdapterOfWish: EntityDeleteOrUpdateAdapter<Wish>

  init {
    this.__db = __db
    this.__insertAdapterOfWish = object : EntityInsertAdapter<Wish>() {
      protected override fun createQuery(): String =
        "INSERT OR IGNORE INTO `wish-table` (`id`,`title`,`description`) VALUES (nullif(?, 0),?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: Wish) {
        statement.bindLong(1, entity.id)
        statement.bindText(2, entity.title)
        statement.bindText(3, entity.description)
      }
    }
    this.__deleteAdapterOfWish = object : EntityDeleteOrUpdateAdapter<Wish>() {
      protected override fun createQuery(): String = "DELETE FROM `wish-table` WHERE `id` = ?"
      // ...
    }
    this.__updateAdapterOfWish = object : EntityDeleteOrUpdateAdapter<Wish>() {
      protected override fun createQuery(): String =
        "UPDATE OR ABORT `wish-table` SET `id` = ?,`title` = ?,`description` = ? WHERE `id` = ?"
      // ...
    }
  }
  // ...
}
```

애노테이션 한 줄이 어떤 SQL로 펼쳐지는지가 그대로 보인다.

| 우리가 쓴 것 | 생성된 SQL |
| --- | --- |
| `@Insert(onConflict = IGNORE)` | `INSERT OR IGNORE INTO \`wish-table\` (...) VALUES (nullif(?, 0),?,?)` |
| `@Delete` | `DELETE FROM \`wish-table\` WHERE \`id\` = ?` |
| `@Update` | `UPDATE OR ABORT \`wish-table\` SET ... WHERE \`id\` = ?` |

`nullif(?, 0)`이 눈에 띈다. `@PrimaryKey(autoGenerate = true)`인 `id`가 `0`이면 `NULL`로 바꿔 넣어 **SQLite가 키를 자동 생성하게** 만드는 장치다. `Wish(id = 0L, ...)`로 만든 객체가 새 행으로 들어가는 이유가 여기 있다.

이것이 "구현체가 없는데 어떻게 동작하는가"의 답이다. **구현체는 있다. 우리가 안 썼을 뿐이다.**

### 누가 만드는가 — KSP

`build.gradle.kts`를 보면 Room 컴파일러가 붙어 있다.

```kotlin
plugins {
    alias(libs.plugins.ksp)          // Kotlin Symbol Processing
}
dependencies {
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)  // ← 이것이 코드를 생성한다
}
```

`ksp(...)`로 선언한 의존성은 **앱에 포함되지 않는다.** 빌드할 때만 돌면서 소스를 읽고 새 파일을 뱉는 도구다.

```
WishDao.kt (@Dao 표시)
   ↓  KSP: room-compiler 가 애노테이션을 읽는다
   ↓  SQL 검증 + 코드 생성
WishDao_Impl.kt   (app/build/generated/ksp/debug/kotlin/...)
   ↓  평범하게 컴파일
WishDao_Impl.class  →  APK
```

| | KAPT | KSP |
| --- | --- | --- |
| 방식 | 자바 스텁을 만들어 자바 애노테이션 프로세서 실행 | 코틀린 소스를 직접 분석 |
| 속도 | 느리다 | 약 2배 빠르다 |
| 권장 | ❌ 유지 보수 모드 | ✅ **현재 권장** |

새 프로젝트라면 KSP를 쓴다. AGP 9에서는 KAPT가 `com.android.legacy-kapt`로 밀려났다. → [`010-android-agp-kgp-and-compiler-plugins.md`](010-android-agp-kgp-and-compiler-plugins.md)

### 컴파일러 플러그인과도 다르다

앞서 본 `@Parcelize`와 비교하면 차이가 분명해진다.

| | `@Parcelize` | `@Dao` |
| --- | --- | --- |
| 도구 | 코틀린 **컴파일러 플러그인** | **KSP**(애노테이션 프로세서) |
| 하는 일 | 기존 클래스의 바이트코드에 멤버를 **추가** | **새 파일**을 생성 |
| 결과물 | 소스 파일 없음. 바이트코드에만 존재 | `.kt` 소스 파일이 남는다 |
| 확인 방법 | `javap`으로 바이트코드 열어 보기 | 생성된 소스 열어 보기 |

→ [`024-android-parcelable-vs-serializable.md`](024-android-parcelable-vs-serializable.md)

### 인스턴스는 언제 연결되는가

```kotlin
val database = Room.databaseBuilder(context, WishDatabase::class.java, "wishlist.db").build()
val dao = database.wishDao()     // ← 여기서 WishDao_Impl 을 돌려준다
```

`WishDatabase`도 추상 클래스이고, 역시 `WishDatabase_Impl`이 생성된다. 그 안에서 `wishDao()`가 `WishDao_Impl`을 만들어 돌려준다(한 번 만들고 캐싱한다).

```kotlin
// WishDatabase_Impl.kt (생성된 코드, 요약)
public override fun wishDao(): WishDao = _wishDao.value      // lazy 로 한 번만 만든다
```

`Room.databaseBuilder`가 리플렉션으로 `WishDatabase_Impl` 클래스를 찾아 인스턴스화한다. **리플렉션은 이 한 번뿐이고**, 이후 모든 쿼리는 평범한 메서드 호출이다.

### 왜 `abstract`여야 하는가

Room이 **상속해서 구현할 대상**이 필요하기 때문이다.

```kotlin
@Dao abstract class WishDao          →  class WishDao_Impl extends WishDao
@Dao interface WishDao               →  class WishDao_Impl implements WishDao
```

`final class`나 본문 있는 메서드만 있으면 Room이 끼어들 자리가 없다. `@Database`가 붙은 클래스도 같은 이유로 `abstract`다.

**추상 클래스가 유용한 경우**는 여러 쿼리를 묶어야 할 때다.

```kotlin
@Dao
abstract class WishDao {
    @Insert abstract suspend fun insert(wish: Wish)
    @Delete abstract suspend fun delete(wish: Wish)

    @Transaction                                   // 직접 구현한 메서드
    open suspend fun replace(old: Wish, new: Wish) {
        delete(old)
        insert(new)
    }
}
```

이런 게 없다면 `interface`로 두는 편이 간결하다.

## 관련 아키텍처와 베스트 프랙티스

### 생성된 코드를 읽는 습관

Room이 예상과 다르게 동작할 때 **생성된 `_Impl` 파일을 열어 보는 것이 가장 빠른 디버깅**이다. 실제로 실행되는 SQL이 문자열로 적혀 있다.

```bash
find app/build/generated/ksp -name "*_Impl.kt"
```

"이 쿼리가 왜 이런 결과를 주지?"의 답이 대개 거기 있다. → [`012-gradle-build-problem-debugging.md`](012-gradle-build-problem-debugging.md)

### 컴파일 에러 메시지가 곧 문서다

Room의 에러는 구체적이다.

```
error: Not sure how to convert a Cursor to this method's return type
error: There is a problem with the query: [SQLITE_ERROR] no such column: titl
error: Room cannot verify the data integrity. Looks like you've changed schema
       but forgot to update the version number.
```

마지막 메시지는 **엔티티를 바꾸고 `version`을 안 올렸을 때** 나온다. 런타임 크래시가 아니라 빌드 실패로 잡아 준다.

### `@Transaction`을 빠뜨리지 않기

여러 쿼리가 하나의 논리적 작업이면 `@Transaction`을 붙인다. 안 붙이면 중간에 실패했을 때 반쯤 적용된 상태가 남는다.

```kotlin
@Transaction
open suspend fun moveAll(from: Long, to: Long) { /* 여러 쿼리 */ }
```

`@Relation`을 쓰는 조회에도 필요하다. Room이 여러 쿼리로 나눠 실행하기 때문이다.

### DAO는 데이터 레이어 밖으로 노출하지 않는다

```
ViewModel  →  Repository  →  DAO
                  ↑ 여기까지만 DAO를 안다
```

`chapter205`의 `WishRepository`가 이 경계를 잘 지키고 있다. `ViewModel`은 `WishDao`를 모르고 `WishRepository`만 안다. → [`049-android-repository-single-source-of-truth.md`](049-android-repository-single-source-of-truth.md)

### 테스트에서는 인메모리 DB

```kotlin
val db = Room.inMemoryDatabaseBuilder(context, WishDatabase::class.java)
    .allowMainThreadQueries()
    .build()
```

생성된 `_Impl`이 그대로 쓰이므로 **실제 SQL이 검증된다.** DAO를 목으로 대체하는 것보다 낫다.

## 체크리스트

- [ ] Room이 런타임 프록시가 아니라 컴파일 타임 코드 생성 방식임을 설명할 수 있다.
- [ ] `WishDao_Impl.kt`를 찾아 열어 볼 수 있다.
- [ ] `@Insert`가 어떤 SQL로 펼쳐지는지 확인할 수 있다.
- [ ] KSP와 KAPT의 차이를 알고 KSP를 쓰는 이유를 말할 수 있다.
- [ ] `ksp(...)` 의존성이 앱에 포함되지 않는 이유를 안다.
- [ ] 애노테이션 프로세서와 코틀린 컴파일러 플러그인의 차이를 설명할 수 있다.
- [ ] DAO와 Database가 `abstract`여야 하는 이유를 설명할 수 있다.
- [ ] `interface`와 `abstract class` 중 무엇을 쓸지 판단할 수 있다.
- [ ] 리플렉션이 쓰이는 지점이 한 번뿐이라는 것을 안다.
- [ ] 여러 쿼리를 묶을 때 `@Transaction`이 필요한 이유를 안다.

## 공식 참고 자료

- [Android Developers: Accessing data using Room DAOs](https://developer.android.com/training/data-storage/room/accessing-data)
- [Android Developers: Save data in a local database using Room](https://developer.android.com/training/data-storage/room)
- [Android Developers: Kotlin Symbol Processing (KSP)](https://developer.android.com/build/migrate-to-ksp)
- [Kotlin Docs: Kotlin Symbol Processing API](https://kotlinlang.org/docs/ksp-overview.html)
- [Android Developers: Defining relationships between objects](https://developer.android.com/training/data-storage/room/relationships)
- [Android Developers: Test your database](https://developer.android.com/training/data-storage/room/testing-db)
