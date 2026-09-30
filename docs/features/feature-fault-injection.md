---
id: feature-fault-injection
title: Faults in the middle of a test, on a fixture the test owns
type: feature
status: active
owner: unassigned
involved_services: [kontainer, kontainer-docker]
client_entries: []
api: []
tags: [faults]
---

# Faults in the middle of a test, on a fixture the test owns

## 1. Overview

A test pauses, stops or kills a service of its own fixture in the middle of what it is checking, and gets
it back ready before the next line runs. This is what kontainer exists for: kafkakn's seven fault tests
already pause and stop their broker, but the broker is the one the whole suite uses, so they are gated
behind switches and never run in CI ([research §1.1, D1](../research/research-architecture.md)).

## 2. Business rules

* Faults are allowed only on an owned fixture; on a shared one they fail with `SharedFixture` and touch
  nothing (research D7).
* `paused(service, containerPort, probe) { … }` and `stopped(service, containerPort, probe, grace) { … }`
  restore the service — also when the block throws or is cancelled — and wait for the probe
  ([feature-readiness](feature-readiness.md)) before returning (B-07).
* `unpause` of a service that is not paused is not an error; `pause` of a paused one is `Conflict`.
* The port does not change across `stopped { }` ([feature-compose-fixture](feature-compose-fixture.md)).

## 4. Code anchors

| Service | Code |
|---|---|
| kontainer | `kontainer/src/commonMain/kotlin/io/github/youndie/kontainer/Fixture.kt` — `pause`, `stop`, `paused { }`, `stopped { }` |
| kontainer | `kontainer/src/linuxX64Test/kotlin/io/github/youndie/kontainer/FaultsTest.kt` — the fixture's own scenarios |
| kafkakn | `youndie/kafkakn@22a9876!/kafkakn-core/src/linuxX64Test/kotlin/io/github/youndie/kafkakn/FaultBroker.linuxX64.kt` — a fault test's own broker |

## 5. Scenarios (BDD / test cases)

Verified on the Linux build box and in CI: kontainer's `FaultsTest` (B-07) and kafkakn's own suite, whose fault
tests take an owned broker from kontainer since kafkakn B-105 (B-11).

### Scenario: a paused broker, and then a working one
* **Given:** an owned Kafka fixture and a kafkakn producer connected to it.
* **When:** the test pauses the broker (`fixture.pause("broker")`), sends, and unpauses it.
* **Then:** while paused the broker answers nothing and the send is cut; after it the record lands — the end
  offset counts it.
* **Automated:** kafkakn CancelledSendTest

### Scenario: a stopped service comes back where it was
* **Given:** an owned Postgres fixture.
* **When:** the test runs `stopped("pg", 5432, Probe.postgres()) { … }`.
* **Then:** inside the block connections to the port are refused; after it Postgres answers on the same port.
* **Automated:** kontainer FaultsTest

### Scenario: an exception inside the block
* **Given:** an owned Postgres fixture.
* **When:** the test runs `paused("pg", 5432, Probe.postgres()) { error("x") }`.
* **Then:** the service is unpaused and ready, and the exception `x` reaches the caller unchanged.
* **Automated:** kontainer FaultsTest

### Scenario: a fault on a shared fixture
* **Given:** a shared fixture.
* **When:** the test calls `pause("broker")`.
* **Then:** it fails with `SharedFixture`, and the broker is not paused.
* **Automated:** kontainer FaultsTest

### Scenario: kafkakn's fault tests in CI
* **Given:** kafkakn's native test suite with its broker from an owned kontainer fixture.
* **When:** CI runs `linuxX64Test`.
* **Then:** `EnqueueTest`, `CancelledSendTest` and `StoppedBrokerTest` — five of the seven fault tests — run
  without `KAFKAKN_BROKER_CONTROL` / `KAFKAKN_BROKER_STOP`, each on a broker of its own, and the shared broker
  the rest of the suite uses is never paused or stopped (B-11, youndie/kafkakn#134). The other two are
  `CloseWithBrokerGoneTest`'s measurements, which stay behind `KAFKAKN_CLOSE_VARIANT`.
* **Automated:** kafkakn StoppedBrokerTest

## 6. Out of scope

* Network faults (latency, loss, partitions).
* The JVM half of kafkakn's fault tests: it stays on `ProcessBuilder` behind its switch until kontainer has a
  JVM target (research D2).

## 7. Quirks

* **Whether a broker per fault test is affordable is not known yet** — B-09 measures the start time; the
  fallback is one owned fixture per test class (research Risk 4).
