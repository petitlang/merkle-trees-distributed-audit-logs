package fr.isep.auditlog;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.List;

/** Small console demonstration; verification belongs to the separate Auditor. */
public final class LogServerDemo {
    private LogServerDemo() { }

    public static void main(String[] args) throws IOException {
        Path ledger = Files.createTempFile("merkle-log-demo-", ".txt");
        try {
            Files.write(ledger, List.of("e1", "e2", "e3", "e4", "e5"),
                    StandardCharsets.UTF_8);
            LogServer server = new LogServer(ledger);
            System.out.println("Loaded 5 events from " + ledger);
            server.append("e6");
            System.out.println("After single append: size=" + server.getSize());
            byte[] oldRoot = server.getRoot();
            System.out.println("Saved old root: " + HexFormat.of().formatHex(oldRoot));

            server.append(List.of("e7", "e8"));
            System.out.println("After batch append: size=" + server.getSize());
            System.out.println("New root: " + server.getRootHex());
            printHashes("genPath(4): [h3, h1..2, h5..8]", server.genPath(4));
            printHashes("genProof(6): [h7..8, h5..6, h1..4]", server.genProof(6));

            LogServer reloaded = new LogServer(ledger);
            System.out.println("Reopened ledger: size=" + reloaded.getSize()
                    + ", same root=" + server.getRootHex().equals(reloaded.getRootHex()));
        } finally {
            Files.deleteIfExists(ledger);
        }
    }

    private static void printHashes(String label, List<byte[]> hashes) {
        System.out.println(label);
        for (int i = 0; i < hashes.size(); i++) {
            System.out.println("  [" + i + "] " + HexFormat.of().formatHex(hashes.get(i)));
        }
    }
}
