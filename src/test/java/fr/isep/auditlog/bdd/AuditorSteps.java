package fr.isep.auditlog.bdd;

import fr.isep.auditlog.Auditor;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AuditorSteps {
    private static final HexFormat HEX = HexFormat.of();
    private static final String ROOT_4 = "7abfaa969284fb86f37f1e0aa9ade022007b377e88766f4d0d2d6419a8d20d40";
    private static final String ROOT_6 = "0ec8a9f5275054d630180595e5d356faf48ea547c96e4b9c9ebe89303ce326b8";
    private static final String ROOT_8 = "7dc0f08884fa7f18ba933c2d46d41f0de6246cdaff7d4808e675beea75e52cd8";
    private static final String HASH_5_TO_8 = "dda44e8c742b4d19cbd22526fd4418606dd10bb2422929669853d9f8dca94591";

    private Auditor auditor;
    private List<byte[]> proof;
    private boolean accepted;

    @Given("the published checkpoint for 8 numbered events")
    public void eightEventCheckpoint() {
        auditor = new Auditor(8, HEX.parseHex(ROOT_8));
    }

    @Given("the published audit path for the fourth event")
    public void fourthEventPath() {
        proof = List.of(
                HEX.parseHex("d5bdb58a19173a6a0111df3f1f62dbaa00e44221e0f09b47489812a010cd5314"),
                HEX.parseHex("1ae210a506da2f0264c59f2041fe70fe9bfceda1cbb394471e9587e44b430216"),
                HEX.parseHex(HASH_5_TO_8));
    }

    @Given("the published consistency proof from {int} to 8 events")
    public void consistencyProof(int oldSize) {
        proof = switch (oldSize) {
            case 4 -> List.of(HEX.parseHex(HASH_5_TO_8));
            case 6 -> List.of(
                    HEX.parseHex("5eda4828534046536288bbb71ddfd3da6d3cf0ecddc8f4883206c00dd8ed3180"),
                    HEX.parseHex("522d9d790d281d06b68af79d5212826fe65eb8248cf004464d4e8b0f2df0d392"),
                    HEX.parseHex(ROOT_4));
            case 8 -> List.of();
            default -> throw new IllegalArgumentException("no fixture for size " + oldSize);
        };
    }

    @Given("the first hash in the auditor's proof is changed")
    public void changeFirstHash() {
        proof.get(0)[0] ^= 1;
    }

    @When("the auditor checks event {string} at index {int}")
    public void checkEvent(String event, int index) {
        accepted = auditor.isMember(event, index, proof);
    }

    @When("the auditor checks the extension from {int} events")
    public void checkExtension(int oldSize) {
        String oldRoot = switch (oldSize) {
            case 4 -> ROOT_4;
            case 6 -> ROOT_6;
            case 8 -> ROOT_8;
            default -> throw new IllegalArgumentException("no checkpoint for size " + oldSize);
        };
        accepted = auditor.isConsistent(oldSize, HEX.parseHex(oldRoot), proof);
    }

    @Then("the auditor accepts the claim")
    public void acceptsClaim() {
        assertTrue(accepted);
    }

    @Then("the auditor rejects the claim")
    public void rejectsClaim() {
        assertFalse(accepted);
    }
}
