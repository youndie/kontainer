# CLAUDE.md — kontainer

Containers for Kotlin/Native tests: fixtures from compose files, ports kontainer chooses, readiness by
protocol, and faults in the middle of a test. v1 is `linuxX64` only; the JVM comes later.

**State (2026-09-30): B-01…B-09 done, B-10 a question** — `kontainer-docker` is a working Docker Engine
client on `linuxX64`; the `kontainer` module brings compose fixtures up and down on host ports it chooses,
removes those of dead processes, waits for readiness by protocol, and pauses or stops a service mid-test. An
owned Kafka broker is ready in about 5 s. Publishing (B-10) waits for the owner, and B-11/B-12 with it. The research and the backlog are on `main`; the feature and
module documents are drafted on the branch `docs/v1-layers` and become `active` on `main` as the code
lands. This paragraph is dated so that its age is visible; `backlog.md` is what cannot go stale.

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
3. The feature and module documents for the item (`epic` in its frontmatter). Until a document is on
   `main`, read it from the drafts branch: `git show origin/docs/v1-layers:docs/features/<id>.md`.

## Workflow

The repository is public on GitHub (`youndie/kontainer`) since 2026-09-30. The backlog loop (`/loop` over
`backlog-item`) opens a pull request per item and **merges its own pull requests on green**, by the owner's
decision:

1. Branch first, before reading code: `feat/b-NN-<slug>` from `main`; the first commit sets the item to
   `wip`. Check `git branch --show-current` before every commit and push.
2. Verify on the Linux build box, not on the Mac: `~/.claude/bin/wsl-run <command>` (mutagen session
   `kontainer`, one-way replica). Anything the build must produce *into the repository* — the Gradle
   wrapper, formatter output, lock files — is produced on the Mac, because the replica never syncs back.
   `make` targets that only read the documents run on the Mac with `LOCAL=1`.
3. Green means both required checks, `check` and `build`, concluded `success` **on the pull request's head
   commit** — decided in `jq` over `gh api …/commits/<sha>/check-runs`, not by grepping `gh pr checks`.
   A red run is never merged, and no check is loosened to get green.
4. Merge: `gh pr merge <n> --squash --match-head-commit <full sha>`, the title
   `<type>(<scope>): <subject>` and `Refs: B-NN` in the body. Branches are kept.
5. Documents: a feature or module document moves from the drafts branch `docs/v1-layers` to `main` as
   `active` in the item that makes its behaviour real and verified, with `**Automated:**` lines. In the same
   step it is deleted on `docs/v1-layers` and `main` is merged into it; that merge conflicts in the coverage
   map in `docs/README.md` — keep the drafts branch's side, which already lists the landed documents.
6. Publishing: `main` publishes a snapshot to reposilite with this repository's own token, stored in its
   secrets as `REPOSILITE_USER` / `REPOSILITE_SECRET` by the owner's token workflow (B-10).

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
