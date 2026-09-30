---
id: B-10
title: "Publish kontainer-docker and kontainer to reposilite"
status: done
priority: P1
size: S
stage: stage-3-consumers
epic: feature-engine-client
blocked_by: [B-07]
---

# B-10 — Publish kontainer-docker and kontainer to reposilite

kafkakn resolves its dependencies from published artefacts; a library that exists only in this repository cannot be tried there.

Feature: [feature-engine-client](../features/feature-engine-client.md).

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

## Findings (2026-09-30)

- **The owner's answer:** the publishing token is made by the owner's token workflow, which put
  `REPOSILITE_USER` and `REPOSILITE_SECRET` into this repository's secrets (a token named `kontainer`, allowed
  to write `/snapshots/io/github/youndie/kontainer/` and nothing else). Nothing was published from the build box.
- **Publishing:** both modules take sborka's `publish` convention. `publish snapshot` runs the suite and
  publishes on every push to `main`; its `consumer` job resolves `io.github.youndie.kontainer:kontainer` and
  `:kontainer-docker` from the server with `youndie/proba`. The publications also assemble in `build` on every
  pull request (`publishToMavenLocal`: a root and a `linuxx64` publication per module, with `.klib`, `.module`
  and `.pom`).
- **Acceptance:** the first run on `main` (9196b58) published and its consumer resolved both coordinates from
  reposilite — `maven-metadata.xml` of `kontainer`, `kontainer-docker` and `kontainer-linuxx64` all name it.
- **A version defect, found on that first publish:** `gradle.properties` held `0.1.0-SNAPSHOT`, and the
  determine-version action appends the run number to whatever it finds there, so it went out as
  `0.1.0-SNAPSHOT.1`. The head is `0.1.0` now, as in chronik (`0.2.0` → `0.2.0.19`); the next publish is
  `0.1.0.<run>`. The odd version stays on the server.
