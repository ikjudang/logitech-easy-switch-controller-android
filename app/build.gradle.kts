plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.easyswitchcontroller"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.easyswitchcontroller"
        minSdk = 31
        targetSdk = 35
        versionCode = 34
        versionName = "0.3.4-exp56"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // 배포용 고정 서명 키. 키 파일은 저장소에 두지 않고 환경 변수로만 받는다(CI는 GitHub Secrets).
    // 변수가 없으면 release는 서명 없이 빌드된다 — 로컬 개발은 debug 빌드를 쓴다.
    val releaseKeystore = System.getenv("RELEASE_KEYSTORE_FILE")?.let { file(it) }
    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        // 입력 검증 전용 설치본. 공개 앱 및 기기에 설치된 실험판과 패키지를 분리한다.
        create("inputProbe") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".inputprobe"
            matchingFallbacks += listOf("debug")
        }
        release {
            if (releaseKeystore != null) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    // 실기 렌더러 검증은 본 앱과 분리된 inputProbe 패키지에서도 실행할 수 있다.
    testBuildType = providers.gradleProperty("instrumentedBuildType").getOrElse("debug")

    testOptions {
        unitTests {
            // android.util.Log 등 android.* 스텁이 예외 대신 기본값을 반환하게 한다(순수 로직 테스트용).
            isReturnDefaultValues = true
        }
    }
}

androidComponents {
    beforeVariants(selector().withBuildType("inputProbe")) { variant ->
        variant.hostTests.getValue(com.android.build.api.variant.HostTestBuilder.UNIT_TEST_TYPE).enable = true
    }
    onVariants(selector().withBuildType("inputProbe")) { variant ->
        variant.outputs.forEach { output -> output.versionName.set("0.3.4-exp56") }
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))

    debugImplementation(libs.androidx.ui.tooling)
}
