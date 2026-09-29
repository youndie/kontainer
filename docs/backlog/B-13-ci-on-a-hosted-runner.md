---
id: B-13
title: "The suite runs on a hosted runner once the repository has a remote"
status: question
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

## Question

Create the remote now, or keep the repository local until later? Either way nothing else in the
backlog is blocked by this item.
