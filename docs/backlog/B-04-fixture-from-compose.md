---
id: B-04
title: "A fixture comes up from a compose file under its own project name"
status: done
priority: P1
size: M
stage: stage-2-fixture
epic: feature-compose-fixture
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

## Findings (2026-09-29)

- **Measured before coding** (compose 2.40.3): `-p` wins over `name:` in the file; labels from an override
  file merge onto every container, which is how the fixture adds `kontainer.owner` and `kontainer.fixture`
  without touching the consumer's file; `down -v` needs only the project name; a foreign container under a
  service's `container_name` makes compose fail with the engine's name conflict, and compose removes
  nothing. The fixture checks first anyway, so the error names the project that owns the container.
- **H4 refuted:** class-level hooks exist on native (`@BeforeClass`/`@AfterClass` on a companion object ran
  on `linuxX64`). The research says what that changes.
- **Acceptance, on the build box** (`FixtureTest`, 5 tests; `./gradlew check` green): an owned Postgres
  fixture comes up under its own name despite `name:` in the file, its container is found by label and
  carries both kontainer labels, and `down` removes it with its named volume; a container named like the
  service in project `other` gives `ForeignContainer("…", "other")` and keeps running; a compose failure
  carries what compose printed; owned project names do not repeat.
- **Mutations, each killed:** the foreign check off, `-p` dropped, `down` without `-v`, the label override not
  applied, the owned-name counter frozen. Leftovers of the mutant runs (one volume) were removed by hand.
- **Two build facts.** A module named like the root project (`:kontainer` in `kontainer`) makes Gradle's
  type-safe project accessors generate `getKontainer()` twice, so they are off and the module is referenced
  by path. And the tests' own temporary compose files leaked into `/tmp` on the first run; they are removed now.
- Not here: ports (B-05), readiness (B-06), reaping (B-08). `feature-compose-fixture` stays a draft until those land.
