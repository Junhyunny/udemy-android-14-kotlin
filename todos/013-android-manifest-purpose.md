# `AndroidManifest.xml`의 용도

## 질문이 나온 코드

- [`chapter078/app/src/main/AndroidManifest.xml`](../chapter078/app/src/main/AndroidManifest.xml)
- 질문: 안드로이드 매니페스트 XML 파일은 어떤 용도이며 무엇을 위해 사용하는가?

## 질문 전제 점검

질문에 잘못된 전제는 없다. 다만 "설정 파일" 정도로 뭉뚱그려 이해하면 놓치는 지점이 있어 한 가지를 강조해 둔다.

매니페스트는 앱 내부 설정이 아니라 **앱이 시스템과 스토어에 제시하는 공개 선언서**다. 여기에 적힌 내용은 앱 코드가 한 줄도 실행되기 전에 시스템이 읽는다. 그래서 "코드로 대신할 수 있는 설정"이 아니라, 코드보다 먼저 존재해야 하는 계약이다.

## 공부할 내용

`AndroidManifest.xml`은 앱 소스 세트 루트에 반드시 있어야 하는 파일이다. 공식 문서는 "모든 앱 프로젝트는 정확히 그 이름의 `AndroidManifest.xml` 파일을 소스 세트 루트에 가져야 하며, 매니페스트 파일은 안드로이드 빌드 도구, 안드로이드 운영체제, 구글 플레이에 앱의 핵심 정보를 설명한다"라고 정의한다.

핵심은 앱 코드가 실행되기 *전에* 시스템이 읽는 선언서라는 점이다. 안드로이드 앱에는 `main()` 같은 단일 진입점이 없고 시스템이 구성요소를 직접 시작하므로, 시스템은 무엇을 시작할 수 있는지 매니페스트로 미리 알아야 한다.

### 매니페스트가 선언하는 것

- **앱 구성요소**: `<activity>`, `<service>`, `<receiver>`, `<provider>`. "이 구성요소를 상속하고도 매니페스트에 선언하지 않으면 시스템이 그것을 시작할 수 없다."
- **인텐트 필터**: 어떤 인텐트에 반응할지 정의한다. `MAIN` 액션과 `LAUNCHER` 카테고리를 가진 액티비티가 런처에서 진입하는 시작 액티비티가 된다.
- **권한**: `<uses-permission>`으로 앱이 필요로 하는 권한을 선언한다.
- **하드웨어·소프트웨어 요구사항**: `<uses-feature>` 등으로 선언하며, 구글 플레이에서 설치 가능한 기기를 거르는 기준이 된다.
- **앱 수준 속성**: 아이콘, 라벨, 테마, 백업 정책 같은 `<application>` 속성.

### `chapter078` 매니페스트 읽기

```xml
<application
    android:icon="@mipmap/ic_launcher"
    android:label="@string/app_name"
    android:theme="@style/Theme.Chapter078">
    <activity
        android:name=".MainActivity"
        android:exported="true"
        android:windowSoftInputMode="adjustResize">
        <intent-filter>
            <action android:name="android.intent.action.MAIN" />
            <category android:name="android.intent.category.LAUNCHER" />
        </intent-filter>
    </activity>
</application>
```

- `android:name=".MainActivity"`: `namespace`를 기준으로 한 상대 클래스 이름이다.
- `android:exported="true"`: 다른 앱이나 시스템이 이 액티비티를 시작할 수 있다는 의미다. 런처에서 실행하려면 필요하다.
- `<intent-filter>`의 `MAIN` + `LAUNCHER` 조합이 이 액티비티를 앱의 시작 화면으로 지정한다.
- `android:windowSoftInputMode="adjustResize"`: 소프트 키보드가 올라올 때 창을 줄여 입력 필드가 가려지지 않게 한다.

AGP는 빌드 시 라이브러리 모듈과 빌드 변형의 매니페스트를 하나로 병합하므로, 최종 APK의 매니페스트는 이 파일과 완전히 같지 않을 수 있다.

