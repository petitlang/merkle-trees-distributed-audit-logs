package fr.isep.auditlog;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/** Verifies proofs for a log checkpoint identified by its size and root hash. */
public final class Auditor {
    private static final int SHA256_BYTES = 32;

    private final int size;
    private final byte[] root;

    public Auditor(int size, byte[] root) {
        if (size < 0 || !isHash(root)) {
            throw new IllegalArgumentException("a checkpoint needs a nonnegative size and a SHA-256 root");
        }
        this.size = size;
        this.root = root.clone();
    }

    /** Checks a one-based index using sibling hashes ordered from leaf to root. */
    public boolean isMember(String event, int index, List<byte[]> path) {
        if (event == null || index < 1 || index > size || path == null) {
            return false;
        }
        Iterator<byte[]> hashes = path.iterator();
        byte[] computed = rebuildMemberRoot(MerkleTree.hashLeaf(event), index, size, hashes);
        return !hashes.hasNext() && Arrays.equals(root, computed);
    }

    /** Checks that this checkpoint extends the given old checkpoint. */
    public boolean isConsistent(int oldSize, byte[] oldRoot, List<byte[]> proof) {
        if (oldSize < 1 || oldSize > size || !isHash(oldRoot) || proof == null) {
            return false;
        }
        if (oldSize == size) {
            return proof.isEmpty() && Arrays.equals(root, oldRoot);
        }
        Iterator<byte[]> hashes = proof.iterator();
        Roots computed = rebuildConsistencyRoots(oldSize, size, oldRoot, hashes);
        return computed != null && !hashes.hasNext()
                && Arrays.equals(oldRoot, computed.oldRoot())
                && Arrays.equals(root, computed.currentRoot());
    }

    private static byte[] rebuildMemberRoot(byte[] leaf, int index, int size, Iterator<byte[]> hashes) {
        if (size == 1) {
            return leaf;
        }
        int leftSize = Integer.highestOneBit(size - 1);
        boolean inLeft = index <= leftSize;
        byte[] child = inLeft
                ? rebuildMemberRoot(leaf, index, leftSize, hashes)
                : rebuildMemberRoot(leaf, index - leftSize, size - leftSize, hashes);
        if (child == null) {
            return null;
        }
        byte[] sibling = readHash(hashes);
        if (sibling == null) {
            return null;
        }
        return inLeft ? MerkleTree.hashParent(child, sibling) : MerkleTree.hashParent(sibling, child);
    }

    private static Roots rebuildConsistencyRoots(int oldSize, int size, byte[] knownOldRoot,
                                                  Iterator<byte[]> hashes) {
        int leftSize = Integer.highestOneBit(size - 1);
        if (oldSize == leftSize) {
            // At the boundary, the lab puts the right sibling before the seed.
            byte[] right = readHash(hashes);
            byte[] left = knownOldRoot != null ? knownOldRoot : readHash(hashes);
            if (left == null || right == null) {
                return null;
            }
            return new Roots(left, MerkleTree.hashParent(left, right));
        }

        boolean inLeft = oldSize < leftSize;
        // The trusted old root covers the entire prefix, so it cannot seed a right subtree.
        Roots child = inLeft
                ? rebuildConsistencyRoots(oldSize, leftSize, knownOldRoot, hashes)
                : rebuildConsistencyRoots(oldSize - leftSize, size - leftSize, null, hashes);
        if (child == null) {
            return null;
        }
        byte[] sibling = readHash(hashes);
        if (sibling == null) {
            return null;
        }
        if (inLeft) {
            return new Roots(child.oldRoot(), MerkleTree.hashParent(child.currentRoot(), sibling));
        }
        return new Roots(MerkleTree.hashParent(sibling, child.oldRoot()),
                MerkleTree.hashParent(sibling, child.currentRoot()));
    }

    private record Roots(byte[] oldRoot, byte[] currentRoot) { }

    private static byte[] readHash(Iterator<byte[]> hashes) {
        byte[] hash = hashes.hasNext() ? hashes.next() : null;
        return isHash(hash) ? hash : null;
    }

    private static boolean isHash(byte[] hash) {
        return hash != null && hash.length == SHA256_BYTES;
    }
}
