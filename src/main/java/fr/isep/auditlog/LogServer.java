package fr.isep.auditlog;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * A local, file-backed append-only log using one-based event indices.
 * Reuses the immutable MerkleTree nodes; network transport belongs to the bonus.
 * This instance owns the ledger while running: external changes require reopening.
 */
public final class LogServer {
    private final Path ledger;
    private List<String> events;
    private MerkleTree tree;
    private boolean lineTerminated;

    /**
     * Loads an existing UTF-8 ledger, one event per line, without trimming or
     * discarding empty lines. Line terminators are not part of an event.
     * An empty file is a valid empty log, represented without a MerkleTree node.
     *
     * @throws IOException if the file cannot be read or is not valid UTF-8
     */
    public LogServer(Path ledger) throws IOException {
        this.ledger = Objects.requireNonNull(ledger, "ledger must not be null");

        String content = Files.readString(ledger, StandardCharsets.UTF_8);
        this.events = content.lines().toList();

        // An empty ledger has no tree node.
        if (events.isEmpty()) {
            this.tree = null;
        } else {
            this.tree = MerkleTree.build(events);
        }

        // An append may need to insert a separator before the first new event.
        this.lineTerminated = content.isEmpty() || content.endsWith("\n") || content.endsWith("\r");
    }

    public int getSize() {
        return events.size();
    }

    /** Returns a fresh raw hash; an empty log uses SHA-256 of the empty byte string. */
    public byte[] getRoot() {
        if (tree != null) {
            return tree.getHash();
        }
        try {
            return MessageDigest.getInstance("SHA-256").digest(new byte[0]);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by Java", e);
        }
    }

    public String getRootHex() {
        return HexFormat.of().formatHex(getRoot());
    }

    public void append(String event) throws IOException {
        append(List.of(event));
    }

    /**
     * Appends a validated batch to the file, then publishes the rebuilt tree.
     * Empty strings are events; an empty batch is a no-op. Nulls and embedded
     * CR/LF characters are rejected before anything is written.
     * Rebuilding n total events takes O(n) time and space, plus event byte lengths.
     * A write error is propagated without publishing the candidate in-memory tree.
     */
    public void append(List<String> newEvents) throws IOException {
        List<String> batch = validatedBatch(newEvents);
        if (batch.isEmpty()) {
            return;
        }
        List<String> updated = new ArrayList<>(events);
        updated.addAll(batch);
        MerkleTree updatedTree = MerkleTree.build(updated);
        appendToLedger(batch);
        events = List.copyOf(updated);
        tree = updatedTree;
        lineTerminated = true;
    }

    private static List<String> validatedBatch(List<String> newEvents) {
        List<String> batch = List.copyOf(newEvents);
        for (String event : batch) {
            if (event.indexOf('\n') >= 0 || event.indexOf('\r') >= 0) {
                throw new IllegalArgumentException("each event must fit on one ledger line");
            }
        }
        return batch;
    }

    private void appendToLedger(List<String> batch) throws IOException {
        StringBuilder text = new StringBuilder();
        if (!lineTerminated) {
            // A file ending in "e1" must become "e1\ne2\n", not "e1e2\n".
            text.append('\n');
        }
        for (String event : batch) {
            text.append(event).append('\n');
        }
        Files.writeString(ledger, text, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
    }

    /**
     * Returns sibling hashes from the target leaf towards the root, excluding
     * the leaf and root themselves. For eight events genPath(4) is [h3,h12,h58].
     * An auditor derives left/right order using the index and committed log size.
     * Time, output space, and recursion stack are O(log n); no subtrees are rebuilt.
     */
    public List<byte[]> genPath(int index) {
        if (index < 1 || index > getSize()) {
            throw new IllegalArgumentException("event index must be between 1 and the log size");
        }
        List<byte[]> path = new ArrayList<>();
        collectPath(tree, index, path);
        return List.copyOf(path);
    }

    private static void collectPath(MerkleTree node, int index, List<byte[]> path) {
        if (node.isLeaf()) {
            return;
        }
        if (index <= node.getLeft().getEndIndex()) {
            collectPath(node.getLeft(), index, path);
            path.add(node.getRight().getHash());
        } else {
            collectPath(node.getRight(), index, path);
            path.add(node.getLeft().getHash());
        }
    }

    /**
     * Returns a prefix-consistency proof in the assignment's order. For 6 -> 8,
     * this is [h78,h56,h14]. If a seed hash is needed, its first sibling comes
     * before the seed; the remaining siblings are bottom-up. An already trusted
     * old root is omitted when the old size is a power of two. Equal sizes return
     * an empty proof, requiring the auditor to compare the two trusted roots.
     * Time, output space, and recursion stack are O(log n).
     *
     * @param oldSize committed previous size, between 1 and the current size
     */
    public List<byte[]> genProof(int oldSize) {
        if (oldSize < 1 || oldSize > getSize()) {
            throw new IllegalArgumentException("previous size must be between 1 and the log size");
        }
        List<byte[]> proof = new ArrayList<>();
        collectProof(tree, oldSize, true, proof);
        return List.copyOf(proof);
    }

    private static void collectProof(MerkleTree node, int oldSize, boolean oldRootKnown,
                                     List<byte[]> proof) {
        if (oldSize == node.getSize()) {
            // Only the unchanged-log case reaches a complete node here.
            return;
        }
        MerkleTree left = node.getLeft();
        MerkleTree right = node.getRight();
        if (oldSize == left.getSize()) {
            // Match the lab's sibling-before-seed order at the prefix boundary.
            proof.add(right.getHash());
            if (!oldRootKnown) {
                proof.add(left.getHash());
            }
        } else if (oldSize < left.getSize()) {
            collectProof(left, oldSize, oldRootKnown, proof);
            proof.add(right.getHash());
        } else {
            // The old prefix spans the left subtree and part of the right.
            // The right prefix root is not already known by the auditor.
            collectProof(right, oldSize - left.getSize(), false, proof);
            proof.add(left.getHash());
        }
    }
}
