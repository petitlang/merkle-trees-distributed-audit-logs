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

Membership paths, consistency proofs, server operations, and gRPC are outside
this review and will have separate acceptance scenarios.
