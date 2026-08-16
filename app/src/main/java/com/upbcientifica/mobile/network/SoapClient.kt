package com.upbcientifica.mobile.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.w3c.dom.Document
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Cliente SOAP mínimo construido sobre OkHttp (sin librerías externas).
 * Arma el sobre SOAP 1.1 como XML, lo envía por POST y parsea la respuesta.
 *
 * TODO: reemplazar NAMESPACE/URL por los valores reales del WSDL publicado
 * (ver "Contrato_Auth": Protocolo SOAP exclusivamente, vía WSDL versionado).
 * Cuando tengan el WSDL, se puede refinar el mapeo de parámetros/namespaces.
 */
class SoapClient(
    private val namespace: String = "urn:upbcientifica-auth",
    // 10.0.2.2 es la IP especial con la que el EMULADOR de Android ve el "localhost" de tu PC.
    // Si pruebas en un CELULAR FÍSICO, usa la IP local de tu PC en la red (ej. 192.168.1.X).
    // TODO Dev A: reemplazar por el host:puerto reales del Authentication Service.
    private val endpointUrl: String = "http://192.168.20.31:8080/AuthenticationService"
)  {
    private val http = OkHttpClient()

    /**
     * Invoca una operación SOAP. [params] son los argumentos en el mismo
     * orden que declara el WSDL para esa operación. Devuelve el Document XML
     * de la respuesta para que el repositorio extraiga los campos que necesita.
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

            http.newCall(request).execute().use { response ->
                val xml = response.body?.string()
                    ?: throw IllegalStateException("Respuesta SOAP vacía para $operation")
                if (!response.isSuccessful) {
                    throw IllegalStateException("Error SOAP (${response.code}) en $operation: $xml")
                }
                parseXml(xml)
            }
        }

    private fun buildSoapEnvelope(operation: String, params: Map<String, Any?>): String {
        val paramsXml = params.entries.joinToString("") { (key, value) ->
            "<$key>${value ?: ""}</$key>"
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

    private fun parseXml(xml: String): Document =
        DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(xml.byteInputStream())
}
