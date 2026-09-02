plugins {
    alias(libs.plugins.android.application)
}

val logDebug = providers.gradleProperty("log-debug").map { it.toBoolean() }.orElse(false)
val gameOver = providers.gradleProperty("game-over").map { it.toBoolean() }.orElse(false)

android {
    namespace = "shapes"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "shapes.game"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("Boolean", "LOG_DEBUG", logDebug.get().toString())
        buildConfigField("Boolean", "GAME_OVER", gameOver.get().toString())
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

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.datastore)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)

    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.13.0")
}
