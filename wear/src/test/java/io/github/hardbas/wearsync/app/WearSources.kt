package io.github.hardbas.wearsync.app

import java.io.File

/**
 * Locates a source file for the source audits, independently of the working
 * directory Gradle happens to launch the JVM with.
 *
 * The audits read `.kt` files as text, which only works if the path resolves.
 * A relative path silently becomes a `FileNotFoundException` when the tests run
 * from a different directory, and an exception is not an assertion: it says
 * nothing about the code under audit. This walks up from the working directory
 * until it finds the module root, and the audits assert on a clear message when
 * the file is missing.
 */
object WearSources {

    private const val MODULE_MARKER = "src/main/java/io/github/hardbas/wearsync"

    fun file(relativePath: String): File {
        var directory: File? = File(System.getProperty("user.dir")).absoluteFile
        while (directory != null) {
            if (File(directory, MODULE_MARKER).isDirectory) {
                return File(directory, relativePath)
            }
            directory = directory.parentFile
        }
        // Let the caller's assertion report it rather than throwing from here.
        return File(relativePath)
    }
}
