@log-server
Feature: Manage an append-only ledger and provide independently checkable proofs
  As an audit log operator
  I want to load and extend a text ledger and return compact proofs
  So that an auditor can check records against previously trusted roots

  Scenario Outline: Load one event per line and reproduce the expected root
    Given a ledger containing <size> numbered events
    When I open the log server
    Then the server contains <size> events
    And the server root is "<root>"

    Examples: Single-event boundary
      | size | root                                                             |
      | 1    | d38b823b070424fc9303ff6f20f4ddeef6dc32aa02c75d9c8c3ae46062c78f6b |

    @first-three @scenario-1
    Examples: Two, four, and eight events from a ledger
      | size | root                                                             |
      | 2    | 1ae210a506da2f0264c59f2041fe70fe9bfceda1cbb394471e9587e44b430216 |
      | 4    | 7abfaa969284fb86f37f1e0aa9ade022007b377e88766f4d0d2d6419a8d20d40 |
      | 8    | 7dc0f08884fa7f18ba933c2d46d41f0de6246cdaff7d4808e675beea75e52cd8 |

    @first-three @scenario-3
    Examples: Non-power-of-two ledgers
      | size | root                                                             |
      | 5    | 7fd3c099308e01bbab60705cd997b86c446f1b9506e812d0497ade31835435fc |
      | 6    | 0ec8a9f5275054d630180595e5d356faf48ea547c96e4b9c9ebe89303ce326b8 |
      | 7    | d4c3f3d9e11b8582989a2f71b1bde67120ae3183c355f6b5895004ddc5c55ad2 |

  Scenario: Append one event and retain it after reopening the ledger
    Given a ledger containing 6 numbered events
    And I open the log server
    When I append the event "e7"
    Then the server contains 7 events
    And reopening the ledger preserves its events and root

  Scenario: Append a batch and prove that the original six events remain intact
    Given a ledger containing 6 numbered events
    And I open the log server
    And I save the current size and root
    When I append numbered events 7 through 8 as a batch
    Then the server contains 8 events
    And reopening the ledger preserves its events and root
    When I request a consistency proof for the saved version
    Then the auditor accepts the new checkpoint
    And the proof hashes cover ranges "7-8,5-6,1-4" in that order

  Scenario: Separate an appended event from an unterminated last line
    Given a ledger containing 1 numbered events without a final newline
    And I open the log server
    When I append the event "e2"
    Then reopening the ledger preserves its events and root
    And the server contains 2 events

  Scenario Outline: Prove membership of first, middle, and last events
    Given a ledger containing <size> numbered events
    And I open the log server
    When I request the audit path for event <index>
    Then the auditor confirms membership for event <index>

    @first-three @scenario-2
    Examples: First, middle, and last events in the eight-event log
      | size | index |
      | 8    | 1     |
      | 8    | 4     |
      | 8    | 8     |

    @first-three @scenario-3
    Examples: First, middle, and last events in non-power-of-two logs
      | size | index |
      | 5    | 1     |
      | 5    | 3     |
      | 5    | 5     |
      | 6    | 1     |
      | 6    | 3     |
      | 6    | 6     |
      | 7    | 1     |
      | 7    | 4     |
      | 7    | 7     |

  Scenario: Return the exact audit path in the assignment
    Given a ledger containing 8 numbered events
    And I open the log server
    When I request the audit path for event 4
    Then the proof hashes cover ranges "3-3,1-2,5-8" in that order

  Scenario: Reject a changed event against the saved root
    Given a ledger containing 8 numbered events
    And I open the log server
    When I request the audit path for event 4
    Then the auditor rejects "tampered" at index 4

  Scenario: Reject a changed audit path against the saved root
    Given a ledger containing 8 numbered events
    And I open the log server
    When I request the audit path for event 4
    And I change the first proof hash
    Then the auditor rejects the path for event 4

  Scenario Outline: Prove consistency for different old and new sizes
    Given a ledger containing <old> numbered events
    And I open the log server
    And I save the current size and root
    When I append numbered events <first> through <new> as a batch
    And I request a consistency proof for the saved version
    Then the auditor accepts the new checkpoint

    Examples:
      | old | first | new |
      | 1   | 2     | 8   |
      | 2   | 3     | 3   |
      | 3   | 4     | 4   |
      | 4   | 5     | 8   |
      | 5   | 6     | 8   |
      | 6   | 7     | 8   |
      | 7   | 8     | 8   |

  Scenario: An unchanged log needs no consistency hashes
    Given a ledger containing 8 numbered events
    And I open the log server
    And I save the current size and root
    When I request a consistency proof for the saved version
    Then the proof contains 0 hashes
    And the auditor accepts the new checkpoint

  Scenario: Reject a changed consistency proof
    Given a ledger containing 6 numbered events
    And I open the log server
    And I save the current size and root
    When I append numbered events 7 through 8 as a batch
    And I request a consistency proof for the saved version
    And I change the first proof hash
    Then the auditor rejects the new checkpoint

  Scenario: Detect a rewritten historical record after loading the modified file
    Given a ledger containing 6 numbered events
    And I open the log server
    And I save the current size and root
    When I rewrite the ledger with a changed first event and reopen it
    And I append numbered events 7 through 8 as a batch
    And I request a consistency proof for the saved version
    Then the auditor rejects the new checkpoint

  Scenario Outline: Reject invalid audit indices
    Given a ledger containing 8 numbered events
    And I open the log server
    When I try to request the audit path for event <index>
    Then the server rejects the request with an illegal argument

    Examples:
      | index |
      | 0     |
      | 9     |

  Scenario Outline: Reject invalid previous sizes
    Given a ledger containing 8 numbered events
    And I open the log server
    When I try to request a consistency proof for size <size>
    Then the server rejects the request with an illegal argument

    Examples:
      | size |
      | 0    |
      | 9    |
