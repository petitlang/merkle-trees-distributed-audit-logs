package fr.isep.auditlog;

import fr.isep.auditlog.support.ProofChecks;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class LogServerTest {
    @TempDir
    Path directory;

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 8, 9, 15, 16, 17, 31, 32, 33, 64, 65})
    void everyEventHasAnIndependentMembershipProof(int size) throws IOException {
        List<String> events = events(size);
        LogServer server = open(events);
        byte[] trustedRoot = ProofChecks.root(events);
        assertArrayEquals(trustedRoot, server.getRoot());
        for (int index = 1; index <= size; index++) {
            List<byte[]> path = server.genPath(index);
            String event = events.get(index - 1);
            assertTrue(ProofChecks.isMember(event, index, size, path, trustedRoot),
                    "valid path: size=" + size + ", index=" + index);
            assertFalse(ProofChecks.isMember(event + "-changed", index, size, path, trustedRoot));
            assertTrue(path.size() <= 32 - Integer.numberOfLeadingZeros(size - 1));
            if (!path.isEmpty()) {
                path.get(0)[0] ^= 1;
                assertFalse(ProofChecks.isMember(event, index, size, path, trustedRoot));
            }
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 8, 9, 15, 16, 17, 31, 32, 33, 64, 65})
    void everyPreviousSizeHasAnIndependentConsistencyProof(int size) throws IOException {
        List<String> events = events(size);
        LogServer server = open(events);
        byte[] newRoot = ProofChecks.root(events);
        for (int oldSize = 1; oldSize <= size; oldSize++) {
            byte[] oldRoot = ProofChecks.root(events.subList(0, oldSize));
            List<byte[]> proof = server.genProof(oldSize);
            assertTrue(ProofChecks.isConsistent(oldSize, oldRoot, size, newRoot, proof),
                    "valid extension: " + oldSize + " -> " + size);
            assertTrue(proof.size() <= 1 + 32 - Integer.numberOfLeadingZeros(size - 1));
            if (!proof.isEmpty()) {
                proof.get(0)[0] ^= 1;
                assertFalse(ProofChecks.isConsistent(oldSize, oldRoot, size, newRoot, proof));
            }
        }
    }

    @Test
    void followsBothProofExamplesFromTheAssignmentExactly() throws IOException {
        LogServer server = open(events(8));
        assertHashes(List.of(
                ProofChecks.root(List.of("e3")),
                ProofChecks.root(events(2)),
                ProofChecks.root(List.of("e5", "e6", "e7", "e8"))), server.genPath(4));
        assertHashes(List.of(
                ProofChecks.root(List.of("e7", "e8")),
                ProofChecks.root(List.of("e5", "e6")),
                ProofChecks.root(events(4))), server.genProof(6));
    }

    @Test
    void oldPowerOfTwoRootIsAlreadyKnownAndNeedNotBeSentAgain() throws IOException {
        LogServer server = open(events(8));
        assertHashes(List.of(ProofChecks.root(List.of("e5", "e6", "e7", "e8"))),
                server.genProof(4));
        assertTrue(server.genProof(8).isEmpty());
    }

    @Test
    void persistsSingleAndBatchAppendsAndPreservesTheOldPrefix() throws IOException {
        LogServer server = open(events(5));
        byte[] oldRoot = server.getRoot();
        server.append("e6");
        server.append(List.of("e7", "e8"));
        assertEquals(events(8), Files.readAllLines(ledger(), StandardCharsets.UTF_8));
        assertArrayEquals(ProofChecks.root(events(8)), server.getRoot());
        assertArrayEquals(server.getRoot(), new LogServer(ledger()).getRoot());
        assertTrue(ProofChecks.isConsistent(5, oldRoot, 8, server.getRoot(), server.genProof(5)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"e1", "e1\n", "e1\r\n", "e1\r"})
    void appendHandlesDifferentExistingLineEndings(String content) throws IOException {
        Files.writeString(ledger(), content, StandardCharsets.UTF_8);
        LogServer server = new LogServer(ledger());
        server.append("e2");
        assertEquals(List.of("e1", "e2"), Files.readAllLines(ledger(), StandardCharsets.UTF_8));
        assertArrayEquals(ProofChecks.root(events(2)), new LogServer(ledger()).getRoot());
    }

    @Test
    void preservesWhitespaceUnicodeAndBlankLines() throws IOException {
        List<String> expected = List.of("  e1  ", "", "\u7528\u6237\u767b\u5f55", "\ud83d\ude42", "");
        Files.writeString(ledger(), String.join("\r\n", expected) + "\r\n", StandardCharsets.UTF_8);
        LogServer server = new LogServer(ledger());
        assertEquals(expected.size(), server.getSize());
        assertArrayEquals(ProofChecks.root(expected), server.getRoot());
        server.append(List.of("", "  "));
        List<String> appended = new ArrayList<>(expected);
        appended.addAll(List.of("", "  "));
        assertEquals(appended, Files.readAllLines(ledger(), StandardCharsets.UTF_8));
        assertArrayEquals(ProofChecks.root(appended), new LogServer(ledger()).getRoot());
    }

    @Test
    void anEmptyLedgerCanReceiveItsFirstAndAnEmptyEvent() throws IOException {
        LogServer server = open(List.of());
        assertEquals(0, server.getSize());
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                server.getRootHex());
        assertThrows(IllegalArgumentException.class, () -> server.genPath(1));
        assertThrows(IllegalArgumentException.class, () -> server.genProof(0));
        server.append("");
        server.append("e2");
        assertEquals(List.of("", "e2"), Files.readAllLines(ledger(), StandardCharsets.UTF_8));
        assertArrayEquals(ProofChecks.root(List.of("", "e2")), new LogServer(ledger()).getRoot());
    }

    @Test
    void anEmptyBatchDoesNotChangeTheFileOrRoot() throws IOException {
        Files.writeString(ledger(), "e1", StandardCharsets.UTF_8);
        LogServer server = new LogServer(ledger());
        byte[] root = server.getRoot();
        server.append(List.of());
        assertEquals("e1", Files.readString(ledger(), StandardCharsets.UTF_8));
        assertArrayEquals(root, server.getRoot());
    }

    @Test
    void validatesTheEntireBatchBeforeChangingTheFileOrTree() throws IOException {
        LogServer server = open(events(2));
        byte[] file = Files.readAllBytes(ledger());
        byte[] root = server.getRoot();
        assertThrows(NullPointerException.class, () -> server.append((String) null));
        assertThrows(NullPointerException.class, () -> server.append((List<String>) null));
        assertThrows(NullPointerException.class, () -> server.append(Arrays.asList("e3", null)));
        assertThrows(IllegalArgumentException.class, () -> server.append(List.of("e3", "bad\nevent")));
        assertThrows(IllegalArgumentException.class, () -> server.append("bad\revent"));
        assertArrayEquals(file, Files.readAllBytes(ledger()));
        assertArrayEquals(root, server.getRoot());
        assertEquals(2, server.getSize());
    }

    @Test
    void reportsFileErrorsAndDoesNotPublishAnUnwrittenAppend() throws IOException {
        assertThrows(NullPointerException.class, () -> new LogServer(null));
        assertThrows(IOException.class, () -> new LogServer(directory.resolve("missing.txt")));
        Files.write(ledger(), new byte[]{(byte) 0xff});
        assertThrows(IOException.class, () -> new LogServer(ledger()));
        LogServer server = open(events(2));
        byte[] root = server.getRoot();
        Files.delete(ledger());
        assertThrows(IOException.class, () -> server.append("e3"));
        assertArrayEquals(root, server.getRoot());
        assertEquals(2, server.getSize());
    }

    @ParameterizedTest
    @ValueSource(ints = {Integer.MIN_VALUE, -1, 0, 9, Integer.MAX_VALUE})
    void rejectsIndicesAndPreviousSizesOutsideTheCurrentLog(int invalid) throws IOException {
        LogServer server = open(events(8));
        assertThrows(IllegalArgumentException.class, () -> server.genPath(invalid));
        assertThrows(IllegalArgumentException.class, () -> server.genProof(invalid));
    }

    @Test
    void returnedArraysAndCallerOwnedBatchesCannotMutateServerState() throws IOException {
        LogServer server = open(events(6));
        List<String> batch = new ArrayList<>(List.of("e7", "e8"));
        server.append(batch);
        batch.set(0, "changed");
        byte[] root = server.getRoot();
        Arrays.fill(root, (byte) 0);
        List<byte[]> originalPath = server.genPath(4);
        List<byte[]> originalProof = server.genProof(6);
        List<byte[]> changedPath = server.genPath(4);
        List<byte[]> changedProof = server.genProof(6);
        changedPath.get(0)[0] ^= 1;
        changedProof.get(0)[0] ^= 1;
        assertThrows(UnsupportedOperationException.class, () -> changedPath.add(new byte[32]));
        assertThrows(UnsupportedOperationException.class, () -> changedProof.clear());
        assertArrayEquals(ProofChecks.root(events(8)), server.getRoot());
        assertHashes(originalPath, server.genPath(4));
        assertHashes(originalProof, server.genProof(6));
        assertEquals(HexFormat.of().formatHex(server.getRoot()), server.getRootHex());
    }

    @Test
    void proofChecksRejectChangedTrustedRootsAndMisstatedIndices() throws IOException {
        LogServer server = open(events(8));
        byte[] oldRoot = ProofChecks.root(events(6));
        byte[] newRoot = server.getRoot();
        byte[] changedOld = oldRoot.clone();
        changedOld[0] ^= 1;
        byte[] changedNew = newRoot.clone();
        changedNew[0] ^= 1;
        List<byte[]> proof = server.genProof(6);
        assertFalse(ProofChecks.isConsistent(6, changedOld, 8, newRoot, proof));
        assertFalse(ProofChecks.isConsistent(6, oldRoot, 8, changedNew, proof));
        assertFalse(ProofChecks.isMember("e4", 3, 8, server.genPath(4), newRoot));
        assertFalse(ProofChecks.isConsistent(8, newRoot, 8, changedNew, List.of()));
    }

    private LogServer open(List<String> events) throws IOException {
        Files.write(ledger(), events, StandardCharsets.UTF_8);
        return new LogServer(ledger());
    }

    private Path ledger() {
        return directory.resolve("ledger.txt");
    }

    private static List<String> events(int size) {
        return IntStream.rangeClosed(1, size).mapToObj(i -> "e" + i).toList();
    }

    private static void assertHashes(List<byte[]> expected, List<byte[]> actual) {
        assertEquals(expected.size(), actual.size());
        for (int i = 0; i < expected.size(); i++) {
            assertArrayEquals(expected.get(i), actual.get(i), "hash " + i);
        }
    }
}
