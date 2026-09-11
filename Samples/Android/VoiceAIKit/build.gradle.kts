plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.tencent.voiceai.kit"
    compileSdk = 34

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
        // 让 Kotlin 接口的默认方法在字节码中生成真正的 Java default 方法，
        // 这样 Java 宿主实现 OnVoiceInputListener 时可以只覆写关心的方法
        freeCompilerArgs += "-Xjvm-default=all-compatibility"
    }

    buildFeatures {
        // 采用传统 View + XML 实现 UI（AIChatFragment / RealtimeChatFragment），
        // 不引入 Compose，保证第三方在纯 Java / 传统 View 工程中也能零成本接入。
        viewBinding = true
    }

    lint {
        // Kit 面向宿主集成，lint 由宿主统一执行
        abortOnError = false
    }
}

// LiteAVSDK VoiceAI 依赖来源开关，见 gradle.properties 的 useLocalVoiceAiSdk：
//   true  = 本地测试，用 libs/ 下的 aar（api 暴露，宿主仅依赖本模块即可获得 SDK 能力）
//   false = 对外发布，用 Maven 坐标
val useLocalVoiceAiSdk: Boolean = providers
    .gradleProperty("useLocalVoiceAiSdk")
    .map { it.toBoolean() }
    .getOrElse(true)

dependencies {
    if (useLocalVoiceAiSdk) {
        // 本地 aar：仓库根目录 libs/，经 settings.gradle.kts 的 flatDir 解析
        api(":LiteAVSDK_VoiceAI_13.6.0.237@aar")
    } else {
        implementation("com.tencent.liteav:LiteAVSDK_VoiceAI:13.6.0.237")
    }

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.annotation:annotation:1.8.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.fragment:fragment-ktx:1.8.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
}
