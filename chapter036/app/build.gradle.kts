// TODO: [todos/008-gradle-build-files-in-android-project.md](../../todos/008-gradle-build-files-in-android-project.md)
plugins {
    // TODO: [todos/009-android-application-plugin-role.md](../../todos/009-android-application-plugin-role.md)
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.chapter_036"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.chapter_036"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}