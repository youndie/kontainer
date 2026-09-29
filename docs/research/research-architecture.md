---
id: research-architecture
title: kontainer — architecture research
type: research
status: active
date: 2026-09-29
---

# Research: the architecture of kontainer

kontainer lets a Kotlin/Native test own the containers it runs against: bring up a fixture from a compose
file under its own project name and its own ports, wait until each service answers **its own
protocol** on the port the test will use, and pause, stop or restart a service in the middle of the
test. The JVM has Testcontainers for this; a `linuxX64` test binary has nothing, so every repository
that tests a native client against a real broker or database has built its own shell harness around
Gradle, and learned the same lessons about readiness separately.

This document records **verified facts** (what was read in code and artefacts, or measured), the
**decisions** taken, and the **risks**. Anything unverified is a hypothesis and names the backlog
item that settles it. It is the entry point of the documentation and is amended, not archived.

---

## 1. Verified facts

### 1.1 How native tests get a broker or a database today

Read on each repository's default branch on 2026-09-29.

| Fact | Where verified |
|---|---|
| kafkakn brings its broker up **before Gradle**, from a shell harness over `docker compose`, and trusts only an answer from the broker's own tool, not `--wait` | `youndie/kafkakn@f2b75a8!/ci/harness/broker.sh`, `youndie/kafkakn@f2b75a8!/ci/suite/run.sh` |
| `docker compose up --wait` reported a crash-looping broker **Healthy** — twice, for two different missing keys | `youndie/kafkakn@f2b75a8!/ci/broker/docker-compose.yml` (comment on `KAFKA_CONTROLLER_LISTENER_NAMES`), `youndie/kafkakn@f2b75a8!/ci/broker/docker-compose.tls.yml` |
| Tests read the address from the environment with a default: `testEnv("KAFKAKN_BOOTSTRAP") ?: "127.0.0.1:9092"` | `youndie/kafkakn@f2b75a8!/kafkakn-core/src/commonTest/kotlin/io/github/youndie/kafkakn/Observations.kt` |
| kafkakn tests **already** pause and stop the broker from inside the test: `platform.posix.system("docker pause kafkakn-broker")` on native, `ProcessBuilder` on the JVM | `youndie/kafkakn@f2b75a8!/kafkakn-core/src/nativeTest/kotlin/io/github/youndie/kafkakn/Observations.native.kt`, `youndie/kafkakn@f2b75a8!/kafkakn-core/src/jvmTest/kotlin/io/github/youndie/kafkakn/Observations.jvm.kt` |
| The seven tests that do it are **gated** behind `KAFKAKN_BROKER_CONTROL` / `KAFKAKN_BROKER_STOP`, and the CI suite does not set them: the broker is shared with the whole suite | `EnqueueTest.kt`, `CancelledSendTest.kt`, `StoppedBrokerTest.kt`, `CloseWithBrokerGoneTest.kt` under `youndie/kafkakn@f2b75a8!/kafkakn-core/src/commonTest/kotlin/io/github/youndie/kafkakn/`; `ci/suite/run.sh` ("stay behind their own switches") |
| kafkakn and mostik once shared one compose project (both named after the directory `broker`) and removed each other's broker | `youndie/kafkakn@f2b75a8!/docs/backlog/B-97-the-fixture-shares-a-compose-project-with-mostik.md` |
| mostik keeps its own copy of the fixture; its CI starts no broker, and everything that needs one is a shell script per backlog item under its ci directory | `youndie/mostik@16ae64e!/ci/broker/broker.sh`, `youndie/mostik@16ae64e!/.github/workflows/check.yaml` |
| petich starts Postgres from a Gradle `Exec` task before both test tasks and asks `pg_isready` **from the host, through the published port** — after `docker exec … pg_isready` answered 0.7–0.9 s early (petich B-17) | `youndie/petich@55cb6fd!/petich-sqlx4k-postgres/build.gradle.kts` (`startTestPostgres`) |
| s3kn and smtpkn start their servers with `docker compose up --wait` as a CI step and call the compose file the single source of truth | `youndie/s3kn@6a8085d!/.github/workflows/ci.yaml`, `youndie/smtpkn@2645801!/.github/workflows/ci.yaml` |
| mongkn starts `mongo:8` with `docker run` in CI and polls `docker exec mongod mongosh` | `youndie/mongkn@b1ebbbf!/.github/workflows/build.yml` |
| xyk needs no broker or database in its tests: SQLite is embedded, and its Kafka sink is tested through its envelope and a fake | `youndie/xyk@c4ba99f!/server/src/commonTest/kotlin/io/github/youndie/xyk/sink/SinkEnvelopeTest.kt` |

**Consequence 1.** Pressing pause is not the gap: two lines of `system()` already do it. The gap is
**isolation** — a fault test needs a broker nobody else is using, so that it can run in the suite
instead of behind a switch. That is what the library is for, and its first acceptance criterion is
kafkakn's seven gated tests running in CI.

