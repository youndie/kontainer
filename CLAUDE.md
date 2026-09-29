# CLAUDE.md — kontainer

Containers for Kotlin/Native tests: fixtures from compose files, ports kontainer chooses, readiness by
protocol, and faults in the middle of a test. v1 is `linuxX64` only; the JVM comes later.

**State (2026-09-29): stage 1 done (B-01…B-03), B-04 done** — `kontainer-docker` is a working Docker
Engine client on `linuxX64`, and the `kontainer` module brings compose fixtures up and down under their own
project name. The research and the backlog are on `main`; the feature and
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
   `main`, read it from the drafts branch: `git show docs/v1-layers:docs/features/<id>.md`.

## Local workflow (no remote)

The repository has no remote yet, by the owner's decision. The backlog loop (`/loop` over
`backlog-item`) replaces "push and open a pull request" with a local merge, and **merges its own items
into `main` on green**:

1. Branch first, before reading code: `feat/b-NN-<slug>` from `main`; the first commit sets the item
   to `wip`. Check `git branch --show-current` before every commit.
2. Verify on the Linux build box, not on the Mac: `~/.claude/bin/wsl-run <command>` (mutagen session
   `kontainer`, one-way replica). Anything the build must produce *into the repository* — the Gradle
   wrapper, formatter output, lock files — is produced on the Mac, because the replica never syncs back.
3. Green means all of: `make check`, `python3 scripts/docs_check.py --on-main` on the result, and the
   Gradle tasks the item's acceptance names, run on the box, with their result files read. A red run is
   never merged, and no check is loosened to get green.
4. Merge: rebase the branch on `main`, `git merge --no-ff feat/b-NN-<slug>` into `main`, message
   `<type>(<scope>): <subject>` with the acceptance checklist in the body and `Refs: B-NN`. Branches are
   kept after merging.
5. Documents: a feature or module document moves from `docs/v1-layers` to `main` as `active` in the
   item that makes its behaviour real and verified, with `**Automated:**` lines for the scenarios its
   tests cover. In the same step it is deleted on `docs/v1-layers` (`docs: drop <id>, landed on main`)
   and `main` is merged into `docs/v1-layers`. That merge conflicts in one place, the coverage map in
   `docs/README.md`: keep the drafts branch's side, which already lists the landed documents (B-03).

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
