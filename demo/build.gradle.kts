
plugins {
    id("com.android.application")
    id("kotlin-android")
}

android {
    compileSdk = property("compileSdkVersion") as Int
    defaultConfig {
        applicationId = "com.otaliastudios.cameraview.demo"
        minSdk = property("minSdkVersion") as Int
        targetSdk = property("targetSdkVersion") as Int
        versionCode = 1
        versionName = "1.0"
        vectorDrawables.useSupportLibrary = true
    }
    sourceSets["main"].java.srcDir("src/main/kotlin")
    signingConfigs {
        create("release") {
            storeFile = file("/baiteLocate.jks")
            storePassword = "mao123456"
            keyAlias = "keyNew"
            keyPassword = "password"
        }
        getByName("debug") {
            storeFile = file("/baiteLocateDebug.jks")
            storePassword = "mao123456"
            keyAlias = "keyNewDebug"
            keyPassword = "mao123456"
        }
    }
    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isDebuggable = false
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
        }

        getByName("debug") {
//            isMinifyEnabled = false
//            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = false
            isDebuggable = true
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
        }
    }
    lint {
        baseline = file("lint-baseline.xml")
    }
}

dependencies {
    implementation(project(":cameraview"))
    implementation("androidx.appcompat:appcompat:1.3.1")
    implementation("com.google.android.material:material:1.4.0")
    implementation("com.amap.api:location:latest.integration")
}
