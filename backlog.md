# kontainer — backlog

> Role of this document: the product backlog. **One file per item in [`docs/backlog/`](docs/backlog/)** —
> `B-NN-<slug>.md`. What lives here is the index (generated) and everything that is not an item: the
> goal, the stages, the scope and the decisions.
>
> New item: copy [`docs/templates/backlog-item.md`](docs/templates/backlog-item.md), take the next free
> `B-NN`, and run `make fix` after editing.

## Goal

A Kotlin/Native test owns the containers it runs against: it brings a fixture up from the compose file
the repository already has, under its own project name and ports, waits until each service answers its
own protocol, and can pause or stop a service in the middle of the test without breaking anybody else's.

The measure of done is one sentence: **kafkakn's seven fault tests run in CI** (B-11). Today they are
gated behind switches because the broker they break is the one the whole suite uses. Why the library is
shaped this way is in [research](docs/research/research-architecture.md).

## Scope of v1

In: `linuxX64`; fixtures from compose files; ports chosen by kontainer; readiness probes for Kafka, Postgres,
HTTP and a custom check; pause, unpause, stop, start, kill; exec and logs; refusing foreign containers;
removing fixtures left by dead processes.

Out, on purpose: the JVM and every other target (the JVM comes later); describing containers in Kotlin
instead of compose (later); an existing Docker client library; image builds, Swarm, network faults;
generating a consumer's certificates or users. Mongo and SMTP probes come after v1.

## Stages

A stage is a field on the item, not a directory.

| Stage id | Stage | What closes it |
|---|---|---|
| `stage-1-engine` | The client | a `linuxX64` test talks to Docker over the socket, on the build box and in CI, and every engine failure arrives as a type |
| `stage-2-fixture` | The fixture | owned and shared fixtures from compose, stable ports, readiness by protocol, scoped faults, and the time an owned broker costs is known |
| `stage-3-consumers` | The consumers | kafkakn's fault tests run in CI through kontainer, and a second consumer with another protocol works |

A stage closes as a whole and gets a line here: what came out beyond the plan, and which research
hypothesis was confirmed or refuted.

## Marks

`[ ]` open · `[~]` in progress · `[x]` done · `[?]` open question · `[-]` dropped

<!-- BEGIN INDEX -->

## Open (9)

| Task | | Priority | Size | Blocked by |
|---|---|---|---|---|
| [B-05](docs/backlog/B-05-ports-chosen-before-up.md) `[ ]` | Host ports are chosen before up and survive stop and start | P1 | S | B-04 |
| [B-06](docs/backlog/B-06-readiness-by-protocol.md) `[ ]` | Readiness is a protocol answer from the host through the published port | P1 | M | B-02 |
| [B-07](docs/backlog/B-07-scoped-faults.md) `[ ]` | paused {} and stopped {} restore the service, and refuse on a shared fixture | P1 | S | B-05, B-06 |
| [B-09](docs/backlog/B-09-kafka-start-time.md) `[ ]` | Measure how long an owned Kafka broker takes to become ready | P1 | S | B-06 |
| [B-10](docs/backlog/B-10-publish-to-reposilite.md) `[ ]` | Publish kontainer-docker and kontainer to reposilite | P1 | S | B-07 |
| [B-11](docs/backlog/B-11-kafkakn-fault-tests-in-ci.md) `[ ]` | kafkakn's seven fault tests run in CI on their own broker | P1 | M | B-09, B-10 |
| [B-08](docs/backlog/B-08-reap-abandoned-fixtures.md) `[ ]` | Fixtures left by a dead test process are removed on the next up | P2 | S | B-04 |
| [B-12](docs/backlog/B-12-s3kn-brings-its-own-minio.md) `[ ]` | s3kn's linuxX64Test brings up its own MinIO | P2 | S | B-10 |
| [B-13](docs/backlog/B-13-ci-on-a-hosted-runner.md) `[?]` | The suite runs on a hosted runner once the repository has a remote | P2 | S | B-01 |

## Closed (4)

**The client**

- [B-01](docs/backlog/B-01-ping-over-the-socket.md) `[x]` - A linuxX64 test pings Docker over the unix socket
- [B-02](docs/backlog/B-02-containers-and-typed-errors.md) `[x]` - Containers by label, inspect, pause, stop, start, kill, with typed errors
- [B-03](docs/backlog/B-03-exec-and-logs.md) `[x]` - Exec returns the exit code, stdout and stderr separately; logs are readable

**The fixture**

- [B-04](docs/backlog/B-04-fixture-from-compose.md) `[x]` - A fixture comes up from a compose file under its own project name

<!-- END INDEX -->

## Decisions worth not re-litigating

- **The gap is isolation, not the pause button.** kafkakn already pauses its broker from inside a test
  with `system()`. What it lacks is a broker it may break (research D1).
- **The compose file stays the description of the fixture**, and `docker compose` runs it (D4).
- **TCP accepted is not ready.** The proxy accepts while the container is frozen (D6).
