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

The Java 17 implementation includes `MerkleTree`, a file-backed `LogServer`,
and `Auditor`, with Cucumber scenarios and JUnit tests. The client and optional
gRPC service are planned.

## Build and test

Requires JDK 17 and Maven 3. Set `JAVA_HOME` to the JDK 17 installation and put
its `bin` directory on `PATH`. From the repository root, check the Java version
and build the project:

```powershell
mvn -version
mvn clean verify
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

To run only lab testing scenarios 1-3, including root output, explicit small-log
hash calculations, and first/middle/last membership checks:

```powershell
mvn "-Dtest=RunCucumberTest" "-Dcucumber.filter.tags=@first-three" test
```

See [testing scenarios 1-3](docs/testing-first-three.md) for the numbered groups
and screenshot instructions.

## MerkleTree usage

```java
import fr.isep.auditlog.MerkleTree;
import java.util.List;

MerkleTree tree = MerkleTree.build(
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
- Building the tree makes n indexed reads and stores O(n) nodes, with O(log n)
  recursion depth. Use a list with fast indexed reads, such as `ArrayList`, and
  keep it unchanged during construction.

For events e1 through e8, the expected root is:

```text
7dc0f08884fa7f18ba933c2d46d41f0de6246cdaff7d4808e675beea75e52cd8
```

The MerkleTree coverage contains 17 Cucumber examples and 35 JUnit cases. It covers independent
hash vectors, empty/UTF-8/emoji events, whitespace preservation, tree sizes up to
1025, node ranges, first/middle/last-event tampering, event order, immutability,
invalid inputs, and indices near Integer.MAX_VALUE.

The initial BLUE review passed on JDK 17 with 100% branch coverage and 55/57 executable
lines covered in MerkleTree. The two uncovered lines handle an unavailable
SHA-256 implementation, which a standard JDK provides. These coverage figures
apply to `MerkleTree`. The development record lists the verification commands.

## LogServer and Auditor example

The server loads an existing UTF-8 text ledger, with one event per line.
It supports `append(String)` for one event and `append(List<String>)` for a batch.
The following example creates a temporary ledger, saves its six-event checkpoint,
appends two events, and verifies the new checkpoint and event 4.

```java
import fr.isep.auditlog.Auditor;
import fr.isep.auditlog.LogServer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

Path ledger = Files.createTempFile("audit-example-", ".txt");
try {
    Files.write(ledger, List.of("e1", "e2", "e3", "e4", "e5", "e6"));
    LogServer server = new LogServer(ledger);
    int oldSize = server.getSize();
    byte[] oldRoot = server.getRoot();

    server.append(List.of("e7", "e8"));
    Auditor auditor = new Auditor(server.getSize(), server.getRoot());
    boolean consistent = auditor.isConsistent(oldSize, oldRoot, server.genProof(oldSize));
    boolean member = consistent && auditor.isMember("e4", 4, server.genPath(4));

    System.out.println("Consistent extension: " + consistent);
    System.out.println("Event 4 verified: " + member);
} finally {
    Files.deleteIfExists(ledger);
}
```

Both checks print `true`. For a read-only example, the sample ledger
[`examples/ledger.txt`](examples/ledger.txt) already contains events `e1` to `e8`.

For an eight-event log, `genPath(4)` returns `[h3, h1..2, h5..8]` and
`genProof(6)` returns `[h7..8, h5..6, h1..4]`, matching the PDF. The auditor
must check these against trusted roots. See [LogServer contract and BDD
record](docs/log-server.md) for hash ordering, empty-log handling, complexity,
and the RED/GREEN/BLUE development record.

The caller supplies the trusted starting checkpoint. After a consistency check
succeeds, the caller can accept the new checkpoint and use it to verify membership.
An audit path contains raw SHA-256 sibling hashes in leaf-to-root order. The event
index is one-based. Missing, extra, malformed, or incorrect proof hashes cause
verification to return `false`.

The suite has 63 Cucumber examples and 95 JUnit cases. Fixed checkpoint and
proof vectors test the verifier independently of the server. The server tests
also check generated proofs with the auditor across a range of log sizes.

## Deliverables

- Source code and build/run instructions.
- PDF report covering design, output screenshots, test scenarios, complexity analysis, and participant contributions.
- A 3-5 minute demonstration video.
