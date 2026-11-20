package com.example.lab10workmanager

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Registro persistente de las ejecuciones del mantenimiento.
 * Cada línea del archivo es: marca;origen;canciones;duracionSeg
 * Las funciones hacen E/S de disco: deben llamarse fuera del hilo principal.
 */
object RegistroMantenimiento {
    private const val ARCHIVO = "registro_mantenimiento.txt"
    private val formato = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.forLanguageTag("es-PE"))

    data class Entrada(val marca: String, val origen: String, val canciones: Int, val duracionSeg: Int) {
        override fun toString() = "$marca · $origen · $canciones pistas · ${duracionSeg / 60} min ${duracionSeg % 60} s"
    }

    private fun archivo(contexto: Context) = File(contexto.filesDir, ARCHIVO)

    @Synchronized
    fun agregar(contexto: Context, origen: String, canciones: Int, duracionSeg: Int): Entrada {
        val entrada = Entrada(formato.format(Date()), origen, canciones, duracionSeg)
        archivo(contexto).appendText("${entrada.marca};$origen;$canciones;$duracionSeg\n")
        return entrada
    }

    fun leer(contexto: Context): List<Entrada> {
        val f = archivo(contexto)
        if (!f.exists()) return emptyList()
        return f.readLines().mapNotNull { linea ->
            val p = linea.split(";")
            if (p.size == 4) Entrada(p[0], p[1], p[2].toInt(), p[3].toInt()) else null
        }
    }

    fun borrar(contexto: Context) {
        archivo(contexto).delete()
    }
}