**Consequence 2.** Readiness has been re-learned in every repository, and the wrong answers look the
same everywhere: `compose --wait`, a probe inside the container, a TCP connect to the published port.
The one answer that held is "the protocol, from the host, through the port the test uses".

**Consequence 3.** The compose files already exist and are already the description of each fixture.
A library that asks for a second description would be one more thing to keep in step.

### 1.2 Docker Engine, measured on the Linux build box

`curl --unix-socket /var/run/docker.sock` and `docker` on the Linux build box (Ubuntu 24.04 in WSL2),
2026-09-29, with `postgres:18-alpine` as the subject. One run of each; the port change was seen once.

| Fact | Where verified |
|---|---|
| Docker Engine 29.1.3, API 1.52, minimum API 1.44; Docker Compose 2.40.3 | `docker version`, `docker compose version` |
| The socket is `root:docker 0660`; the build user is in the `docker` group | `ls -l /var/run/docker.sock`, `id -nG` |
| `docker compose -p <name> up -d` labels containers `com.docker.compose.project=<name>` and `…service=<service>`; `GET /v1.44/containers/json?filters={"label":[…]}` finds them | curl, project `stendprobe` |
| `-p <name>` wins over a top-level `name:` in the file; labels in an extra `-f` override file are merged onto every container; `docker compose -p <name> down -v` removes a project's containers, networks and volumes with no file given | compose 2.40.3, B-04, 2026-09-29 |
| A service whose `container_name` is taken by another project's container fails `up` with the engine's `Conflict. The container name "/…" is already in use` — compose removes nothing | compose 2.40.3, B-04 |
| `docker compose config --format json` prints the normalised model with variables substituted | `PG_PORT=55432 docker compose -p stendprobe config --format json` |
| `exec/start` with `Detach:false, Tty:false` and no `Upgrade` header returns the output as the body, framed: `[stream, 0, 0, 0, size as big-endian u32]`, stdout = 1, stderr = 2 | `sh -c 'echo out; echo err >&2; exit 3'` → frames `1 0 0 0 0 0 0 4 "out\n"`, `2 0 0 0 0 0 0 4 "err\n"` |
| The exit code of an exec is read afterwards from `GET /exec/{id}/json` | same run: `ExitCode 3`, `Running false` |
| `POST …/pause` → `204`; a second `pause` → `409`; an unknown container → `404 {"message":"No such container: …"}` | curl |
| A second `start` of a running container and a second `stop` of a stopped one answer **`304`**; `kill` of a stopped container answers **`409`**; **`unpause` of a container that is not paused answers `500`** `{"message":"Container … is not paused"}`, not `409` | curl, B-02, 2026-09-29 |
| `POST /exec/{id}/start` answers `Content-Type: application/vnd.docker.raw-stream` with no length, no chunked encoding and no `Connection: close` — with or without `Connection: close` in the request — and its body is framed; `GET /containers/{id}/logs` is chunked and labelled `…multiplexed-stream`, or `…raw-stream` (unframed, CR LF line ends) for a container with a TTY | `curl -i`, B-03, 2026-09-29 |
| `exec` in a stopped container answers `409 {"message":"container … is not running"}` | curl, B-03 |
| A stopped container's `NetworkSettings.Ports` is `{}`: the engine reports published ports only for a running one | curl, B-02 |
| **Paused:** the published port still **accepts** TCP (3 of 3), and the protocol does not answer (`pg_isready` from the host exits 2) | curl + `/dev/tcp` + `docker run --network host postgres:18-alpine pg_isready` |
| **Stopped:** the published port **refuses** TCP (2 of 2) | same |
| **An ephemeral host port changes across stop/start**: `127.0.0.1::5432` was 37810, and 37811 after `stop` + `start` | `GET /containers/{id}/json` → `NetworkSettings.Ports["5432/tcp"][0].HostPort`, before and after |
| **A fixed port on a shared box can route the test to someone else's service.** The first run of these measurements published 55432, compose reported `Started`, the container's port list stayed empty — and every probe was answered by another project's Postgres, which had held `127.0.0.1:55432` for days | the aborted first run; `ss -ltnp` and `docker ps` afterwards |

**Consequence 4.** TCP accepted is not readiness, and not even liveness: the proxy accepts while the
container is frozen. A probe that stops at `connect()` would report a paused broker ready.

**Consequence 5.** Neither kind of host port is safe as it is. An ephemeral port moves under a test
that stops and starts its service; a fixed one collides silently with anything else on a shared
machine. The library picks the port, passes it in, and checks afterwards that the container really
published it.

