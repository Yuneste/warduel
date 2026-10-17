package com.warduel.warduel.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * A scripted game client for integration tests: connects to /game, records every message
 * and lets tests wait for specific ones.
 */
class TestPlayer extends TextWebSocketHandler implements AutoCloseable {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);

    private final List<JsonNode> received = new ArrayList<>();
    private final WebSocketSession session;
    private final Set<Integer> returned = new HashSet<>(); // indexes already handed out by next()

    TestPlayer(int port) throws Exception {
        session = new StandardWebSocketClient()
                .execute(this, "ws://localhost:" + port + "/game")
                .get();
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonNode json = MAPPER.readTree(message.getPayload());
        synchronized(received) {
            received.add(json);
            received.notifyAll();
        }
    }

    void send(Map<String, Object> message) throws Exception {
        synchronized(session) {
            session.sendMessage(new TextMessage(MAPPER.writeValueAsString(message)));
        }
    }

    /** Enters public matchmaking (null name: the server picks "Player 1"/"Player 2") */
    void join(String name) throws Exception {
        Map<String, Object> message = new java.util.HashMap<>(Map.of("type", "JOIN_GAME"));
        if(name != null) {
            message.put("playerName", name);
        }
        send(message);
    }

    void answer(int value) throws Exception {
        send(Map.of("type", "ANSWER", "answer", value));
    }

    /** Answers the most recent question correctly and waits for the resulting score update. */
    JsonNode answerCurrentCorrectly() throws Exception {
        answer(solve(last("QUESTION").get("questionText").asText()));
        return next("SCORE_UPDATE");
    }

    /** Waits for the next not-yet-returned message of this type (already received ones count). */
    JsonNode next(String type) throws InterruptedException {
        return next(type, m -> true, DEFAULT_TIMEOUT);
    }

    JsonNode next(String type, Predicate<JsonNode> filter, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        synchronized(received) {
            while(true) {
                for(int i = 0; i < received.size(); i++) {
                    JsonNode m = received.get(i);
                    if(!returned.contains(i) && type.equals(m.path("type").asText()) && filter.test(m)) {
                        returned.add(i);
                        return m;
                    }
                }
                long waitMs = (deadline - System.nanoTime()) / 1_000_000;
                if(waitMs <= 0) {
                    throw new AssertionError("No " + type + " within " + timeout + "; received: " + received);
                }
                received.wait(waitMs);
            }
        }
    }

    JsonNode last(String type) {
        return all(type).stream().reduce((a, b) -> b)
                .orElseThrow(() -> new AssertionError("No " + type + " received"));
    }

    List<JsonNode> all(String type) {
        synchronized(received) {
            return received.stream().filter(m -> type.equals(m.path("type").asText())).toList();
        }
    }

    /** Message types in arrival order */
    List<String> types() {
        synchronized(received) {
            return received.stream().map(m -> m.path("type").asText()).toList();
        }
    }

    boolean isOpen() {
        return session.isOpen();
    }

    @Override
    public void close() throws Exception {
        if(session.isOpen()) {
            session.close(CloseStatus.NORMAL);
        }
    }

    static int solve(String questionText) {
        String[] parts = questionText.split(" ");
        int a = Integer.parseInt(parts[0]);
        int b = Integer.parseInt(parts[2]);
        return switch(parts[1]) {
            case "+" -> a + b;
            case "-" -> a - b;
            case "×" -> a * b;
            case "÷" -> a / b;
            default -> throw new IllegalArgumentException(questionText);
        };
    }
}