## 관련 아키텍처와 베스트 프랙티스

### 매니페스트를 인터페이스로 보기

매니페스트에 `<intent-filter>`와 함께 선언한 컴포넌트는 사실상 **다른 앱과 시스템에 공개하는 API**다. 한번 공개하면 다른 앱이 그 진입점에 의존할 수 있고, 잘못 열어두면 보안 문제가 된다. 그래서 다음 원칙이 따라온다.

- 외부에서 시작할 이유가 없는 컴포넌트는 `android:exported="false"`로 닫는다. 안드로이드 12(API 31)부터는 인텐트 필터가 있는 컴포넌트에 `exported` 명시가 필수다.
- 권한은 실제로 필요한 것만 선언한다. 선언한 권한은 스토어 노출과 설치 필터에 영향을 준다.
- 카메라 같은 하드웨어 기능은 `<uses-feature android:required="false">`로 선택 사항임을 밝혀 설치 가능한 기기를 좁히지 않도록 한다.

### 매니페스트 병합을 이해하기

최종 APK의 매니페스트는 이 파일 하나가 아니다. AGP는 앱 모듈, 라이브러리 모듈, 빌드 변형의 매니페스트를 우선순위에 따라 병합한다. 실무에서 자주 겪는 상황이 여기서 나온다.

- 추가한 적 없는 권한이 들어 있다 → 라이브러리가 선언한 것이다.
- 속성이 충돌해 빌드가 실패한다 → `tools:replace`, `tools:remove` 같은 병합 규칙 마커로 해결한다.
- 최종 결과가 궁금하다 → 안드로이드 스튜디오의 Merged Manifest 뷰에서 확인한다.

`chapter078` 매니페스트 루트에 선언된 `xmlns:tools`가 바로 이 병합 규칙을 쓰기 위한 네임스페이스다.

### 하드코딩하지 않기

`android:label="@string/app_name"`처럼 사용자에게 보이는 문자열은 리소스로 참조한다. 지역화와 구성별 대체 리소스가 이 참조 위에서 동작하기 때문이다. 아이콘, 테마도 마찬가지다.

### 코드와 매니페스트의 역할 분담

정적으로 고정된 계약(어떤 컴포넌트가 있는지, 어떤 권한이 필요한지)은 매니페스트에, 실행 중 달라지는 결정(권한을 실제로 요청할지, 어떤 화면으로 보낼지)은 코드에 둔다. 런타임 권한 요청이 대표적인 예로, 선언은 매니페스트에 두고 요청은 코드에서 한다.

## 체크리스트

- [ ] 매니페스트가 앱 코드보다 먼저 읽히는 이유를 설명할 수 있다.
- [ ] 매니페스트에 선언하지 않은 액티비티를 시스템이 시작할 수 없는 이유를 설명할 수 있다.
- [ ] `MAIN` 액션과 `LAUNCHER` 카테고리의 역할을 설명할 수 있다.
- [ ] `android:exported`가 필요한 상황을 설명할 수 있다.
- [ ] 매니페스트 병합이 무엇인지 설명할 수 있다.

## 공식 참고 자료

- [Android Developers: App manifest overview](https://developer.android.com/guide/topics/manifest/manifest-intro)
- [Android Developers: Application fundamentals](https://developer.android.com/guide/components/fundamentals)
- [Android Developers: Merge multiple manifest files](https://developer.android.com/build/manage-manifests)
- [Android Developers: `<activity>` element](https://developer.android.com/guide/topics/manifest/activity-element)
- [Android Developers: Merge multiple manifest files](https://developer.android.com/build/manage-manifests)
- [Android Developers: Permissions on Android](https://developer.android.com/guide/topics/permissions/overview)
- [Android Developers: App resources overview](https://developer.android.com/guide/topics/resources/providing-resources)
- [Android Developers: App security best practices](https://developer.android.com/privacy-and-security/security-best-practices)
