# Room은 무엇인가 — JPA 같은 ORM인가

## 질문이 나온 코드

- [`chapter205/app/build.gradle.kts`](../chapter205/app/build.gradle.kts)
- 질문: Room은 뭔가? 아키텍처를 설명해 달라. 데이터베이스 엔진은 SQLite인데 JPA 같은 추상화된 API인가? 이 라이브러리를 쓰면 내부적으로 컨텍스트 캐싱이나 영속성 컨텍스트 같은 기능도 제공하나? 하이버네이트처럼? 특장점과 제약·단점도 함께 정리해 달라.

## 질문 전제 점검

- **"JPA 같은 추상화된 API인가"** → **비슷해 보이지만 계층이 다르다.** JPA는 "객체와 관계형 DB를 매핑하는 명세"이고 하이버네이트가 그 구현체다. Room은 명세가 아니라 **SQLite 한 종류만을 대상으로 하는 컴파일 타임 코드 생성기**다.

  > "The Room persistence library provides an abstraction layer over SQLite to allow fluent database access while harnessing the full power of SQLite."

  결정적 차이는 **SQL을 누가 쓰느냐**다. JPA/하이버네이트는 JPQL이나 메서드 이름으로 SQL을 **대신 만들어 준다**. Room은 그러지 않는다. `@Query("select * from \`wish-table\`")`처럼 **SQL을 개발자가 직접 쓴다.** Room이 하는 일은 그 SQL을 컴파일 시점에 검증하고, 결과를 객체로 옮겨 담는 코드를 생성하는 것이다.

  ```
  하이버네이트:  객체 조작 → 프레임워크가 SQL 생성 → 실행
  Room:         개발자가 SQL 작성 → 컴파일 시 검증 → 매핑 코드 생성
  ```

  그래서 Room을 **ORM이라기보다 "타입 안전한 SQL 매퍼"**로 보는 편이 정확하다. 자바 진영에서 굳이 비교하자면 하이버네이트보다 **MyBatis나 jOOQ**에 가깝다.

- **"영속성 컨텍스트나 컨텍스트 캐싱 같은 기능도 제공하나?"** → **제공하지 않는다.** 이것이 하이버네이트와의 가장 큰 차이다. Room에는 다음이 **전부 없다.**

  | 하이버네이트/JPA 기능 | Room |
  | --- | --- |
  | 영속성 컨텍스트(1차 캐시) | ❌ 없음 |
  | 더티 체킹(변경 감지 자동 UPDATE) | ❌ 없음. `@Update`를 직접 호출 |
  | 지연 로딩(lazy loading) | ❌ 없음 |
  | 쓰기 지연(write-behind) | ❌ 없음. 호출 즉시 실행 |
  | 동일성 보장(같은 ID = 같은 인스턴스) | ❌ 없음. 조회할 때마다 새 객체 |
  | 연관관계 매핑(`@OneToMany` 등) | ❌ 없음. `@Relation`으로 조회만 |
  | 2차 캐시 | ❌ 없음 |

  Room의 `@Entity`는 하이버네이트의 `@Entity`와 **이름만 같고 의미가 다르다.** Room에서는 그냥 "이 데이터 클래스가 이 테이블의 한 행에 대응한다"는 표시일 뿐, 생명주기를 관리받는 객체가 아니다. `wish.title = "새 제목"`으로 바꿔도 아무 일도 일어나지 않는다.

- **"제약이나 단점"** → 위 목록 자체가 제약이다. 다만 **그게 설계 의도**다. 모바일은 서버와 상황이 다르다. 영속성 컨텍스트는 메모리를 쓰고, 지연 로딩은 예측 못 할 시점에 쿼리를 날린다. 배터리와 메모리가 제한된 환경에서 **"언제 무슨 쿼리가 나가는지 코드에 다 보이는 것"**이 더 안전하다. Room은 그 쪽을 택했다.

## 공부할 내용

### 세 가지 구성 요소

Room은 딱 세 조각으로 이루어진다. `chapter205`의 코드가 그 셋을 그대로 보여 준다.

```
┌─────────────────────────────────────────────┐
│ @Database  WishDatabase : RoomDatabase()     │  ① 진입점. DAO를 꺼내 준다
│    └─ abstract fun wishDao(): WishDao        │
├─────────────────────────────────────────────┤
│ @Dao       WishDao                           │  ② 쿼리 정의
│    @Query / @Insert / @Update / @Delete      │
├─────────────────────────────────────────────┤
│ @Entity    Wish                              │  ③ 테이블 한 행
│    @PrimaryKey / @ColumnInfo                 │
└─────────────────────────────────────────────┘
```

#### ① Database — 진입점

```kotlin
@Database(
    entities = [Wish::class],
    version = 1,
    exportSchema = false
)
abstract class WishDatabase : RoomDatabase() {
    abstract fun wishDao(): WishDao
}
```