**Consequence 6a.** The engine's own codes do not line up with "already in that state": `304` for
stop and start, `409` for pause and kill, `500` for unpause. The client maps `404` and `409` to types
and treats `304` as success; `500` stays an `EngineError`, so a caller that wants an idempotent unpause
(kontainer's `paused { }`, B-07) has to decide by the container's state, not by the error.

**Consequence 6.** The API is small and plain, and nothing v1 needs takes a hijacked connection.

**Correction found while implementing B-03:** this used to say exec output is ordinary
request/response HTTP. Through curl it is; through an HTTP client it is not. `POST /exec/{id}/start`
answers with neither `Content-Length` nor chunked encoding, and without `Connection: close` even when
the request asks for it, so the body ends only when the engine closes the connection. Ktor's CIO client
refuses such a response ("request body length should be specified, chunked transfer encoding should be
used or keep-alive should be disabled"). The working replacement is one request written to the socket
and read to its close (`kontainer-docker/src/commonMain/kotlin/io/github/youndie/kontainer/docker/RawResponse.kt`);
logs are chunked and stay on the client.

### 1.3 The transport and the alternative client

| Fact | Where verified |
|---|---|
| `HttpRequestBuilder.unixSocket(path)` is in `ktor-client-core`, common source set | [api.ktor.io — unixSocket](https://api.ktor.io/ktor-client-core/io.ktor.client.request/unix-socket.html) |
| The CIO client supports Unix domain sockets from Ktor 3.2.0 | [Ktor 3.2.0 release post](https://blog.jetbrains.com/kotlin/2025/06/ktor-3-2-0-is-now-available/) |
| The portfolio's Kotlin/Native libraries pin Ktor 3.6.0 | `gradle/libs.versions.toml` in `youndie/kafkakn@f2b75a8`, `youndie/mostik@16ae64e`, `youndie/petich@55cb6fd` |
| **The CIO client speaks HTTP over a unix socket on `linuxX64`** (H1, confirmed): `HttpClient(CIO)` with `unixSocket(path)` per request reached the build box's engine, `GET /_ping` and `GET /v1.44/version`, Ktor 3.6.0, Kotlin 2.4.20 | B-01, `kontainer-docker/src/commonTest/kotlin/io/github/youndie/kontainer/docker/PingTest.kt`, run on the build box 2026-09-29 |
| Left to itself, CIO reports a missing socket as `kotlinx.io.IOException: Failed to connect to UnixSocketAddress(/nonexistent.sock)` — the path, not the reason | B-01, the same test with the socket preflight disabled, 2026-09-29 |
| kmp-docker-client (Ktor, JVM + linuxX64 + Node) has exec, logs, pause and port lookup; no readiness, no macOS native; marked WIP | [LimeBeck/kmp-docker-client README](https://github.com/LimeBeck/kmp-docker-client), read 2026-09-29 |

**Consequence 7.** The transport exists in the version already pinned, and B-01 confirmed it on
`linuxX64` (H1). A missing socket still has to be told from a forbidden one before the request, since
the client's error names only the path.

**Correction found while implementing B-01:** this document used to imply the client's error on a
missing socket names nothing. It names the path; what it does not say is whether the socket is absent
or forbidden, which is why the preflight stays.

---

## 2. Decisions

### D1. A library inside the test process, for faults *(deviation from the brief)*

Brief: "the JVM Testcontainers is unavailable, so native tests have no way to control a container".
Decision: an in-process library whose reason to exist is **a fixture per fault test** — its own
project, its own ports — plus readiness by protocol and cleanup.

Why:

- the brief's premise is half true: controlling a container from a native test already works
  through `system()` (§1.1). What does not exist is a broker the test may break without breaking
  the suite, which is why seven kafkakn tests never run in CI;
- the alternative weighed — real Testcontainers in a Gradle build service, the address passed by
  environment — would have covered "start before the tests" for every repository without a new
  Docker client, and was rejected because it cannot give a test its own broker to pause;
- the price: a Docker client to write and maintain (D3).

### D2. `linuxX64` only in v1; the JVM later

Decision of the user. The price is concrete: kafkakn's fault tests live in `commonTest` and kafkakn
compares its JVM arm against its native arm. Until kontainer has a JVM target, the JVM half of those
tests stays on `ProcessBuilder` against the shared broker, behind its switch, and only the native
half joins CI (B-11).

### D3. Our own Docker client, not kmp-docker-client

Decision of the user. For the record: kmp-docker-client covers the calls v1 needs, but has no
readiness layer, no macOS native, and is a work in progress with one maintainer. v1 needs about ten
endpoints (§1.2), all plain request/response.

### D4. The compose file describes the fixture; the lifecycle goes through `docker compose`

Decision: a fixture is a list of compose files plus an environment. `up` and `down` run the
`docker compose` CLI with the project name kontainer chooses; everything else — finding containers by
label, ports, pause, stop, exec, logs — goes through the client.

Why:

- the compose files exist already and are the single source of truth in s3kn and smtpkn (§1.1);
- re-implementing compose from `docker compose config --format json` (§1.2) was rejected: networks,
  `depends_on`, healthchecks, overlays and profiles are a second product, and every difference from
  the CLI would be a fixture that works in the shell harness and not in the test;
- the price: the test process needs the `docker compose` binary, not only the socket. Describing
  containers in Kotlin is planned after v1 (the user's words: "kotlin in the future").

### D5. kontainer picks the host ports and passes them in

Decision: before `up`, kontainer picks a free port for each published port it is asked about and passes
it as `KONTAINER_PORT_<SERVICE>_<CONTAINER_PORT>`; the compose file publishes
`"127.0.0.1:${KONTAINER_PORT_BROKER_9092}:9092"`. After `up`, kontainer reads the container's published
ports and refuses the fixture if they are not the ones it chose.

Why: an ephemeral port changes across stop/start and a fixed one collides silently (§1.2,
Consequence 5). A port known before `up` is also what Kafka needs for `ADVERTISED_LISTENERS`.
The price: consumer compose files change from `9092:9092` to a variable (with the old value as the
default, so the shell harness keeps working).

### D6. Ready means "answered its protocol, from the host, through the published port"

Decision: readiness is a `Probe` — `kafka` (an ApiVersions request), `postgres` (the server's reply
to a startup packet), `http` (an expected status on a path), or `custom`. Rejected, each with the
evidence against it: `compose up --wait` (Healthy while crash-looping, §1.1), a probe inside the
container (early by 0.7–0.9 s, §1.1), a TCP connect (accepted while paused, §1.2). The Kafka probe
does not use kafkakn, because kafkakn will be tested through kontainer.

### D7. Faults only on a fixture the test owns

Decision: `pause`, `stop` and `kill` refuse on a shared fixture. A fault on the shared broker is the
reason kafkakn's tests are gated today; allowing it would reproduce the problem inside the library.

### D8. Requests pinned to `/v1.44`

Decision: every request carries the API version in the path, the server's minimum on the build box
(§1.2), with no negotiation. A newer field v1 needs would move the pin, and that is a visible change.
Whether `ubuntu-latest` accepts it is H2.

### D9. Public repository, published to reposilite, documentation in English

Decisions of the user (public, reposilite). English because the documentation is read by the
library's consumers, as in kafkakn and mostik. Code, commits and pull requests are English regardless.

---

## 3. Risks and open questions

**Risk 1. A port picked free is taken before compose binds it.** Mitigation: `up` recognises the
engine's "port is already allocated" failure, picks new ports and retries a bounded number of times;
after `up`, the published ports are compared with the chosen ones (D5), so a lost race is an error
and not a test against the wrong service. Open: how often it happens on the shared box — unmeasured.

**Risk 2. A fixture that started and published nothing.** Seen in §1.2: `Started`, an empty port
list, and a probe answered by somebody else. Mitigation: the post-`up` port check of D5 fails the
fixture before any probe runs, naming the service and the port.

**Risk 3. A killed test process leaves its fixture running.** A native test has no reliable shutdown
hook. Mitigation: every container carries `kontainer.owner=<pid>@<host>`; the next `up` on that host
removes projects whose owner is dead, and says which (B-08).

**Risk 4. Starting a broker per fault test is too slow for CI.** Unmeasured. Mitigation: B-09 times
five starts on the box and in CI before kafkakn adopts it; the fallback is one owned fixture per test
class, restored to ready between tests.

**H1 — confirmed by B-01** (§1.3): the CIO client speaks HTTP over a unix socket on `linuxX64`.

**H2. `ubuntu-latest` runs a Docker Engine that accepts `/v1.44` and has Compose v2.** Open: the
repository has no remote yet (owner's decision, 2026-09-29), so there is no hosted runner to ask.
Moved from B-01 to B-13.

**H3. A hand-written ApiVersions v0 request is enough to tell a Kafka broker that answers from
anything else.** Settled by B-06, with the negative control of pointing it at Postgres.

**H4 — refuted by B-04.** `kotlin.test.BeforeClass` and `AfterClass` on a companion object compile and
run on `linuxX64` (Kotlin 2.4.20): a probe printed its before-class and after-class lines around two
tests. A fixture can live as long as a test class; the reaper (B-08) is for processes that are killed,
not for the ones that end normally.

**H5 — refuted by B-03.** The exec output does not arrive as a plain body through Ktor; see the
correction under Consequence 6.

**Open question 1.** What else is out of scope for v1, in the user's words. Until answered, the
scope is the list in [backlog.md](../../backlog.md).

---

## 4. What happens next

The order and the acceptance criteria are in [backlog.md](../../backlog.md). B-01 confirmed the
transport (H1); the engine calls (B-02, B-03) are built on it.
