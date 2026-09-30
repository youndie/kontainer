---
id: B-08
title: "Fixtures left by a dead test process are removed on the next up"
status: done
priority: P2
size: S
stage: stage-2-fixture
epic: feature-compose-fixture
blocked_by: [B-04]
---

# B-08 — Fixtures left by a dead test process are removed on the next up

A native test process killed by a timeout or a crash runs no cleanup, and its owned fixture keeps its ports and memory on a box shared by several projects (research Risk 3).

Feature: [feature-compose-fixture](../features/feature-compose-fixture.md).

- **The owner label is the key**: a project whose `kontainer.owner` names this host and a pid that is not alive is removed with `down -v`, and the removal is logged by project name.
- Rejected: a reaper container (as Testcontainers' Ryuk) — one more image and a socket mount, for a problem the next run can solve.
- Not covered: fixtures owned by another host.

- AC: a fixture whose owner pid does not exist is removed by the next `up` on the same host, and the log names it.
- AC: a fixture owned by a live process is left alone.
- Anchors: `kontainer/src/commonMain/kotlin/io/github/youndie/kontainer/`

## Findings (2026-09-30)

- **Only owned fixtures are reaped.** A shared fixture is meant to outlive the run that made it (kafkakn's broker
  stays up between runs), so its dead owner is no reason to remove it. The item did not say so; the code and the
  feature now do.
- **Liveness is `kill(pid, 0)`**, with `EPERM` counted as alive. A reused pid keeps a dead owner's fixture alive —
  the safe side; written as a quirk of the feature.
- **Acceptance, on the build box** (`ReapTest`, 4 tests; `./gradlew check` green): an owned leftover whose owner pid
  is gone is removed with its volume by the next `up`, printed and listed in `fixture.reaped`; a live owner's
  (the test runner's parent), another host's and a shared leftover are left alone.
- **Mutations, each killed:** the host check off, shared fixtures reaped too, liveness ignored, reaping off.
- With this item every scenario of `feature-compose-fixture` is automated, and it lands on `main` as `active`.
