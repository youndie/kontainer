---
id: B-01
title: "A linuxX64 test pings Docker over the unix socket"
status: open
priority: P1
size: S
stage: stage-1-engine
---

# B-01 — A linuxX64 test pings Docker over the unix socket

Everything in kontainer rests on one unverified claim: that Ktor's CIO client speaks HTTP over a unix socket on `linuxX64` (research H1). The documentation says `unixSocket(path)` is a common API and that CIO supports it since Ktor 3.2; nobody here has run it on this target. The second unknown is the runner: whether `ubuntu-latest` accepts `/v1.44` and has Compose v2 (H2).

Feature: `feature-engine-client` (drafted in the open documentation pull request).

- **A spike with a real assertion, not a prototype.** One `linuxX64Test` calls `GET /_ping` and `GET /version` over `/var/run/docker.sock` (or `DOCKER_HOST=unix://…`) and asserts the server's API version is at least 1.44. It runs on the build box and in CI, because H2 is about CI.
- The Gradle build, sborka conventions and the CI job that runs `linuxX64Test` arrive here, since this is the first code.
- Rejected: starting with the fixture layer and "fixing the transport later". If H1 fails, the transport decides the shape of every call above it.
- Not covered: any endpoint beyond ping and version.

- AC: `linuxX64Test` prints the server's version and API version on the build box and on `ubuntu-latest`, and fails if the API is below 1.44.
- AC: with `DOCKER_HOST=unix:///nonexistent.sock` the call fails with an error naming that path — the negative control, run once and recorded in the PR.
- AC: research H1 and H2 are rewritten as facts or refutations, with where they were verified.
- Anchors: `kontainer-docker/src/commonMain/kotlin/io/github/youndie/kontainer/docker/`, `kontainer-docker/build.gradle.kts`, `.github/workflows/`