`abstract class`인 이유는 **Room이 컴파일 시점에 `WishDatabase_Impl`을 생성**하기 때문이다. 우리가 구현할 것이 없다. → [`android-room-dao-code-generation.md`](android-room-dao-code-generation.md)

`exportSchema = false`는 스키마 JSON을 내보내지 않는다는 뜻이다. **실제 앱에서는 `true`로 두고 스키마 파일을 버전 관리에 넣는 것이 권장된다.** 마이그레이션 테스트에 필요하기 때문이다. 학습 예제라 꺼 둔 것이다.

#### ② Entity — 테이블 한 행

```kotlin
@Entity(tableName = "wish-table")
data class Wish(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "title") val title: String = "",
    @ColumnInfo(name = "description") val description: String = ""
)
```

`val`로 선언된 **불변 데이터 클래스**라는 점을 눈여겨볼 만하다. 하이버네이트 엔티티는 더티 체킹을 위해 가변이어야 하지만, Room은 더티 체킹이 없으므로 불변이 자연스럽다. 값을 바꾸려면 `copy()`로 새 객체를 만들어 `@Update`에 넘긴다.

테이블 이름에 하이픈(`wish-table`)이 들어가서 쿼리에서 백틱이 필요해졌다.

```kotlin
@Query("select * from `wish-table`")   // 백틱 없으면 SQL 문법 에러
```

**테이블 이름은 `wish_table`처럼 밑줄을 쓰는 편이 낫다.** SQL 식별자 규칙에 맞고 백틱을 잊을 일도 없다.

#### ③ DAO — 쿼리

```kotlin
@Dao
abstract class WishDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract fun addWish(wish: Wish)

    @Query("select * from `wish-table`")
    abstract fun getAll(): Flow<List<Wish>>
    // ...
}
```

`@Insert`, `@Update`, `@Delete`는 SQL을 쓰지 않아도 된다. 엔티티의 기본 키를 보고 Room이 문장을 만든다. 나머지는 전부 `@Query`로 직접 쓴다.

### 컴파일 타임 검증 — Room의 핵심 가치

> "Room validates SQL queries at compile time. This means that if there's a problem with your query, a compilation error occurs instead of a runtime failure."

오타 하나를 내면 앱을 실행하기도 전에 빌드가 실패한다.

```kotlin
@Query("select * from `wish-tabel`")     // 오타
// error: There is a problem with the query:
//   [SQLITE_ERROR] no such table: wish-tabel
```

컬럼 이름, 반환 타입 불일치까지 잡아 준다.

```kotlin
@Query("select title from `wish-table`")
abstract fun getTitles(): Flow<List<Wish>>
// error: Not sure how to convert a Cursor to this method's return type
```

**이것이 Room을 쓰는 가장 큰 이유다.** SQLite API를 직접 쓰면 이 모든 것이 런타임 크래시가 된다.

> "We recommend using Room instead of using the SQLite APIs directly."

### 관찰 가능한 쿼리 — 실질적인 킬러 기능

```kotlin
@Query("select * from `wish-table`")
abstract fun getAll(): Flow<List<Wish>>
```

반환 타입을 `Flow`로 두면 **테이블이 바뀔 때마다 Room이 알아서 다시 조회해 새 값을 흘려보낸다.** `chapter205`에서 항목을 삭제하면 목록이 저절로 갱신되는 이유가 이것이다. 아무도 "다시 조회해"라고 말하지 않는다.

```
deleteWish() 호출
   → wish-table 변경
   → Room의 InvalidationTracker 가 감지
   → getAll() 쿼리 재실행
   → Flow 가 새 List<Wish> emit
   → collectAsState 가 State 갱신
   → LazyColumn 재구성
```

하이버네이트에는 이런 것이 없다. **모바일 UI에 특화된 설계**다. → [`kotlin-flow-concepts-and-suspend.md`](kotlin-flow-concepts-and-suspend.md)

### 데이터베이스 만들기

```kotlin
// Graph.kt
database = Room.databaseBuilder(context, WishDatabase::class.java, "wishlist.db").build()
```

**`RoomDatabase` 인스턴스는 비싸다.** 앱 전체에서 하나만 만들어 재사용해야 한다. `chapter205`가 `object Graph`에 담아 둔 이유가 이것이다. → [`android-manual-di-graph-object.md`](android-manual-di-graph-object.md)

## 관련 아키텍처와 베스트 프랙티스

### 장점과 단점 정리

**장점**

| | 내용 |
| --- | --- |
| 컴파일 타임 SQL 검증 | 오타·타입 불일치를 빌드에서 잡는다 |
| 보일러플레이트 제거 | `Cursor` 순회, `ContentValues` 조립이 사라진다 |
| 관찰 가능한 쿼리 | `Flow`/`LiveData`로 UI 자동 갱신 |
| 코루틴 통합 | `suspend` 지원, main-safety 보장 |
| 마이그레이션 지원 | `Migration` 클래스, 자동 마이그레이션 |
| 예측 가능성 | 언제 무슨 SQL이 나가는지 코드에 다 보인다 |

