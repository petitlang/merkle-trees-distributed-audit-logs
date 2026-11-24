import fr.isep.auditlog.Auditor;
import fr.isep.auditlog.LogServer;
import fr.isep.auditlog.MerkleTree;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;

/** 场景 3：分别用 5、6、7 条记录建树，并检查各自的首、中、末记录。 */
public class Scenario3NonPowerOfTwo {
    public static void main(String[] args) throws Exception {
        System.out.println("============================================================");
        System.out.println("SCENARIO 3 - NON-POWER-OF-TWO LOGS: 5, 6 AND 7 EVENTS");
        System.out.println("============================================================");

        String[] referenceRoots = {
                "7fd3c099308e01bbab60705cd997b86c446f1b9506e812d0497ade31835435fc",
                "0ec8a9f5275054d630180595e5d356faf48ea547c96e4b9c9ebe89303ce326b8",
                "d4c3f3d9e11b8582989a2f71b1bde67120ae3183c355f6b5895004ddc5c55ad2"
        };
        int passedChecks = 0;
        for (int size = 5; size <= 7; size++) {
            System.out.println("\n================ " + size + "-EVENT LOG ================");
            Path ledger = Path.of("scenario-tests/ledgers/events" + size + ".txt");
            List<String> events = Files.readAllLines(ledger, StandardCharsets.UTF_8);
            MerkleTree tree = MerkleTree.build(events);
            LogServer server = new LogServer(ledger);
            String referenceRoot = referenceRoots[size - 5];
            byte[] trustedRoot = HexFormat.of().parseHex(referenceRoot);

            System.out.println("Ledger: " + ledger);
            System.out.println("Records: " + events);
            System.out.println("Record count: " + events.size());
            System.out.println("Tree nodes (ranges and full hashes):");
            printTree(tree, "  ");
            int leaves = countLeaves(tree);
            int leftSize = tree.getLeft().getSize();
            int rightSize = tree.getRight().getSize();
            System.out.println("Root split: " + leftSize + " + " + rightSize);
            System.out.println("Actual leaf count: " + leaves + "; expected: " + size);
            System.out.println("No padding: each input event has exactly one leaf.");
            System.out.println("MerkleTree root: " + tree.getHashHex());
            System.out.println("LogServer root:  " + server.getRootHex());
            System.out.println("Reference root:  " + referenceRoot);

            boolean correctTree = events.size() == size && server.getSize() == size && leaves == size
                    && leftSize == 4 && rightSize == size - 4
                    && tree.getStartIndex() == 1 && tree.getEndIndex() == size
                    && tree.getHashHex().equals(referenceRoot) && server.getRootHex().equals(referenceRoot);
            System.out.println("Tree and root checks passed: " + correctTree);
            if (!correctTree) {
                throw new IllegalStateException("Tree check failed for size " + size);
            }

            Auditor auditor = new Auditor(size, trustedRoot);
            int[] indices = {1, (size + 1) / 2, size};
            for (int index : indices) {
                System.out.println("\n--- Membership in the " + size + "-event log ---");
                verifyEvent(server, auditor, tree, events, index, trustedRoot);
                passedChecks++;
            }
            System.out.println("\n" + size + "-EVENT LOG PASSED: tree check and 3 memberships.");
        }
        System.out.println("\nSCENARIO 3 PASSED: 3 tree checks and " + passedChecks + " memberships.");
    }

    private static void verifyEvent(LogServer server, Auditor auditor, MerkleTree tree,
                                    List<String> events, int index, byte[] trustedRoot) throws Exception {
        String event = events.get(index - 1);
        List<byte[]> path = server.genPath(index);
        System.out.println("Event index (one-based): " + index + "; content: \"" + event + "\"");
        System.out.println("LogServer.genPath(" + index + ") returned " + path.size() + " sibling hashes.");

        // 从树上取范围只用于讲解；实际证明来自 server.genPath(index)。
        List<MerkleTree> siblings = new ArrayList<>();
        MerkleTree node = tree;
        while (!node.isLeaf()) {
            if (index <= node.getLeft().getEndIndex()) {
                siblings.add(node.getRight());
                node = node.getLeft();
            } else {
                siblings.add(node.getLeft());
                node = node.getRight();
            }
        }
        Collections.reverse(siblings);

        byte[] current = new MerkleTree(event, index).getHash();
        System.out.println("Start: SHA256(0x00 | UTF8(event)) = " + HexFormat.of().formatHex(current));
        for (int step = 0; step < path.size(); step++) {
            MerkleTree sibling = siblings.get(step);
            boolean siblingOnLeft = sibling.getEndIndex() < index;
            System.out.println("  Step " + (step + 1) + ": sibling [" + sibling.getStartIndex()
                    + ".." + sibling.getEndIndex() + "] is on the " + (siblingOnLeft ? "LEFT" : "RIGHT"));
            System.out.println("    Proof hash: " + HexFormat.of().formatHex(path.get(step)));
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update((byte) 0x01);
            digest.update(siblingOnLeft ? path.get(step) : current);
            current = digest.digest(siblingOnLeft ? current : path.get(step));
            System.out.println("    Formula: SHA256(0x01 | "
                    + (siblingOnLeft ? "sibling | current" : "current | sibling") + ")");
            System.out.println("    Computed parent: " + HexFormat.of().formatHex(current));
        }

        boolean sameRoot = Arrays.equals(current, trustedRoot);
        boolean member = auditor.isMember(event, index, path);
        System.out.println("Reconstructed root: " + HexFormat.of().formatHex(current));
        System.out.println("Trusted root:       " + HexFormat.of().formatHex(trustedRoot));
        System.out.println("Roots match: " + sameRoot);
        System.out.println("Auditor.isMember(\"" + event + "\", " + index + ", path) = " + member);
        if (!sameRoot || !member) {
            throw new IllegalStateException("Membership failed for size " + events.size() + ", index " + index);
        }
        System.out.println("CHECK PASSED for event " + index);
    }

    private static void printTree(MerkleTree node, String indent) {
        String type = node.isLeaf() ? "LEAF" : "NODE";
        System.out.println(indent + type + " [" + node.getStartIndex() + ".."
                + node.getEndIndex() + "] " + node.getHashHex());
        if (!node.isLeaf()) {
            printTree(node.getLeft(), indent + "  ");
            printTree(node.getRight(), indent + "  ");
        }
    }

    private static int countLeaves(MerkleTree node) {
        if (node.isLeaf()) {
            return 1;
        }
        return countLeaves(node.getLeft()) + countLeaves(node.getRight());
    }
}
