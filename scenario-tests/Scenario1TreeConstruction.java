import fr.isep.auditlog.LogServer;
import fr.isep.auditlog.MerkleTree;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;

/** 场景 1：八条记录建树，再用两条记录按公式核对。 */
public class Scenario1TreeConstruction {
    public static void main(String[] args) throws Exception {
        System.out.println("============================================================");
        System.out.println("SCENARIO 1 - TREE CONSTRUCTION AND A SMALL-LOG CHECK");
        System.out.println("============================================================");

        // 第一步：读取实际的日志文件，一行对应一条记录。
        Path ledger = Path.of("scenario-tests/ledgers/events8.txt");
        List<String> events = Files.readAllLines(ledger, StandardCharsets.UTF_8);
        System.out.println("\n[1] Read the ledger: " + ledger);
        for (int i = 0; i < events.size(); i++) {
            System.out.println("  Event " + (i + 1) + ": \"" + events.get(i) + "\"");
        }
        if (events.size() != 8) {
            throw new IllegalStateException("This scenario requires exactly 8 events.");
        }

        // 第二步：调用项目里已有的建树方法，打印每个节点的范围和完整哈希。
        MerkleTree tree = MerkleTree.build(events);
        System.out.println("\n[2] Build the Merkle tree using MerkleTree.build(events)");
        System.out.println("  Leaf:   SHA256(0x00 | UTF8(event))");
        System.out.println("  Parent: SHA256(0x01 | leftHash | rightHash)");
        System.out.println("  Hashes below are displayed in hex; hashing uses raw bytes.");
        printTree(tree, "  ");

        // 第三步：与文件服务器和独立算好的固定根哈希比较。
        LogServer server = new LogServer(ledger);
        String expectedRoot = "7dc0f08884fa7f18ba933c2d46d41f0de6246cdaff7d4808e675beea75e52cd8";
        System.out.println("\n[3] Compare the eight-event roots");
        System.out.println("  MerkleTree root: " + tree.getHashHex());
        System.out.println("  LogServer root:  " + server.getRootHex());
        System.out.println("  Reference root:  " + expectedRoot);
        boolean correctRoot = tree.getHashHex().equals(expectedRoot)
                && server.getRootHex().equals(expectedRoot) && server.getSize() == 8;
        System.out.println("  All values agree: " + correctRoot);
        if (!correctRoot) {
            throw new IllegalStateException("The eight-event root check failed.");
        }

        // 第四步：不调用 MerkleTree 的哈希方法，直接展开两条记录的公式。
        System.out.println("\n[4] Check a small log by expanding the two-event formula");
        System.out.println("  Records: e1, e2");
        System.out.println("  UTF8(e1) = 6531; leaf input bytes = 00 65 31");
        System.out.println("  UTF8(e2) = 6532; leaf input bytes = 00 65 32");
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update((byte) 0x00);
        byte[] h1 = digest.digest("e1".getBytes(StandardCharsets.UTF_8));
        // digest() 结束一次计算后会重置，因此这里可以继续计算第二片叶子。
        digest.update((byte) 0x00);
        byte[] h2 = digest.digest("e2".getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0x01);
        digest.update(h1);
        byte[] calculatedRoot = digest.digest(h2);

        MerkleTree smallTree = MerkleTree.build(List.of("e1", "e2"));
        String smallReference = "1ae210a506da2f0264c59f2041fe70fe9bfceda1cbb394471e9587e44b430216";
        System.out.println("  h1 = SHA256(0x00 | UTF8(e1)) = " + HexFormat.of().formatHex(h1));
        System.out.println("  h2 = SHA256(0x00 | UTF8(e2)) = " + HexFormat.of().formatHex(h2));
        System.out.println("  Parent input: 0x01 + h1 (32 bytes) + h2 (32 bytes) = 65 bytes");
        System.out.println("  Calculated SHA256(0x01 | h1 | h2): " + HexFormat.of().formatHex(calculatedRoot));
        System.out.println("  MerkleTree two-event root:        " + smallTree.getHashHex());
        System.out.println("  Reference two-event root:         " + smallReference);
        boolean smallRootCorrect = Arrays.equals(calculatedRoot, smallTree.getHash())
                && smallTree.getHashHex().equals(smallReference);
        System.out.println("  Small-log calculation agrees: " + smallRootCorrect);
        if (!smallRootCorrect) {
            throw new IllegalStateException("The two-event calculation failed.");
        }
        System.out.println("\nSCENARIO 1 PASSED: eight-event tree and two-event calculation.");
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
}
