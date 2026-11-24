@auditor
Feature: Verify Merkle proofs against a published checkpoint

  @membership
  Scenario: Verify the fourth event using the published audit path
    Given the published checkpoint for 8 numbered events
    And the published audit path for the fourth event
    When the auditor checks event "e4" at index 4
    Then the auditor accepts the claim

  @membership
  Scenario: Reject a changed event against the original checkpoint
    Given the published checkpoint for 8 numbered events
    And the published audit path for the fourth event
    When the auditor checks event "changed" at index 4
    Then the auditor rejects the claim

  @membership
  Scenario: Reject a modified hash from the audit path
    Given the published checkpoint for 8 numbered events
    And the published audit path for the fourth event
    And the first hash in the auditor's proof is changed
    When the auditor checks event "e4" at index 4
    Then the auditor rejects the claim

  @consistency
  Scenario Outline: Verify an extension using its published consistency proof
    Given the published checkpoint for 8 numbered events
    And the published consistency proof from <oldSize> to 8 events
    When the auditor checks the extension from <oldSize> events
    Then the auditor accepts the claim

    Examples:
      | oldSize |
      | 4       |
      | 6       |
      | 8       |

  @consistency
  Scenario: Reject a modified hash from the consistency proof
    Given the published checkpoint for 8 numbered events
    And the published consistency proof from 6 to 8 events
    And the first hash in the auditor's proof is changed
    When the auditor checks the extension from 6 events
    Then the auditor rejects the claim
