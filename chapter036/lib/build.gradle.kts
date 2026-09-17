// TODO: [todos/011-android-module-main-function-run.md](../../todos/011-android-module-main-function-run.md)
plugins {
    kotlin("jvm") version "2.2.10"
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(24))
    }
}
