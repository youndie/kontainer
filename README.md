# kontainer

Containers for Kotlin/Native tests: a fixture from the compose file you already have, under its own
project name and ports, ready when each service answers its own protocol, and a service you can pause
or stop in the middle of a test without breaking anyone else's.

**Status: design.** Nothing is built yet. Why it is shaped this way:
[research](docs/research/research-architecture.md); what comes first: [backlog](backlog.md). The
documentation is in [`docs/`](docs/README.md).

v1 targets `linuxX64` only.

## License

MIT — see [LICENSE](LICENSE).
