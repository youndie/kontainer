---
id: B-02
title: "Containers by label, inspect, pause, stop, start, kill, with typed errors"
status: wip
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
