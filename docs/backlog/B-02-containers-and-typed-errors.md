---
id: B-02
title: "Containers by label, inspect, pause, stop, start, kill, with typed errors"
status: done
priority: P1
size: M
stage: stage-1-engine
blocked_by: [B-01]
---

# B-02 — Containers by label, inspect, pause, stop, start, kill, with typed errors

kafkakn pauses its broker with `system("docker pause kafkakn-broker")` and learns only an exit code. The engine says more than that — `409` for a container already paused, `404` with the name for one that does not exist (measured, research §1.2) — and a test deciding what went wrong needs those answers as types.

Feature: `feature-engine-client` (drafted in the open documentation pull request).

- **One sealed error type for the module**: `NoSuchContainer`, `Conflict` (with the engine's message), `EngineError(status, message)`, `SocketNotFound`, `SocketPermissionDenied`, `ApiTooOld`. Because a test that catches "something failed" cannot tell a missing fixture from a frozen one.
- Containers are found by compose labels (`com.docker.compose.project`, `…service`), not by name: a name is exactly what two projects collided on (kafkakn B-97).
- Rejected: returning the raw status code. Every caller would map it again, differently.
- Not covered: exec and logs (B-03).

- AC: against a real container, a second `pause` fails with `Conflict`, an unknown id with `NoSuchContainer` naming the id, and `inspect` returns the published host port that `docker port` prints.
- AC: `stop` then `start` leaves the container running, observed through `inspect`.
- Anchors: `kontainer-docker/src/commonMain/kotlin/io/github/youndie/kontainer/docker/`

## Findings (2026-09-29)

- **Measured before coding** (curl, the build box): a second `start` or `stop` answers `304`, `kill` of a
  stopped container `409`, and `unpause` of one that is not paused **`500 … is not paused`** — not the
  `409` the drafts assumed. The client maps `404`/`409` to `NoSuchContainer`/`Conflict`, takes `304` as
  success, and leaves the `500` an `EngineError`; the research says why (Consequence 6a).
- **Acceptance, all on the build box against `postgres:18-alpine`** (`ContainersTest`, 5 tests, and
  `PingTest`, 4, green in `./gradlew check`): a second `pause` is `Conflict`; an unknown id is
  `NoSuchContainer` naming it; `inspect` reports the host port exactly as `docker port` prints it; stop
  then start leaves the container running, seen through `inspect`; found by label and only by label.
  No test container is left behind (`docker ps -a --filter label=kontainer.test` is empty after each run).
- **Mutations, each killed by the test it belongs to:** `409` mapped as `410` failed both conflict tests;
  `304` not taken as success failed `stop_then_start_leaves_it_running`; the inspected host port off by one
  failed `inspect_reports_the_host_port_docker_port_prints`; the label filter dropped failed
  `containers_are_found_by_label_and_only_by_label`. A first form of the `409` mutant (`false` in the
  `when`) did not compile under warnings-as-errors and is not counted.
- The client's per-request timeout is off (`requestTimeout = 0`): `stop` waits out the grace period it is
  given, and a bound is the caller's `withTimeout`.
