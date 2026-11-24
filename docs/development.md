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

Expected hashes were calculated independently with Python hashlib. The hash
input is built with `ByteArrayOutputStream`, which writes the tag and payload
bytes in order before `digest.digest(nodeBytes)` computes SHA-256.

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

After replacing manual array copying with `ByteArrayOutputStream`, `mvn test`
on JDK 17 passed all 51 cases.

The next BLUE review reused `getSize()` when validating a parent node, used
`Collectors.toCollection` for mutable test fixtures, and replaced exception
capture in the empty sequence scenario with JUnit's `assertThrows`.
`mvn -B -ntp test` passed all 51 cases on JDK 17.0.18.

`MerkleTree.build(events)` reads the event list directly. The caller keeps it
unchanged during construction. After removing the snapshot and updating callers
to use `build`, `mvn -B -ntp test` passed all 51 cases on JDK 17.0.18.

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

The integrated build uses `MerkleTree.build(events)` when opening the ledger
and after an append. `mvn -B -ntp clean verify` on JDK 17.0.18 passed all 143
tests and produced the JAR.

## Auditor verification

The auditor verifies sibling hashes and prefix proofs against a checkpoint.
It shares leaf and parent hashing with `MerkleTree`. Fixed checkpoint roots
and proof hashes were calculated independently with Python `hashlib`.

Each round began with Gherkin and executable tests while the corresponding
verifier still returned `false`:

| Round | RED | GREEN |
| --- | --- | --- |
| Membership (`@membership`) | The published path and two valid JUnit paths failed. | Recursive root reconstruction made the focused suite pass. |
| Consistency (`@consistency`) | Three published proofs and two valid JUnit checks failed. | Reconstructing both roots made all 157 tests pass. |

Focused commands used `mvn -B -ntp "-Dtest=RunCucumberTest,AuditorTest"`
with `"-Dcucumber.filter.tags=@membership" test` or
`"-Dcucumber.filter.tags=@consistency" test`.

For BLUE, the server scenarios and unit tests were migrated to the production
auditor. The duplicate test verifier was removed. Fixed roots and exact-order
proof vectors still check both sides of the integration independently.
`mvn -B -ntp clean verify` on JDK 17.0.18 passed 62 Cucumber examples and 95
JUnit cases, with no failures or skips, and produced the JAR.

## Focused presentation of testing scenarios 1-3

Reviewed main commit `324f8d1`, then updated yuefan from main. Existing
construction and valid-membership examples now have `@first-three` and
numbered tags. One explicit two-event hash calculation was added, with
intermediate hashes independent of the production hashing helpers. Tagged
scenarios log roots, splits, paths, and membership results to the console
and Cucumber report. Production classes and scenarios 4/5 were not changed.

The calculation uses direct MessageDigest operations for two leaves and their
parent. It needs no general-purpose test hash helper or size-dependent branch.
The original four-event fixed-vector examples remain in place.

On 2026-11-24, JDK 17.0.12 and Maven 3.9.9 passed the 25 selected scenarios
(7 for scenario 1, 3 for scenario 2, and 15 for scenario 3; 108 steps). A clean
regression verification passed all 158 tests: 63 Cucumber and 95 JUnit cases.
There were no failures or skips in that full build. See [the focused testing
guide](testing-first-three.md) for formulas, commands, and screenshot guidance.

## Independent scenario programs for video recording

The `scenario-tests/` directory contains three separate Java source-launcher
programs for scenarios 1, 2, and 3. A PowerShell launcher compiles the current
production classes into `target/scenario-tests/classes` and runs only the
selected scenario. The programs use fixture ledgers and the existing public
APIs. They print full trees and roots, an explicit two-event hash calculation,
and membership proofs with sibling direction and intermediate parent hashes.
Final membership decisions use the production Auditor against fixed reference
checkpoints. The tree used for trace labels is not passed to the Auditor.

All three programs ran successfully on JDK 17.0.12 on 2026-11-24: scenario 1
passed the eight-event and two-event checks; scenario 2 passed three memberships;
scenario 3 passed three tree checks and nine memberships. Runtime transcripts
are local ignored files under `target/scenario-tests/output/`. The production
classes and the existing automated test suites were unchanged in this addition.
