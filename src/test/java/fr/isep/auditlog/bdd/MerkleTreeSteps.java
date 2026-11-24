package fr.isep.auditlog.bdd;

import fr.isep.auditlog.MerkleTree;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/** Cucumber creates a new instance for every scenario; no shared static state. */
public class MerkleTreeSteps {
    private List<String> events;
    private MerkleTree tree;
    private String originalRoot;
    private String rebuiltRoot;
    private Scenario scenario;

    @Before("@first-three and @merkle-tree")
    public void enableFirstThreeOutput(Scenario scenario) {
        this.scenario = scenario;
    }

    @Given("the single event {string}")
    public void singleEvent(String event) {
        events = new ArrayList<>(List.of(event));
    }

    @Given("a Unicode login event")
    public void unicodeLoginEvent() {
        singleEvent("\u7528\u6237\u767b\u5f55");
    }

    @Given("the first {int} events named e1 onwards")
    public void numberedEvents(int size) {
        events = IntStream.rangeClosed(1, size)
                .mapToObj(i -> "e" + i)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    @Given("an empty event sequence")
    public void emptySequence() {
        events = new ArrayList<>();
    }

    @When("I build the Merkle tree")
    public void buildTree() {
        tree = MerkleTree.build(events);
        originalRoot = tree.getHashHex();
        report("Events: " + events);
        report("Tree size=" + tree.getSize() + ", range="
                + tree.getStartIndex() + ".." + tree.getEndIndex());
        report("Root: " + originalRoot);
        if (!tree.isLeaf()) {
            report("Root split: " + tree.getLeft().getSize() + " + " + tree.getRight().getSize());
        }
    }

    @Then("the root agrees with the two-event hash calculation")
    public void checkTwoEventRoot() throws NoSuchAlgorithmException {
        assertEquals(List.of("e1", "e2"), events);
        MessageDigest digest = MessageDigest.getInstance("SHA-256");

        // First leaf: SHA256(0x00 | UTF8("e1")). digest() resets the digest.
        digest.update((byte) 0x00);
        byte[] h1 = digest.digest("e1".getBytes(StandardCharsets.UTF_8));

        // Second leaf: SHA256(0x00 | UTF8("e2")).
        digest.update((byte) 0x00);
        byte[] h2 = digest.digest("e2".getBytes(StandardCharsets.UTF_8));

        // Parent: SHA256(0x01 | h1 | h2), using raw bytes.
        digest.update((byte) 0x01);
        digest.update(h1);
        byte[] expectedRoot = digest.digest(h2);

        report("h1 = " + HexFormat.of().formatHex(h1));
        report("h2 = " + HexFormat.of().formatHex(h2));
        report("Calculated root = " + HexFormat.of().formatHex(expectedRoot));
        assertArrayEquals(expectedRoot, tree.getHash());
        report("Two-event calculation matches the built root: true");
    }

    @Then("the root hash is {string}")
    public void rootHash(String expected) {
        assertEquals(expected, tree.getHashHex());
    }

    @Then("the root covers events {int} through {int}")
    public void rootRange(int start, int end) {
        assertRange(tree, start, end);
    }

    @Then("the root is a leaf without children")
    public void leafWithoutChildren() {
        assertTrue(tree.isLeaf());
        assertNull(tree.getLeft());
        assertNull(tree.getRight());
    }

    @Then("its left subtree covers events {int} through {int}")
    public void leftRange(int start, int end) {
        assertRange(tree.getLeft(), start, end);
    }

    @Then("its right subtree covers events {int} through {int}")
    public void rightRange(int start, int end) {
        assertRange(tree.getRight(), start, end);
    }

    @Then("the tree has {int} leaves in their original order")
    public void orderedLeaves(int size) {
        List<MerkleTree> leaves = new ArrayList<>();
        collectLeaves(tree, leaves);
        assertEquals(size, leaves.size());
        for (int i = 0; i < size; i++) {
            assertRange(leaves.get(i), i + 1, i + 1);
            assertEquals(new MerkleTree(events.get(i), i + 1).getHashHex(),
                    leaves.get(i).getHashHex());
        }
    }

    @When("I replace event {int} with {string} and rebuild")
    public void replaceAndRebuild(int index, String event) {
        events.set(index - 1, event);
        rebuiltRoot = MerkleTree.build(events).getHashHex();
    }

    @When("I swap events {int} and {int} and rebuild")
    public void swapAndRebuild(int first, int second) {
        Collections.swap(events, first - 1, second - 1);
        rebuiltRoot = MerkleTree.build(events).getHashHex();
    }

    @Then("the rebuilt root differs from the original root")
    public void changedRoot() {
        assertNotEquals(originalRoot, rebuiltRoot);
    }

    @When("I modify the source event list and the returned hash bytes")
    public void mutateExternalValues() {
        events.set(0, "tampered");
        Arrays.fill(tree.getHash(), (byte) 0);
    }

    @Then("the committed root remains unchanged")
    public void unchangedRoot() {
        assertEquals(originalRoot, tree.getHashHex());
    }

    @Then("building the Merkle tree throws an IllegalArgumentException")
    public void rejectedConstruction() {
        assertThrows(IllegalArgumentException.class, () -> MerkleTree.build(events));
    }

    private static void assertRange(MerkleTree node, int start, int end) {
        assertNotNull(node);
        assertEquals(start, node.getStartIndex());
        assertEquals(end, node.getEndIndex());
        assertEquals(end - start + 1, node.getSize());
    }

    private static void collectLeaves(MerkleTree node, List<MerkleTree> leaves) {
        if (node.isLeaf()) {
            leaves.add(node);
        } else {
            collectLeaves(node.getLeft(), leaves);
            collectLeaves(node.getRight(), leaves);
        }
    }

    private void report(String text) {
        if (scenario != null) {
            scenario.log(text);
        }
    }
}
