---
id: B-08
title: "Fixtures left by a dead test process are removed on the next up"
status: wip
priority: P2
size: S
stage: stage-2-fixture
blocked_by: [B-04]
---

# B-08 — Fixtures left by a dead test process are removed on the next up

A native test process killed by a timeout or a crash runs no cleanup, and its owned fixture keeps its ports and memory on a box shared by several projects (research Risk 3).

Feature: `feature-compose-fixture` (drafted in the open documentation pull request).

- **The owner label is the key**: a project whose `kontainer.owner` names this host and a pid that is not alive is removed with `down -v`, and the removal is logged by project name.
- Rejected: a reaper container (as Testcontainers' Ryuk) — one more image and a socket mount, for a problem the next run can solve.
- Not covered: fixtures owned by another host.

- AC: a fixture whose owner pid does not exist is removed by the next `up` on the same host, and the log names it.
- AC: a fixture owned by a live process is left alone.
- Anchors: `kontainer/src/commonMain/kotlin/io/github/youndie/kontainer/`
