---
id: B-06
title: "Readiness is a protocol answer from the host through the published port"
status: open
priority: P1
size: M
stage: stage-2-fixture
blocked_by: [B-02]
---

# B-06 — Readiness is a protocol answer from the host through the published port

Every wrong readiness check in the portfolio was one of three: `compose up --wait` (Healthy while crash-looping), a probe inside the container (early by 0.7–0.9 s), a TCP connect (accepted by the proxy even while the container is paused). All three are in research §1.

Feature: `feature-readiness` (drafted in the open documentation pull request).

- **Probes in v1: `kafka` (ApiVersions), `postgres` (reply to a startup packet), `http` (expected status on a path), `custom`.** Decision of the user; Mongo and SMTP come later.
- **The Kafka probe does not use kafkakn**, because kafkakn is tested through kontainer.
- A timeout fails with the last cause and the tail of the service's log.
- Rejected: TCP connect as a first stage "to save time" — it answers yes to a frozen container.

- AC: `awaitReady` on a fresh Postgres returns no earlier than `pg_isready` from the host answers on that port.
- AC: on a paused Postgres, `awaitReady(timeout = 3.seconds)` fails with `NotReady` while TCP connects succeed.
- AC: the `kafka` probe pointed at Postgres fails with a cause saying the answer was not Kafka — the negative control for research H3.
- AC: with kafkakn's compose file minus `KAFKA_CONTROLLER_LISTENER_NAMES`, `awaitReady(kafka)` times out and the log tail names the cause.
- Anchors: `kontainer/src/commonMain/kotlin/io/github/youndie/kontainer/`
