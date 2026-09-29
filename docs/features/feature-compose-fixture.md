---
id: feature-compose-fixture
title: A fixture from the compose file the repository already has
type: feature
status: active
owner: unassigned
involved_services: [kontainer, kontainer-docker]
client_entries: []
api: []
tags: [docker, compose]
---

# A fixture from the compose file the repository already has

## 1. Overview

A test brings up its broker or database itself, from the compose file the repository already uses for
its shell harness, and brings it down when it is done. A fixture is either **shared** — a fixed project
name, reused between runs, as kafkakn's harness does today — or **owned** by the test: its own project
name and its own ports, which it may break (`fixture.paused { }`, `fixture.stopped { }`; the fault
feature is still drafted, because two of its scenarios need kafkakn).

The lifecycle runs through `docker compose`; everything after `up` goes through
[kontainer-docker](../services/kontainer-docker.md). Why: [research D4, D5](../research/research-architecture.md).

## 2. Business rules

* kontainer always gives the project name (`-p`); a name taken from the directory is what made kafkakn and
  mostik share one project (kafkakn B-97).
* kontainer picks the host port of every published port it is asked about, before `up`, and passes it as
  `KONTAINER_PORT_<SERVICE>_<CONTAINER_PORT>`. After `up` it compares the container's published ports with
  its choice and fails the fixture on any difference.
* A container of the same name in another compose project is never touched: `up` fails with
  `ForeignContainer` naming that project.
* Every container carries `kontainer.owner=<pid>@<host>` and `kontainer.fixture=owned|shared`; the next `up`
  on that host removes **owned** projects whose owner process is dead, prints which, and lists them in
  `fixture.reaped`. Shared fixtures, other hosts' and live owners' are left alone.
* `up` is not readiness; readiness is [feature-readiness](feature-readiness.md).

## 3. Flow

1. The test creates `Fixture.owned(composeFiles, environment, ports)` (or `shared(project, …)`).
2. `up()`: remove dead owners' projects → pick ports → `docker compose -p <project> up -d` with the port
   variables → find the containers by label → compare published ports with the chosen ones.
3. The test reads `port(service, containerPort)` and waits with `awaitReady`.
4. `down()`: `docker compose -p <project> down -v`.

## 4. Code anchors

| Service | Code |
|---|---|
| kontainer | `kontainer/src/commonMain/kotlin/io/github/youndie/kontainer/Fixture.kt` — up, down, ports, reaping |
| kontainer | `kontainer/src/linuxX64Main/kotlin/io/github/youndie/kontainer/Platform.linuxX64.kt` — the compose CLI, a free port, process liveness |
| kontainer | `kontainer/src/linuxX64Test/kotlin/io/github/youndie/kontainer/` — the scenarios (`FixtureTest`, `PortsTest`, `ReapTest`) |

## 5. Scenarios (BDD / test cases)

Verified against Docker Engine 29.1.3 and Compose 2.40.3 on the Linux build box (B-04, B-05, B-08).

### Scenario: an owned fixture
* **Given:** a compose file publishing `"127.0.0.1:${KONTAINER_PORT_PG_5432}:5432"` for `postgres:18-alpine`.
* **When:** the test brings up an owned fixture from it.
* **Then:** `port("pg", 5432)` is the port kontainer chose, and Postgres answers on it; the container carries
  the fixture's project name even though the file names another, and `down` removes it with its volume.
* **Automated:** `FixtureTest::an_owned_fixture_comes_up_under_its_own_name_and_down_with_its_volumes`

### Scenario: the port survives stop and start
* **Given:** an owned Postgres fixture that is up.
* **When:** the container is stopped and started again.
* **Then:** `port("pg", 5432)` is the same as before, and `pg_isready` from the host answers on it.
* **Automated:** `PortsTest::the_chosen_port_survives_stop_and_start_and_postgres_answers_on_it`

### Scenario: a fixed port in the compose file
* **Given:** a compose file publishing a fixed `"127.0.0.1:<port>:5432"` instead of the variable.
* **When:** the test brings the fixture up.
* **Then:** `up` fails with `PortMismatch` naming `pg`, `5432` and both host ports, and its message names the
  variable to publish instead.
* **Automated:** `PortsTest::a_fixed_port_in_the_compose_file_fails_up_naming_the_service_and_the_port`

### Scenario: somebody else's container
* **Given:** a running container in the compose project `other`, named like a service's `container_name`.
* **When:** a shared fixture with that service is brought up.
* **Then:** `up` fails with `ForeignContainer` naming `other`, and that container is still running.
* **Automated:** `FixtureTest::a_container_of_another_project_with_the_same_name_is_left_alone`

### Scenario: a fixture left by a dead process
* **Given:** an owned project whose containers carry `kontainer.owner` with a pid that does not exist on this host.
* **When:** any fixture on this host is brought up.
* **Then:** that project is removed with its volumes, the log names it, and it is in `fixture.reaped` — while a
  live owner's, another host's and a shared fixture are left alone.
* **Automated:** `ReapTest::an_owned_fixture_of_a_dead_process_is_removed_by_the_next_up`

## 6. Out of scope

* Describing containers in Kotlin instead of compose — after v1.
* Parsing or re-implementing compose (`docker compose config --format json` was considered; research D4).
* Fixtures owned by another host.

## 7. Quirks

* **An ephemeral port is not used even when nobody asked for a stable one.** It moved from 37810 to
  37811 across one stop/start ([research §1.2](../research/research-architecture.md)), and a fault test
  is exactly the test that stops and starts.
* **A lost race for a port retries `up`, and that path has no test**: the race cannot be provoked on demand
  (research Risk 1).
* **`compose config` gets placeholder `0`s** for the port variables, since a file that uses them does not parse
  without; `up` then gets the chosen ports.
* **A reused pid keeps a dead owner's fixture alive.** Liveness is `kill(pid, 0)`; if the kernel has handed the pid
  to another process, the fixture waits for a later run. The safe side of the mistake.
