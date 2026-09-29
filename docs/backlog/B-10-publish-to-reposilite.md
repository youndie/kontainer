---
id: B-10
title: "Publish kontainer-docker and kontainer to reposilite"
status: open
priority: P1
size: S
stage: stage-3-consumers
blocked_by: [B-07]
---

# B-10 — Publish kontainer-docker and kontainer to reposilite

kafkakn resolves its dependencies from published artefacts; a library that exists only in this repository cannot be tried there.

Feature: `feature-engine-client` (drafted in the open documentation pull request).

- **reposilite, not Central**, for v1 (decision of the user), through sborka's publish convention, under `io.github.youndie.kontainer`.
- Rejected: an included build in kafkakn — it would test the consumer against a tree, not a version.

- AC: a clean project resolves `io.github.youndie.kontainer:kontainer` and `:kontainer-docker` for `linuxX64` from reposilite and links a test against them.
- Anchors: `kontainer/build.gradle.kts`, `kontainer-docker/build.gradle.kts`
