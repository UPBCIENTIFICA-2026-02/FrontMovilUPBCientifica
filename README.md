# Cliente Móvil — UPB-CIENTÍFICA

Aplicación Android (Kotlin + Jetpack Compose) que sirve como cliente móvil del
sistema distribuido **UPB-CIENTÍFICA**: una plataforma tipo "Google Drive" con
servicios de autenticación, almacenamiento, compartición, sincronización,
álbum de fotos, streaming y cómputo de alto rendimiento (HPC), construida sobre
una arquitectura orientada a servicios (SOA).

> Última revisión: 2026-10-13. Todos los servicios están conectados al backend
> real del monorepo `UPB-CIENTIFICA`; ya no queda ninguno simulado.

---

## Tabla de contenido

1. Descripción y decisiones de arquitectura
2. Requisitos previos
3. Configuración del entorno (paso a paso, Windows)
4. Ejecutar la aplicación
5. Servicios contra los que se prueba
6. Estructura del proyecto
7. Estado de cada servicio
8. Notas para los desarrolladores de los servidores
9. Pendientes del equipo
10. Solución de problemas frecuentes

---

## 1. Descripción y decisiones de arquitectura

Este cliente se conecta **directamente** a cada servicio usando su protocolo
nativo, sin pasar por un gateway intermedio (el gateway del diagrama, node-web,
es únicamente para el navegador, que no puede hablar SOAP/RMI/gRPC).

| Servicio             | Protocolo                  | Puerto |
|----------------------|----------------------------|--------|
| Authentication       | SOAP (sobre OkHttp)        | 8081   |
| Distributed Storage  | HTTP/REST (Retrofit)       | 8084   |
| Shared File          | HTTP/REST                  | 8085   |
| Photo Album + Streaming | HTTP/REST + ExoPlayer   | 8083   |
| File Sync            | gRPC (`grpc-okhttp`)       | 9090   |
| HPC Service          | REST (job-api hace el RMI) | 8082   |

**Por qué Kotlin nativo y no React Native / Flutter:** al ser un sistema
distribuido SOA, el móvil debe hablar los protocolos reales (SOAP, gRPC, etc.).
Kotlin corre sobre la JVM, que tiene librerías maduras para esos protocolos.

**Manejo de estado:** StateFlow + ViewModel, sin librerías externas. Cada
pantalla observa un `UiState` (Idle / Loading / Success / Error).

**Arquitectura interna por capas** (misma filosofía que el backend):

```
ui/       -> Presentación (Compose + ViewModel, expone UiState)
data/     -> Un paquete por servicio; cada uno habla SU protocolo y expone
             una interfaz simple hacia arriba (el ViewModel no sabe si algo
             es SOAP, REST o gRPC)
network/  -> Clientes de bajo nivel compartidos (SoapClient, RestClient,
             GrpcClient) y Configuracion (todas las direcciones)
session/  -> TokenManager: guarda el token de sesión (cifrado) y lo inyecta
             en cada llamada a los demás servicios
```

---

## 2. Requisitos previos

- **JDK 17 o superior** (probado con JDK 21).
- **Android SDK** (viene con Android Studio; no hace falta usar el IDE).
- **VSCode** con `Kotlin Language` (fwcd.kotlin) y `Gradle for Java`
  (vscjava.vscode-gradle), o Android Studio.

> El ciclo de trabajo es: editar → `.\gradlew.bat installDebug` → revisar en el
> dispositivo. Compilar descarga la primera vez el compilador de protobuf y los
> plugins de gRPC (hace falta Internet).

---

## 3. Configuración del entorno (paso a paso, Windows)

### 3.1 Ubicar el Android SDK
Si tienes Android Studio instalado, el SDK ya está en
`C:\Users\TU_USUARIO\AppData\Local\Android\Sdk`. Asegúrate de tener, en SDK
Tools: **Command-line Tools**, **Platform-Tools** y **Build-Tools**.

### 3.2 Variables de entorno
En PowerShell (reemplaza TU_USUARIO), luego **reabre PowerShell**:
```powershell
setx ANDROID_HOME "C:\Users\TU_USUARIO\AppData\Local\Android\Sdk"
setx PATH "%PATH%;%ANDROID_HOME%\platform-tools;%ANDROID_HOME%\cmdline-tools\latest\bin;%ANDROID_HOME%\emulator"
```
Verifica con: `adb --version`

### 3.3 Crear `local.properties`
En la raíz del proyecto (usa barras `/` aunque estés en Windows):
```
sdk.dir=C:/Users/TU_USUARIO/AppData/Local/Android/Sdk
```

### 3.4 Compilar
El proyecto trae el Gradle wrapper (`gradlew.bat`); usa siempre ese:
```powershell
.\gradlew.bat assembleDebug
```
Si termina en `BUILD SUCCESSFUL`, el entorno está bien configurado.

