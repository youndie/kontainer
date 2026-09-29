package io.github.youndie.kontainer

/** A compose file written for one test, in a directory of its own that is removed after [block]. */
internal inline fun <T> withComposeFile(
    yaml: String,
    block: (path: String) -> T,
): T {
    val directory = createTemporaryDirectory("kontainer-test-")
    try {
        val path = "$directory/compose.yml"
        writeTextFile(path, yaml.trimIndent() + "\n")
        return block(path)
    } finally {
        removeDirectory(directory)
    }
}

/** The `docker` CLI as a second witness and for setup; never the subject. */
internal fun docker(vararg arguments: String): String {
    val result = runCommand(listOf("docker") + arguments, emptyMap())
    check(
        result.exitCode == 0,
    ) { "docker ${arguments.joinToString(" ")} exited with ${result.exitCode}: ${result.output}" }
    return result.output.trim()
}
