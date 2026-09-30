# kontainer

Containers for Kotlin/Native tests. A test brings up the broker or database it needs from the compose
file your repository already has, gets it on ports nobody else is using, waits until it answers its
own protocol, and can pause or stop it mid-test without breaking anyone else's run.

The JVM has Testcontainers. A `linuxX64` test binary has nothing, so native suites usually start their
services from a shell script before Gradle and read an address from the environment. That works until
a test needs to break the broker — then it breaks it for the whole suite, and ends up behind a switch
CI never sets. kontainer is the missing piece: a Docker client that runs inside the test process.

```kotlin
val db = Fixture.owned(listOf("../compose/postgres.yml"), ports = mapOf("pg" to listOf(5432)))
try {
    db.up()
    db.awaitReady("pg", 5432, Probe.postgres())
    val url = "postgresql://127.0.0.1:${db.port("pg", 5432)}/postgres"

    db.paused("pg", 5432, Probe.postgres()) {
        // The database is frozen here: connections open, nothing answers.
    }
    // And back, answering again, on the same port.
} finally {
    db.down()
}
```

## Getting it

Published for `linuxX64` to the portfolio's Maven repository:

```kotlin
repositories {
    maven("https://reposilite.kotlin.website/snapshots") {
        content { includeGroupByRegex("io\\.github\\.youndie.*") }
    }
}

kotlin {
    sourceSets.getByName("linuxX64Test").dependencies {
        implementation("io.github.youndie.kontainer:kontainer:0.1.0.8")
    }
}
```

`kontainer` brings `kontainer-docker`, the engine client, with it; the client can be used on its own.

It needs, on the machine the tests run on: a Docker Engine with API 1.44 or newer, its socket
readable and writable by the test process (the `docker` group, or `DOCKER_HOST=unix://…`), and the
`docker compose` v2 plugin. `ubuntu-latest` runners have all three.

## The compose file

Your file stays the description of the service. The one thing kontainer asks of it is to publish each
port it will choose through a variable named after the service and the port:

```yaml
services:
  pg:
    image: postgres:18-alpine
    environment: { POSTGRES_PASSWORD: kontainer }
    ports: ["127.0.0.1:${KONTAINER_PORT_PG_5432:-5432}:5432"]
```

The default keeps the file working for a person running `docker compose up` by hand. The chosen
port is passed in before `up`, which is also what lets Kafka advertise it
(`KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://127.0.0.1:${KONTAINER_PORT_BROKER_9092}`). After `up`,
kontainer checks the container really published that port and fails the fixture if not.

A test binary runs from its module's directory, so paths to compose files are relative to that.

## What you get

- **Owned and shared fixtures.** `Fixture.owned(...)` is the test's own: a project name no other
  process uses (`kontainer-<pid>-<n>`) and its own ports. `Fixture.shared(project, ...)` keeps a fixed
  name and is reused between runs; faults on it are refused.
- **Readiness by protocol**, from the host, through the published port: `Probe.kafka()` (an
  ApiVersions request), `Probe.postgres()`, `Probe.http(path, status)`, `Probe.custom { host, port -> }`.
  A TCP connect is not readiness — the engine's proxy accepts it even for a paused container.
- **Faults.** `pause`, `unpause`, `stop`, `start`, `kill`, and the scoped `paused { }` and
  `stopped { }`, which put the service back and wait for it to answer again even when the block throws.
- **Cleanup.** `down()` removes containers, networks and volumes. A test process that was killed
  cannot clean up, so the next `up` on the same host removes owned fixtures whose owner process is gone.
- **The engine client** (`DockerEngine`): containers by label, `inspect`, lifecycle calls, `exec` with
  the exit code and both streams, `logs`. Every engine failure arrives as a type (`DockerError`).

Every call that talks to Docker is `suspend`. A fixture can live for one test or, with
`@BeforeClass`/`@AfterClass` on a companion object, for a test class.

## Numbers

An owned `apache/kafka:4.3.1` broker, from `up` to its first ApiVersions answer: a median of 3.5 s on
an `ubuntu-latest` runner and 5.1 s on a loaded shared build box (five cold starts each, image pulled).
That is what one fault test with a broker of its own costs. How it was measured is in the
[research](docs/research/research-architecture.md), §1.4.

## Who uses it

- [kafkakn](https://github.com/youndie/kafkakn) — its fault tests pause and stop a broker of their
  own on `linuxX64`, in the suite that gates every merge, instead of behind a switch.
- [s3kn](https://github.com/youndie/s3kn) — its live tests start their own S3 server when no endpoint
  is given.

## What it does not do

- **Only `linuxX64`.** The JVM target is planned; macOS and arm64 are not.
- **No containers described in Kotlin.** A compose file is the description, and kontainer runs the
  `docker compose` CLI for `up` and `down`.
- **No images** — no pulling as an API call, no building; compose pulls what it needs.
- **No network faults** such as latency or loss; a service is paused, stopped or killed.
- **A lost race for a port** retries `up` up to three times. That path has no test, because the race
  cannot be provoked on demand.

## Documentation

[`docs/`](docs/README.md) is the full account: the [research](docs/research/research-architecture.md)
— what was measured about the Docker Engine and why the library is shaped this way — the features
with their scenarios, and the two modules with their APIs and quirks. Work in progress is in the
[backlog](backlog.md).

## License

MIT — see [LICENSE](LICENSE).
