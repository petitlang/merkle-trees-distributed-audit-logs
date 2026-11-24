package fr.isep.auditlog;

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
    private static final HexFormat HEX = HexFormat.of();
    private static final String ROOT_8 = "7dc0f08884fa7f18ba933c2d46d41f0de6246cdaff7d4808e675beea75e52cd8";
    private static final String HASH_5_TO_8 = "dda44e8c742b4d19cbd22526fd4418606dd10bb2422929669853d9f8dca94591";

    @TempDir
    Path directory;

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 8, 9, 15, 16, 17, 31, 32, 33, 64, 65})
    void theAuditorVerifiesEveryEvent(int size) throws IOException {
        List<String> events = events(size);
        LogServer server = open(events);
        byte[] trustedRoot = MerkleTree.build(events).getHash();
        assertArrayEquals(trustedRoot, server.getRoot());
        Auditor auditor = new Auditor(size, trustedRoot);
        for (int index = 1; index <= size; index++) {
            List<byte[]> path = server.genPath(index);
            String event = events.get(index - 1);
            assertTrue(auditor.isMember(event, index, path),
                    "valid path: size=" + size + ", index=" + index);
            assertFalse(auditor.isMember(event + "-changed", index, path));
            assertTrue(path.size() <= 32 - Integer.numberOfLeadingZeros(size - 1));
            if (!path.isEmpty()) {
                path.get(0)[0] ^= 1;
                assertFalse(auditor.isMember(event, index, path));
            }
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 8, 9, 15, 16, 17, 31, 32, 33, 64, 65})
    void theAuditorVerifiesEveryPreviousSize(int size) throws IOException {
        List<String> events = events(size);
        LogServer server = open(events);
        Auditor auditor = new Auditor(size, MerkleTree.build(events).getHash());
        for (int oldSize = 1; oldSize <= size; oldSize++) {
            byte[] oldRoot = MerkleTree.build(events.subList(0, oldSize)).getHash();
            List<byte[]> proof = server.genProof(oldSize);
            assertTrue(auditor.isConsistent(oldSize, oldRoot, proof),
                    "valid extension: " + oldSize + " -> " + size);
            assertTrue(proof.size() <= 1 + 32 - Integer.numberOfLeadingZeros(size - 1));
            if (!proof.isEmpty()) {
                proof.get(0)[0] ^= 1;
                assertFalse(auditor.isConsistent(oldSize, oldRoot, proof));
            }
        }
    }

    @Test
    void followsBothProofExamplesFromTheAssignmentExactly() throws IOException {
        LogServer server = open(events(8));
        // Fixed vectors calculated with Python hashlib, in the assignment's order.
        assertHashes(List.of(
                HEX.parseHex("d5bdb58a19173a6a0111df3f1f62dbaa00e44221e0f09b47489812a010cd5314"),
                HEX.parseHex("1ae210a506da2f0264c59f2041fe70fe9bfceda1cbb394471e9587e44b430216"),
                HEX.parseHex(HASH_5_TO_8)), server.genPath(4));
        assertHashes(List.of(
                HEX.parseHex("5eda4828534046536288bbb71ddfd3da6d3cf0ecddc8f4883206c00dd8ed3180"),
                HEX.parseHex("522d9d790d281d06b68af79d5212826fe65eb8248cf004464d4e8b0f2df0d392"),
                HEX.parseHex("7abfaa969284fb86f37f1e0aa9ade022007b377e88766f4d0d2d6419a8d20d40")),
                server.genProof(6));
    }

    @Test
    void oldPowerOfTwoRootIsAlreadyKnownAndNeedNotBeSentAgain() throws IOException {
        LogServer server = open(events(8));
        assertHashes(List.of(HEX.parseHex(HASH_5_TO_8)), server.genProof(4));
        assertTrue(server.genProof(8).isEmpty());
    }

    @Test
    void persistsSingleAndBatchAppendsAndPreservesTheOldPrefix() throws IOException {
        LogServer server = open(events(5));
        byte[] oldRoot = server.getRoot();
        server.append("e6");
        server.append(List.of("e7", "e8"));
        assertEquals(events(8), Files.readAllLines(ledger(), StandardCharsets.UTF_8));
        assertEquals(ROOT_8, server.getRootHex());
        assertArrayEquals(server.getRoot(), new LogServer(ledger()).getRoot());
        assertTrue(new Auditor(8, HEX.parseHex(ROOT_8)).isConsistent(5, oldRoot, server.genProof(5)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"e1", "e1\n", "e1\r\n", "e1\r"})
    void appendHandlesDifferentExistingLineEndings(String content) throws IOException {
        Files.writeString(ledger(), content, StandardCharsets.UTF_8);
        LogServer server = new LogServer(ledger());
        server.append("e2");
        assertEquals(List.of("e1", "e2"), Files.readAllLines(ledger(), StandardCharsets.UTF_8));
        assertEquals("1ae210a506da2f0264c59f2041fe70fe9bfceda1cbb394471e9587e44b430216",
                new LogServer(ledger()).getRootHex());
    }

    @Test
    void preservesWhitespaceUnicodeAndBlankLines() throws IOException {
        List<String> expected = List.of("  e1  ", "", "\u7528\u6237\u767b\u5f55", "\ud83d\ude42", "");
        Files.writeString(ledger(), String.join("\r\n", expected) + "\r\n", StandardCharsets.UTF_8);
        LogServer server = new LogServer(ledger());
        assertEquals(expected.size(), server.getSize());
        assertEquals("949351435708e8d6c1ff38a05c66536645f91e567f102f484eee80f12547a5b8",
                server.getRootHex());
        server.append(List.of("", "  "));
        List<String> appended = new ArrayList<>(expected);
        appended.addAll(List.of("", "  "));
        assertEquals(appended, Files.readAllLines(ledger(), StandardCharsets.UTF_8));
        assertEquals("cecfe8084e2eaa09a252cef77ff3762c44921b93057469d911708e8236100c50",
                new LogServer(ledger()).getRootHex());
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
        assertEquals("7d61a489abea5f2bfa1d198650bccc9de0c3b643334a64d1a9574e827bccfcbe",
                new LogServer(ledger()).getRootHex());
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
        assertEquals(ROOT_8, server.getRootHex());
        assertHashes(originalPath, server.genPath(4));
        assertHashes(originalProof, server.genProof(6));
        assertEquals(HEX.formatHex(server.getRoot()), server.getRootHex());
    }

    @Test
    void theAuditorRejectsChangedTrustedRootsAndMisstatedIndices() throws IOException {
        LogServer server = open(events(8));
        byte[] oldRoot = HEX.parseHex("0ec8a9f5275054d630180595e5d356faf48ea547c96e4b9c9ebe89303ce326b8");
        byte[] newRoot = HEX.parseHex(ROOT_8);
        byte[] changedOld = oldRoot.clone();
        changedOld[0] ^= 1;
        byte[] changedNew = newRoot.clone();
        changedNew[0] ^= 1;
        List<byte[]> proof = server.genProof(6);
        Auditor auditor = new Auditor(8, newRoot);
        Auditor changedAuditor = new Auditor(8, changedNew);
        assertFalse(auditor.isConsistent(6, changedOld, proof));
        assertFalse(changedAuditor.isConsistent(6, oldRoot, proof));
        assertFalse(auditor.isMember("e4", 3, server.genPath(4)));
        assertFalse(changedAuditor.isConsistent(8, newRoot, List.of()));
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
