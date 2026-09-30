---
id: B-13
title: "The suite runs on a hosted runner once the repository has a remote"
status: done
priority: P2
size: S
stage: stage-1-engine
epic: feature-engine-client
blocked_by: [B-01]
---

# B-13 — The suite runs on a hosted runner once the repository has a remote

B-01's acceptance asked for the ping test on `ubuntu-latest` as well as on the build box, because
research H2 — whether a hosted runner's engine accepts `/v1.44` and has Compose v2 — is about CI.
On 2026-09-29 the owner decided the repository stays local for now ("делай локально"), so there is
no runner to ask. B-01 closed on the build box alone and handed this half here rather than leave an
acceptance line that could not be exercised.

- **The decision it waits for:** when the repository gets a remote (public `youndie/kontainer`, as
  research D9 says). Until then nothing here can run; that is why the item is a `question`, not `open`.
- Then: a workflow that runs `make check` and `./gradlew check` (which runs `linuxX64Test`) on
  `ubuntu-latest` against the runner's own Docker Engine, with a `~/.konan` cache.
- Not covered: publishing (B-10).

- AC: on the hosted runner, `PingTest` prints the engine's version and passes; research H2 becomes a
  fact or a refutation with the run's link.
- AC: five runs of `KafkaStartTest` on the hosted runner, their `KAFKA-START` figures added to research §1.4
  beside the build box's (B-09 handed this half over).
- Anchors: `kontainer-docker/src/commonTest/kotlin/io/github/youndie/kontainer/docker/PingTest.kt`,
  `.github/workflows/`

## Question (answered 2026-09-30)

Create the remote now, or keep the repository local until later? The owner: public, now.

## Findings (2026-09-30)

- **The remote:** `youndie/kontainer`, public; `main` and the drafts branch pushed. `build` runs on
  `ubuntu-latest` with the Kotlin/Native cache, runs `./gradlew check`, and refuses a run in which a module
  produced no linuxX64 results or any failed.
- **H2 confirmed:** Docker Engine 28.0.4 (API 1.48, minimum 1.24), Compose v2.38.2; `PingTest` printed it and the
  whole suite passed there (youndie/kontainer#2).
- **The first hosted run was red, and rightly:** `ContainersTest` got `NoSuchContainer` because the tests' own
  `DockerCli` read `docker run -d`'s whole output as the id, and a runner without the image prints the pull
  progress first. The build box's warm cache had hidden it. The helper now takes the last line and checks it is
  an id.
- **Kafka on the hosted runner:** five starts, median 3.46 s (research §1.4).
- **A private name kept out:** a draft of the workflow section named the owner's private infrastructure
  repository; it was rewritten before the branch was pushed again, and the pushed history carries no trace of it.
