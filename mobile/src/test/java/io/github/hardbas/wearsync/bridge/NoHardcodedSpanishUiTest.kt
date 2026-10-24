package io.github.hardbas.wearsync.bridge

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Source audit: no user-facing text may be written in a specific language inside Kotlin.
 *
 * The phone screen was authored in Spanish as literals (`text = "Sincronizar desde
 * RingConn"`), so every user saw Spanish regardless of their system language, and the
 * strings could not be translated at all. Android picks a language from resources; text
 * in code has no language.
 *
 * This audit fails while any UI literal still carries Spanish, and names every offender
 * so the list never depends on someone remembering it.
 */
class NoHardcodedSpanishUiTest {

    private val sources = File("src/main/java")

    /**
     * Words that only appear in the Spanish UI copy of this app, plus accented characters.
     *
     * The first version of this list was short and case-sensitive, so `"🩺 Diagnóstico de
     * sueño"` and `"Sin permiso de lectura"` slipped through while the audit reported success.
     * A guard that passes because it did not look is worse than no guard: it moves the
     * Spanish strings from the UI into a green check.
     */
    private val spanishMarkers = listOf(
        "sincroniz", "anillo", "batería", "bateria", "puntaje", "comprobando", "guardar",
        "pasos ", "puntos", "prom:", "bueno", "en uso", "meta:", "relajado", "garantizar",
        "sin restricciones", "vfc", "índice", "frecuencia",
        "activo", "oficial", "calibración", "calibracion", "permiso", "fuente:", "telemetría",
        "telemetria", "concedelo", "abrir configuración", "pasos •", "requerido", "enviando",
        "consultando", "exacto", "vinculado", "activá", "activa '", "buscando", "conectado con",
        "ocultar", "modo manual", "diagnóstico", "diagnostico", "cerrar", "leyendo", "sueño",
        "sueno", "lectura de", "volvé", "volve"
    )

    /**
     * The audit lists Spanish in every literal it can see, and exempts only what cannot reach a
     * user.
     *
     * The previous version did the opposite — it inspected only lines containing a known UI
     * sink such as `text =` — and that let a whole screen of Spanish through: metric cards pass
     * their labels as arguments to helpers (`createCard("PUNTAJE DE SUEÑO", ...)`), so no sink
     * appeared on the line and the audit stayed green while the app was Spanish.
     */
    private fun isExempt(fileName: String, line: String): Boolean {
        val trimmed = line.trimStart()
        if (isParserFile(fileName)) return true
        return trimmed.startsWith("//") ||
            trimmed.startsWith("*") ||
            trimmed.contains("Log.") ||
            trimmed.contains("TAG")
    }

    /**
     * The parser's Spanish keywords are patterns for reading the official app's Spanish labels.
     * They must stay Spanish; migrating them would break parsing.
     */
    private fun isParserFile(fileName: String) = fileName == "RingConnTelemetryParser.kt"

    /** Any accented Spanish letter is proof the copy was authored in Spanish. */
    private val accented = Regex("[áéíóúñ¿¡ÁÉÍÓÚÑ]")

    private fun looksSpanish(literal: String): Boolean {
        if (accented.containsMatchIn(literal)) return true
        val lower = literal.lowercase()
        return spanishMarkers.any { lower.contains(it) }
    }

    private fun offenders(): List<String> {
        if (!sources.isDirectory) return emptyList()
        val found = mutableListOf<String>()
        sources.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                file.readLines().forEachIndexed { index, line ->
                    if (line.trimStart().startsWith("//") || line.trimStart().startsWith("*")) return@forEachIndexed
                    if (isExempt(file.name, line)) return@forEachIndexed
                    val literals = Regex("\"([^\"\\\\]|\\\\.){4,}\"").findAll(line).map { it.value }
                    for (literal in literals) {
                        if (looksSpanish(literal)) {
                            found += "${file.name}:${index + 1} $literal"
                        }
                    }
                }
            }
        return found
    }

    @Test
    fun `no UI text is written in Spanish inside Kotlin`() {
        val found = offenders()
        assertTrue(
            "these literals never reach the language system; move them to res/values " +
                "(English default) and res/values-es:\n" + found.joinToString("\n"),
            found.isEmpty()
        )
    }
}
