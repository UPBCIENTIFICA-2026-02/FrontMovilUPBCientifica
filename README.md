# Cliente Móvil — UPB-CIENTÍFICA

Aplicación Android (Kotlin + Jetpack Compose) que sirve como cliente móvil del
sistema distribuido **UPB-CIENTÍFICA**: una plataforma tipo "Google Drive" con
servicios de autenticación, almacenamiento, compartición, sincronización,
streaming y cómputo de alto rendimiento (HPC), construida sobre una
arquitectura orientada a servicios (SOA).

---

## Tabla de contenido

1. Descripción y decisiones de arquitectura
2. Requisitos previos
3. Configuración del entorno (paso a paso, Windows)
4. Ejecutar la aplicación
5. Levantar el mock del Authentication Service
6. Estructura del proyecto
7. Estado de cada servicio
8. Notas para los desarrolladores de los servidores
9. Pendientes del equipo
10. Solución de problemas frecuentes

---

## 1. Descripción y decisiones de arquitectura

Este cliente se conecta **directamente** a cada servicio usando su protocolo
nativo, sin pasar por un gateway intermedio (el gateway del diagrama de
topología es únicamente para el cliente Web, porque un navegador no puede hablar
SOAP/RMI/gRPC nativamente).

| Servicio             | Protocolo   | Estado en el cliente          |
|----------------------|-------------|-------------------------------|
| Authentication       | SOAP        | Implementado (sobre OkHttp)   |
| Distributed Storage  | HTTP/REST   | Implementado                  |
| Shared File          | HTTP/REST   | Implementado                  |
| Streaming            | HTTP/REST   | Implementado                  |
| File Sync            | gRPC        | Pendiente (falta .proto)      |
| HPC Service          | RMI         | Bloqueado (ver sección 9)     |

**Por qué Kotlin nativo y no React Native / Flutter:** al ser un sistema
distribuido SOA, el móvil debe hablar los protocolos reales (SOAP, gRPC, etc.).
Kotlin corre sobre la JVM, que tiene librerías maduras para esos protocolos —
algo que JavaScript (React Native) no soporta bien.

**Manejo de estado:** StateFlow + ViewModel (arquitectura estándar de Jetpack),
sin librerías externas. Cada pantalla observa un `UiState` (Idle / Loading /
Success / Error).

**Arquitectura interna por capas** (misma filosofía que el backend):

```
ui/       -> Presentación (Compose + ViewModel, expone UiState)
data/     -> Un paquete por servicio; cada uno habla SU protocolo y expone
             una interfaz simple hacia arriba (el ViewModel no sabe si algo
             es SOAP o REST)
network/  -> Clientes de bajo nivel compartidos (SoapClient, RestClient)
session/  -> TokenManager: guarda el token de sesión (cifrado) y lo inyecta
             en cada llamada a los demás servicios
```

---

## 2. Requisitos previos

- **JDK 17 o superior** (probado con JDK 21).
- **Android SDK** (viene con Android Studio; no hace falta usar el IDE).
- **Node.js** (solo para correr el mock del Authentication Service).
- **VSCode** con las extensiones:
  - `Kotlin Language` (fwcd.kotlin)
  - `Gradle for Java` (vscjava.vscode-gradle)

> Nota: VSCode no ofrece vista previa de Compose ni editor visual de layouts.
> El ciclo de trabajo es: editar → `.\gradlew.bat installDebug` → revisar en el
> dispositivo. Para iterar más rápido en UI se puede abrir el mismo proyecto en
> Android Studio cuando se prefiera.

---

## 3. Configuración del entorno (paso a paso, Windows)

### 3.1 Ubicar el Android SDK
Si tienes Android Studio instalado, el SDK ya está en:
`C:\Users\TU_USUARIO\AppData\Local\Android\Sdk`
(confírmalo en Android Studio → SDK Manager → "Android SDK Location").

Asegúrate de tener instalados, en SDK Tools: **Command-line Tools**,
**Platform-Tools** y **Build-Tools**.

### 3.2 Variables de entorno
En PowerShell (reemplaza TU_USUARIO), luego **reabre PowerShell**:
```powershell
setx ANDROID_HOME "C:\Users\TU_USUARIO\AppData\Local\Android\Sdk"
setx PATH "%PATH%;%ANDROID_HOME%\platform-tools;%ANDROID_HOME%\cmdline-tools\latest\bin;%ANDROID_HOME%\emulator"
```
Verifica con: `adb --version`

