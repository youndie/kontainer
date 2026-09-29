# docs — kontainer

kontainer gives a Kotlin/Native test the containers it runs against: fixtures from compose files, ports it
chooses, readiness by protocol, and faults in the middle of a test. The documentation is layered; links
run top to bottom.

```
[ Research — why it is built this way; verified vs hypothesis ]
                              │
[ Feature — what a test gets; BDD scenarios = acceptance ]
                              │
[ Service — the two modules, their public API, how they are built ]
```

| Layer | Directory | Answers | Source of truth |
|---|---|---|---|
| Research | `research/` | *why* it is built this way; what is verified, what is a hypothesis | the artefacts and measurements each fact names |
| Feature | `features/` | *what* a test gets and *why*; BDD scenarios | this repository |
| Service | `services/` | the modules: public API, dependencies, publishing, quirks | this repository |

There is no `screens/` layer (no client) and no `api/` layer (a library has no HTTP surface); the public
Kotlin API of each module is in its service document.

**Backlog** — [backlog.md](../backlog.md): the goal, the stages and the index; the items are one file each
in [`backlog/`](backlog/).

## Conventions

- **`id`** in the frontmatter is unique and equals the filename.
- Cross-layer links are ids in the frontmatter and ordinary markdown links in the body.
- One document, one entity. A feature spanning both modules is **one** file.
- Scenarios are written against behaviour read in the code or measured against a real Docker Engine,
  never from memory. Until the code exists they are marked *target*.
- **The primary consumer is a coding agent.** Every document carries paths into the code. Do not copy
  what the code holds; give the path.
- Language: English. Code identifiers, endpoint paths and header names verbatim.

The format is [docs-bootstrap](https://github.com/youndie/docs-bootstrap)'s; `templates/` holds a copy.

## Checks

```bash
pip install pyyaml
make check
```

## Coverage map

The list below is **checked** against the files on disk; the grouping and the descriptions are written
by a person.

### Research (1)

- [x] [research-architecture](research/research-architecture.md) — how native tests get containers today, what the Docker Engine does under pause and stop, decisions and risks

### Services (2)

- [x] [kontainer-docker](services/kontainer-docker.md) — the Docker Engine client: containers, pause and stop, exec, logs, typed errors
- [x] [kontainer](services/kontainer.md) — fixtures from compose, chosen ports, readiness probes

### Features (3)

- [x] [feature-engine-client](features/feature-engine-client.md) — talking to the engine over the socket, with answers a test can act on
- [x] [feature-compose-fixture](features/feature-compose-fixture.md) — owned and shared fixtures from an existing compose file, on chosen ports
- [x] [feature-readiness](features/feature-readiness.md) — ready means the service answered its protocol through the published port
