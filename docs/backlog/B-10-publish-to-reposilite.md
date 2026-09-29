---
id: B-10
title: "Publish kontainer-docker and kontainer to reposilite"
status: question
priority: P1
size: S
stage: stage-3-consumers
epic: feature-engine-client
blocked_by: [B-07]
---

# B-10 — Publish kontainer-docker and kontainer to reposilite

kafkakn resolves its dependencies from published artefacts; a library that exists only in this repository cannot be tried there.

Feature: `feature-engine-client` (drafted in the open documentation pull request).

- **reposilite, not Central**, for v1 (decision of the user), through sborka's publish convention, under `io.github.youndie.kontainer`.
- Rejected: an included build in kafkakn — it would test the consumer against a tree, not a version.

- AC: a clean project resolves `io.github.youndie.kontainer:kontainer` and `:kontainer-docker` for `linuxX64` from reposilite and links a test against them.
- Anchors: `kontainer/build.gradle.kts`, `kontainer-docker/build.gradle.kts`

## Question (2026-09-30)

The loop reached this item and stopped short of it, because publishing is not local: it puts
`io.github.youndie.kontainer:kontainer` and `:kontainer-docker` (`0.1.0-SNAPSHOT`, `linuxX64`) on the portfolio's
reposilite, which the other repositories resolve from, while the owner asked on 2026-09-29 to keep this
repository local ("делай локально"). The POM would also name `youndie/kontainer` on GitHub, which does not exist.

What is ready: the build box has `REPOSILITE_USER` and `REPOSILITE_SECRET` in its environment (names checked,
values not read), and sborka's `publish` convention takes the snapshot repository by default.

Choices, for the owner:
1. **Publish the snapshot now from the build box.** B-11 and B-12 become reachable. B-11 then needs a pull
   request to kafkakn, which is public on GitHub — a second outward step, asked separately.
2. **Wait for the remote (B-13)** and publish from CI, as the rest of the portfolio does.
3. **Consume it without publishing**: kafkakn takes kontainer through `mavenLocal()` or an included build on
   the box for B-11's measurement, and publishing waits.

B-11 and B-12 are blocked on this item either way.