> **OneDrive:** si el proyecto está en una carpeta sincronizada por OneDrive,
> puede sincronizar `build/` a mitad de compilación y causar errores
> intermitentes. Mejor una ruta como `C:\dev\`.

---

## 4. Ejecutar la aplicación

### En dispositivo físico
1. Activa **Opciones de desarrollador** y **Depuración USB** en el celular.
2. Conéctalo por USB y verifica: `adb devices`.
3. Instala: `.\gradlew.bat installDebug`

### En emulador
1. Android Studio → Virtual Device Manager → crea un emulador y arráncalo.
2. `.\gradlew.bat installDebug`

---

## 5. Servicios contra los que se prueba

En el PC de desarrollo, todos los servicios los arranca el script 7 del monorepo
(guía completa en `UPB-CIENTIFICA/docs/Guia_Cluster_VirtualBox_Local.md`):

```powershell
cd UPB-CIENTIFICA\despliegue\cluster-virtualbox
.\7-iniciar-servicios-windows.ps1 -HpcLocal -ModoAuth soap
```

La autenticación la atiende **`auth-local`** (puerto 8081), que imita node-soap.
Usuarios: `user1 / 1234`, `investigador / test`, `estudiante / test`
(`estudiante` no tiene HPC en su mapa de servicios: el gestor le responde 403).
File Sync corre en una VM Ubuntu con el 9090 reenviado al PC (paso 7.4 de la guía).

> El antiguo `mock-auth-server/server.js` (puerto 8080) ya no se usa: ese puerto
> es ahora del portal web, y sus tokens no los aceptan los servicios en modo soap.

### Dónde se configura todo
`network/Configuracion.kt`:

```kotlin
val entorno: Entorno = Entorno.DISPOSITIVO_FISICO   // o EMULADOR / LABORATORIO
const val IP_PC_EN_WIFI = "192.168.20.31"           // ipconfig -> adaptador Wi-Fi
const val USAR_MOCK_AUTH = false                    // false = auth-local / node-soap en el 8081
```

Si añades una IP nueva, añádela **también** en
`res/xml/network_security_config.xml`, o Android corta la conexión HTTP sin
mensaje útil.

### Firewall de Windows (PowerShell como administrador, una vez)
```powershell
New-NetFirewallRule -DisplayName "UPB-CIENTIFICA pruebas locales" -Direction Inbound `
  -Protocol TCP -LocalPort 8080,8081,8082,8083,8084,8085,9090 -Action Allow -Profile Any
```

---

## 6. Estructura del proyecto

```
FrontUPBCientifica/
├── build.gradle.kts / settings.gradle.kts / gradle.properties
├── local.properties                # ruta del SDK (crear a mano, no se versiona)
└── app/
    ├── build.gradle.kts            # dependencias + plugin protobuf (gRPC)
    └── src/main/
        ├── AndroidManifest.xml     # incluye ReceptorWifi (File Sync)
        ├── proto/filesync.proto    # COPIA del contrato de node-sync (no editar aquí)
        ├── res/xml/network_security_config.xml
        └── java/com/upbcientifica/mobile/
            ├── MainActivity.kt      # composición de dependencias + navegación
            ├── network/
            │   ├── Configuracion.kt # direcciones de todos los servicios
            │   ├── SoapClient.kt    # SOAP sobre OkHttp (Auth)
            │   ├── RestClient.kt    # Retrofit (Storage, Shared File, Media, HPC)
            │   └── GrpcClient.kt    # canal gRPC hacia node-sync (atado al Wi-Fi)
            ├── session/TokenManager.kt
            ├── data/
            │   ├── auth/            # AuthRepository
            │   ├── storage/        # StorageApi/Repository, ArchivosDelDispositivo
            │   ├── sharedfile/     # SharedFileApi/Repository/ViewModel
            │   ├── streaming/      # StreamingApi/Repository (videos + fotos, orden)
            │   ├── filesync/       # File Sync: ver abajo
            │   └── hpc/            # HpcApi (job-api REST), HpcRepository
            └── ui/
                ├── components/     # VisorMedia, IndicadorSincronizacion, Cuenta…
                ├── viewmodel/      # un ViewModel por pantalla
                └── screens/        # Login, Home, Storage, SharedFile, Streaming,
                                    #  Hpc, Sync
```

`data/filesync/`: `CarpetaLocal` (carpeta del teléfono vía SAF),
`AlmacenSync` (configuración y manifiesto por usuario), `MotorSincronizacion`
(una sincronización: hashes, `IniciarSync`, subir/bajar/borrar/conflicto),
`RedLocal` (detecta el Wi-Fi y avisa al conectarse aunque la app esté cerrada),
`TrabajoSincronizacion` (WorkManager: al conectar, vigilancia cada 15 min,
horario) y `FileSyncRepository` (estado compartido por la UI y el segundo plano).

