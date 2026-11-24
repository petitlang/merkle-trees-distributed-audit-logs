# Development record

## 2026-11-24: Introduce Cucumber for the existing MerkleTree

Environment: Java 17.0.18, Maven 3.9.9, branch `mouzheng`.

The production class and its 19 JUnit cases existed before the user specified
the Gherkin -> RED -> GREEN -> BLUE process. This entry records retrospective
acceptance coverage, not a claim that the original implementation was test-first.

### Gherkin

Added 11 executable examples in `src/test/resources/features/merkle_tree.feature`:
UTF-8 leaf hashing, independently calculated roots for 2/4/8 events, non-power-of-two
trees for 5/6/7 events, changed-event detection, immutability, and empty-input rejection.
The expected hashes are fixed vectors calculated independently using Python hashlib.

### RED: acceptance wiring

Wrote the Java step definitions with the two construction actions initially
throwing Cucumber PendingException. Ran:

```powershell
mvn "-Dtest=RunCucumberTest" test
```

Observed: Maven exit code 1; 11 cases errored due to the deliberately pending
construction actions. This proves discovery and incomplete step wiring fail the
build; it is not a missing-production-behavior RED.

### GREEN: connect the existing implementation

Implemented the construction actions using MerkleTree.fromEvents and captured
the expected exception for the empty-input case. Ran `mvn test`.

Observed: 11 Cucumber cases plus 19 JUnit cases passed, 30 total.
No new production behavior was needed for these existing requirements.

### Regression sensitivity check

Temporarily changed the leaf hash tag from 0x00 to 0x01 and ran the Cucumber
suite. Maven exited with code 1 and the fixed-vector assertions reported root
hash mismatches. Restored the exact original source in a finally block.
This is a deliberate fault check, not a substitute for test-first development.

### BLUE: refactor hashing

Extracted the shared tagged SHA-256 operation into a private taggedHash helper.
Leaf and internal-node constructors still use their distinct tag bytes and raw
hash inputs. The public API and tree layout remain the same.

Ran `mvn test` after the refactor using JDK 17.0.18. Observed: 30 tests passed
(11 Cucumber examples and 19 JUnit cases), with zero failures, errors, or skips.
HTML and JSON reports were generated under `target/cucumber/`.
`git diff --check` also passed.

Local console logs are retained in `target/tdd-evidence/`: cucumber-red.log,
cucumber-green.log, cucumber-fault-check.log, and cucumber-blue.log. Generated
reports and logs are ignored by Git; this document records their observed results.

### Future changes

For LogServer, Auditor, Client, and gRPC functionality: write the Gherkin examples,
implement executable assertions, observe a failure caused by the missing behavior,
implement that behavior, then refactor and rerun tests. Record the actual commands
and results for each cycle. Do not manufacture an implementation history.

## 2026-11-24: Match the assignment's MessageDigest style (BLUE)

At the user's request, taggedHash now assembles the tag and all payload bytes
into a single nodeBytes array. The method directly calls
MessageDigest.getInstance("SHA-256") followed by digest.digest(nodeBytes), as in
the assignment example. Removed the now-unused newDigest helper.

This is a behavior-preserving refactor; no new Gherkin behavior or RED phase was
needed. Ran `mvn test` with JDK 17.0.18: all 11 Cucumber examples and 19 JUnit cases
passed (30 total). `git diff --check` passed. The console log is retained locally
at `target/tdd-evidence/cucumber-digest-style.log`.

## 2026-11-24: BLUE review before the first implementation push

Reviewed all public methods and compared the tree behavior with the assignment:

| Behavior | Acceptance / unit evidence |
| --- | --- |
| Tagged UTF-8 leaf hashing | Fixed vectors for ASCII, Chinese, emoji, and an empty event |
| Tagged raw-byte internal hashing | Independent roots for 2/3/4/6/8/9/16/17/1025 events |
| Non-power-of-two construction | Fixed roots and subtree ranges for 5/6/7; structural checks around power-of-two boundaries |
| Children, inclusive one-based ranges, node counts | Recursive structure checks for 15 tree sizes; manual 2- and 6-event trees |
| Tamper evidence and event ordering | Modified first/middle/last entries and swapped entries change the root |
| Immutability | Changing source lists and returned hash arrays preserves committed nodes |
| Input boundaries | Empty list, null list/event/children, invalid indices, gaps, overlap, reversed ranges, and invalid splits |
| Event contents and index limits | Empty/duplicate events, preserved whitespace/newline, and adjacent indices near Integer.MAX_VALUE |

Extended existing acceptance scenarios and unit coverage before this review;
all behavior already existed, so no new production implementation or behavior
RED was needed. Preserved the user's source comments and the requested digest
style. No functional changes were necessary.

Ran the following with Java 17.0.18 and Maven 3.9.9:

```powershell
mvn -B -ntp clean org.jacoco:jacoco-maven-plugin:0.8.12:prepare-agent verify org.jacoco:jacoco-maven-plugin:0.8.12:report
```

The clean build passed all 51 tests (16 Cucumber, 35 JUnit) and built the JAR.
The initial coverage run did not find its execution data due to the Windows
non-ASCII destination path. Reran coverage with an ASCII temporary destination:

```powershell
# Use an unused ASCII path for coverage data on Windows.
mvn -B -ntp "-Djacoco.destFile=C:/Users/Public/merkle-review.exec" "-Djacoco.dataFile=C:/Users/Public/merkle-review.exec" org.jacoco:jacoco-maven-plugin:0.8.12:prepare-agent verify org.jacoco:jacoco-maven-plugin:0.8.12:report
```

The actual run used a unique temporary filename; after generating the report,
copied execution data to target/jacoco.exec and removed the temporary file.
All 51 tests passed again. JaCoCo reported:

- Methods: 13/13 covered (100%).
- Branches: 16/16 covered (100%).
- Executable lines: 55/57 covered (96.49%).
- The two missed lines are the NoSuchAlgorithmException catch/throw path.
  SHA-256 is supplied by a standard JDK; the test suite does not replace or
  remove JVM security providers merely to execute this fallback.

Reports: target/site/jacoco/index.html and target/cucumber/report.html.
Review logs: tmp/verification/blue-verify.log and blue-coverage.log. Maven clean
removes earlier target reports/logs; historical observations remain recorded
above. Generated artifacts are ignored by Git.

Scope: this review covers MerkleTree construction and node access. Membership
paths, consistency proofs, LogServer, Auditor, and gRPC remain future work and
will follow their own Gherkin -> RED -> GREEN -> BLUE cycles.
