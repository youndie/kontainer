---
id: kontainer
title: kontainer — fixtures, readiness and faults
type: service
module: kontainer
tech_stack: [Kotlin Multiplatform, Kotlin/Native linuxX64, kotlinx.coroutines, ktor-network, docker compose CLI v2]
owner: unassigned
depends_on: [kontainer-docker, docker compose CLI v2]
publishes: [io.github.youndie.kontainer:kontainer (reposilite, B-10)]
---

# kontainer — fixtures, readiness and faults

## 1. Responsibility

What a test uses: a `Fixture` brought up from compose files under a project name kontainer chooses, host
ports kontainer chooses and checks, readiness by protocol, and faults — pause, stop, kill — on a fixture the
test owns.

It deliberately does **not**: describe containers in Kotlin (after v1); implement compose itself (it runs
the CLI); generate a consumer's certificates or users; inject network faults; run on anything but
`linuxX64` in v1.

## 2. API contracts

The public Kotlin API. Failures are one sealed type, `FixtureError`: `ComposeFailed(command, exitCode,
output)`, `ForeignContainer(containerName, project)`, `PortMismatch(service, containerPort, chosen,
published)`, `PortNotPublished(service, containerPort)`, `NotReady(service, lastCause, logTail)`,
`SharedFixture(service, project)`, `NoSuchService(service, project)`; failures of the engine arrive as `DockerError` from
[kontainer-docker](kontainer-docker.md), unchanged.

| Call | Returns | Failures |
|---|---|---|
| `Fixture.owned(composeFiles, environment, ports)` / `Fixture.shared(project, composeFiles, environment, ports)` | a fixture, not yet up; `ports` is service → container ports whose host port kontainer chooses | — |
| `fixture.up()` | — | `ComposeFailed`, `ForeignContainer`, `PortMismatch`, `PortNotPublished`, `NotReady` (a container that already exited) |
| `fixture.down()` | — (containers, networks and volumes removed) | `ComposeFailed` |
| `fixture.reaped` | the owned projects of dead processes the last `up` removed (B-08) | — |
| `fixture.containerId(service)` | the id of the service's container, running or not | `NoSuchService` |
| `fixture.port(service, containerPort)` | the host port chosen before `up` and checked after it | `PortNotPublished` when not asked for |
| `fixture.awaitReady(service, containerPort, probe, timeout = 60 s, attempt = 2 s)` | — | `NotReady` |
| `fixture.pause/stop(service, grace)/start/kill(service)` | — | `SharedFixture`; `Conflict` from the engine (a second pause) |
| `fixture.unpause(service)` | — (not an error when the service is not paused) | `SharedFixture` |
| `fixture.paused(service, containerPort, probe) { … }` / `fixture.stopped(service, containerPort, probe, grace) { … }` | the block's result, once the service is restored and answers `probe` again | the block's own exception, unchanged, with a failed restore as suppressed; `SharedFixture`; `NotReady` after it |
| `Probe.kafka()`, `Probe.postgres(user)`, `Probe.http(path, status)`, `Probe.custom { host, port -> }` | a probe | — |
| `portVariable(service, containerPort)` | `KONTAINER_PORT_<SERVICE>_<PORT>` | — |

What a consumer's compose file must do: publish each port kontainer is asked about as
`"127.0.0.1:${KONTAINER_PORT_<SERVICE>_<PORT>}:<PORT>"`. Call `down()` in `finally`, also after a failed `up`.

## 2a. Code anchors

| File | What is there |
|---|---|
| `kontainer/src/commonMain/kotlin/io/github/youndie/kontainer/Fixture.kt` | up, down, ports, readiness |
| `kontainer/src/commonMain/kotlin/io/github/youndie/kontainer/Probe.kt` | the protocol probes |
| `kontainer/src/commonMain/kotlin/io/github/youndie/kontainer/FixtureError.kt` | the error type |
| `kontainer/src/linuxX64Main/kotlin/io/github/youndie/kontainer/Platform.linuxX64.kt` | the compose CLI, pid, host name, temporary files, a free port |
| `kontainer/src/linuxX64Test/kotlin/io/github/youndie/kontainer/` | tests against the real engine and compose |

## 3. How it is built

- **Two owners of the lifecycle, on purpose.** `up` and `down` run `docker compose -p <project>`; after that
  every container is found by its compose labels and driven through kontainer-docker. Re-implementing
  compose was rejected (research D4).
- **Labels through an override file.** Compose has no flag for labels, so `up` writes a small compose file
  that adds `kontainer.owner=<pid>@<host>` and `kontainer.fixture=owned|shared` to every service and passes
  it as the last `-f` (B-04).
- **Ports before `up`, checked after it.** kontainer binds `127.0.0.1:0` to find a free port, passes it as the
  variable, and after `up` compares the container's published ports with its choice — a fixture that
  started and published nothing, or something else, fails before any probe (research D5, Risk 2). A lost
  race for the port retries `up` up to three times; that path has no test (research Risk 1).
- **Readiness asks the protocol, from the host** (research D6), each attempt bounded, on real time.
- **Abandoned fixtures are removed by the next `up`** on the same host: owned projects whose
  `kontainer.owner` names this host and a pid `kill(pid, 0)` says is gone (research Risk 3, B-08).
- **Owned and shared fixtures differ in their name and in one rule**: `kontainer-<pid>-<n>` against a fixed
  one, and faults are refused on a shared fixture before anything is touched (research D7, B-07).
- **A scoped fault always restores.** `paused { }` and `stopped { }` unpause or start the service and wait for
  its probe whatever the block did — an exception or a cancellation — under `NonCancellable`, and only then let
  the block's own exception go on, unchanged (B-07). A fixture left frozen would break every test after it.
- **Unpause decides by state:** the engine answers an unpause of a running container with `500`, so the
  container is inspected first (research Consequence 6a).
- **A fixture can live as long as a test class**: `@BeforeClass`/`@AfterClass` on a companion object run on
  native (research H4, refuted by B-04).

## 4. Dependencies

| Kind | Name | What for |
|---|---|---|
| Module | [kontainer-docker](kontainer-docker.md) | everything after `up` |
| External | `docker compose` CLI v2 | `up`, `down`, `config` |
| Library | ktor-network, Ktor CIO client, 3.6.0 | the probes |

## 5. Infrastructure and publishing

- **Artefact:** `io.github.youndie.kontainer:kontainer`, `linuxX64`, to reposilite — B-10.
- **CI:** none yet (B-13); the suite runs on the Linux build box.

## 6. Local setup

```bash
./gradlew :kontainer:linuxX64Test
```

Needs Docker Engine, the `docker compose` plugin, and the images the tests use (`postgres:18-alpine`,
`apache/kafka:4.3.1`, `nginx:alpine`, `alpine/socat`), pulled on first use.

## 7. Configuration

| Key | Description | Required |
|---|---|---|
| `DOCKER_HOST` | passed to kontainer-docker and, through the environment, to the compose CLI | no |
| `KONTAINER_PORT_<SERVICE>_<CONTAINER_PORT>` | set **by kontainer** for compose; not read from the caller | — |

## 8. Quirks

- **An ephemeral port moves across stop/start**, which is why kontainer never uses one (research §1.2).
- **`compose up --wait` is not readiness** — it reported crash-looping brokers Healthy in kafkakn; kontainer does
  not pass `--wait` and trusts only a probe.
- **`compose config` needs the port variables to parse** a file that uses them; it is given placeholder `0`s.
- **A module named like the root project** (`:kontainer` in `kontainer`) breaks Gradle's type-safe project
  accessors, so they are off and modules are referenced by path.
