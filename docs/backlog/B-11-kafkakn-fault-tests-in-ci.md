---
id: B-11
title: "kafkakn's seven fault tests run in CI on their own broker"
status: open
priority: P1
size: M
stage: stage-3-consumers
blocked_by: [B-09, B-10]
---

# B-11 — kafkakn's seven fault tests run in CI on their own broker

Seven kafkakn tests pause or stop the broker and are gated behind `KAFKAKN_BROKER_CONTROL` / `KAFKAKN_BROKER_STOP`, because the broker is shared with the suite; `ci/suite/run.sh` never sets the switches (research §1.1). This item is what kontainer exists for.

Feature: `feature-fault-injection` (drafted in the open documentation pull request).

- **The native arm takes its broker from an owned kontainer fixture**, built from kafkakn's own compose file with its ports as variables (research D5); the switches no longer gate the native arm.
- **The JVM arm stays as it is** — `ProcessBuilder` against the shared broker, behind its switch — until kontainer has a JVM target (research D2).
- The change is a pull request to kafkakn; this item tracks it and its result.

- AC: in kafkakn's CI, `linuxX64Test` runs all seven tests without switches, the native test count in the report grows by seven, and the shared broker the rest of the suite uses is not paused or stopped at any point.
- AC: the pull request's link and the CI run are recorded in this item.
- Anchors: `youndie/kafkakn@f2b75a8!/kafkakn-core/src/nativeTest/kotlin/io/github/youndie/kafkakn/Observations.native.kt`, `youndie/kafkakn@f2b75a8!/ci/broker/docker-compose.yml`
