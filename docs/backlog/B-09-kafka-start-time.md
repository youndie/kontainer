---
id: B-09
title: "Measure how long an owned Kafka broker takes to become ready"
status: done
priority: P1
size: S
stage: stage-2-fixture
epic: feature-fault-injection
blocked_by: [B-06]
---

# B-09 — Measure how long an owned Kafka broker takes to become ready

Whether kafkakn can give each fault test its own broker depends on how long one takes to answer ApiVersions, and nobody has measured it (research Risk 4).

Feature: `feature-fault-injection` (drafted in the open documentation pull request).

- **Five cold starts each on the build box and on `ubuntu-latest`**, from `up` to the `kafka` probe answering, with the image already pulled; the pull is reported separately.
- The number decides between a fixture per test and one per test class, and the decision is written into research.

- AC: the median and spread of the five starts on each machine are in research §1, with how they were measured.
- AC: research Risk 4 is closed with a decision: per test or per class.
- Anchors: `kontainer/src/linuxX64Test/kotlin/io/github/youndie/kontainer/`

## Findings (2026-09-30)

- **Measured on the build box** (five runs of `KafkaStartTest` with `--rerun`, each a new owned project, the
  image already pulled): ready in 5 341, 5 058, 5 014, 5 071 and 5 047 ms — median 5.06 s; `up` itself about
  1.8 s. The box was under load (average 8–10 on 20 cores). The table and the two limits on the numbers are in
  research §1.4.
- **Decision:** a broker per fault test (research Consequence 8). kafkakn's seven fault tests add about 35 s.
- **`KafkaStartTest` stays in the suite** as a regression guard (ready within 60 s) that also prints the figure
  each run; the bound is far above the measurement on purpose. It can go red: with the bound at 1 s the test
  failed (control run, reverted).
- **Not done here:** the same five runs on `ubuntu-latest`. The repository has no remote (owner's decision), so
  that half joins B-13.
