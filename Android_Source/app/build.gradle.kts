plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "jp.stackchan.pocket"
    compileSdk = 35
    ndkVersion = "28.2.13676358"
    defaultConfig {
        applicationId = "jp.stackchan.pocket"
        minSdk = 33
        targetSdk = 35
        versionCode = 56
        versionName = "0.3.50"
        ndk { abiFilters += "arm64-v8a" }
        externalNativeBuild { cmake { arguments += listOf("-DANDROID_STL=c++_shared", "-DCMAKE_BUILD_TYPE=Release", "-DPOCKET_VULKAN_ROOT=" + (providers.gradleProperty("pocketVulkanRoot").orNull ?: System.getenv("POCKET_VULKAN_ROOT") ?: "")) } }
    }
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1" } }
    signingConfigs { getByName("debug") {
        System.getenv("STACKCHAN_DEBUG_KEYSTORE")?.let { storeFile=file(it) }
    } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    packaging { jniLibs.useLegacyPackaging = true; resources.excludes += setOf("META-INF/DEPENDENCIES", "META-INF/INDEX.LIST") }
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

