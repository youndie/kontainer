---
id: B-07
title: "paused {} and stopped {} restore the service, and refuse on a shared fixture"
status: done
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

## Findings (2026-09-30)

- **The signature needs the port and the probe:** `paused(service, containerPort, probe) { }` and
  `stopped(service, containerPort, probe, grace) { }`, because "restored" means "answers again", which only a
  probe on a port can tell. The drafts' `paused(service) { }` is corrected.
- **Restore always, then the block's own outcome:** unpause or start plus `awaitReady` run under
  `NonCancellable`, also after an exception or a cancellation; the block's exception reaches the caller as the
  same object, a failed restore added as suppressed. kapkan's `cancellation-swallowed` rule flagged the first
  version (`runCatching`); the catch that remains is suppressed with its reason.
- **Acceptance, on the build box** (`FaultsTest`, 5 tests; `./gradlew check` green): an exception inside
  `paused { }` is the same object outside, and the service is unpaused and answers `pg_isready` at once; inside
  `paused { }` the probe gets no answer; inside `stopped { }` the port refuses TCP and after it Postgres answers
  on the same port; `pause` and `stopped { }` on a shared fixture fail with `SharedFixture` and the container
  stays running and unpaused; `unpause` of a running service is no error.
- **Mutations, each killed:** no restore after a throwing block, the shared check off, the forgiving unpause
  removed, the exception rewrapped, and — only after a test change — the wait after the restore removed. That
  last one first survived: the `pg_isready` witness runs in a container of its own and took long enough to hide
  a return that came before Postgres was back. The stopped test now asks Postgres in-process at once.
- **Not observable:** after `paused { }` Postgres answers the instant it is thawed, so the wait there cannot be
  told from no wait; it is the same code path as `stopped { }`, which is.
- `feature-fault-injection` stays a draft: two of its scenarios need kafkakn (B-11).
