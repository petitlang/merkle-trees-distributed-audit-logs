# Development and testing

This project uses JDK 17, Maven, Cucumber, and JUnit 5.

## Workflow

1. Gherkin: describe the expected behavior in a feature file.
2. RED: implement the steps and assertions, then confirm that the test fails
   because the required behavior is missing.
3. GREEN: implement the behavior and rerun the tests.
4. BLUE: refactor and rerun the relevant suite.

The initial MerkleTree implementation and unit tests preceded the Cucumber
suite. Acceptance coverage was added afterward. Subsequent functionality follows
this workflow from the first scenario.

## Test scope

| Behavior | Tests |
| --- | --- |
| Leaf hashing | Fixed SHA-256 vectors for ASCII, multibyte UTF-8, emoji, and empty events |
| Internal hashing | Independent roots for 2, 3, 4, 6, 8, 9, 16, 17, and 1025 events |
| Non-power-of-two trees | Fixed roots and subtree ranges for 5, 6, and 7 events |
| Tree structure | Ordered leaves, child references, inclusive indices, and node counts |
| Manual construction | Two-event and six-event trees; adjacent ranges near Integer.MAX_VALUE |
| Changed input | First, middle, and last event modifications; reordered events |
| Immutability | Changes to input lists and returned hash arrays |
| Validation | Empty/null inputs, invalid indices, missing children, gaps, overlaps, reversed ranges, and invalid splits |
| Event contents | Duplicate events, empty strings, whitespace, and line breaks |

Expected hashes were calculated independently with Python hashlib. Production
hashing concatenates the tag and payload bytes before calling
MessageDigest.getInstance("SHA-256") and digest.digest(nodeBytes).

## Validation results

The BLUE review on 2026-11-24 used JDK 17.0.18 and Maven 3.9.9:

- Clean build and JAR packaging passed.
- 16 Cucumber scenarios and 35 JUnit cases passed, with no failures or skips.
- JaCoCo 0.8.12: 13/13 methods, 16/16 branches, and 55/57 executable lines covered.
- The two uncovered lines handle an unavailable SHA-256 implementation.
- Temporarily changing the leaf tag from 0x00 to 0x01 caused fixed-vector
  assertions to fail. The original tag was restored before the passing run.

The initial Cucumber wiring failed with 11 pending construction steps. Once the
steps invoked the existing implementation, all 30 tests passed. This was a
runner setup check; it was not a missing-production-behavior RED. Additional
boundary cases brought the suite to 51 tests. The hashing refactor and comment
changes preserved the results.

After the documentation cleanup, the Unicode fixture kept its original code
points using Java escapes and an English Gherkin description. Running `mvn test`
on JDK 17 again passed all 51 cases.

## Commands and reports

```powershell
mvn clean verify
mvn "-Dtest=RunCucumberTest" test
```

Cucumber reports are written to target/cucumber/report.html and report.json.
JUnit results are in target/surefire-reports. Coverage reports are in
target/site/jacoco when running JaCoCo prepare-agent and report goals.
On Windows, an ASCII-only execution-data path avoids encoding issues in the
coverage agent arguments. Build output, coverage data, and local tool settings
are excluded from version control.

Membership paths, consistency proofs, server operations, and gRPC were outside
the original MerkleTree review above.

## LogServer addition on yuefan

LogServer now has its own acceptance scenarios. See [the behavior contract
and RED/GREEN/BLUE record](log-server.md). The original MerkleTree source and
tests were preserved. Production Auditor and network transport remain separate
components.

Final BLUE validation on 2026-11-24 used this machine's JDK 17.0.12 and Maven
3.9.9 (the installed Maven executable was invoked directly because it is not
on PATH):

- `mvn clean verify` passed: 55 Cucumber examples, 53 LogServer JUnit cases,
  and 35 existing MerkleTree JUnit cases; 143 total, no failures or skips.
- `java -cp target/classes fr.isep.auditlog.LogServerDemo` passed. It loaded
  five records, appended e6, saved the six-record root, appended e7/e8 as a
  batch, printed both exact assignment proof lists, and reopened eight records
  with the same root.
- The six-record root was
  `0ec8a9f5275054d630180595e5d356faf48ea547c96e4b9c9ebe89303ce326b8`;
  the eight-record root was
  `7dc0f08884fa7f18ba933c2d46d41f0de6246cdaff7d4808e675beea75e52cd8`.
- A final `mvn test` after removing an unreachable consistency-helper branch
  preserved the same 143 passing cases.

RED, GREEN, and BLUE command logs are local ignored files under `tmp/`.
Current HTML/JSON Cucumber output is under `target/cucumber/`. This work
validates LogServer behavior with independent test-only verifiers; it does
not validate a production Auditor or a distributed deployment.
