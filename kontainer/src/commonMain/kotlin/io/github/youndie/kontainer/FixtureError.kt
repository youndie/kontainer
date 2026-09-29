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

    /**
     * The service's container publishes [containerPort] on a host port other than the one this fixture
     * chose — the compose file names a fixed port instead of the variable (research D5).
     */
    public class PortMismatch(
        public val service: String,
        public val containerPort: Int,
        public val chosen: Int,
        public val published: Int,
    ) : FixtureError(
            "service $service publishes $containerPort on $published, not on $chosen: publish it as " +
                "\"127.0.0.1:${'$'}{${portVariable(service, containerPort)}}:$containerPort\"",
        )

    /**
     * The service's container does not publish [containerPort] at all. A fixture that started and
     * published nothing was once answered by another project's service on the same box (research §1.2).
     */
    public class PortNotPublished(
        public val service: String,
        public val containerPort: Int,
    ) : FixtureError("service $service does not publish $containerPort; its compose file has to publish it")

    /** The compose files have no service by this name, or its container is gone. */
    public class NoSuchService(
        public val service: String,
        public val project: String,
    ) : FixtureError("no service $service in fixture $project")
}
