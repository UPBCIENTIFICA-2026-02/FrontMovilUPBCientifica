package com.upbcientifica.mobile.network

/**
 * File Sync Service habla gRPC (Contrato_FileSync: streaming bidireccional).
 *
 * DESACTIVADO por ahora: las dependencias de gRPC y el plugin de protobuf
 * están comentados en build.gradle.kts hasta que Dev D entregue el .proto real.
 * Cuando llegue:
 *   1. Descomentar las 3 dependencias grpc-* y el plugin protobuf en build.gradle.kts.
 *   2. Poner el .proto en app/src/main/proto/.
 *   3. Restaurar aquí la construcción del ManagedChannel con OkHttpChannelBuilder.
 */
object GrpcClient {
    // Placeholder — sin dependencias de gRPC activas todavía.
}
