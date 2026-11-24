package fr.isep.auditlog.bdd;

import fr.isep.auditlog.Auditor;
import fr.isep.auditlog.LogServer;
import fr.isep.auditlog.MerkleTree;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

public class LogServerSteps {
    private Path directory;
    private Path ledger;
    private List<String> events;
    private LogServer server;
    private Auditor auditor;
    private int oldSize;
    private byte[] oldRoot;
    private List<byte[]> proof;
    private IllegalArgumentException requestError;
    private Scenario scenario;

    @Before("@first-three and @log-server")
    public void enableFirstThreeOutput(Scenario scenario) {
        this.scenario = scenario;
    }

    @Before("@log-server")
    public void createLedgerDirectory() throws IOException {
        directory = Files.createTempDirectory("merkle-log-bdd-");
        ledger = directory.resolve("ledger.txt");
    }

    @After("@log-server")
    public void cleanLedgerDirectory() throws IOException {
        Files.deleteIfExists(ledger);
        Files.deleteIfExists(directory);
    }

    @Given("a ledger containing {int} numbered events")
    public void ledgerWithEvents(int size) throws IOException {
        events = new ArrayList<>(numberedEvents(1, size));
        Files.write(ledger, events, StandardCharsets.UTF_8);
    }

    @Given("a ledger containing {int} numbered events without a final newline")
    public void unterminatedLedger(int size) throws IOException {
        events = new ArrayList<>(numberedEvents(1, size));
        Files.writeString(ledger, String.join("\n", events), StandardCharsets.UTF_8);
    }

    @When("I open the log server")
    public void openServer() throws IOException {
        server = new LogServer(ledger);
        auditor = auditorForCurrentCheckpoint();
        report("Ledger records: " + events);
        report("Checkpoint size=" + server.getSize() + ", root=" + server.getRootHex());
    }

    @Then("the server contains {int} events")
    public void serverSize(int size) {
        assertEquals(size, server.getSize());
    }

    @Then("the server root is {string}")
    public void serverRoot(String root) {
        assertEquals(root, server.getRootHex());
    }

    @When("I append the event {string}")
    public void appendOne(String event) throws IOException {
        server.append(event);
        events.add(event);
    }

    @When("I append numbered events {int} through {int} as a batch")
    public void appendBatch(int first, int last) throws IOException {
        List<String> batch = numberedEvents(first, last);
        server.append(batch);
        events.addAll(batch);
    }

    @Then("reopening the ledger preserves its events and root")
    public void reloadLedger() throws IOException {
        LogServer reloaded = new LogServer(ledger);
        assertEquals(events, Files.readAllLines(ledger, StandardCharsets.UTF_8));
        assertEquals(events.size(), reloaded.getSize());
        assertArrayEquals(MerkleTree.build(events).getHash(), server.getRoot());
        assertArrayEquals(server.getRoot(), reloaded.getRoot());
    }

    @When("I request the audit path for event {int}")
    public void requestPath(int index) {
        proof = server.genPath(index);
        report("genPath(" + index + "): " + proof.size() + " sibling hashes, leaf-to-root");
        for (int i = 0; i < proof.size(); i++) {
            report("  path[" + i + "]=" + HexFormat.of().formatHex(proof.get(i)));
        }
    }

    @Then("the auditor confirms membership for event {int}")
    public void membershipSucceeds(int index) {
        String event = events.get(index - 1);
        boolean member = auditor.isMember(event, index, proof);
        report("isMember(event=" + event + ", index=" + index
                + ", size=" + server.getSize() + ") = " + member);
        assertTrue(member);
    }

    @Then("the auditor rejects the path for event {int}")
    public void membershipFails(int index) {
        assertFalse(auditor.isMember(events.get(index - 1), index, proof));
    }

    @Then("the auditor rejects {string} at index {int}")
    public void rejectTamperedEvent(String event, int index) {
        assertFalse(auditor.isMember(event, index, proof));
    }

    @Then("the proof hashes cover ranges {string} in that order")
    public void exactProof(String ranges) {
        String[] expectedRanges = ranges.split(",");
        assertEquals(expectedRanges.length, proof.size());
        for (int i = 0; i < expectedRanges.length; i++) {
            String[] bounds = expectedRanges[i].split("-");
            int first = Integer.parseInt(bounds[0]);
            int last = Integer.parseInt(bounds[1]);
            assertArrayEquals(MerkleTree.build(events.subList(first - 1, last)).getHash(), proof.get(i));
        }
    }

    @Given("I save the current size and root")
    public void saveVersion() {
        oldSize = server.getSize();
        oldRoot = server.getRoot();
    }

    @When("I request a consistency proof for the saved version")
    public void requestConsistency() {
        proof = server.genProof(oldSize);
    }

    @Then("the auditor accepts the new checkpoint")
    public void consistencySucceeds() {
        assertTrue(auditorForCurrentCheckpoint().isConsistent(oldSize, oldRoot, proof));
    }

    @Then("the auditor rejects the new checkpoint")
    public void consistencyFails() {
        assertFalse(auditorForCurrentCheckpoint().isConsistent(oldSize, oldRoot, proof));
    }

    @Then("the proof contains {int} hashes")
    public void proofLength(int length) {
        assertEquals(length, proof.size());
    }

    @When("I change the first proof hash")
    public void changeProof() {
        proof.get(0)[0] ^= 1;
    }

    @When("I rewrite the ledger with a changed first event and reopen it")
    public void rewriteHistory() throws IOException {
        events.set(0, "tampered");
        Files.write(ledger, events, StandardCharsets.UTF_8);
        server = new LogServer(ledger);
    }

    @When("I try to request the audit path for event {int}")
    public void invalidPath(int index) {
        requestError = assertThrows(IllegalArgumentException.class, () -> server.genPath(index));
    }

    @When("I try to request a consistency proof for size {int}")
    public void invalidProof(int size) {
        requestError = assertThrows(IllegalArgumentException.class, () -> server.genProof(size));
    }

    @Then("the server rejects the request with an illegal argument")
    public void rejectedRequest() {
        assertNotNull(requestError);
    }

    private Auditor auditorForCurrentCheckpoint() {
        return new Auditor(server.getSize(), server.getRoot());
    }

    private static List<String> numberedEvents(int first, int last) {
        return IntStream.rangeClosed(first, last).mapToObj(i -> "e" + i).toList();
    }

    private void report(String text) {
        if (scenario != null) {
            scenario.log(text);
        }
    }
}
