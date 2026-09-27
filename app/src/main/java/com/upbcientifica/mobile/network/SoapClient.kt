package com.upbcientifica.mobile.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import org.w3c.dom.Document
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Cliente SOAP mínimo construido sobre OkHttp (sin librerías externas).
 * Arma el sobre SOAP 1.1 como XML, lo envía por POST y parsea la respuesta.
 *
 * El namespace y la URL salen ahora de [Configuracion]. Antes estaban escritos
 * aquí con los valores del mock (`urn:upbcientifica-auth` y el puerto 8080);
 * el WSDL real —users.wsdl— usa el namespace `http://192.168.1.195/soap` y
 * publica la ruta /soap/user.php en el 8081.
 */
class SoapClient(
    private val namespace: String = Configuracion.NAMESPACE_AUTH,
    private val endpointUrl: String = Configuracion.urlAuth
) {
    private val http = OkHttpClient.Builder()
        // Sin timeouts explícitos, una VM apagada deja la app colgada sin más.
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .addInterceptor(
            HttpLoggingInterceptor().apply {
                // Con BODY se ve el sobre SOAP entero en Logcat, que es la
                // forma práctica de depurar un desajuste de contrato.
                level = HttpLoggingInterceptor.Level.BODY
            }
        )
        .build()

    /**
     * Invoca una operación SOAP. [params] son los argumentos en el mismo
     * orden que declara el WSDL para esa operación. Devuelve el Document XML
     * de la respuesta para que el repositorio extraiga los campos que necesita.
     *
     * La respuesta se parsea TAMBIÉN cuando el servidor contesta con error:
     * un 500 de SOAP trae un Fault con el motivo, y el mock responde 500 con el
     * detalle cuando las credenciales no valen. Antes se descartaba ese cuerpo
     * y el usuario solo veía un mensaje genérico.
     */
    suspend fun invoke(operation: String, params: Map<String, Any?>): Document =
        withContext(Dispatchers.IO) {
            val body = buildSoapEnvelope(operation, params)

            val request = Request.Builder()
                .url(endpointUrl)
                .addHeader("Content-Type", "text/xml; charset=utf-8")
                .addHeader("SOAPAction", "$namespace/$operation")
                .post(body.toRequestBody("text/xml; charset=utf-8".toMediaType()))
                .build()

            val (codigo, xml) = http.newCall(request).execute().use { response ->
                response.code to response.body?.string().orEmpty()
            }

            if (xml.isBlank()) {
                throw IllegalStateException(
                    "El servicio de autenticación respondió vacío (HTTP $codigo) en $operation"
                )
            }

            runCatching { parseXml(xml) }.getOrElse {
                throw IllegalStateException(
                    "El servicio de autenticación no devolvió XML en $operation " +
                        "(HTTP $codigo): ${xml.take(200)}"
                )
            }
        }

    /**
     * El elemento de la operación va en el namespace del WSDL; sus HIJOS no.
     *
     * El esquema de users.wsdl no declara `elementFormDefault`, así que rige el
     * valor por omisión, "unqualified": solo el elemento global lleva
     * namespace. Por eso la operación va con prefijo (`ns:`) y los parámetros
     * van sueltos: con `xmlns=` por defecto los hijos heredarían el namespace
     * y PHP los leería como vacíos, respondiendo "usuario o contraseña
     * incorrectos" aunque fueran correctos.
     */
    private fun buildSoapEnvelope(operation: String, params: Map<String, Any?>): String {
        val paramsXml = params.entries.joinToString("") { (key, value) ->
            "<$key>${escaparXml(value?.toString().orEmpty())}</$key>"
        }
        return """
            <?xml version="1.0" encoding="utf-8"?>
            <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"
                           xmlns:ns="$namespace">
              <soap:Body>
                <ns:$operation>$paramsXml</ns:$operation>
              </soap:Body>
            </soap:Envelope>
        """.trimIndent()
    }

    /** Una contraseña con & o < rompería el sobre si no se escapa. */
    private fun escaparXml(valor: String): String = valor
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    private fun parseXml(xml: String): Document =
        DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(xml.byteInputStream())
}
