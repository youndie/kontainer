package io.github.youndie.kontainer

/** Every way a fixture fails, as a type a test can act on. */
public sealed class FixtureError(
    message: String,
) : Exception(message) {
    /** `docker compose` exited non-zero; [output] is what it printed, both streams. */
    public class ComposeFailed(
        public val command: String,
        public val exitCode: Int,
        public val output: String,
    ) : FixtureError("`$command` exited with $exitCode: ${output.trim().takeLast(2000)}")

    /**
     * A container with the name a service asks for belongs to another compose project (or to none), and
     * is left alone. Two repositories once shared a project and removed each other's broker (kafkakn B-97).
     */
    public class ForeignContainer(
        public val containerName: String,
        public val project: String?,
    ) : FixtureError(
            "a container named $containerName already exists in " +
                (project?.let { "compose project '$it'" } ?: "no compose project") +
                ", and it is not this fixture's to touch",
        )

    /** The compose files have no service by this name, or its container is gone. */
    public class NoSuchService(
        public val service: String,
        public val project: String,
    ) : FixtureError("no service $service in fixture $project")
}
