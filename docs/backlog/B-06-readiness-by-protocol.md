---
id: B-06
title: "Readiness is a protocol answer from the host through the published port"
status: done
priority: P1
size: M
stage: stage-2-fixture
epic: feature-readiness
blocked_by: [B-02]
---

# B-06 — Readiness is a protocol answer from the host through the published port

Every wrong readiness check in the portfolio was one of three: `compose up --wait` (Healthy while crash-looping), a probe inside the container (early by 0.7–0.9 s), a TCP connect (accepted by the proxy even while the container is paused). All three are in research §1.

Feature: [feature-readiness](../features/feature-readiness.md).

- **Probes in v1: `kafka` (ApiVersions), `postgres` (reply to a startup packet), `http` (expected status on a path), `custom`.** Decision of the user; Mongo and SMTP come later.
- **The Kafka probe does not use kafkakn**, because kafkakn is tested through kontainer.
- A timeout fails with the last cause and the tail of the service's log.
- Rejected: TCP connect as a first stage "to save time" — it answers yes to a frozen container.

- AC: `awaitReady` on a fresh Postgres returns no earlier than `pg_isready` from the host answers on that port.
- AC: on a paused Postgres, `awaitReady(timeout = 3.seconds)` fails with `NotReady` while TCP connects succeed.
- AC: the `kafka` probe pointed at Postgres fails with a cause saying the answer was not Kafka — the negative control for research H3.
- AC: with kafkakn's compose file minus `KAFKA_CONTROLLER_LISTENER_NAMES`, `awaitReady(kafka)` times out and the log tail names the cause.
- Anchors: `kontainer/src/commonMain/kotlin/io/github/youndie/kontainer/`

## Findings (2026-09-30)

- **Measured before coding:** without `KAFKA_CONTROLLER_LISTENER_NAMES` the broker container exits with code 1
  about two seconds after start, and its log names the missing key. So `awaitReady` fails at once on a
  container that is not running, with the exit code and the end of the log, and `up`'s port check does the same.
- **The probes:** Kafka ApiVersions v0 over raw TCP (no kafkakn), a Postgres protocol 3.0 startup packet (`R`
  or any refusal but `57P03` is ready), HTTP status through the CIO client, and `custom`. The wait runs on real
  time inside `runTest`.
- **Acceptance, on the build box** (`ReadinessTest`, 6 tests; `./gradlew check` green): `awaitReady` on Postgres
  returns and `pg_isready` from the host answers at once; a paused Postgres is `NotReady` ("no answer … within
  2s") while TCP connects succeed; the Kafka probe says no to Postgres and to an echo, and yes to a broker whose
  own tool agrees; a broker that never started is `NotReady` within seconds with `controller.listener.names` in
  the log tail; the HTTP probe wants its status (`/nowhere` answered 404).
- **Mutations:** the Postgres probe reading nothing, the HTTP status unchecked, the running check in `awaitReady`
  off (killed by the timing assertion added for it — without it, the outcome after the 60 s timeout was the same)
  and the correlation check off were killed. Two first mutant runs had substitutions that did not apply (a line
  the formatter had wrapped) and are not counted.
- **A redundant guard removed:** the response-size check in the Kafka probe survived every mutant alone, because
  the correlation check caught the same services. It is gone, and the echo test exercises what remains.
- `feature-readiness` and the `kontainer` module document land on `main` as `active`, the module document
  describing only what exists; faults (B-07) and reaping (B-08) are named as not built yet.
