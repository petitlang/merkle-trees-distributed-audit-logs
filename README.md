# Merkle Trees for Distributed Audit Logs

II.3502 Distributed Architecture and Programming lab (2026/27). Developed by
**Mouzheng LI** and **Yuefan LIU**. See the [lab specification](lab_merkle.pdf).

This Java 17 project stores an append-only audit log in a UTF-8 text file,
with one event per line. See the [sample ledger](examples/ledger.txt).

- `MerkleTree` builds a SHA-256 tree and computes its root hash.
- `LogServer` appends single events or batches and generates proofs.
- `Auditor` checks event membership and verifies that a new log extends an older checkpoint.

The gRPC deployment is planned as a bonus.

## Build and test

Requires **JDK 17** and **Maven 3**. Set `JAVA_HOME` to JDK 17 and run from the
repository root:

```powershell
mvn clean verify
```

This runs the Cucumber and JUnit suites and builds the JAR in `target/`.
The Cucumber report is at `target/cucumber/report.html`.

## Run the video demos

Run the chosen command from the repository root in PowerShell. The script
uses JDK 17 on `PATH` and compiles the demo dependencies without Maven.

| Command | Demonstrates |
| --- | --- |
| `./scenario-tests/run.ps1 1` | Build the eight-event tree and check the two-event root. |
| `./scenario-tests/run.ps1 2` | Verify the first, middle, and last events. |
| `./scenario-tests/run.ps1 3` | Build and verify logs of 5, 6, and 7 events. |
| `./scenario-tests/run.ps1 4` | Append one event and then a batch, verifying both extensions. |
| `./scenario-tests/run.ps1 5` | Verify an event, edit it, and show that verification fails. |

See the [demo guide](scenario-tests/README.md) for expected output and recording tips.

## Further reading

- [LogServer API and proof format](docs/log-server.md)
- [Focused tests for scenarios 1–3](docs/testing-first-three.md)
- [BDD workflow and test results](docs/development.md)
