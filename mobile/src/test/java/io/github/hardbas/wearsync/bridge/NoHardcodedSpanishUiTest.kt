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

    /** Words that only appear in the Spanish UI copy of this app. */
    private val spanishMarkers = listOf(
        "Sincroniz", "Anillo", "Batería", "Puntaje", "Comprobando", "Guardar",
        "Activo", "Oficial", "Calibración", "Permiso", "Fuente:", "Telemetría",
        "Concedelo", "Abrir Configuración", "Pasos •", "Requerido", "Enviando",
        "Consultando", "Exacto", "Vinculado", "Activá", "Activa '", "Buscando",
        "Conectado con", "Ocultar", "Modo Manual"
    )

    private fun offenders(): List<String> {
        if (!sources.isDirectory) return emptyList()
        val found = mutableListOf<String>()
        sources.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                file.readLines().forEachIndexed { index, line ->
                    if (line.trimStart().startsWith("//") || line.trimStart().startsWith("*")) return@forEachIndexed
                    val literals = Regex("\"([^\"\\\\]|\\\\.){4,}\"").findAll(line).map { it.value }
                    for (literal in literals) {
                        if (spanishMarkers.any { literal.contains(it) }) {
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
