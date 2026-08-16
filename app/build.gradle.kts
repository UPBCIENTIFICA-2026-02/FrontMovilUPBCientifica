plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // Plugin de protobuf/gRPC DESACTIVADO por ahora: se reactiva cuando Dev D
    // entregue el .proto real de File Sync. Sin un .proto, este plugin hace
    // fallar el build (task extractIncludeDebugProto).
    // id("com.google.protobuf")
}

android {
    namespace = "com.upbcientifica.mobile"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.upbcientifica.mobile"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0-mvp"
    }
    buildFeatures { compose = true }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14" // debe hacer pareja con Kotlin 1.9.24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17" // el bytecode se compila a 17 aunque tu JDK local sea el 21
    }
}

dependencies {
    // UI - Jetpack Compose
    implementation("androidx.compose.ui:ui:1.6.7")
    implementation("androidx.compose.material3:material3:1.2.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.activity:activity-compose:1.9.0")

    // Corrutinas (todas las llamadas de red son suspend fun)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")

    // --- Authentication Service (SOAP) ---
    // Sin librería externa: el sobre SOAP se arma como XML y se envía con OkHttp
    // (mismo cliente HTTP que ya usamos para REST). Evita depender de ksoap2,
    // que está desactualizada y es difícil de resolver en Gradle.

    // --- Distributed Storage / Shared File / Streaming (HTTP/REST) ---
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // --- File Sync (gRPC) --- DESACTIVADO hasta tener el .proto de Dev D.
    // implementation("io.grpc:grpc-kotlin-stub:1.4.1")
    // implementation("io.grpc:grpc-okhttp:1.64.0")
    // implementation("io.grpc:grpc-protobuf-lite:1.64.0")

    // Almacenamiento seguro del token de sesión
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
}
