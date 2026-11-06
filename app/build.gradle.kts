import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    kotlin("kapt")
}
android {
    namespace = "app.aivora.vault"
    compileSdk = 35
    defaultConfig {
        applicationId = "app.aivora.vault"
        minSdk = 26
        targetSdk = 35
        versionCode = 11
        ndk { abiFilters += "arm64-v8a" }
        versionName = "0.1.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    // 【签名配置】读 keystore.properties（不入版本库，见 .gitignore）。
    // 文件不存在时该 signingConfig 为空配置，release 仍能构建但产物是 unsigned。
    // CI 上由 workflow 从 Secrets 还原该文件。
    signingConfigs {
        create("release") {
            val ksPropsFile = rootProject.file("keystore.properties")
            if (ksPropsFile.exists()) {
                val ksProps = Properties().apply {
                    ksPropsFile.inputStream().use { load(it) }
                }
                val sf = ksProps.getProperty("storeFile")
                if (sf != null) {
                    // 支持相对路径（相对工程根）与绝对路径
                    storeFile = rootProject.file(sf)
                    storePassword = ksProps.getProperty("storePassword")
                    keyAlias = ksProps.getProperty("keyAlias")
                    keyPassword = ksProps.getProperty("keyPassword")
                    enableV1Signing = false
                    enableV2Signing = true
                    enableV3Signing = true
                }
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // 只有真的读到了密钥才挂签名，否则保持 unsigned（不伪造临时签名）
            signingConfigs.findByName("release")?.let {
                if (it.storeFile != null) signingConfig = it
            }
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    testOptions {
        // 仅在真正传入了 -Dakcp.interop 时才透传给测试 JVM。
        // 不能直接写 it.systemProperty(name, System.getProperty(name))：
        // 值为 null 时 Gradle 会转成字符串 "null"，测试里 `?: return` 的判空失效，
        // 进而去 File("null") 下写文件，在 CI 上报 FileNotFoundException 而构建失败。
        unitTests.all {
            System.getProperty("akcp.interop")?.let { p -> it.systemProperty("akcp.interop", p) }
        }
    }
    buildFeatures { compose = true }
    packaging { resources.excludes += setOf("META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*", "org/bouncycastle/pqc/**", "org/bouncycastle/x509/CertPathReviewerMessages*.properties") }

    // 【产物命名】AGP 默认把 release 包命名为 app-release.apk，与历史发布版的
    // Aivora-vX.Y.Z.apk 不一致：CI 直接上传后，Release 上的文件名就变成了 app-release.apk。
    // 在这里统一定义命名规则，本地构建与 CI 走同一套，不再需要在 workflow 里手工改名。
    // 前缀 Aivora-v、后缀 versionName，例如 Aivora-v0.0.8.apk。
    applicationVariants.all {
        outputs.all {
            (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl).outputFileName =
                "Aivora-v${versionName}.apk"
        }
    }
}
dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    // 【v0.1.0·Lucide 官方图标】composables.composeicons 提供的 Lucide ImageVector 集合
    //   （1521 枚，MIT 许可）。选 1.1.0 而不是最新的 2.2.1：2.2.1 依赖 Kotlin 2.2.21，
    //   本工程是 Kotlin 2.0.21，直接拉会因元数据版本不兼容编译失败。
    //   1.1.0 依赖 Compose foundation 1.6.7（runtime scope），会被上面的 BOM 覆盖到 1.7.x。
    implementation("com.composables:icons-lucide-android:1.1.0")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.biometric:biometric:1.2.0-alpha05")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.bouncycastle:bcprov-jdk18on:1.79")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}