### 3.3 Crear `local.properties`
En la raíz del proyecto, crea un archivo `local.properties` con una línea
(usa barras normales `/` aunque estés en Windows):
```
sdk.dir=C:/Users/TU_USUARIO/AppData/Local/Android/Sdk
```

### 3.4 Generar el Gradle wrapper (una sola vez)
Necesitas Gradle instalado temporalmente para esto. Descarga `gradle-8.6-bin.zip`
de gradle.org, descomprímelo, agrega su carpeta `bin` al PATH y luego, en la raíz
del proyecto:
```powershell
gradle wrapper --gradle-version 8.6
```
De aquí en adelante usa siempre `.\gradlew.bat`, nunca `gradle` directo.

### 3.5 Compilar
```powershell
.\gradlew.bat assembleDebug
```
Si termina en `BUILD SUCCESSFUL`, el entorno está bien configurado.

> Si VSCode marca en rojo imports de `androidx.*` pero el build pasa, ignóralo:
> es solo el editor, no un error real.

> **OneDrive:** evita tener el proyecto dentro de una carpeta sincronizada por
> OneDrive — puede sincronizar `build/` a mitad de compilación y causar errores
> intermitentes. Mejor una ruta como `C:\dev\`.

---

## 4. Ejecutar la aplicación

### En dispositivo físico
1. Activa **Opciones de desarrollador** y **Depuración USB** en el celular.
2. Conéctalo por USB y verifica: `adb devices` (debe listarlo).
3. Instala: `.\gradlew.bat installDebug`

### En emulador
1. Abre Android Studio → Virtual Device Manager → crea un emulador (Pixel +
   Android 14) y dale Play.
2. `.\gradlew.bat installDebug`

---

## 5. Levantar el mock del Authentication Service

Mientras el Authentication Service real no esté disponible, hay un mock en
Node.js que devuelve un token de prueba.

```powershell
cd mock-auth-server
node server.js
```
Queda escuchando en el puerto **8080**. Usuarios de prueba:
`user1 / 1234` y `investigador / test`.

### Apuntar la app al mock
En `network/SoapClient.kt`, ajusta `endpointUrl`:
- **Emulador:** `http://10.0.2.2:8080/AuthenticationService`
  (10.0.2.2 es cómo el emulador ve el localhost de tu PC).
- **Dispositivo físico:** `http://IP_DE_TU_PC:8080/AuthenticationService`
  (obtén la IP con `ipconfig`, adaptador Wi-Fi; debe estar en la misma subred
  que el celular).

### Permitir tráfico HTTP en desarrollo
Android bloquea HTTP sin cifrar por defecto. El archivo
`app/src/main/res/xml/network_security_config.xml` habilita cleartext **solo**
para las IPs de desarrollo. Agrega ahí la IP del mock que estés usando:
```xml
<domain includeSubdomains="true">10.0.2.2</domain>
<domain includeSubdomains="true">192.168.X.X</domain>  <!-- IP de tu PC -->
```

### Firewall de Windows
Si el celular no conecta aun con la IP correcta, permite el puerto 8080
(PowerShell como administrador):
```powershell
New-NetFirewallRule -DisplayName "Mock Auth 8080" -Direction Inbound -LocalPort 8080 -Protocol TCP -Action Allow
```

---

## 6. Estructura del proyecto

```
mobile-client-android/
├── README.md
├── settings.gradle.kts
├── build.gradle.kts                # config raíz (versiones de plugins)
├── gradle.properties
├── local.properties                # ruta del SDK (crear a mano, no se versiona)
├── mock-auth-server/
│   └── server.js                   # mock SOAP del Authentication Service
└── app/
    ├── build.gradle.kts            # dependencias del módulo
    └── src/main/
        ├── AndroidManifest.xml
        ├── res/
        │   ├── values/             # strings.xml, themes.xml
        │   └── xml/
        │       └── network_security_config.xml
        └── java/com/upbcientifica/mobile/
            ├── MainActivity.kt      # punto de entrada + navegación
            ├── network/
            │   ├── SoapClient.kt    # SOAP sobre OkHttp (Auth)
            │   ├── RestClient.kt    # Retrofit (Storage/SharedFile/Streaming)
            │   └── GrpcClient.kt    # placeholder (gRPC desactivado)
            ├── session/
            │   └── TokenManager.kt  # token de sesión cifrado
            ├── data/
            │   ├── auth/            # AuthModels, AuthRepository
            │   ├── storage/        # StorageApi, StorageRepository
            │   ├── sharedfile/     # SharedFileApi, SharedFileRepository
            │   ├── streaming/      # StreamingApi, StreamingRepository
            │   ├── filesync/       # stub (pendiente .proto)
            │   └── hpc/            # stub (bloqueado por RMI)
            └── ui/
                ├── state/          # UiState (Idle/Loading/Success/Error)
                ├── viewmodel/      # un ViewModel por pantalla
                └── screens/        # Login, Home, Storage, SharedFile,
                                    #  Streaming, PantallaConVolver
```

