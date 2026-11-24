# Merkle Trees for Distributed Audit Logs

Lab project for II.3502 - Distributed Architecture and Programming (2026/27).

Developed by **Mouzheng LI** and **Yuefan LIU**, using **JDK 17**.

## Lab specification

The original assignment is available in [lab_merkle.pdf](lab_merkle.pdf).

## Scope

- Implement a SHA-256 Merkle tree with domain separation for leaves and internal nodes.
- Build a log server supporting append operations, membership paths, and consistency proofs.
- Implement an auditor that verifies membership and append-only consistency.
- Demonstrate valid proofs, non-power-of-two log sizes, and tamper detection.
- Optional bonus: expose the server to multiple clients and a separate auditor process.

## Status

Implemented MerkleTree and a file-backed LogServer in Java 17, with Cucumber
acceptance scenarios and JUnit tests. Auditor, Client, and the optional gRPC
service will be added in later steps.

## Build and test

Requires JDK 17 and Maven 3. Set JAVA_HOME to the JDK 17 installation and ensure
that its bin directory is on PATH. Check the Java version used by Maven:

```powershell
mvn -version
mvn test
mvn package
```

The Maven compiler targets Java 17. The tree itself uses only the Java standard
library; Cucumber and JUnit are test dependencies.

## Development workflow: Gherkin -> RED -> GREEN -> BLUE

For each new behavior:

1. Describe acceptance examples using Given/When/Then in
   `src/test/resources/features/*.feature`.
2. RED: write the Java step definitions and any focused JUnit tests. Run the
   tests and confirm failure due to the missing behavior.
3. GREEN: implement the smallest production change that makes the tests pass.
4. BLUE: refactor without changing behavior, then rerun the relevant tests.

Cucumber executes the Gherkin scenarios through Java step definitions. JUnit
also provides assertions and detailed unit coverage. A scenario describing
invalid input should pass when the application correctly rejects that input;
RED means the required behavior is missing or incorrect.

```powershell
# Run only the Cucumber acceptance suite
mvn "-Dtest=RunCucumberTest" test

# Run Cucumber and JUnit together
mvn test
```

The Cucumber runner writes `target/cucumber/report.html` and `report.json`.
See [development and testing](docs/development.md) for the test scope and
validation results.

## MerkleTree usage

```java
import fr.isep.auditlog.MerkleTree;
import java.util.List;

MerkleTree tree = MerkleTree.fromEvents(
        List.of("e1", "e2", "e3", "e4", "e5", "e6", "e7", "e8"));

System.out.println(tree.getHashHex());
System.out.println(tree.getStartIndex() + ".." + tree.getEndIndex()); // 1..8

// A small tree can also be assembled manually.
MerkleTree first = new MerkleTree("e1", 1);
MerkleTree second = new MerkleTree("e2", 2);
MerkleTree parent = new MerkleTree(first, second);
```

- Each node stores its SHA-256 hash, left/right children, and inclusive one-based
  event range. Leaves have null children.
- Leaves hash `0x00 || UTF-8(event)`. Parents hash
  `0x01 || leftHash || rightHash`, using raw hash bytes rather than hexadecimal text.
- For n > 1, the left subtree contains the largest power of two strictly below n.
  For example, 6 events split as 4 + 2, and 7 split as 4 + 3. No leaves are duplicated.
- Nodes are immutable. `getHash()` returns a copy suitable for later gRPC byte fields;
  `getHashHex()` returns a printable representation.
- Empty sequences are rejected because this class represents an actual node.
  Empty event strings and duplicate events are allowed. Null events are rejected.
- Building n events takes O(n) time and O(n) storage, with O(log n) recursion depth.

For events e1 through e8, the expected root is:

```text
7dc0f08884fa7f18ba933c2d46d41f0de6246cdaff7d4808e675beea75e52cd8
```

The MerkleTree coverage contains 16 Cucumber examples and 35 JUnit cases. It covers independent
hash vectors, empty/UTF-8/emoji events, whitespace preservation, tree sizes up to
1025, node ranges, first/middle/last-event tampering, event order, immutability,
invalid inputs, and indices near Integer.MAX_VALUE.

The BLUE review passed on JDK 17 with 100% branch coverage and 55/57 executable
lines covered in MerkleTree. The two uncovered lines handle an unavailable
SHA-256 implementation, which a standard JDK provides. This validation covers
the current tree class; proofs, server operations, and gRPC will receive their
own scenarios as they are implemented. See the development record for commands.

## LogServer usage

The server loads an existing UTF-8 text ledger, with one event per line.
It persists single and batch appends, returns the current size/root, and
generates membership paths and consistency proofs using one-based indices.

```java
LogServer server = new LogServer(Path.of("ledger.txt"));
int oldSize = server.getSize();
byte[] oldRoot = server.getRoot();
server.append("e7");
server.append(List.of("e8", "e9"));
List<byte[]> path = server.genPath(4);
List<byte[]> proof = server.genProof(oldSize);
```

These types use `fr.isep.auditlog.LogServer`, `java.nio.file.Path`, and
`java.util.List`. The sample ledger `examples/ledger.txt` contains events e1
through e8. Cucumber and JUnit exercise the server with temporary ledger files.

For an eight-event log, `genPath(4)` returns `[h3, h1..2, h5..8]` and
`genProof(6)` returns `[h7..8, h5..6, h1..4]`, matching the PDF. The auditor
must check these against trusted roots. See [LogServer contract and BDD
record](docs/log-server.md) for hash ordering, empty-log handling, complexity,
and the RED/GREEN/BLUE development record.

The combined suite contains 55 Cucumber examples and 88 JUnit cases, with
independent proof verification and valid/tampered cases. The verifiers in
`src/test` are test oracles; the production Auditor remains to be implemented.

## Deliverables

- Source code and build/run instructions.
- PDF report covering design, output screenshots, test scenarios, complexity analysis, and participant contributions.
- A 3-5 minute demonstration video.
