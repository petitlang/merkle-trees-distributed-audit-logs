# Project instructions

- Use Java JDK 17 and Maven. Work on the `mouzheng` branch for the current work.
- Follow the user's development cycle for each new behavior:
  1. Cucumber/Gherkin: describe expected behavior in a `.feature` file first.
  2. RED: implement the Java step definitions and focused tests; run them and
     confirm a failure caused by the missing behavior before implementing it.
  3. GREEN: implement the smallest production change that passes those tests.
  4. BLUE: refactor while preserving behavior, then rerun the relevant suite.
- Keep Gherkin scenarios readable as acceptance examples. Keep JUnit unit tests
  for detailed edge cases and assertions. Both run through `mvn test`.
- Record commands and observed RED/GREEN/BLUE results in repository development
  documentation. Do not claim that tests preceded code when they did not.
- Existing MerkleTree code predates this workflow. Its initial Cucumber suite is
  retrospective coverage; future features must start with a real behavior RED.
