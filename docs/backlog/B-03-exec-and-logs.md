---
id: B-03
title: "Exec returns the exit code, stdout and stderr separately; logs are readable"
status: open
priority: P1
size: M
stage: stage-1-engine
blocked_by: [B-01]
---

# B-03 — Exec returns the exit code, stdout and stderr separately; logs are readable

kafkakn's harness asks the broker's own tools for the truth (`docker exec kafkakn-broker kafka-topics.sh …`) and reads failures out of `docker logs`. Inside a test those need the exit code and the two streams apart; `system()` gives an exit code and nothing else.

Feature: `feature-engine-client` (drafted in the open documentation pull request).

- **Frames are parsed, not stripped.** The body is `[stream, 0, 0, 0, size big-endian]` frames, stdout = 1 and stderr = 2 (measured, research §1.2); the exit code comes from `GET /exec/{id}/json` after the stream ends.
- Rejected: a TTY exec, which merges the streams and adds carriage returns.
- Not covered: stdin to an exec, interactive sessions.

- AC: `exec(id, ["sh", "-c", "echo out; echo err >&2; exit 3"])` returns exit code 3, stdout `out\n`, stderr `err\n`.
- AC: an output longer than one frame (at least 1 MiB) arrives complete and in order.
- AC: research H5 (plain body through Ktor) is rewritten as a fact or a refutation.
- Anchors: `kontainer-docker/src/commonMain/kotlin/io/github/youndie/kontainer/docker/`
