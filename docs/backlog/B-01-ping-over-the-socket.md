---
id: B-01
title: "A linuxX64 test pings Docker over the unix socket"
status: done
priority: P1
size: S
stage: stage-1-engine
epic: feature-engine-client
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

## Findings (2026-09-29)

- **H1 confirmed.** `HttpClient(CIO)` with `unixSocket(path)` reaches the engine on `linuxX64`:
  `PingTest` printed `Docker Engine 29.1.3, API 1.52 (min 1.44)` on the build box
  (`TEST-linuxX64Test…PingTest.xml`, 4 tests, 0 failures, 23:16:00 +0200). `./gradlew check` green there,
  ktlint included.
- **The negative control ran.** `unix:///nonexistent.sock` fails with `SocketNotFound` naming the path.
- **Mutations, each killed by the test it belongs to:** removing `unixSocket(socketPath)` failed
  `ping_reports_an_engine_that_speaks_the_pinned_api`; disabling the existence check failed
  `a_missing_socket_is_named_rather_than_reported_as_io` (the path then fell into the permission branch).
- **A claim corrected.** With no preflight at all, CIO reports
  `kotlinx.io.IOException: Failed to connect to UnixSocketAddress(/nonexistent.sock)` — it names the path,
  not the reason. The research said otherwise; it is corrected at the point of divergence.
- **The `ubuntu-latest` half of the acceptance could not run:** the owner decided on 2026-09-29 to keep the
  repository local, so there is no hosted runner. That half, with H2, is B-13. The CI job belongs there too:
  a workflow nothing can run would be written and never called.
- Everything the build needs is copied from s3kn (wrapper 9.7.1, `.editorconfig`) and sborka 0.4.0.93, whose
  `wip` catalog carries Kotlin 2.4.20.
