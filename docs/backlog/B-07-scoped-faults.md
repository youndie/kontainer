---
id: B-07
title: "paused {} and stopped {} restore the service, and refuse on a shared fixture"
status: open
priority: P1
size: S
stage: stage-2-fixture
blocked_by: [B-05, B-06]
---

# B-07 — paused {} and stopped {} restore the service, and refuse on a shared fixture

kafkakn's fault tests pair `brokerPaused(true)` with `brokerPaused(false)` in `finally` by hand, and then wait for nothing: the next test finds a broker that may still be waking. They also act on the shared broker, which is why they are gated.

Feature: `feature-fault-injection` (drafted in the open documentation pull request).

- **A scoped call restores the service in `finally` and waits for readiness before returning**, so the next line of the test starts against a ready service.
- **Faults refuse on a shared fixture** with `SharedFixture` (research D7).
- `unpause` of a service that is not paused is not an error (as in kafkakn today); `pause` of a paused one is `Conflict`.

- AC: an exception thrown inside `paused("pg") { … }` leaves the service unpaused and ready, and reaches the caller unchanged.
- AC: `pause` on a shared fixture fails with `SharedFixture` and the container is untouched.
- AC: inside `stopped("pg") { … }` connections to the port are refused; after it, Postgres answers on the same port.
- Anchors: `kontainer/src/commonMain/kotlin/io/github/youndie/kontainer/`
