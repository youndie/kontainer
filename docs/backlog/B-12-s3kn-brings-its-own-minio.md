---
id: B-12
title: "s3kn's linuxX64Test brings up its own MinIO"
status: done
priority: P2
size: S
stage: stage-3-consumers
epic: feature-compose-fixture
blocked_by: [B-10]
---

# B-12 — s3kn's linuxX64Test brings up its own MinIO

s3kn starts MinIO with `docker compose up --wait` as a CI step, so `linuxX64Test` fails on any machine where nobody ran the step first. A second consumer with a different protocol (`http`) is also the cheapest check that kontainer is not shaped around Kafka.

Feature: [feature-compose-fixture](../features/feature-compose-fixture.md).

- **The test brings up a shared fixture from s3kn's existing compose file** and waits with the `http` probe.
- The bucket-creation service in that compose file stays as it is: a one-shot container is compose's business (research D4).

- AC: the `docker compose up` step is removed from s3kn's CI and `linuxX64Test` is green there.
- AC: on a machine with no MinIO running, `./gradlew :s3-client:linuxX64Test` starts it itself.
- Anchors: `youndie/s3kn@6a8085d!/.github/workflows/ci.yaml`, `youndie/s3kn@6a8085d!/docker-compose.yml`

## Iteration 1 (2026-09-30)

- **Built:** s3kn's live tests on `linuxX64`, when no `S3_E2E_ENDPOINT` is given, start MinIO from s3kn's own
  `docker-compose.yml` through kontainer — ports chosen, MinIO's health checked, the `create-buckets` one-shot
  waited for with a `custom` probe — once per test process; the next run removes the copy
  (youndie/s3kn#19, a draft). On the build box, with `S3_E2E_REQUIRED=1` and no endpoint, the 21 live tests passed
  against it, and a second run removed the first run's fixture ("removed the abandoned fixture …").
- **The first acceptance line cannot hold as written:** s3kn's live tests are in `commonTest`, so CI's `jvmTest`
  needs a server too, and kontainer has no JVM target (research D2). The compose step stays for the JVM; CI runs the
  linuxX64 live tests with no endpoint first, on a clean runner, to exercise the new path.
- **What stopped it:** that CI run failed to pull MinIO. The pinned `quay.io/minio/minio:RELEASE.2025-09-07T16-13-09Z`
  and `quay.io/minio/mc:RELEASE.2025-08-13T08-35-41Z` are gone — `no such manifest` on quay.io, on Docker Hub and on
  mirror.gcr.io (checked 2026-09-30). The build box passed only because it had them cached. s3kn's own `main` pulled
  them on 2026-09-17 and will fail the same way on its next run, with or without this change.

## Question (answered 2026-09-30)

The owner: pick a minimal replacement yourself, and not bochka. Where should s3kn's test server come from now? It is s3kn's decision more than kontainer's, and B-12 waits on it:

1. **Keep MinIO, from the portfolio's own registry:** push the build box's cached copies, by digest
   (`minio/minio@sha256:14cea493…`, `minio/mc@sha256:a7fe349e…`), to a registry the portfolio controls, and pin them there.
2. **Another S3 server:** a maintained MinIO fork, or another implementation — the live tests assert MinIO's error
   shapes in places, so the suite would have to be read against it.
3. **Leave B-12 aside:** s3kn stays broken until its own decision; kontainer's second consumer waits.

## Findings (2026-09-30)

- **The test server first (s3kn M-130, youndie/s3kn#20):** SeaweedFS's S3 gateway, `chrislusf/seaweedfs:4.48`
  pinned by digest — one container, buckets made by its own `weed shell`, the same port 9000 and compose commands.
  The whole live suite passed against it on the build box but for the one test that pinned MinIO's own `411` on a
  body without a length; it now pins SeaweedFS's `200`, the body read back. `versitygw` was tried first and
  rejected: it ignores `encoding-type=url` in ListObjectsV2 and returns keys unencoded, so the awkward-keys test
  fails on it.
- **Then this item (s3kn M-129, youndie/s3kn#19):** with no `S3_E2E_ENDPOINT`, s3kn's linuxX64 live tests bring up
  the repository's own `docker-compose.yml` through kontainer, on a port it chooses, ready by `Probe.http("/healthz")`
  and by a `custom` probe that waits for the `create-buckets` one-shot to exit 0 — the second consumer, and the first
  on a protocol other than Kafka and Postgres. In CI a clean runner runs `:s3-client:linuxX64Test` with no endpoint,
  green; on the build box a second run removed the first run's fixture.
- **The compose step stays in s3kn's CI:** its live tests are in `commonTest`, so `jvmTest` needs a server, and
  kontainer has no JVM target (research D2). The acceptance line that asked to remove the step could not hold.
- **Found on the way:** on versitygw the two arms of s3kn read the same listing differently (the JVM decoded keys
  the native arm did not), which points at a difference inside s3kn rather than the server; recorded for s3kn, not
  chased here.
