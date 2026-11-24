package fr.isep.auditlog;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class MerkleTreeTest {
    // Fixed vectors calculated independently with Python hashlib, including tag bytes.
    @Test
    void hashesLeafWithDomainTagAndUtf8() {
        MerkleTree leaf = new MerkleTree("e1", 1);
        assertEquals("d38b823b070424fc9303ff6f20f4ddeef6dc32aa02c75d9c8c3ae46062c78f6b",
                leaf.getHashHex());
        assertEquals("056e4ccd70746090c5087c1f0279edbfa2a34009fe32cce9dce5bc23d77fce6b",
                new MerkleTree("\u7528\u6237\u767b\u5f55", 2).getHashHex());
        assertTrue(leaf.isLeaf());
        assertNull(leaf.getLeft());
        assertNull(leaf.getRight());
        assertEquals(1, leaf.getStartIndex());
        assertEquals(1, leaf.getEndIndex());
        assertEquals(32, leaf.getHash().length);
    }

    @ParameterizedTest
    @CsvSource({
            "2, 1ae210a506da2f0264c59f2041fe70fe9bfceda1cbb394471e9587e44b430216",
            "3, 7a0bacf7f540e3637cfb12301b64e796a47c1260efae340fd7078c9394992310",
            "4, 7abfaa969284fb86f37f1e0aa9ade022007b377e88766f4d0d2d6419a8d20d40",
            "6, 0ec8a9f5275054d630180595e5d356faf48ea547c96e4b9c9ebe89303ce326b8",
            "8, 7dc0f08884fa7f18ba933c2d46d41f0de6246cdaff7d4808e675beea75e52cd8",
            "9, b2e56a6fd66905b56d42bb03f83cc19113a101039540d6412c1bc6bed25fbde8",
            "16, e92a772e6ffa1631a9c78eb5517036e8d43150ffadd7fe81353481d9e683a197",
            "17, be5ce902e371b34e612738da57640f74da744c7f8607c22280cb7e60b101ddec",
            "1025, 3afa8fb5d6704ba5f355da49c2403da27c8e48a7d47d6f2532fe2c2006abe389"
    })
    void matchesIndependentRootVectors(int size, String expectedHash) {
        assertEquals(expectedHash, MerkleTree.fromEvents(events(size)).getHashHex());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 8, 9, 16, 17, 31, 32, 33, 1025})
    void preservesEveryLeafOnceAndAssignsContiguousRanges(int size) {
        MerkleTree tree = MerkleTree.fromEvents(events(size));
        assertEquals(1, tree.getStartIndex());
        assertEquals(size, tree.getEndIndex());
        assertEquals(2 * size - 1, checkStructure(tree));
    }

    private int checkStructure(MerkleTree node) {
        assertEquals(node.getSize() == 1, node.isLeaf());
        if (node.isLeaf()) {
            assertEquals(1, node.getSize());
            assertEquals(new MerkleTree("e" + node.getStartIndex(), node.getStartIndex()).getHashHex(),
                    node.getHashHex());
            return 1;
        }
        MerkleTree left = node.getLeft();
        MerkleTree right = node.getRight();
        assertNotNull(left);
        assertNotNull(right);
        assertEquals(node.getStartIndex(), left.getStartIndex());
        assertEquals(node.getEndIndex(), right.getEndIndex());
        assertEquals(left.getEndIndex() + 1, right.getStartIndex());
        assertEquals(Integer.highestOneBit(node.getSize() - 1), left.getSize());
        return 1 + checkStructure(left) + checkStructure(right);
    }

    @Test
    void allowsManualConstruction() {
        MerkleTree left = new MerkleTree("e1", 1);
        MerkleTree right = new MerkleTree("e2", 2);
        MerkleTree parent = new MerkleTree(left, right);
        assertSame(left, parent.getLeft());
        assertSame(right, parent.getRight());
        assertEquals(MerkleTree.fromEvents(events(2)).getHashHex(), parent.getHashHex());
    }

    @Test
    void hashAndSourceListCannotMutateAnExistingTree() {
        List<String> source = new ArrayList<>(events(4));
        MerkleTree tree = MerkleTree.fromEvents(source);
        String originalHash = tree.getHashHex();
        Arrays.fill(tree.getHash(), (byte) 0);
        source.set(0, "tampered");
        assertEquals(originalHash, tree.getHashHex());
        assertNotEquals(originalHash, MerkleTree.fromEvents(source).getHashHex());
    }

    @Test
    void detectsChangedEventsAndOrder() {
        String original = MerkleTree.fromEvents(events(4)).getHashHex();
        assertNotEquals(original,
                MerkleTree.fromEvents(List.of("e1", "modified", "e3", "e4")).getHashHex());
        assertNotEquals(original,
                MerkleTree.fromEvents(List.of("e2", "e1", "e3", "e4")).getHashHex());
    }

    @Test
    void rejectsInvalidInputAndMalformedChildRanges() {
        assertThrows(IllegalArgumentException.class, () -> MerkleTree.fromEvents(List.of()));
        assertThrows(NullPointerException.class, () -> MerkleTree.fromEvents(null));
        assertThrows(NullPointerException.class,
                () -> MerkleTree.fromEvents(Arrays.asList("e1", null)));
        assertThrows(NullPointerException.class, () -> new MerkleTree((String) null, 1));
        assertThrows(IllegalArgumentException.class, () -> new MerkleTree("e1", 0));
        MerkleTree first = new MerkleTree("e1", 1);
        MerkleTree second = new MerkleTree("e2", 2);
        MerkleTree third = new MerkleTree("e3", 3);
        assertThrows(NullPointerException.class, () -> new MerkleTree(first, null));
        assertThrows(NullPointerException.class, () -> new MerkleTree(null, first));
        assertThrows(IllegalArgumentException.class, () -> new MerkleTree(second, first));
        assertThrows(IllegalArgumentException.class, () -> new MerkleTree(first, first));
        assertThrows(IllegalArgumentException.class, () -> new MerkleTree(first, third));
        assertThrows(IllegalArgumentException.class,
                () -> new MerkleTree(first, new MerkleTree(second, third)));
    }

    @Test
    void acceptsEmptyAndDuplicateEventStringsAsDistinctEntries() {
        MerkleTree tree = MerkleTree.fromEvents(List.of("", ""));
        assertEquals(2, tree.getSize());
        assertArrayEquals(tree.getLeft().getHash(), tree.getRight().getHash());
        assertNotEquals(tree.getLeft().getHashHex(), tree.getHashHex());
    }

    @Test
    void preservesWhitespaceAndLineBreaksInAnEvent() {
        MerkleTree leaf = new MerkleTree(" e1\n", 1);
        assertEquals("4d8c0673ccc0ecc038cb132b7aee9b52fa9c84c245676b528ed94cd70320cf17",
                leaf.getHashHex());
        assertNotEquals(new MerkleTree("e1", 1).getHashHex(), leaf.getHashHex());
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, Integer.MIN_VALUE})
    void rejectsNegativeEventIndices(int index) {
        assertThrows(IllegalArgumentException.class, () -> new MerkleTree("e1", index));
    }

    @Test
    void acceptsAdjacentRangesAtTheMaximumIndexWithoutOverflow() {
        MerkleTree left = new MerkleTree("e1", Integer.MAX_VALUE - 1);
        MerkleTree right = new MerkleTree("e2", Integer.MAX_VALUE);
        MerkleTree parent = new MerkleTree(left, right);
        assertEquals(Integer.MAX_VALUE - 1, parent.getStartIndex());
        assertEquals(Integer.MAX_VALUE, parent.getEndIndex());
        assertEquals(2, parent.getSize());
        assertEquals(1, right.getSize());
        assertEquals(MerkleTree.fromEvents(events(2)).getHashHex(), parent.getHashHex());
        assertThrows(IllegalArgumentException.class,
                () -> new MerkleTree(right, new MerkleTree("e3", 1)));
    }

    @Test
    void allowsManualConstructionOfANonPowerOfTwoTree() {
        MerkleTree firstFour = MerkleTree.fromEvents(events(4));
        MerkleTree lastTwo = new MerkleTree(
                new MerkleTree("e5", 5), new MerkleTree("e6", 6));
        MerkleTree parent = new MerkleTree(firstFour, lastTwo);
        assertEquals("0ec8a9f5275054d630180595e5d356faf48ea547c96e4b9c9ebe89303ce326b8",
                parent.getHashHex());
        assertEquals(1, parent.getStartIndex());
        assertEquals(6, parent.getEndIndex());
    }

    private static List<String> events(int size) {
        return IntStream.rangeClosed(1, size).mapToObj(i -> "e" + i).toList();
    }
}
