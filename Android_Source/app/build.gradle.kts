plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "jp.stackchan.pocket"
    compileSdk = 35
    defaultConfig {
        applicationId = "jp.stackchan.pocket"
        minSdk = 33
        targetSdk = 35
        versionCode = 51
        versionName = "0.3.45"
        ndk { abiFilters += "arm64-v8a" }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    packaging { resources.excludes += setOf("META-INF/DEPENDENCIES", "META-INF/INDEX.LIST") }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies {
    implementation("com.github.mik3y:usb-serial-for-android:3.11.0")
    implementation(files("libs/sherpa-onnx-1.13.8.aar"))
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.17.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}

