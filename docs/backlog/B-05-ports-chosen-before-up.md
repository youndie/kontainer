---
id: B-05
title: "Host ports are chosen before up and survive stop and start"
status: done
priority: P1
size: S
stage: stage-2-fixture
epic: feature-compose-fixture
blocked_by: [B-04]
---

# B-05 — Host ports are chosen before up and survive stop and start

An ephemeral port moved from 37810 to 37811 across one stop/start, and a fixed port on the shared box silently routed a probe to another project's Postgres (research §1.2). A fault test that stops its broker and starts it again must find it where it was.

Feature: [feature-compose-fixture](../features/feature-compose-fixture.md).

- **kontainer picks each port and passes it as `KONTAINER_PORT_<SERVICE>_<CONTAINER_PORT>`**; the compose file publishes `"127.0.0.1:${KONTAINER_PORT_PG_5432}:5432"`. The same variable is what Kafka's `ADVERTISED_LISTENERS` names.
- **After `up`, the published ports are compared with the chosen ones**; a mismatch or an empty port list fails the fixture (research Risk 2).
- A lost race ("port is already allocated") picks new ports and retries a bounded number of times (Risk 1).
- Rejected: reading the ephemeral port after `up` — it changes on restart.

- AC: an owned Postgres fixture is stopped and started; `port("pg", 5432)` is the same before and after, and Postgres answers on it.
- AC: a compose file that publishes a fixed port instead of the variable fails `up` with an error naming the service and the port.
- Anchors: `kontainer/src/commonMain/kotlin/io/github/youndie/kontainer/`

## Findings (2026-09-30)

- **The API:** `Fixture.owned(files, ports = mapOf("pg" to listOf(5432)))`; `fixture.port("pg", 5432)` is the
  port chosen before `up` and checked after it. `portVariable(service, port)` names the variable
  (`KONTAINER_PORT_PG_5432`; anything but letters and digits in the service name becomes `_`). `compose
  config` gets placeholder `0`s for the variables, since a file that publishes them does not parse without.
- **Acceptance, on the build box** (`PortsTest`, 5 tests; `./gradlew check` green): an owned Postgres fixture
  keeps its host port across `stop` and `start` and `pg_isready` from the host answers on it before and after;
  a compose file with a fixed port fails `up` with `PortMismatch("pg", 5432, chosen, published)`, whose message
  names the variable to use; a port the file does not publish fails with `PortNotPublished`.
- **Mutations, each killed:** the post-`up` check skipped, the ports not passed to `up`, the variable not
  upper-cased.
- **Not covered by a test:** the retry on "port is already allocated". The race cannot be provoked on demand;
  the research says so under Risk 1.
- Two stale temporary directories from B-04's mutant runs were found and removed; this item's runs leave nothing.
