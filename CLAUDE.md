# CLAUDE.md — kontainer

Containers for Kotlin/Native tests: fixtures from compose files, ports kontainer chooses, readiness by
protocol, and faults in the middle of a test. v1 is `linuxX64` only; the JVM comes later.

**State (2026-09-29): design, no code.** The research and the backlog are on `main`; the feature and
module documents are drafted in an open pull request and become `active` as the code lands. This
paragraph is dated so that its age is visible; `backlog.md` is what cannot go stale.

## How to start a session

1. [docs/research/research-architecture.md](docs/research/research-architecture.md) — what was read in
   the neighbouring repositories and measured against a real Docker Engine, and what follows. The
   findings that most often contradict the obvious implementation:
   - **TCP accepted is not ready.** The published port accepts while the container is paused (§1.2);
     readiness is the service's protocol, from the host, through that port (D6).
   - **Neither kind of host port is safe.** An ephemeral one moves across stop/start; a fixed one
     collides silently on a shared box (§1.2). kontainer picks the port and passes it in (D5).
   - **The gap is isolation, not pausing.** kafkakn already pauses its broker with `system()`; what it
     lacks is a broker it may break (D1).
2. [backlog.md](backlog.md) — the goal, the stages, the index. Items are one file each in
   `docs/backlog/`; take the next unblocked one by priority.
3. The feature and module documents for the item (`epic` in its frontmatter), from the open
   documentation pull request until they merge.

## Rules

- Code, KDoc, test names, exception messages, commits and pull requests: English. Documentation: English.
- Scenarios in `docs/features/` are *target* until they are verified against the code; a document
  goes `active` only when its behaviour exists.
- Every claim about Docker, Ktor or a neighbouring repository names where it was verified — an address
  with a commit (`youndie/kafkakn@f2b75a8!/path`) or a measurement with its date.
- Tests run against a real Docker Engine. A test that passes because Docker was not there is a defect.

## Checks

```bash
make check
```
