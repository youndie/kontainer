---
id: kontainer-docker
title: kontainer-docker — the Docker Engine client
type: service
module: kontainer-docker
tech_stack: [Kotlin Multiplatform, Kotlin/Native linuxX64, Ktor CIO client, ktor-network, kotlinx.serialization]
owner: unassigned
depends_on: [Docker Engine API 1.44+]
publishes: [io.github.youndie.kontainer:kontainer-docker (reposilite, B-10)]
---

# kontainer-docker — the Docker Engine client

## 1. Responsibility

A minimal client of the Docker Engine API over the unix socket, usable on its own: find containers by
label, inspect them, pause, unpause, stop, start, kill, run a command with the exit code and the two
streams apart, read logs. Every engine failure a caller acts on arrives as a type.

It deliberately does **not**: run compose, pick ports, wait for readiness, or know what a fixture is
(that is the `kontainer` module, still drafted); create, pull or build images and containers; negotiate
API versions; attach to stdin or give a command a TTY.

## 2. API contracts

The public Kotlin API, in `DockerEngine`. Every call is `suspend` and has no timeout of its own
(`stop` waits out its grace period); a caller bounds it with `withTimeout`. Failures are one sealed type,
`DockerError`: `SocketNotFound(path)`, `SocketPermissionDenied(path)`, `UnsupportedHost(dockerHost)`,
`ApiTooOld(server, required)`, `NoSuchContainer(id, engineMessage)`, `Conflict(id, engineMessage)`,
`EngineError(status, engineMessage)`.

| Call | Engine endpoint (versioned ones under `/v1.44`) | Returns | Failures |
|---|---|---|---|
| `ping()` | `GET /_ping`, `GET /version` | `EngineVersion(version, apiVersion, minApiVersion)` | `SocketNotFound`, `SocketPermissionDenied`, `ApiTooOld` |
| `containers(labels)` | `GET /containers/json?all=true&filters=` | `ContainerSummary` per container, running or not | `EngineError` |
| `inspect(id)` | `GET /containers/{id}/json` | `ContainerDetails`: state, labels, published ports | `NoSuchContainer` |
| `pause(id)` / `unpause(id)` | `POST /containers/{id}/pause`, `/unpause` | — | `NoSuchContainer`; `Conflict` for a second pause; `EngineError(500)` for unpause of a container that is not paused |
| `stop(id, timeout)` / `start(id)` / `kill(id)` | `POST /containers/{id}/stop?t=`, `/start`, `/kill` | — | `NoSuchContainer`; `Conflict` for kill of a stopped container; a second stop or start succeeds (`304`) |
| `exec(id, command)` | `POST /containers/{id}/exec`, `POST /exec/{id}/start` (read off the socket), `GET /exec/{id}/json` | `ExecResult(exitCode, stdout, stderr)` | `NoSuchContainer`; `Conflict` when the container is not running |
| `logs(id, tail)` | `GET /containers/{id}/logs?stdout=1&stderr=1&tail=` | `ContainerLogs(stdout, stderr)` | `NoSuchContainer` |

`DockerEngine.fromEnvironment()` reads `DOCKER_HOST`; `DockerEngine.socketPathOf` is the parsing. The
engine's answers these map from were measured before the code was written:
[research §1.2](../research/research-architecture.md).

## 2a. Code anchors

| File | What is there |
|---|---|
| `kontainer-docker/src/commonMain/kotlin/io/github/youndie/kontainer/docker/DockerEngine.kt` | every call, the request and the error mapping |
| `kontainer-docker/src/commonMain/kotlin/io/github/youndie/kontainer/docker/DockerError.kt` | the error type |
| `kontainer-docker/src/commonMain/kotlin/io/github/youndie/kontainer/docker/Containers.kt` | container models and the engine JSON they are read from |
| `kontainer-docker/src/commonMain/kotlin/io/github/youndie/kontainer/docker/Streams.kt` | `ExecResult`, `ContainerLogs`, the frame parser |
| `kontainer-docker/src/commonMain/kotlin/io/github/youndie/kontainer/docker/RawResponse.kt` | the one request read off the socket, and why |
| `kontainer-docker/src/linuxX64Main/kotlin/io/github/youndie/kontainer/docker/Platform.linuxX64.kt` | the socket preflight and the environment |
| `kontainer-docker/src/linuxX64Test/kotlin/io/github/youndie/kontainer/docker/` | tests against the real engine; `DockerCli` sets them up |

## 3. How it is built

- **The API version is in the path, fixed at `/v1.44`.** It is the minimum the build box's engine
  accepts; pinning it makes a newer field a visible change instead of a runtime surprise (research D8).
- **Two transports over one socket.** Every call but one goes through `HttpClient(CIO)` with
  `unixSocket(path)` per request (research H1, confirmed by B-01). `exec/start` answers with neither a
  length nor chunked encoding and never with `Connection: close`, so its body ends only with the
  connection; CIO refuses such a response, and `RawResponse.kt` writes the request and reads the socket
  to its end instead (B-03). The obvious fix — asking for `Connection: close` — was measured and does
  not change the answer.
- **The socket is checked before each request.** CIO's own error names the path of a missing socket but
  not the reason, and a missing socket and a forbidden one call for different fixes.
- **Exec output is always framed** — `[stream, 0, 0, 0, size big-endian u32]`, stdout 1, stderr 2 —
  whatever its content type says; a log is framed only when its content type is
  `application/vnd.docker.multiplexed-stream`, which is how a container started with a TTY is told apart.
  The exit code is read from `GET /exec/{id}/json`, asked again while the engine still reports the
  command running just after its output ended.

## 4. Dependencies

| Kind | Name | What for |
|---|---|---|
| External | Docker Engine, API 1.44 or newer | everything |
| Library | Ktor client, CIO engine, 3.6.0 | HTTP over the unix socket |
| Library | ktor-network, 3.6.0 (already under CIO) | the exec request read to the close |
| Library | kotlinx.serialization-json | engine JSON, read as a tree |

## 5. Infrastructure and publishing

- **Artefact:** `io.github.youndie.kontainer:kontainer-docker`, `linuxX64`, to reposilite — B-10.
- **CI:** none yet; the repository has no remote (B-13). The suite runs on the Linux build box.

## 6. Local setup

```bash
./gradlew :kontainer-docker:linuxX64Test
```

Needs a Docker Engine on the machine, read-write access to its socket (the `docker` group), and
`postgres:18-alpine` (pulled on first use). Tests run against the real engine; there is no fake, and an
absent engine fails the first one.

## 7. Configuration

| Key | Description | Required |
|---|---|---|
| `DOCKER_HOST` | `unix://<path>`; otherwise `/var/run/docker.sock`; any other scheme is `UnsupportedHost` | no |

## 8. Quirks

- **A paused container's published port still accepts TCP.** The engine's proxy answers the connect;
  nothing behind it answers anything else. Measured: [research §1.2](../research/research-architecture.md).
- **The engine's codes for "already in that state" differ per call** (B-02): `304` for a second stop or
  start, `409` for a second pause or a kill of a stopped container, and `500 … is not paused` for unpause
  of a running one. They are reported as the engine said them; an idempotent unpause has to decide by
  the container's state.
- **`exec/start` is labelled `application/vnd.docker.raw-stream` and is framed all the same** (B-03). The
  label on logs is truthful; the one on exec output is not.
- **A stopped container publishes no ports:** `inspect` returns an empty list until it runs again.
- **Across stdout and stderr, a log's order is the order the lines arrived in**, which B-03 saw differ
  from the order they were written; `tail` counts that stored order.
