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
     * A word list is only a heuristic, and a heuristic always misses the word nobody thought of:
     * `"Leer Widgets Directos"` and `"Conectado en Vivo"` have no accents and were on no list, so
     * they shipped in Spanish. The language-agnostic rule below is the real guard; this one stays
     * because it catches text that reaches users through helpers.
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

    /** Where user-facing text is painted. */
    private val textSinks = listOf(
        "text =", "setText(", "setTitle(", "setMessage(", "Toast.makeText(",
        "setPositiveButton(", "setContentText(")

    /**
     * Language-agnostic guard: a line that paints text must take it from resources.
     *
     * This is the rule that cannot be fooled by vocabulary. Any literal on a text sink that is
     * not a `getString(...)` call is UI copy, in whatever language it happens to be.
     */
    @Test
    fun `every painted string comes from resources whatever the language`() {
        val violations = mutableListOf<String>()
        sources.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                if (isParserFile(file.name)) return@forEach
                file.readLines().forEachIndexed { index, line ->
                    val trimmed = line.trimStart()
                    if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.contains("Log.")) return@forEachIndexed
                    if (textSinks.none { line.contains(it) }) return@forEachIndexed
                    if (line.contains("getString(")) return@forEachIndexed
                    val literal = Regex("\"([^\"\\\\]|\\\\.){3,}\"").find(line)?.value
                    // A literal that is only interpolation, digits or a format spec paints a value,
                    // not copy: `"${vitals.lastHeartRate}"` needs no translation.
                    val prose = literal?.replace(Regex("\\$\\{[^}]*}"), "")
                        // `$variable` without braces is interpolation too.
                        ?.replace(Regex("\\$[A-Za-z_][A-Za-z0-9_]*"), "")
                        ?.replace(Regex("%%?[-\\d.]*[dfsx]"), "")
                    if (literal != null && prose != null && Regex("[A-Za-zÁÉÍÓÚÑáéíóúñ]{3,}").containsMatchIn(prose)) {
                        violations += "${file.name}:${index + 1} $literal"
                    }
                }
            }
        assertTrue(
            "paint text with getString(R.string.…) so the system language applies:\n" +
                violations.joinToString("\n"),
            violations.isEmpty()
        )
    }
}
