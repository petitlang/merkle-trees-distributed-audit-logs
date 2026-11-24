package fr.isep.auditlog;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuditorTest {
    // These checkpoints were calculated with Python hashlib.
    private static final HexFormat HEX = HexFormat.of();
    private static final String ROOT_1 = "d38b823b070424fc9303ff6f20f4ddeef6dc32aa02c75d9c8c3ae46062c78f6b";
    private static final String ROOT_2 = "1ae210a506da2f0264c59f2041fe70fe9bfceda1cbb394471e9587e44b430216";
    private static final String LEAF_2 = "e76eb1b97b06757150f95e06357a7dc404ca511b63a90330a56a94df3d0d66d1";
    private static final String EMPTY_ROOT = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    @Test
    void verifiesAnEmptyPathForTheOnlyEventAndCopiesTheCheckpointRoot() {
        byte[] root = HEX.parseHex(ROOT_1);
        Auditor auditor = new Auditor(1, root);
        root[0] ^= 1;

        assertTrue(auditor.isMember("e1", 1, List.of()));
        assertFalse(auditor.isMember("e2", 1, List.of()));
    }

    @Test
    void verifiesTheSecondEventOnlyAtItsClaimedPosition() {
        Auditor auditor = new Auditor(2, HEX.parseHex(ROOT_2));
        List<byte[]> path = List.of(HEX.parseHex(ROOT_1));

        assertTrue(auditor.isMember("e2", 2, path));
        assertFalse(auditor.isMember("e2", 1, path));
        assertFalse(auditor.isMember("e2", 0, path));
        assertFalse(auditor.isMember("e2", 3, path));
        assertFalse(new Auditor(0, HEX.parseHex(EMPTY_ROOT)).isMember("e2", 1, List.of()));
    }

    @Test
    void rejectsMissingExtraAndMalformedPathHashes() {
        Auditor auditor = new Auditor(2, HEX.parseHex(ROOT_2));
        byte[] sibling = HEX.parseHex(ROOT_1);

        assertFalse(auditor.isMember("e2", 2, null));
        assertFalse(auditor.isMember(null, 2, List.of(sibling)));
        assertFalse(auditor.isMember("e2", 2, List.of()));
        assertFalse(auditor.isMember("e2", 2, List.of(sibling, sibling)));
        assertFalse(auditor.isMember("e2", 2, Collections.singletonList(null)));
        assertFalse(auditor.isMember("e2", 2, List.of(Arrays.copyOf(sibling, 31))));
    }

    @Test
    void rejectsInvalidCheckpoints() {
        assertThrows(IllegalArgumentException.class, () -> new Auditor(-1, new byte[32]));
        assertThrows(IllegalArgumentException.class, () -> new Auditor(1, null));
        assertThrows(IllegalArgumentException.class, () -> new Auditor(1, new byte[31]));
    }

    @Test
    void verifiesAnExtensionAndRejectsAnIncorrectOldRootOrSize() {
        Auditor auditor = new Auditor(2, HEX.parseHex(ROOT_2));
        byte[] oldRoot = HEX.parseHex(ROOT_1);
        List<byte[]> proof = List.of(HEX.parseHex(LEAF_2));

        assertTrue(auditor.isConsistent(1, oldRoot, proof));
        assertFalse(auditor.isConsistent(0, oldRoot, proof));
        assertFalse(auditor.isConsistent(3, oldRoot, proof));
        assertFalse(auditor.isConsistent(1, null, proof));
        assertFalse(auditor.isConsistent(1, new byte[31], proof));
        oldRoot[0] ^= 1;
        assertFalse(auditor.isConsistent(1, oldRoot, proof));
    }

    @Test
    void rejectsMissingExtraAndMalformedConsistencyHashes() {
        Auditor auditor = new Auditor(2, HEX.parseHex(ROOT_2));
        byte[] oldRoot = HEX.parseHex(ROOT_1);
        byte[] sibling = HEX.parseHex(LEAF_2);

        assertFalse(auditor.isConsistent(1, oldRoot, null));
        assertFalse(auditor.isConsistent(1, oldRoot, List.of()));
        assertFalse(auditor.isConsistent(1, oldRoot, List.of(sibling, sibling)));
        assertFalse(auditor.isConsistent(1, oldRoot, Collections.singletonList(null)));
        assertFalse(auditor.isConsistent(1, oldRoot, List.of(Arrays.copyOf(sibling, 31))));
    }

    @Test
    void equalSizesRequireEqualRootsAndAnEmptyProof() {
        byte[] root = HEX.parseHex(ROOT_2);
        Auditor auditor = new Auditor(2, root);

        assertTrue(auditor.isConsistent(2, root, List.of()));
        assertFalse(auditor.isConsistent(2, root, List.of(HEX.parseHex(LEAF_2))));
        assertFalse(auditor.isConsistent(2, HEX.parseHex(ROOT_1), List.of()));
        assertFalse(new Auditor(0, HEX.parseHex(EMPTY_ROOT)).isConsistent(0, HEX.parseHex(EMPTY_ROOT), List.of()));
    }
}
