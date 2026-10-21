package com.demo.auth.client;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The feed is read on a caller's request, so it must be cached; with no good read for {@link Revocations#STALE_AFTER} it refuses rather than guesses. */
class RevocationsTests {

    @Test
    void theFeedIsReadOnceAWindowNotOnceARequest() throws Exception {
        AtomicInteger reads = new AtomicInteger();
        HttpServer auth = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        auth.createContext("/token-versions", exchange -> {
            reads.incrementAndGet();
            byte[] body = "{\"7\":2,\"agent:9\":3}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        auth.start();
        try {
            Revocations revocations = new Revocations("http://localhost:" + auth.getAddress().getPort() + "/token-versions");
            assertEquals(2, revocations.minimumVersion("7"));
            assertEquals(3, revocations.minimumAgentVersion(9L));
            assertEquals(0, revocations.minimumVersion("8"), "a user never revoked needs no version");
            assertEquals(0, revocations.minimumVersion("9"), "an agent's entry is not a user's: agent:9 is not 9");
            assertEquals(1, reads.get(), "four questions, one read");

            auth.stop(0);
            assertEquals(2, revocations.minimumVersion("7"), "inside the window the cached list answers, server or no server");
        } finally {
            auth.stop(0);
        }
    }

    @Test
    void requestsThatArriveDuringTheFirstReadWaitForItRatherThanBeingRefused() throws Exception {
        HttpServer slow = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        slow.createContext("/token-versions", exchange -> {
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        slow.start();
        try {
            Revocations revocations = new Revocations("http://localhost:" + slow.getAddress().getPort() + "/token-versions");
            // A page opening fires its calls together; all but the one doing the read used to be refused, a 401.
            Callable<Integer> ask = () -> revocations.minimumVersion("7");
            List<Future<Integer>> answers;
            try (ExecutorService callers = Executors.newFixedThreadPool(8)) {
                answers = callers.invokeAll(Collections.nCopies(8, ask));
            }
            for (Future<Integer> answer : answers) {
                assertEquals(0, answer.get());
            }
        } finally {
            slow.stop(0);
        }
    }

    @Test
    void anUnreachableOrBrokenFeedRefusesEveryTokenUntilARead() throws Exception {
        HttpServer down = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        int port = down.getAddress().getPort(); // bound, never started: nothing answers
        down.stop(0);
        Revocations neverRead = new Revocations("http://localhost:" + port + "/token-versions");
        assertThrows(IllegalStateException.class, () -> neverRead.minimumVersion("7"), "no list yet, so nothing passes");

        HttpServer broken = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        broken.createContext("/token-versions", exchange -> exchange.sendResponseHeaders(500, -1));
        broken.start();
        try {
            Revocations revocations = new Revocations("http://localhost:" + broken.getAddress().getPort() + "/token-versions");
            assertThrows(IllegalStateException.class, () -> revocations.minimumVersion("7"));
        } finally {
            broken.stop(0);
        }
    }
}
