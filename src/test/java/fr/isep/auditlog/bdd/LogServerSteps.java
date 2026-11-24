package fr.isep.auditlog.bdd;

import fr.isep.auditlog.LogServer;
import fr.isep.auditlog.support.ProofChecks;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

public class LogServerSteps {
    private Path directory;
    private Path ledger;
    private List<String> events;
    private LogServer server;
    private byte[] trustedRoot;
    private int oldSize;
    private byte[] oldRoot;
    private List<byte[]> proof;
    private IllegalArgumentException requestError;

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
        trustedRoot = server.getRoot();
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
        assertArrayEquals(ProofChecks.root(events), server.getRoot());
        assertArrayEquals(server.getRoot(), reloaded.getRoot());
    }

    @When("I request the audit path for event {int}")
    public void requestPath(int index) {
        proof = server.genPath(index);
    }

    @Then("the independent membership check for event {int} succeeds")
    public void membershipSucceeds(int index) {
        assertTrue(ProofChecks.isMember(events.get(index - 1), index,
                server.getSize(), proof, trustedRoot));
    }

    @Then("the independent membership check for event {int} fails")
    public void membershipFails(int index) {
        assertFalse(ProofChecks.isMember(events.get(index - 1), index,
                server.getSize(), proof, trustedRoot));
    }

    @Then("the independent membership check rejects {string} at index {int}")
    public void rejectTamperedEvent(String event, int index) {
        assertFalse(ProofChecks.isMember(event, index, server.getSize(), proof, trustedRoot));
    }

    @Then("the proof hashes cover ranges {string} in that order")
    public void exactProof(String ranges) {
        String[] expectedRanges = ranges.split(",");
        assertEquals(expectedRanges.length, proof.size());
        for (int i = 0; i < expectedRanges.length; i++) {
            String[] bounds = expectedRanges[i].split("-");
            int first = Integer.parseInt(bounds[0]);
            int last = Integer.parseInt(bounds[1]);
            assertArrayEquals(ProofChecks.root(events.subList(first - 1, last)), proof.get(i));
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

    @Then("an independent consistency check succeeds")
    public void consistencySucceeds() {
        assertTrue(ProofChecks.isConsistent(oldSize, oldRoot,
                server.getSize(), server.getRoot(), proof));
    }

    @Then("the independent consistency check fails")
    public void consistencyFails() {
        assertFalse(ProofChecks.isConsistent(oldSize, oldRoot,
                server.getSize(), server.getRoot(), proof));
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

    private static List<String> numberedEvents(int first, int last) {
        return IntStream.rangeClosed(first, last).mapToObj(i -> "e" + i).toList();
    }
}