**단점·제약**

| | 내용 | 대응 |
| --- | --- | --- |
| SQL을 직접 써야 한다 | SQLite 문법을 알아야 한다 | 학습 비용으로 감수 |
| 연관관계 자동 매핑 없음 | JOIN을 직접 쓰거나 `@Relation` | 명시적이라 오히려 낫다 |
| SQLite 전용 | 다른 DB로 못 바꾼다 | 안드로이드에선 문제없음 |
| 캐시·동일성 보장 없음 | 조회마다 새 객체 | `Flow`로 단일 소스 유지 |
| 빌드 시간 증가 | KSP 코드 생성 | KAPT 대신 KSP 사용 |
| 스키마 변경 시 마이그레이션 필수 | 안 하면 크래시 | `exportSchema = true` + 테스트 |

### 마이그레이션은 반드시 준비한다

`version = 1`에서 엔티티를 바꾸고 `version = 2`로 올리면, 마이그레이션이 없을 때 앱이 크래시한다.

```kotlin
Room.databaseBuilder(context, WishDatabase::class.java, "wishlist.db")
    .addMigrations(MIGRATION_1_2)             // 직접 작성
    .build()

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `wish-table` ADD COLUMN priority INTEGER NOT NULL DEFAULT 0")
    }
}
```

학습 중에 흔히 쓰는 `fallbackToDestructiveMigration()`은 **데이터를 전부 지우고 다시 만든다.** 개발 중에는 편하지만 운영 앱에 넣으면 사용자 데이터가 날아간다.

### DAO 메서드를 `suspend`로 만든다

`chapter205`의 DAO는 `suspend`가 없다.

```kotlin
@Insert abstract fun addWish(wish: Wish)        // suspend 없음
```

그래서 **메인 스레드에서 호출하면 앱이 죽는다.** Room이 막아 주기 때문이다(`IllegalStateException: Cannot access database on the main thread`). 지금은 `ViewModel`이 `Dispatchers.IO`로 감싸서 피하고 있다.

```kotlin
viewModelScope.launch(Dispatchers.IO) { wishRepository.addWish(wish) }
```

**DAO에 `suspend`를 붙이면 그 배려가 필요 없어진다.** Room이 알아서 자기 스레드 풀에서 실행한다.

```kotlin
@Insert abstract suspend fun addWish(wish: Wish)
// → viewModelScope.launch { repository.addWish(wish) }  로 충분
```

→ [`kotlin-coroutine-dispatchers.md`](kotlin-coroutine-dispatchers.md)

### 데이터 레이어 안에 가둔다

```
UI → ViewModel → Repository → DAO → Room → SQLite
                     ↑
             여기까지만 Room을 안다
```

`Wish` 엔티티가 UI까지 그대로 올라오는 것은 학습 예제에서는 괜찮지만, 규모가 커지면 DB 스키마 변경이 화면 코드까지 흔든다. → [`android-repository-single-source-of-truth.md`](android-repository-single-source-of-truth.md)

## 체크리스트

- [ ] Room이 SQLite 위의 추상화 계층임을 설명할 수 있다.
- [ ] Room과 JPA/하이버네이트의 계층 차이를 설명할 수 있다.
- [ ] Room에 영속성 컨텍스트·더티 체킹·지연 로딩이 없다는 것을 안다.
- [ ] Room을 ORM이 아니라 SQL 매퍼로 보는 이유를 설명할 수 있다.
- [ ] `@Database`, `@Entity`, `@Dao` 세 구성 요소의 역할을 말할 수 있다.
- [ ] Room 엔티티를 불변 데이터 클래스로 두는 것이 자연스러운 이유를 안다.
- [ ] 컴파일 타임 SQL 검증이 무엇을 잡아 주는지 예를 들 수 있다.
- [ ] `Flow` 반환 타입이 어떻게 UI 자동 갱신으로 이어지는지 설명할 수 있다.
- [ ] `RoomDatabase` 인스턴스를 하나만 만들어야 하는 이유를 안다.
- [ ] 마이그레이션 없이 버전을 올리면 무슨 일이 생기는지 안다.
- [ ] DAO 메서드에 `suspend`를 붙이면 무엇이 좋아지는지 설명할 수 있다.

## 공식 참고 자료

- [Android Developers: Save data in a local database using Room](https://developer.android.com/training/data-storage/room)
- [Android Developers: Accessing data using Room DAOs](https://developer.android.com/training/data-storage/room/accessing-data)
- [Android Developers: Defining data using Room entities](https://developer.android.com/training/data-storage/room/defining-data)
- [Android Developers: Write asynchronous DAO queries](https://developer.android.com/training/data-storage/room/async-queries)
- [Android Developers: Migrate your Room database](https://developer.android.com/training/data-storage/room/migrating-db-versions)
- [Android Developers: Data layer](https://developer.android.com/topic/architecture/data-layer)
- [SQLite: Query language](https://www.sqlite.org/lang.html)
