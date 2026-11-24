# LogServer: usage and proof contract

`LogServer` manages the ledger and generates proofs. It reuses the existing
`MerkleTree`; it does not implement the production Auditor or network bonus.

## Run the example

```powershell
mvn test
mvn package
java -cp target/classes fr.isep.auditlog.LogServerDemo
```

The example creates a temporary five-event ledger, appends e6, saves the
six-event root, appends e7 and e8 as a batch, prints the eight-event root and
both assignment proof examples, then reloads the ledger. Its temporary file
is deleted on exit.

## API

For a first, read-only example, the repository includes `examples/ledger.txt`
with events e1 through e8. From the repository root, construct
`new LogServer(Path.of("examples/ledger.txt"))`. Its size is 8 and its root is
`7dc0f08884fa7f18ba933c2d46d41f0de6246cdaff7d4808e675beea75e52cd8`.

```java
LogServer server = new LogServer(Path.of("ledger.txt")); // existing UTF-8 file
int oldSize = server.getSize();
byte[] oldRoot = server.getRoot(); // save a trusted checkpoint
server.append("next event");
server.append(List.of("another event", "one more event"));
byte[] newRoot = server.getRoot();
List<byte[]> path = server.genPath(1);
List<byte[]> proof = server.genProof(oldSize); // requires oldSize >= 1
```

All indices are one-based, as in the PDF. Hashes are raw 32-byte SHA-256
values. Use `getRootHex()` for printing. Roots and proof hashes are independent
copies, so changing returned arrays does not change the server's state. Proof
lists cannot be structurally modified.

The constructor preserves spaces, Unicode, duplicate records, and blank lines.
LF, CRLF, and CR delimit records and are excluded from their hashes. A final
line terminator does not create an extra record. A blank physical line is an
empty-string event. New events cannot contain CR or LF. A missing or invalid
UTF-8 file raises `IOException`; the constructor does not silently create it.

An empty file has size 0 and root `SHA256(empty bytes)`. It can receive its
first event. It has no membership proofs; `genProof` follows the lab's domain
1 <= oldSize <= currentSize. An empty append batch leaves the file untouched.

Appends validate the complete batch and build a candidate tree before writing.
Only after a successful file append is that tree published. I/O errors are
propagated; this is a classroom implementation, without crash-atomic writes
or coordinated access by multiple writers. Use one owner per ledger. An
external file edit is read when constructing a new server instance.

## Membership paths: genPath(index)

Only sibling hashes are sent, bottom-up. The event hash and final root are
computed by the auditor and are not redundant entries in the returned list.
For eight records, `genPath(4)` returns:

```text
[h3, h1..2, h5..8]
```

The auditor hashes e4 with the leaf tag, combines h3 on its left, h1..2 on
its left, then h5..8 on its right. It compares the result with its previously
trusted root. The index and committed size determine the directions even
for non-power-of-two trees. Never accept a new root merely because it comes
with a self-consistent path from an untrusted server.

## Consistency proofs: genProof(oldSize)

For old size 6 and current size 8, the list is exactly the PDF example:

```text
[h7..8, h5..6, h1..4]
oldRoot = H(0x01 | h1..4 | h5..6)
newRoot = H(0x01 | h1..4 | H(0x01 | h5..6 | h7..8))
```

For other sizes the proof follows the same recursive prefix boundary. The
seed is the hash of the complete subtree where the old prefix ends. If the
old size is a power of two, that seed is the already trusted old root and
is not sent. Otherwise the first sibling precedes the seed, matching the
PDF's order; subsequent siblings run bottom-up. To use a standard seed-first
iterative consistency verifier, swap the first two hashes only when oldSize
is not a power of two. The test-only `ProofChecks` demonstrates this contract
independently of the production generator.

Examples:

```text
1 -> 2: [h2]                         (h1 is already known)
3 -> 4: [h4, h3, h1..2]
4 -> 8: [h5..8]                      (h1..4 is already known)
5 -> 8: [h6, h5, h7..8, h1..4]
6 -> 8: [h7..8, h5..6, h1..4]
7 -> 8: [h8, h7, h5..6, h1..4]
8 -> 8: []                           (compare roots directly)
```

The auditor needs oldSize, oldRoot, currentSize, currentRoot, and this proof.
It must reconstruct and check both roots. A changed record, hash, or trusted
root must fail verification. After a valid extension, the auditor can adopt
the new checkpoint. Hash order here is an explicit lab contract, not an
unchanged wire encoding from another Merkle protocol.

## Complexity

With n events and fixed-size hashes, `genPath` and `genProof` follow a single
root-to-leaf boundary. Each visited level adds at most two existing hashes.
Both take O(log n) time and O(log n) output and stack space; they never rebuild
subtrees. Single-event trees and unchanged-size proofs take O(1).

Membership verification (`isMember`, implemented later in the Auditor) uses
one leaf hash and one parent hash per sibling: O(log n) time plus the event's
UTF-8 byte length. It can fold the path with O(1) working space apart from
the supplied proof. The test-only checker also materializes O(log n) direction
flags for clarity.

This implementation rebuilds the tree on append: O(n) hashes and O(n) space,
plus encoding/hashing all event bytes. This is sufficient for the lab; an
incremental append algorithm is a separate optimization.

## BDD development record (2026-11-24)

1. Baseline: the existing 16 Cucumber examples and 35 MerkleTree JUnit cases
   passed (51 total) on the yuefan branch.
2. RED: added 39 LogServer Cucumber examples, executable step definitions,
   an independent proof checker, and a production API skeleton. Running
   the LogServer acceptance suite failed at the explicitly unimplemented
   constructor with `UnsupportedOperationException`. These were missing
   production behaviors, not undefined or pending Cucumber steps.
3. GREEN: implemented loading, persisted appends, paths, and general prefix
   proofs. All 143 tests passed: 55 Cucumber examples (16 original + 39 new),
   35 MerkleTree JUnit cases, and 53 LogServer JUnit cases.
4. BLUE: extracted batch validation and ledger writing, documented the proof
   contract and complexity, and added the runnable console example. The final
   clean verification and console result are recorded in `development.md`.

Run only the new acceptance scenarios:

```powershell
mvn "-Dtest=RunCucumberTest" "-Dcucumber.filter.tags=@log-server" test
```

Independent checks cover every event and every possible old size for 17 tree
sizes from 1 to 65, especially around powers of two. Additional cases cover
tampered events/proofs/history, invalid requests, defensive copies, UTF-8,
line endings, empty files, batch validation, persistence, and I/O errors.

The reference verifiers live only under `src/test`; they validate the server's
proof output and do not claim to complete the production Auditor component.
The full assignment still needs that Auditor, the distributed bonus if chosen,
and the final report/screenshots/video.
