---
id: B-03
title: "Exec returns the exit code, stdout and stderr separately; logs are readable"
status: done
priority: P1
size: M
stage: stage-1-engine
epic: feature-engine-client
blocked_by: [B-01]
---

# B-03 — Exec returns the exit code, stdout and stderr separately; logs are readable

kafkakn's harness asks the broker's own tools for the truth (`docker exec kafkakn-broker kafka-topics.sh …`) and reads failures out of `docker logs`. Inside a test those need the exit code and the two streams apart; `system()` gives an exit code and nothing else.

Feature: [feature-engine-client](../features/feature-engine-client.md).

- **Frames are parsed, not stripped.** The body is `[stream, 0, 0, 0, size big-endian]` frames, stdout = 1 and stderr = 2 (measured, research §1.2); the exit code comes from `GET /exec/{id}/json` after the stream ends.
- Rejected: a TTY exec, which merges the streams and adds carriage returns.
- Not covered: stdin to an exec, interactive sessions.

- AC: `exec(id, ["sh", "-c", "echo out; echo err >&2; exit 3"])` returns exit code 3, stdout `out\n`, stderr `err\n`.
- AC: an output longer than one frame (at least 1 MiB) arrives complete and in order.
- AC: research H5 (plain body through Ktor) is rewritten as a fact or a refutation.
- Anchors: `kontainer-docker/src/commonMain/kotlin/io/github/youndie/kontainer/docker/`

## Findings (2026-09-29)

- **H5 refuted.** The first run failed: CIO refused the `exec/start` answer, which carries neither a length
  nor chunked encoding nor `Connection: close` — asking for `Connection: close` changes nothing (measured
  with `curl -i`). That one request is now written to the socket and read to its close with ktor-network
  (`RawResponse.kt`, a chunked answer handled too, in case another engine version frames it). Recorded in
  the research as a correction.
- **Two labels, measured:** exec output says `raw-stream` and is framed all the same; logs say
  `multiplexed-stream` (framed) or, for a container with a TTY, `raw-stream` (one unframed stream, CR LF).
  Exec is always demultiplexed; logs by their label.
- **Acceptance, on the build box** (`./gradlew check`, 20 tests): `sh -c 'echo out; echo err >&2; exit 3'`
  returns exit 3, `out\n`, `err\n`; `seq 1 200000` (1 288 895 bytes, many frames) arrives whole and in
  order; exec in a stopped container is `Conflict`; logs keep the streams apart; a TTY container's log is
  one stream; `tail` returns the last lines.
- **One expectation of mine was wrong.** A test asserted `tail=2` over `first`, `to-out` (stdout) and
  `to-err` (stderr) and got `first` and `to-out`: the stored order across the two streams was not the order
  written. The tail test now uses one stream; the quirk is in the module document.
- **Mutations, each killed:** streams swapped (six tests), exit code forced to 0, frame size read from the
  low byte only (the big-endian unit test and the 1 MiB test), the log's content type ignored (the TTY test),
  `tail` dropped. A first exit-code mutant did not compile and is not counted.
- **The feature closes with this item:** all four `feature-engine-client` scenarios are automated, so the
  feature and the `kontainer-docker` module document move to `main` as `active`.
