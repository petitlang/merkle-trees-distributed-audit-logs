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

/** 场景 2：分别验证八条记录中的第一条、中间一条和最后一条。 */
public class Scenario2ValidMembership {
    public static void main(String[] args) throws Exception {
        System.out.println("============================================================");
        System.out.println("SCENARIO 2 - VALID MEMBERSHIP: FIRST, MIDDLE AND LAST");
        System.out.println("============================================================");

        Path ledger = Path.of("scenario-tests/ledgers/events8.txt");
        List<String> events = Files.readAllLines(ledger, StandardCharsets.UTF_8);
        LogServer server = new LogServer(ledger);
        MerkleTree tree = MerkleTree.build(events);
        String referenceRoot = "7dc0f08884fa7f18ba933c2d46d41f0de6246cdaff7d4808e675beea75e52cd8";
        byte[] trustedRoot = HexFormat.of().parseHex(referenceRoot);
        if (events.size() != 8 || server.getSize() != 8 || !server.getRootHex().equals(referenceRoot)) {
            throw new IllegalStateException("The input must match the eight-event checkpoint.");
        }

        System.out.println("\nLedger: " + ledger);
        System.out.println("Records: " + events);
        System.out.println("Trusted checkpoint size: 8");
        System.out.println("Trusted checkpoint root: " + referenceRoot);
        System.out.println("The reference checkpoint was calculated independently.");
        System.out.println("Auditor receives only the checkpoint, event, index and proof.");
        Auditor auditor = new Auditor(8, trustedRoot);

        int[] indices = {1, 4, 8};
        String[] positions = {"FIRST", "MIDDLE", "LAST"};
        for (int i = 0; i < indices.length; i++) {
            System.out.println("\n---------------- " + positions[i] + " EVENT ----------------");
            verifyEvent(server, auditor, tree, events, indices[i], trustedRoot);
        }
        System.out.println("\nSCENARIO 2 PASSED: all 3 membership checks returned true.");
    }

    private static void verifyEvent(LogServer server, Auditor auditor, MerkleTree tree,
                                    List<String> events, int index, byte[] trustedRoot) throws Exception {
        String event = events.get(index - 1);
        List<byte[]> path = server.genPath(index);
        System.out.println("Event index (one-based): " + index + "; content: \"" + event + "\"");
        System.out.println("LogServer.genPath(" + index + ") returned " + path.size() + " sibling hashes.");

        // 只为讲解标注兄弟节点的范围；正式 Auditor 不需要整棵树。
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
        Collections.reverse(siblings); // 生成从叶子到根的讲解顺序。

        byte[] current = new MerkleTree(event, index).getHash();
        System.out.println("Start: SHA256(0x00 | UTF8(event)) = " + HexFormat.of().formatHex(current));
        for (int step = 0; step < path.size(); step++) {
            MerkleTree sibling = siblings.get(step);
            boolean siblingOnLeft = sibling.getEndIndex() < index;
            System.out.println("  Step " + (step + 1) + ": sibling [" + sibling.getStartIndex()
                    + ".." + sibling.getEndIndex() + "] is on the " + (siblingOnLeft ? "LEFT" : "RIGHT"));
            System.out.println("    Proof hash: " + HexFormat.of().formatHex(path.get(step)));

            // 这段逐层计算用于打印视频中的过程，最终结论仍调用正式 Auditor。
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
            throw new IllegalStateException("Membership failed for event " + index);
        }
        System.out.println("CHECK PASSED for event " + index);
    }
}
