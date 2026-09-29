---
id: feature-readiness
title: Ready means the service answered its protocol
type: feature
status: active
owner: unassigned
involved_services: [kontainer]
client_entries: []
api: []
tags: [readiness]
---

# Ready means the service answered its protocol

## 1. Overview

A test waits until the service answers **its own protocol**, from the host, through the published port
the test will use — not until compose says Healthy, not until a tool inside the container says ready,
and not until a TCP connect succeeds. Each of those three has been wrong in a neighbouring repository or
in a measurement ([research §1](../research/research-architecture.md)).

## 2. Business rules

* v1 probes: `Probe.kafka()` (an ApiVersions v0 request, answered with the same correlation id and no
  error), `Probe.postgres(user)` (a startup packet; asked to authenticate or refused for any reason but
  `57P03` "starting up" is ready), `Probe.http(path, status)`, and `Probe.custom { host, port -> }`.
* A probe is asked through the host port the fixture chose (`fixture.port(service, containerPort)`).
* Each attempt gets two seconds by default; attempts repeat until the timeout, which fails with
  `FixtureError.NotReady(service, lastCause, logTail)`.
* A container that is not running fails at once, with its status and exit code — nothing will answer it.
* The wait runs on real time even inside `runTest`.
* The Kafka probe does not use kafkakn: kafkakn is tested through kontainer.

## 4. Code anchors

| Service | Code |
|---|---|
| kontainer | `kontainer/src/commonMain/kotlin/io/github/youndie/kontainer/Probe.kt` — the probes |
| kontainer | `kontainer/src/commonMain/kotlin/io/github/youndie/kontainer/Fixture.kt` — `awaitReady` |
| kontainer | `kontainer/src/linuxX64Test/kotlin/io/github/youndie/kontainer/ReadinessTest.kt` — the scenarios |

## 5. Scenarios (BDD / test cases)

Verified against Postgres 18, Kafka 4.3.1 and nginx on the Linux build box (B-06).

### Scenario: ready
* **Given:** an owned Postgres fixture just brought up.
* **When:** the test calls `awaitReady("pg", 5432, Probe.postgres())`.
* **Then:** it returns no earlier than `pg_isready` from the host answers on that port.
* **Automated:** `ReadinessTest::postgres_is_ready_only_when_pg_isready_agrees_and_not_while_paused`

### Scenario: paused is not ready
* **Given:** an owned Postgres fixture whose service is paused.
* **When:** the test calls `awaitReady("pg", 5432, Probe.postgres(), timeout = 3.seconds)`.
* **Then:** it fails with `NotReady` ("no answer on 127.0.0.1:<port> within 2s"), although TCP connects to
  the port succeed.
* **Automated:** `ReadinessTest::postgres_is_ready_only_when_pg_isready_agrees_and_not_while_paused`

### Scenario: the probe can say no
* **Given:** an owned Postgres fixture that is ready, and an echo server that returns what it is sent.
* **When:** the test points `Probe.kafka()` at each.
* **Then:** both fail with `NotReady` whose cause says the answer is not Kafka (a correlation id that is
  not the one sent) — and against a real broker the same probe succeeds, which the broker's own
  `kafka-broker-api-versions.sh` confirms.
* **Automated:** `ReadinessTest::the_kafka_probe_says_no_to_postgres`

### Scenario: a broker that never started
* **Given:** kafkakn's broker settings without `KAFKA_CONTROLLER_LISTENER_NAMES`.
* **When:** the test brings it up and calls `awaitReady("broker", 9092, Probe.kafka())`.
* **Then:** it fails with `NotReady` within seconds — the container has exited with code 1 — and the log
  tail carries the broker's own `Missing required configuration "controller.listener.names"`, where
  `docker compose up --wait` reported such a container Healthy (kafkakn).
* **Automated:** `ReadinessTest::a_broker_that_never_started_is_not_ready_and_its_log_says_why`

## 6. Out of scope

* Mongo and SMTP probes — after v1 (decision of the user).
* Probing from inside the container (`docker exec … pg_isready`): early by 0.7–0.9 s in petich B-17.

## 7. Quirks

* **A paused container accepts TCP.** A probe that stops at `connect()` reports a frozen service ready
  ([research §1.2](../research/research-architecture.md)).
* **A container that exits early can fail `up` rather than `awaitReady`**: `up` checks the published ports,
  finds the container stopped, and raises the same `NotReady` with the same log tail. Which one reports it
  depends on how fast the service dies.
* **The Kafka probe judges the correlation id, not the response size.** A size check beside it caught
  nothing the correlation check did not (B-06), so it was removed.
