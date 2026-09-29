---
id: B-09
title: "Measure how long an owned Kafka broker takes to become ready"
status: open
priority: P1
size: S
stage: stage-2-fixture
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
