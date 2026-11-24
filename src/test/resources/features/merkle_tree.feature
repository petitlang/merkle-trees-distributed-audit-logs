@merkle-tree
Feature: Build a tamper-evident Merkle tree
  As an audit log operator
  I want ordered events to have a reproducible Merkle root
  So that changed events cannot retain the same commitment

  Scenario Outline: Commit a single event using tagged UTF-8 hashing
    Given the single event "<event>"
    When I build the Merkle tree
    Then the root hash is "<hash>"
    And the root covers events 1 through 1
    And the root is a leaf without children

    Examples:
      | event    | hash                                                             |
      | e1       | d38b823b070424fc9303ff6f20f4ddeef6dc32aa02c75d9c8c3ae46062c78f6b |
      |          | 6e340b9cffb37a989ca544e6bb780a2c78901d3fb33738768511a30617afa01d |
      | 🙂       | a07d9492ff6d9dbe8d9b22ef2247924a8598c53e8f6944e057e2a3a605c9988b |

  Scenario: Commit a multibyte UTF-8 event
    Given a Unicode login event
    When I build the Merkle tree
    Then the root hash is "056e4ccd70746090c5087c1f0279edbfa2a34009fe32cce9dce5bc23d77fce6b"
    And the root covers events 1 through 1
    And the root is a leaf without children

  Scenario Outline: Reproduce independently calculated root hashes
    Given the first <size> events named e1 onwards
    When I build the Merkle tree
    Then the root hash is "<hash>"
    And the root covers events 1 through <size>

    Examples:
      | size | hash                                                             |
      | 2    | 1ae210a506da2f0264c59f2041fe70fe9bfceda1cbb394471e9587e44b430216 |
      | 4    | 7abfaa969284fb86f37f1e0aa9ade022007b377e88766f4d0d2d6419a8d20d40 |
      | 8    | 7dc0f08884fa7f18ba933c2d46d41f0de6246cdaff7d4808e675beea75e52cd8 |

  Scenario Outline: Preserve non-power-of-two event sequences without padding
    Given the first <size> events named e1 onwards
    When I build the Merkle tree
    Then the root hash is "<hash>"
    And the root covers events 1 through <size>
    And its left subtree covers events 1 through 4
    And its right subtree covers events 5 through <size>
    And the tree has <size> leaves in their original order

    Examples:
      | size | hash                                                             |
      | 5    | 7fd3c099308e01bbab60705cd997b86c446f1b9506e812d0497ade31835435fc |
      | 6    | 0ec8a9f5275054d630180595e5d356faf48ea547c96e4b9c9ebe89303ce326b8 |
      | 7    | d4c3f3d9e11b8582989a2f71b1bde67120ae3183c355f6b5895004ddc5c55ad2 |

  Scenario Outline: Detect a changed first, middle, or last event
    Given the first 8 events named e1 onwards
    When I build the Merkle tree
    And I replace event <index> with "tampered" and rebuild
    Then the rebuilt root differs from the original root

    Examples:
      | index |
      | 1     |
      | 4     |
      | 8     |

  Scenario: Detect reordered events
    Given the first 8 events named e1 onwards
    When I build the Merkle tree
    And I swap events 1 and 8 and rebuild
    Then the rebuilt root differs from the original root

  Scenario: Protect a committed tree from external mutation
    Given the first 4 events named e1 onwards
    When I build the Merkle tree
    And I modify the source event list and the returned hash bytes
    Then the committed root remains unchanged

  Scenario: Reject an empty sequence because it has no node
    Given an empty event sequence
    When I try to build the Merkle tree
    Then construction is rejected with an illegal argument
