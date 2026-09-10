import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "io.github.bileizhen.leifetch"
    compileSdk = 37
    defaultConfig {
        applicationId = "io.github.bileizhen.leifetch"
        minSdk = 23
        targetSdk = 36
        versionCode = 2
        versionName = "0.1.0-beta.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    val keystoreFile = rootProject.file("leifetch-release.jks")
    val keystoreProperties = Properties().apply {
        val f = rootProject.file("keystore.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }
    signingConfigs {
        if (keystoreFile.exists() && keystoreProperties.isNotEmpty()) create("release") {
            storeFile = keystoreFile
            storePassword = keystoreProperties.getProperty("storePassword")
            keyAlias = keystoreProperties.getProperty("keyAlias")
            keyPassword = keystoreProperties.getProperty("keyPassword")
        }
    }
    buildTypes {
        release {
            if (keystoreFile.exists() && keystoreProperties.isNotEmpty()) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = false
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
    packaging { resources.excludes += setOf("META-INF/LICENSE*", "META-INF/NOTICE*") }
}
dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    implementation("androidx.activity:activity-compose:1.12.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation("androidx.documentfile:documentfile:1.1.0")
    implementation("androidx.compose.material:material-icons-core:1.7.8")
    implementation("top.yukonga.miuix.kmp:miuix-ui-android:0.9.3")
    implementation("top.yukonga.miuix.kmp:miuix-preference-android:0.9.3")
    implementation("top.yukonga.miuix.kmp:miuix-blur-android:0.9.3")
    implementation("androidx.navigation3:navigation3-runtime:1.1.4")
    // Provides NavDisplay in the androidx namespace, including Miuix predictive-back transitions.
    implementation("top.yukonga.miuix.kmp:miuix-navigation3-ui-android:0.9.3")
    implementation("androidx.navigationevent:navigationevent-compose:1.1.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.crossbowffs.remotepreferences:remotepreferences:0.8")
    compileOnly("de.robv.android.xposed:api:82")
    // libxposed API 101 入口：让 LSPosed 投递服务 binder（作用域申请）。
    compileOnly("io.github.libxposed:api:101.0.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}
