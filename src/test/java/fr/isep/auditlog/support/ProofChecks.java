package fr.isep.auditlog.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Independent test oracle; deliberately does not call MerkleTree or LogServer. */
public final class ProofChecks {
    private ProofChecks() { }

    public static byte[] root(List<String> events) {
        if (events.isEmpty()) {
            return hash(new byte[0]);
        }
        if (events.size() == 1) {
            byte[] text = events.get(0).getBytes(StandardCharsets.UTF_8);
            byte[] leaf = new byte[text.length + 1];
            System.arraycopy(text, 0, leaf, 1, text.length);
            return hash(leaf);
        }
        int split = Integer.highestOneBit(events.size() - 1);
        return parent(root(events.subList(0, split)), root(events.subList(split, events.size())));
    }

    public static boolean isMember(String event, int index, int size,
                                   List<byte[]> path, byte[] trustedRoot) {
        if (index < 1 || index > size) {
            return false;
        }
        // Derive directions from the claimed index and size, not from the server.
        List<Boolean> siblingOnLeft = new ArrayList<>();
        int position = index;
        for (int width = size; width > 1;) {
            int leftSize = Integer.highestOneBit(width - 1);
            boolean onLeft = position > leftSize;
            siblingOnLeft.add(onLeft);
            if (onLeft) {
                position -= leftSize;
                width -= leftSize;
            } else {
                width = leftSize;
            }
        }
        Collections.reverse(siblingOnLeft);
        if (path.size() != siblingOnLeft.size()) {
            return false;
        }
        byte[] computed = root(List.of(event));
        for (int i = 0; i < path.size(); i++) {
            byte[] sibling = path.get(i);
            if (sibling.length != 32) {
                return false;
            }
            computed = siblingOnLeft.get(i) ? parent(sibling, computed) : parent(computed, sibling);
        }
        return Arrays.equals(trustedRoot, computed);
    }

    public static boolean isConsistent(int oldSize, byte[] oldRoot, int newSize,
                                       byte[] newRoot, List<byte[]> labProof) {
        if (oldSize < 1 || oldSize > newSize) {
            return false;
        }
        if (oldSize == newSize) {
            return labProof.isEmpty() && Arrays.equals(oldRoot, newRoot);
        }
        List<byte[]> proof = new ArrayList<>(labProof);
        // The lab puts the first sibling before the seed: 6 -> 8 is [h78,h56,h14].
        // Normalize that pair to seed-first order for this iterative verifier.
        if (Integer.bitCount(oldSize) != 1) {
            if (proof.size() < 2) {
                return false;
            }
            Collections.swap(proof, 0, 1);
        }
        int oldIndex = oldSize - 1;
        int newIndex = newSize - 1;
        while ((oldIndex & 1) == 1) {
            oldIndex >>= 1;
            newIndex >>= 1;
        }
        int offset = 0;
        byte[] oldComputed = oldRoot;
        byte[] newComputed = oldRoot;
        if (oldIndex != 0) {
            if (proof.isEmpty()) {
                return false;
            }
            oldComputed = newComputed = proof.get(offset++);
        }
        for (; offset < proof.size(); offset++) {
            byte[] sibling = proof.get(offset);
            if (newIndex == 0 || sibling.length != 32) {
                return false;
            }
            if ((oldIndex & 1) == 1 || oldIndex == newIndex) {
                oldComputed = parent(sibling, oldComputed);
                newComputed = parent(sibling, newComputed);
                while (oldIndex != 0 && (oldIndex & 1) == 0) {
                    oldIndex >>= 1;
                    newIndex >>= 1;
                }
            } else {
                newComputed = parent(newComputed, sibling);
            }
            oldIndex >>= 1;
            newIndex >>= 1;
        }
        return newIndex == 0 && Arrays.equals(oldRoot, oldComputed)
                && Arrays.equals(newRoot, newComputed);
    }

    private static byte[] parent(byte[] left, byte[] right) {
        byte[] bytes = new byte[1 + left.length + right.length];
        bytes[0] = 1;
        System.arraycopy(left, 0, bytes, 1, left.length);
        System.arraycopy(right, 0, bytes, 1 + left.length, right.length);
        return hash(bytes);
    }

    private static byte[] hash(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }
}
