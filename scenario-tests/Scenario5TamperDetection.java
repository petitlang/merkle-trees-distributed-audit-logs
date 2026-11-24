import fr.isep.auditlog.Auditor;
import fr.isep.auditlog.LogServer;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public class Scenario5TamperDetection {
    private static final int INDEX = 4;
    private static final String TAMPERED_EVENT = "e4-tampered";
    private static final String ROOT_8 = "7dc0f08884fa7f18ba933c2d46d41f0de6246cdaff7d4808e675beea75e52cd8";

    public static void main(String[] args) throws Exception {
        System.out.println("============================================================");
        System.out.println("SCENARIO 5 - DETECT A CHANGED LEDGER EVENT");
        System.out.println("============================================================");

        Path source = Path.of("scenario-tests/ledgers/events8.txt");
        Path ledger = Files.createTempFile("scenario5-ledger-", ".txt");
        try {
            Files.copy(source, ledger, StandardCopyOption.REPLACE_EXISTING);
            LogServer original = new LogServer(ledger);
            if (original.getSize() != 8 || !original.getRootHex().equals(ROOT_8)) {
                throw new IllegalStateException("The input must match the eight-event checkpoint.");
            }
            List<String> events = new ArrayList<>(Files.readAllLines(ledger, StandardCharsets.UTF_8));
            String event = events.get(INDEX - 1);
            List<byte[]> path = original.genPath(INDEX);
            Auditor auditor = new Auditor(original.getSize(), original.getRoot());

            System.out.println("Source ledger: " + source + " (using a temporary copy)");
            System.out.println("Trusted checkpoint: " + original.getSize() + " events");
            System.out.println("Trusted root: " + original.getRootHex());
            System.out.println("Saved genPath(" + INDEX + "): " + path.size() + " sibling hashes");
            boolean before = auditor.isMember(event, INDEX, path);
            System.out.println("Before: " + event + " | Auditor.isMember = " + before);
            if (!before) {
                throw new IllegalStateException("The original event did not pass verification.");
            }

            System.out.println("\nEdit line " + INDEX + " of the temporary ledger: " + event + " -> " + TAMPERED_EVENT);
            events.set(INDEX - 1, TAMPERED_EVENT);
            Files.write(ledger, events, StandardCharsets.UTF_8);
            LogServer modified = new LogServer(ledger);
            String changedEvent = Files.readAllLines(ledger, StandardCharsets.UTF_8).get(INDEX - 1);
            System.out.println("Reopened ledger: " + modified.getSize() + " events");
            System.out.println("Modified root: " + modified.getRootHex());
            System.out.println("Verifying against the same trusted root, index, and path.");
            boolean after = auditor.isMember(changedEvent, INDEX, path);
            System.out.println("After:  " + changedEvent + " | Auditor.isMember = " + after);
            if (!changedEvent.equals(TAMPERED_EVENT) || modified.getSize() != original.getSize()
                    || modified.getRootHex().equals(original.getRootHex()) || after) {
                throw new IllegalStateException("The expected ledger edit or verification failure did not occur.");
            }
        } finally {
            Files.deleteIfExists(ledger);
        }
        System.out.println("\nSCENARIO 5 PASSED: tampering detected.");
    }
}
