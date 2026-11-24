package fr.isep.auditlog.bdd;

import fr.isep.auditlog.MerkleTree;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
}
