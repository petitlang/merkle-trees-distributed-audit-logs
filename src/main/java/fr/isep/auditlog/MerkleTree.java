package fr.isep.auditlog;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * An immutable Merkle tree node covering an inclusive, one-based event range.
 * Leaves hash {@code 0x00 || UTF-8(event)}; internal nodes hash
 * {@code 0x01 || leftHash || rightHash}, using raw SHA-256 bytes.
 */
public final class MerkleTree {
    private final byte[] hash;
    private final MerkleTree left;
    private final MerkleTree right;
    private final int startIndex;
    private final int endIndex;

    /** Creates 'a leaf' for a single event at a one-based index. */
    public MerkleTree(String event, int index) {
        Objects.requireNonNull(event, "event must not be null");
        if (index < 1) {
            throw new IllegalArgumentException("event index must be at least 1");
        }

        this.hash = taggedHash((byte) 0x00, event.getBytes(StandardCharsets.UTF_8));
        this.left = null;
        this.right = null;
        this.startIndex = index;
        this.endIndex = index;
    }

    /** Creates 'a parent' whose children cover adjacent, ordered event ranges. */
    public MerkleTree(MerkleTree left, MerkleTree right) {
        Objects.requireNonNull(left, "left child must not be null");
        Objects.requireNonNull(right, "right child must not be null");
        if ((long) left.endIndex + 1 != right.startIndex) {
            throw new IllegalArgumentException("child ranges must be adjacent and ordered");
        }
        // The lab's six-event tree splits as 4 + 2, rather than 3 + 3.
        int size = right.endIndex - left.startIndex + 1;
        int expectedLeftSize = Integer.highestOneBit(size - 1);
        if (left.getSize() != expectedLeftSize) {
            throw new IllegalArgumentException(
                    "left child must contain the largest power of two below the parent size");
        }

        this.hash = taggedHash((byte) 0x01, left.hash, right.hash);
        this.left = left;
        this.right = right;
        this.startIndex = left.startIndex;
        this.endIndex = right.endIndex;
    }

    /**
     * Builds a tree from a nonempty sequence, assigning indices 1 through n.
     * Each subtree splits at the largest power of two strictly below its size.
     * This preserves event order without duplicating or padding leaves.
     * Construction takes O(n) time and O(n) space, including the stored nodes.
     *
     * @throws IllegalArgumentException if the sequence is empty
     * @throws NullPointerException if the sequence or any event is null
     */
    public static MerkleTree fromEvents(List<String> events) {
        // Snapshot the input and use constant-time indexed access during recursion.
        List<String> snapshot = List.copyOf(events);
        if (snapshot.isEmpty()) {
            throw new IllegalArgumentException("a Merkle tree needs at least one event");
        }
        return build(snapshot, 0, snapshot.size());
    }

    private static MerkleTree build(List<String> events, int from, int size) {
        if (size == 1) {
            return new MerkleTree(events.get(from), from + 1);
        }
        int leftSize = Integer.highestOneBit(size - 1);
        return new MerkleTree(
                build(events, from, leftSize),
                build(events, from + leftSize, size - leftSize));
    }

    private static byte[] taggedHash(byte tag, byte[]... parts) {
        int length = 1; // Include the leaf/internal-node tag.
        for (byte[] part : parts) {
            length = Math.addExact(length, part.length);
        }
        byte[] nodeBytes = new byte[length];
        nodeBytes[0] = tag;
        int offset = 1;
        for (byte[] part : parts) {
            System.arraycopy(
                    part,        // Source array
                    0,           // Starting index in the source array
                    nodeBytes,   // Destination array
                    offset,      // Starting index in the destination array
                    part.length  // Number of elements to copy
            );
            offset += part.length;
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(nodeBytes);
            return hash;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by Java", e);
        }
    }

    /** Returns a defensive copy of the raw 32-byte hash. */
    public byte[] getHash() {
        return hash.clone();
    }

    public String getHashHex() {
        return HexFormat.of().formatHex(hash);
    }

    /** Returns the left child, or null for a leaf. */
    public MerkleTree getLeft() {
        return left;
    }

    /** Returns the right child, or null for a leaf. */
    public MerkleTree getRight() {
        return right;
    }

    public int getStartIndex() {
        return startIndex;
    }

    public int getEndIndex() {
        return endIndex;
    }

    public int getSize() {
        return endIndex - startIndex + 1;
    }

    public boolean isLeaf() {
        return left == null;
    }
}
