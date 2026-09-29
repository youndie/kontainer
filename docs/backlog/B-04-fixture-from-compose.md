---
id: B-04
title: "A fixture comes up from a compose file under its own project name"
status: wip
priority: P1
size: M
stage: stage-2-fixture
blocked_by: [B-02]
---

# B-04 — A fixture comes up from a compose file under its own project name

The fixtures exist as compose files (kafkakn, mostik, s3kn, smtpkn) and are brought up by shell before Gradle runs. Two repositories once shared a project because compose named it after the directory, and one removed the other's broker (kafkakn B-97).

Feature: `feature-compose-fixture` (drafted in the open documentation pull request).

- **kontainer always passes `-p`.** Shared fixtures get a fixed name (`kafkakn`), owned ones `kontainer-<pid>-<n>`. Every container is labelled `kontainer.owner=<pid>@<host>`.
- **`up` and `down` go through the `docker compose` CLI** (research D4); finding the containers afterwards goes through the client, by label.
- **A container of the same name in another project is left alone**: `up` fails with `ForeignContainer` naming the other project.
- Rejected: re-implementing compose from `docker compose config --format json` — a second product (D4).
- Not covered: ports (B-05), readiness (B-06).

- AC: a test brings up a Postgres fixture from a compose file, finds its container by label, and `down` removes it with its volumes.
- AC: with a container named like the fixture's service in a project called `other`, `up` fails with `ForeignContainer` naming `other`, and that container is still running.
- AC: research H4 (no class-level hooks on native) is settled.
- Anchors: `kontainer/src/commonMain/kotlin/io/github/youndie/kontainer/`, `kontainer/src/linuxX64Main/kotlin/io/github/youndie/kontainer/` — running the compose CLI
