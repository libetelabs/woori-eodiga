import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Pleos My Project 키(음성 서버 방식). 레포에 올리지 않는 local.properties 에서 읽는다
val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

android {
    namespace = "com.woorieodiga.app"
    compileSdk = 35
    // Unity 에 딸린 SDK 에 설치된 버전(라이선스 동의 없이 쓰도록)
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.woorieodiga.app"
        minSdk = 31
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "PLEOS_CLIENT_ID", "\"${localProps.getProperty("pleos.clientId", "")}\"")
        buildConfigField("String", "PLEOS_CLIENT_SECRET", "\"${localProps.getProperty("pleos.clientSecret", "")}\"")
    }

    // AAOS CarPropertyManager (기어 P/D)
    useLibrary("android.car")

    // release 서명 키도 local.properties 에서(키 파일은 레포 밖). 없으면 서명 없이 만든다
    signingConfigs {
        localProps.getProperty("release.storeFile")?.let { path ->
            create("release") {
                storeFile = file(path)
                storePassword = localProps.getProperty("release.storePassword")
                keyAlias = localProps.getProperty("release.keyAlias")
                keyPassword = localProps.getProperty("release.keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
            // 레포에 올리는 APK 라 키는 넣지 않는다(서버 음성은 아직 꺼져 있어 기기 안 합성만 쓴다)
            buildConfigField("String", "PLEOS_CLIENT_ID", "\"\"")
            buildConfigField("String", "PLEOS_CLIENT_SECRET", "\"\"")
        }
    }
    buildFeatures { buildConfig = true }
    // AGP 8.7 lint 가 새 SDK 도구 버전("37.0")을 못 읽고 죽는다. release 빌드 때의 lint 만 끈다
    lint { checkReleaseBuilds = false }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("ai.pleos.playground:NaviHelper:3.0.5")
    implementation("ai.pleos.playground:TextToSpeech:3.3.1")
    implementation("androidx.core:core-ktx:1.13.1")
    testImplementation("junit:junit:4.13.2")
}
