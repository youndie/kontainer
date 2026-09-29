---
id: B-12
title: "s3kn's linuxX64Test brings up its own MinIO"
status: open
priority: P2
size: S
stage: stage-3-consumers
blocked_by: [B-10]
---

# B-12 — s3kn's linuxX64Test brings up its own MinIO

s3kn starts MinIO with `docker compose up --wait` as a CI step, so `linuxX64Test` fails on any machine where nobody ran the step first. A second consumer with a different protocol (`http`) is also the cheapest check that kontainer is not shaped around Kafka.

Feature: `feature-compose-fixture` (drafted in the open documentation pull request).

- **The test brings up a shared fixture from s3kn's existing compose file** and waits with the `http` probe.
- The bucket-creation service in that compose file stays as it is: a one-shot container is compose's business (research D4).

- AC: the `docker compose up` step is removed from s3kn's CI and `linuxX64Test` is green there.
- AC: on a machine with no MinIO running, `./gradlew :s3-client:linuxX64Test` starts it itself.
- Anchors: `youndie/s3kn@6a8085d!/.github/workflows/ci.yaml`, `youndie/s3kn@6a8085d!/docker-compose.yml`
