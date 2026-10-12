plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // Genera el cliente gRPC de File Sync desde src/main/proto/filesync.proto
    // (copia del contrato de node-sync).
    id("com.google.protobuf")
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
    // Iconos de tipos de archivo (PDF, hoja de cálculo, video...). R8 elimina los no usados.
    implementation("androidx.compose.material:material-icons-extended:1.6.7")
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

    // --- Reproductor de video dentro de la app (Streaming y Mis archivos) ---
    // ExoPlayer pide el video por rangos (Range), igual que el <video> del
    // portal, y permite mandar la cabecera Authorization en cada petición.
    // 1.3.1 es la última que compila con compileSdk 34.
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-ui:1.3.1")

    // --- File Sync (gRPC con node-sync) ---
    // okhttp es el transporte recomendado en Android; "lite" es la variante de
    // protobuf para móviles (sin reflexión, mucho más pequeña).
    implementation("io.grpc:grpc-okhttp:1.64.0")
    implementation("io.grpc:grpc-protobuf-lite:1.64.0")
    implementation("io.grpc:grpc-stub:1.64.0")
    implementation("io.grpc:grpc-kotlin-stub:1.4.1")
    implementation("com.google.protobuf:protobuf-javalite:3.25.3")
    // Sincronización en segundo plano: al llegar al Wi-Fi y en horario programado.
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    // Recorrer la carpeta del teléfono elegida con el selector del sistema (SAF).
    implementation("androidx.documentfile:documentfile:1.0.1")

    // Almacenamiento seguro del token de sesión
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
}

protobuf {
    protoc { artifact = "com.google.protobuf:protoc:3.25.3" }
    plugins {
        create("grpc") { artifact = "io.grpc:protoc-gen-grpc-java:1.64.0" }
        create("grpckt") { artifact = "io.grpc:protoc-gen-grpc-kotlin:1.4.1:jdk8@jar" }
    }
    generateProtoTasks {
        all().forEach { tarea ->
            tarea.builtins { create("java") { option("lite") } }
            tarea.plugins {
                create("grpc") { option("lite") }
                create("grpckt") { option("lite") }
            }
        }
    }
}
