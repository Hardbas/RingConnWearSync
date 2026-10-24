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
        "activo", "oficial", "calibración", "calibracion", "permiso", "fuente:", "telemetría",
        "telemetria", "concedelo", "abrir configuración", "pasos •", "requerido", "enviando",
        "consultando", "exacto", "vinculado", "activá", "activa '", "buscando", "conectado con",
        "ocultar", "modo manual", "diagnóstico", "diagnostico", "cerrar", "leyendo", "sueño",
        "sueno", "lectura de", "volvé", "volve"
    )

    /**
     * Where user-facing text is installed. A literal only matters if it reaches one of these.
     *
     * Without this filter the audit also flags `RingConnTelemetryParser`, whose Spanish
     * keywords (`sueño`, `estrés`, `batería`) are *patterns for reading the official app's
     * Spanish labels* — they must stay Spanish, and migrating them would break parsing. Log
     * messages are likewise never shown to anyone.
     */
    private val uiSinks = listOf(
        "text =", "setText(", "setTitle(", "Toast.makeText(", "setPositiveButton(",
        "setContentText(", "buildForegroundNotification(", "setMessage("
    )

    private fun reachesUi(line: String): Boolean = uiSinks.any { line.contains(it) }

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
                    if (!reachesUi(line)) return@forEachIndexed
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
