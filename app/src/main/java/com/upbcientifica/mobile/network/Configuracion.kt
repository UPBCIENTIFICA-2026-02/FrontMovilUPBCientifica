package com.upbcientifica.mobile.network

/**
 * Único lugar donde se configuran las direcciones de los servicios.
 *
 * Antes cada cliente traía su URL escrita a mano (SoapClient tenía
 * "http://192.168.20.31:8080/AuthenticationService" dentro del constructor),
 * lo que obligaba a tocar código en varios sitios cada vez que cambiaba una IP.
 *
 * ---------------------------------------------------------------------------
 * LO ÚNICO QUE NORMALMENTE HAY QUE CAMBIAR AQUÍ ES [entorno].
 * ---------------------------------------------------------------------------
 *
 * Y si usas un celular físico, además [IP_PC_EN_WIFI].
 *
 * Recuerda que toda dirección nueva en HTTP (sin TLS) tiene que estar también
 * en res/xml/network_security_config.xml, o Android bloquea la conexión sin
 * mensaje claro y parece que el servidor no responde.
 */
object Configuracion {

    enum class Entorno {
        /** Emulador de Android Studio: el PC se ve como 10.0.2.2. */
        EMULADOR,

        /** Celular real en la misma Wi-Fi que el PC: se usa [IP_PC_EN_WIFI]. */
        DISPOSITIVO_FISICO,

        /** VMs del CCA / laboratorio: cada servicio en su propia máquina. */
        LABORATORIO
    }

    /** <<< CAMBIA ESTO según dónde estés ejecutando la app. */
    val entorno: Entorno = Entorno.DISPOSITIVO_FISICO

    /**
     * IP de tu PC en la red Wi-Fi, para cuando pruebas en un celular físico.
     *
     * Ojo: tiene que ser la de la tarjeta Wi-Fi real. Las 192.168.56.x son las
     * virtuales de VirtualBox y el celular no las alcanza.
     * En Windows: `ipconfig` -> "Adaptador de LAN inalámbrica Wi-Fi".
     */
    const val IP_PC_EN_WIFI = "192.168.20.31"

    /**
     * Autenticación contra el mock en vez del servicio real.
     *
     * `mock-auth-server/server.js` escucha en el 8080 y acepta user1/1234 e
     * investigador/test. Sirve para trabajar sin la VM de node-soap levantada;
     * emite tokens `mock-token-<usuario>-abc123`, que es justo el formato que
     * aceptan Storage y Media cuando corren con MODO_AUTENTICACION=simulado.
     *
     * En false usa el SOAP del puerto 8081: node-soap en el laboratorio, o
     * herramientas/auth-local.js del monorepo en el PC (lo arranca el script
     * despliegue/cluster-virtualbox/7-iniciar-servicios-windows.ps1). Ojo: el
     * 8080 del PC lo ocupa ahora node-web, así que el mock no puede correr a la vez.
     */
    const val USAR_MOCK_AUTH = false

    // --- Direcciones de las VMs del laboratorio -----------------------------
    // TODO equipo: completar a medida que se desplieguen los servicios.
    private const val HOST_SOAP_LAB = "192.168.1.195"
    private const val HOST_STORAGE_LAB = "192.168.1.195"
    private const val HOST_MEDIA_LAB = "192.168.1.195"
    private const val HOST_SYNC_LAB = "192.168.1.195"
    /** hpc-master: job-api corre junto al gestor del clúster. */
    private const val HOST_HPC_LAB = "192.168.1.195"

    /** Host del PC de desarrollo, visto desde el dispositivo. */
    private val hostDesarrollo: String
        get() = when (entorno) {
            Entorno.EMULADOR -> "10.0.2.2"
            else -> IP_PC_EN_WIFI
        }

    private val enLaboratorio: Boolean
        get() = entorno == Entorno.LABORATORIO

    /**
     * Endpoint del Authentication Service.
     *
     * El WSDL (users.wsdl) publica la ruta /soap/user.php en el puerto 8081.
     * El mock, en cambio, responde a cualquier ruta en el 8080.
     */
    val urlAuth: String
        get() = when {
            USAR_MOCK_AUTH -> "http://$hostDesarrollo:8080/AuthenticationService"
            enLaboratorio -> "https://$HOST_SOAP_LAB:8081/soap/user.php"
            else -> "http://$hostDesarrollo:8081/soap/user.php"
        }

    /**
     * targetNamespace del WSDL de node-soap.
     *
     * Es un IDENTIFICADOR del contrato, no una dirección: aunque parezca una
     * URL con IP, no se navega y no cambia aunque el servicio se mueva de
     * máquina. (Que esté atado a una IP es una deuda conocida del contrato;
     * el día que se cambie a algo tipo urn:upbcientifica:auth:v1 hay que
     * actualizarlo aquí y en todos los clientes a la vez.)
     *
     * El mock no valida el namespace: despacha por SOAPAction, así que este
     * valor sirve para los dos casos.
     */
    const val NAMESPACE_AUTH = "http://192.168.1.195/soap"

    /** Distributed Storage (node-repository). Retrofit exige la barra final. */
    val urlStorage: String
        get() = if (enLaboratorio) "http://$HOST_STORAGE_LAB:8084/"
        else "http://$hostDesarrollo:8084/"

    /** Photo Album + Streaming (node-media). Retrofit exige la barra final. */
    val urlMedia: String
        get() = if (enLaboratorio) "http://$HOST_MEDIA_LAB:8083/"
        else "http://$hostDesarrollo:8083/"

    /**
     * HPC Service: job-api, la fachada REST del gestor RMI (puerto 8082).
     *
     * La app no habla RMI (Android no trae java.rmi): habla REST con job-api.
     * En el PC lo arrancan a mano gestor-cluster.jar y job-api.jar del
     * repositorio RMIUPBCientifica. Retrofit exige la barra final.
     */
    val urlTrabajos: String
        get() = if (enLaboratorio) "http://$HOST_HPC_LAB:8082/"
        else "http://$hostDesarrollo:8082/"

    /**
     * File Sync (node-sync), gRPC sobre TLS.
     * Todavía sin usar: el cliente gRPC está desactivado en build.gradle.kts.
     */
    val direccionSync: String
        get() = if (enLaboratorio) "$HOST_SYNC_LAB:9090" else "$hostDesarrollo:9090"

    /**
     * Shared File Service (node-shared-file): el ejecutable `shared-file-svc`
     * que el diagrama de despliegue pone en la VM node-repository, puerto 8085.
     * Retrofit exige la barra final.
     */
    val urlCompartidos: String
        get() = if (enLaboratorio) "http://$HOST_STORAGE_LAB:8085/"
        else "http://$hostDesarrollo:8085/"

    /** En false, la pestaña Compartidos y el botón Compartir avisan de que no hay servicio. */
    const val SHARED_FILE_DISPONIBLE = true
}
