# `AndroidManifest.xml`의 용도

## 질문이 나온 코드

- [`chapter078/app/src/main/AndroidManifest.xml`](../chapter078/app/src/main/AndroidManifest.xml)
- 질문: 안드로이드 매니페스트 XML 파일은 어떤 용도이며 무엇을 위해 사용하는가?

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
