import fr.isep.auditlog.Auditor;
import fr.isep.auditlog.LogServer;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HexFormat;
import java.util.List;

public class Scenario4AppendAndConsistency {
    private static final HexFormat HEX = HexFormat.of();
    private static final String ROOT_5 = "7fd3c099308e01bbab60705cd997b86c446f1b9506e812d0497ade31835435fc";
    private static final String ROOT_6 = "0ec8a9f5275054d630180595e5d356faf48ea547c96e4b9c9ebe89303ce326b8";
    private static final String ROOT_8 = "7dc0f08884fa7f18ba933c2d46d41f0de6246cdaff7d4808e675beea75e52cd8";

    public static void main(String[] args) throws Exception {
        System.out.println("============================================================");
        System.out.println("SCENARIO 4 - APPEND AND VERIFY CONSISTENCY");
        System.out.println("============================================================");

        Path source = Path.of("scenario-tests/ledgers/events5.txt");
        Path ledger = Files.createTempFile("scenario4-ledger-", ".txt");
        try {
            Files.copy(source, ledger, StandardCopyOption.REPLACE_EXISTING);
            LogServer server = new LogServer(ledger);
            checkCheckpoint(server, 5, ROOT_5);
            System.out.println("Source ledger: " + source + " (using a temporary copy)");
            System.out.println("Starting records: " + Files.readAllLines(ledger, StandardCharsets.UTF_8));

            System.out.println("\nSingle append: e6");
            int oldSize = server.getSize();
            byte[] oldRoot = server.getRoot();
            server.append("e6");
            checkCheckpoint(server, 6, ROOT_6);
            verifyExtension(server, oldSize, oldRoot, server.genProof(oldSize));

            System.out.println("\nBatch append: [e7, e8]");
            oldSize = server.getSize();
            oldRoot = server.getRoot();
            server.append(List.of("e7", "e8"));
            checkCheckpoint(server, 8, ROOT_8);
            List<byte[]> proof = server.genProof(oldSize);
            printAndCheckBatchProof(proof);
            verifyExtension(server, oldSize, oldRoot, proof);

            List<String> records = Files.readAllLines(ledger, StandardCharsets.UTF_8);
            System.out.println("\nPersisted records: " + records);
            if (!records.equals(List.of("e1", "e2", "e3", "e4", "e5", "e6", "e7", "e8"))) {
                throw new IllegalStateException("The ledger did not retain the expected records.");
            }
        } finally {
            Files.deleteIfExists(ledger);
        }
        System.out.println("\nSCENARIO 4 PASSED: both extensions verified.");
    }

    private static void checkCheckpoint(LogServer server, int size, String root) {
        if (server.getSize() != size || !server.getRootHex().equals(root)) {
            throw new IllegalStateException("The checkpoint does not match the reference for size " + size);
        }
    }

    private static void verifyExtension(LogServer server, int oldSize, byte[] oldRoot, List<byte[]> proof) {
        int size = server.getSize();
        System.out.println("Checkpoint: " + oldSize + " -> " + size);
        System.out.println("Old root: " + HEX.formatHex(oldRoot));
        System.out.println("New root: " + server.getRootHex());
        System.out.println("genProof(" + oldSize + "): " + proof.size() + " hashes");
        Auditor auditor = new Auditor(size, server.getRoot());
        boolean consistent = auditor.isConsistent(oldSize, oldRoot, proof);
        System.out.println("Auditor.isConsistent = " + consistent);
        if (!consistent) {
            throw new IllegalStateException("The auditor rejected the extension " + oldSize + " -> " + size);
        }
    }

    private static void printAndCheckBatchProof(List<byte[]> proof) {
        List<String> labels = List.of("h7..8", "h5..6", "h1..4");
        List<String> expected = List.of(
                "5eda4828534046536288bbb71ddfd3da6d3cf0ecddc8f4883206c00dd8ed3180",
                "522d9d790d281d06b68af79d5212826fe65eb8248cf004464d4e8b0f2df0d392",
                "7abfaa969284fb86f37f1e0aa9ade022007b377e88766f4d0d2d6419a8d20d40");
        List<String> hashes = proof.stream().map(HEX::formatHex).toList();
        if (!hashes.equals(expected)) {
            throw new IllegalStateException("The 6 -> 8 proof does not match the reference hashes and order.");
        }
        System.out.println("Proof order: " + labels);
        for (int i = 0; i < labels.size(); i++) {
            System.out.println("  " + labels.get(i) + " = " + hashes.get(i));
        }
    }
}
