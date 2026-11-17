// AGP 9 compiles Kotlin by itself (built-in Kotlin); only the Compose compiler plugin is added.
plugins {
    id("com.android.application") version "9.2.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.10" apply false
}
