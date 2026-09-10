// TODO: [todos/android-module-main-function-run.md](../../todos/android-module-main-function-run.md)
plugins {
    kotlin("jvm") version "2.2.10"
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(24))
    }
}
