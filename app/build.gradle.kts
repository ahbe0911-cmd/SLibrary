import org.jetbrains.kotlin.gradle.dsl.JvmTarget
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
val store = System.getenv("ANDROID_KEYSTORE_PATH")
val pass = System.getenv("ANDROID_KEYSTORE_PASSWORD")
val alias = System.getenv("ANDROID_KEY_ALIAS")
val keyPass = System.getenv("ANDROID_KEY_PASSWORD")
val signed = listOf(store, pass, alias, keyPass).all { !it.isNullOrBlank() }
android {
    namespace = "ir.library123057.app"
    compileSdk = 34
    buildToolsVersion = "34.0.0"
    defaultConfig {
        applicationId = "ir.library123057.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 10
        versionName = "1.6.0"
        buildConfigField("String", "WEBSITE_URL", "\"https://123057.samanpl.ir/\"")
    }
    signingConfigs {
        if (signed) create("release") {
            storeFile = file(requireNotNull(store))
            storePassword = pass
            keyAlias = alias
            keyPassword = keyPass
            storeType = "JKS"
        }
    }
    buildTypes {
        debug { isMinifyEnabled = false }
        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            if (signed) signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    buildFeatures { viewBinding = true; buildConfig = true }
}
kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.activity:activity-ktx:1.9.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.webkit:webkit:1.11.0")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
}