---

## 7. Estado de cada servicio

| Servicio | Estado |
|---|---|
| Authentication | **Conectado** a auth-local o node-soap (`USAR_MOCK_AUTH = false`) |
| Distributed Storage | **Conectado** (8084): navegar, subir archivos y carpetas, crear carpeta, descargar, eliminar, cuota. Imágenes y videos se abren en el visor de la app |
| Shared File | **Conectado** (8085): Conmigo / Que comparto, compartir con usuario o grupo, cambiar nivel, dejar de compartir, recompartir si el nivel lo permite |
| Photo Album y Streaming | **Conectado** (8083): incluye lo compartido contigo ("Compartido por …"), ordenar (Recientes, Más vistos, Título), miniaturas reales (FFmpeg), reproducción dentro de la app (ExoPlayer por rangos), borrar lo propio. Nada se abre en otra app ni en el navegador |
| File Sync | **Conectado** (gRPC 9090): elige una carpeta del teléfono; se respalda en una carpeta de tu Home ("Respaldo celular"). Se sincroniza **sola al llegar al Wi-Fi donde responde el servidor** (aunque la app esté cerrada), en el horario elegido (cada hora o diaria) o con "Sincronizar ahora". Conflictos: se guardan ambas versiones |
| HPC | **Conectado** a job-api (8082), Contrato_HPC: enviar código fuente + dataset (rutas del Home), procesos, tiempo máximo, prioridad y parámetros; estado del clúster y de los nodos; registros; resultado en "Resultados HPC"; cancelar y reintentar |

**Sobre el contrato de autenticación.** El backend expone `iniciarSesion` /
`cerrarSesion`; esta app usa los alias `login` / `logout` (con `contrasena` y
listas separadas por comas), declarados también en `users.wsdl`.

**Sobre File Sync.** El `.proto` es una copia de
`UPB-CIENTIFICA/node-sync/proto/filesync/filesync.proto`: si cambia allí, se
vuelve a copiar. El servidor **ignora** los campos `uid` y saca el usuario del
token (metadata `authorization: Bearer <token>`). "La red local del servidor" se
detecta así: hay Wi-Fi y node-sync responde por esa red en menos de 4 s (sin
pedir permisos de ubicación para leer el SSID).

---

## 8. Notas para los desarrolladores de los servidores

Los contratos están en `UPB-CIENTIFICA/docs/contratos/` (PDF, `Contrato
PhotoAlbum.md` y `Adendas a los contratos.md`). Si el WSDL o los endpoints
reales cambian, solo debe ajustarse la capa `data/` del cliente (nunca `ui/`).

Un detalle del sobre SOAP que conviene no perder: el elemento de la operación va
en el namespace del WSDL, pero **sus hijos no**. El esquema de `users.wsdl` no
declara `elementFormDefault`, así que rige `unqualified`. Por eso `SoapClient`
usa un prefijo (`<ns:login><usuario>…`) y no `xmlns=` por defecto: con el
namespace heredado, PHP lee los parámetros vacíos y responde "usuario o
contraseña incorrectos" aunque sean correctos.

---

## 9. Pendientes del equipo

- **HPC:** elegir el código y el dataset con un selector de Mis archivos (hoy
  se escribe la ruta del Home, como `/Proyectos/pi.c`).
- **File Sync con TLS:** en el laboratorio (`Entorno.LABORATORIO`) el canal usa
  TLS; si el certificado lo firma una CA propia, hay que instalarla en el teléfono.
- **Navegación:** se usa un `when` simple en `MainActivity`; a futuro, Navigation-Compose.
- **Producción:** deshabilitar el cleartext HTTP (`network_security_config.xml`).

---

## 10. Solución de problemas frecuentes

- **`Could not find ...` al compilar:** falta un repositorio o una dependencia
  no resuelve (la primera compilación descarga protoc y los plugins de gRPC).
- **`CLEARTEXT communication not permitted`:** agrega la IP del servidor a
  `network_security_config.xml` (sección 5).
- **`failed to connect ... after 10000ms`:** IP incorrecta o firewall. Usa la IP
  Wi-Fi real del PC (misma subred que el celular), no una IP virtual, y abre los
  puertos (sección 5).
- **La sincronización dice "Esperando la red del servidor":** el teléfono no está
  en el Wi-Fi del PC, o `syncd` no corre en su VM, o el 9090 está cerrado en el firewall.
- **Un servicio responde "Tu perfil no tiene acceso al servicio":** modo `soap` y
  el usuario no tiene ese servicio en su mapa (p. ej. `estudiante` en HPC).
- **Imports en rojo en VSCode pero el build pasa:** es solo el editor; ignóralo.