---

## 7. Estado de cada servicio

- **Authentication (SOAP):** funcional contra el mock. El `SoapClient` arma el
  sobre XML a mano y lo envía con OkHttp (se descartó ksoap2 por estar
  desactualizada y ser difícil de resolver en Gradle).
- **Storage / Shared File / Streaming (REST):** pantallas y ViewModels listos.
  Cada `Repository` tiene un flag `usarDatosSimulados = true` que devuelve datos
  falsos para desarrollar la UI sin backend. Al conectar el servicio real, se le
  pasa el `api` construido con `RestClient.build(baseUrl, tokenManager)` y se
  cambia el flag a `false` — sin tocar ViewModels ni pantallas.
- **File Sync (gRPC):** desactivado. El plugin de protobuf y las dependencias de
  gRPC están comentadas en `app/build.gradle.kts` hasta tener el `.proto`.
- **HPC (RMI):** bloqueado (ver sección 9).

---

## 8. Notas para los desarrolladores de los servidores

Cada `Repository` incluye un comentario `>>> NOTA PARA EL PROGRAMADOR DEL
SERVIDOR <<<` que resume los endpoints y campos que el cliente espera. Resumen:

- **Auth (SOAP):** operaciones `login(usuario, contrasena, origenCliente)`,
  `validarToken(token)`, `logout(token)`. `login` debe devolver `token`, `roles`
  y `mapaServiciosAutorizados`.
- **Storage (REST):** `GET /storage/directorios/{id}`,
  `POST /storage/archivos` (multipart), `GET /storage/archivos/{id}/descargar`,
  `DELETE /storage/archivos/{id}`.
- **Shared File (REST):** `POST /shared-file/compartir`,
  `GET /shared-file/compartidos-conmigo`, `DELETE /shared-file/compartir/{id}`.
- **Streaming (REST):** `GET /streaming/catalogo?pagina=N`,
  `GET /streaming/buscar?criterioTexto=...`, `GET /streaming/{id}/sesion`.

Si el WSDL o los endpoints reales difieren en nombres, solo debe ajustarse la
capa `data/` del cliente (nunca `ui/`), y avisar al equipo móvil.

---

## 9. Pendientes del equipo

- **HPC / RMI:** el contrato de HPC exige RMI, pero `java.rmi` no existe en
  Android ni hay cliente RMI en iOS. **Decisión pendiente con Dev C:** (a) que
  HPC Service exponga también gRPC/REST para clientes no-JVM, o (b) dejar el
  envío/consulta de trabajos HPC fuera del cliente móvil en esta versión.
- **File Sync / gRPC:** falta el `.proto` de Dev D. Al tenerlo: descomentar el
  plugin protobuf y las dependencias grpc en `app/build.gradle.kts`, poner el
  `.proto` en `app/src/main/proto/` y regenerar los stubs.
- **Photo Album:** aún no hay contrato ni pantalla en el cliente.
- **Navegación:** para el MVP se usa un `when` simple en `MainActivity`. A futuro
  conviene migrar a Navigation-Compose.
- **Producción:** antes de desplegar, deshabilitar el cleartext HTTP
  (`network_security_config.xml`) — el roadmap exige HTTPS/TLS.

---

## 10. Solución de problemas frecuentes

- **`Could not find ...` al compilar:** falta un repositorio o una dependencia
  no resuelve. Verifica conexión y que `google()`/`mavenCentral()` estén en
  `settings.gradle.kts`.
- **`CLEARTEXT communication not permitted`:** agrega la IP del servidor a
  `network_security_config.xml` (sección 5).
- **`failed to connect ... after 10000ms`:** IP incorrecta o firewall. Verifica
  con `ipconfig` que uses la IP Wi-Fi de tu PC (misma subred que el celular),
  no una IP virtual (VirtualBox/Hyper-V), y abre el puerto 8080 en el firewall.
- **Los tiles del Home no hacen nada:** revisa que la navegación en
  `MainActivity` esté conectada (el `when` sobre `pantallaActual`).
- **Imports en rojo en VSCode pero el build pasa:** es solo el editor; ignóralo.
```