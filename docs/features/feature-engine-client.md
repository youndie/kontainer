---
id: feature-engine-client
title: A Docker Engine client for native tests
type: feature
status: active
owner: unassigned
involved_services: [kontainer-docker]
client_entries: []
api: []
tags: [docker]
---

# A Docker Engine client for native tests

## 1. Overview

A `linuxX64` test talks to the Docker Engine over its unix socket and gets answers it can act on: which
containers belong to a fixture, which host port a service published, whether a pause took effect, what
a command inside the container printed and how it exited. Today a native test can only run
`system("docker …")` and read an exit code (research §1.1).

It is its own module, [kontainer-docker](../services/kontainer-docker.md), so a repository that only wants to
pause a container it already has does not take the fixture layer with it.

Its first consumers inside this repository are the fixture features, still drafted; outside it, kafkakn's
`brokerPaused` is one call (B-11).

## 2. Business rules

* Every request carries `/v1.44` in its path; there is no version negotiation (research D8).
* An engine `404` is `NoSuchContainer`, a `409` is `Conflict` with the engine's message, any other
  non-2xx is `EngineError(status, message)`.
* A missing socket and a socket without permission are two different errors, each naming the path.
* Exec output keeps stdout and stderr apart and returns the exit code.

## 4. Code anchors

| Service | Code |
|---|---|
| kontainer-docker | `kontainer-docker/src/commonMain/kotlin/io/github/youndie/kontainer/docker/DockerEngine.kt` — every call and the error mapping |
| kontainer-docker | `kontainer-docker/src/commonMain/kotlin/io/github/youndie/kontainer/docker/Streams.kt` — the frame parser |
| kontainer-docker | `kontainer-docker/src/linuxX64Test/kotlin/io/github/youndie/kontainer/docker/` — the scenarios, against the real engine |

## 5. Scenarios (BDD / test cases)

Verified against Docker Engine 29.1.3 on the Linux build box (B-01, B-02, B-03).

### Scenario: ping
* **Given:** a running Docker Engine and its socket readable by the test.
* **When:** the test calls `ping()`.
* **Then:** it returns the server's version and an API version of at least 1.44.
* **Automated:** `PingTest::ping_reports_an_engine_that_speaks_the_pinned_api`

### Scenario: no socket
* **Given:** `DOCKER_HOST=unix:///nonexistent.sock`.
* **When:** the test calls `ping()`.
* **Then:** it fails with `SocketNotFound` naming `/nonexistent.sock` — not with the client's own
  `IOException: Failed to connect to UnixSocketAddress(/nonexistent.sock)`, which names the path but not the reason.
* **Automated:** `PingTest::a_missing_socket_is_named_rather_than_reported_as_io`

### Scenario: a second pause
* **Given:** a container already paused.
* **When:** the test calls `pause` on it again.
* **Then:** it fails with `Conflict` carrying the engine's message (the engine answers `409`).
* **Automated:** `ContainersTest::a_second_pause_is_a_conflict_and_an_unknown_id_is_named`

### Scenario: exec keeps the streams apart
* **Given:** a running container with `sh`.
* **When:** the test runs `exec(id, ["sh", "-c", "echo out; echo err >&2; exit 3"])`.
* **Then:** the result has `exitCode = 3`, `stdout = "out\n"`, `stderr = "err\n"`.
* **Automated:** `StreamsTest::exec_keeps_stdout_and_stderr_apart_and_returns_the_exit_code`

## 6. Out of scope

* Pulling or building images; Swarm; networks and volumes as objects.
* Attaching to a container's stdin; TTY execs.
* Any target but `linuxX64` in v1.

## 7. Quirks

* **Exec output does not come through the HTTP client.** The engine ends that answer only by closing
  the connection, which Ktor's CIO client refuses; one request is read off the socket instead (B-03,
  [kontainer-docker](../services/kontainer-docker.md) §3).
* **Unpause of a running container is `500`, not `409`** (B-02), so it is an `EngineError`, not a
  `Conflict`.
