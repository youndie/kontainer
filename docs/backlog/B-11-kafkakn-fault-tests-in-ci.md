---
id: B-11
title: "kafkakn's seven fault tests run in CI on their own broker"
status: done
priority: P1
size: M
stage: stage-3-consumers
epic: feature-fault-injection
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

## Findings (2026-09-30)

- **Done in kafkakn** as its item B-105, youndie/kafkakn#134 (merged as 22a9876): on `linuxX64` a fault test gets
  a broker of its own — `ci/broker/fault-broker.compose.yml` through `io.github.youndie.kontainer:kontainer:0.1.0.2`,
  its own compose project and host port, ready by the Kafka probe, removed after the test — and runs in the suite
  with no switch. The JVM, a Mac and arm64 keep the shared broker behind the switches (research D2).
- **Acceptance:** `ci/suite/run.sh`, on the build box and in kafkakn's CI, green on both arms (`jvmTest` 178,
  `linuxX64Test` 169) with the arms agreeing on 120 observations. On the build box the native arm's facts show the
  fault tests ran on an owned broker (a host port kontainer chose, not 9092) while the JVM's read `not asked`, and
  `docker events` shows no pause, stop, kill or die of the shared `kafkakn-broker` during the run.
- **Five of the seven, not seven.** `CloseWithBrokerGoneTest`'s two tests are measurements with nothing asserted,
  one per `KAFKAKN_CLOSE_VARIANT` and minutes long; they now get an owned broker when a variant is named and stay out
  of the suite otherwise. The acceptance line said seven; it was written before those two were read.
- **What the fault broker needed that the tests here did not:** a second listener. With one, the advertised address
  names the host port kontainer chose, which does not exist inside the container, so the broker's own traffic had
  nowhere to go. The fault broker advertises `PLAINTEXT` for clients and uses `INTERNAL` between itself.
- **Observations are compared only when both arms ran:** kafkakn's `compare-arms.sh` diffs the two arms' files
  whole, and a native-only test would read as a disagreement. Without the switch the native arm keeps them as facts.
