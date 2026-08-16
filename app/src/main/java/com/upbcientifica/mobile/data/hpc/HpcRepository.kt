package com.upbcientifica.mobile.data.hpc

/**
 * ⚠️ BLOQUEADO — ver README.md, sección "HPC Service (RMI) no es viable
 * tal cual en móvil".
 *
 * El contrato actual (Contrato_HPC) expone EnviarTrabajo, ConsultarEstadoTrabajo
 * y ObtenerEstadoCluster vía RMI, pero `java.rmi` no existe en el subconjunto
 * de OpenJDK que usa Android, y no hay cliente RMI en iOS.
 *
 * No implementar este repositorio hasta que el equipo decida con Dev C:
 *  (a) HPC Service expone también gRPC/REST para clientes no-JVM, o
 *  (b) el envío/consulta de trabajos HPC queda fuera del cliente móvil
 *      en esta primera versión.
 */
class HpcRepository {
    // TODO: implementar según la decisión del equipo
}
