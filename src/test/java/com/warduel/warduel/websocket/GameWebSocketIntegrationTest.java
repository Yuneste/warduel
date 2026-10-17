package com.warduel.warduel.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plays real matches against the running server over WebSocket.
 * Rounds are shortened to 5 seconds and the win score to 3 so the suite stays fast;
 * the 3-second countdown and 1-second rematch delay are the real ones.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "game.duration-seconds=5",
        "game.win-score=3",
})
class GameWebSocketIntegrationTest {

    private static final int DURATION = 5;
    private static final Duration COUNTDOWN_AND_ROUND = Duration.ofSeconds(3 + DURATION + 3);

    @LocalServerPort
    private int port;

    private final List<TestPlayer> players = new ArrayList<>();

    @AfterEach
    void disconnectEveryone() throws Exception {
        for(TestPlayer player : players) {
            player.close();
        }
        Thread.sleep(300); // let the server process the disconnects before the next test matches players
    }

    @Test
    void fullMatchCountsDownThenEndsByTimer() throws Exception {
        TestPlayer a = connect();
        TestPlayer b = connect();

        for(int n = 3; n >= 1; n--) {
            int expected = n;
            a.next("COUNTDOWN", m -> m.get("countdown").asInt() == expected, Duration.ofSeconds(5));
        }
        JsonNode state = a.next("GAME_STATE", m -> m.get("gameStatus").asText().equals("RUNNING"), Duration.ofSeconds(5));
        assertThat(state.get("yourName").asText()).isEqualTo("Player 1");
        assertThat(state.get("opponentName").asText()).isEqualTo("Player 2");

        JsonNode q1 = a.next("QUESTION");
        assertThat(q1.get("questionNumber").asInt()).isEqualTo(1);
        assertThat(q1.get("remainingSeconds").asLong()).isEqualTo(DURATION);
        b.next("QUESTION");

        JsonNode score = a.answerCurrentCorrectly();
        assertThat(score.get("wasCorrect").asBoolean()).isTrue();
        assertThat(score.get("yourScore").asInt()).isEqualTo(1);
        assertThat(b.next("SCORE_UPDATE").get("opponentScore").asInt()).isEqualTo(1);

        // The clock started after the countdown, so the next question still has (almost) the full round left
        JsonNode q2 = a.next("QUESTION");
        assertThat(q2.get("remainingSeconds").asLong()).isGreaterThanOrEqualTo(DURATION - 1);

        b.answer(-999);
        assertThat(b.next("SCORE_UPDATE", m -> m.get("yourScore").asInt() == 0 && !m.get("wasCorrect").asBoolean(),
                Duration.ofSeconds(5))).isNotNull();

        JsonNode overA = a.next("GAME_OVER", m -> true, COUNTDOWN_AND_ROUND);
        JsonNode overB = b.next("GAME_OVER");
        assertThat(overA.get("youWon").asBoolean()).isTrue();
        assertThat(overA.get("yourScore").asInt()).isEqualTo(1);
        assertThat(overB.get("youWon").asBoolean()).isFalse();
        assertThat(overB.get("opponentScore").asInt()).isEqualTo(1);
    }

    @Test
    void answersDuringTheCountdownAreRejected() throws Exception {
        TestPlayer a = connect();
        connect();

        a.next("COUNTDOWN");
        a.answer(1);

        assertThat(a.next("ERROR").get("errorMessage").asText()).contains("not active");
        a.next("QUESTION", m -> true, Duration.ofSeconds(5));
        assertThat(a.all("SCORE_UPDATE")).isEmpty();
    }

    @Test
    void leavingDuringTheCountdownCancelsTheMatch() throws Exception {
        TestPlayer a = connect();
        TestPlayer b = connect();
        a.next("COUNTDOWN");

        b.close();

        assertThat(a.next("ERROR").get("errorMessage").asText()).isEqualTo("Opponent left the queue");
        Thread.sleep(3500); // past the moment the game would have started
        assertThat(a.all("GAME_OVER")).as("no free win").isEmpty();
        assertThat(a.all("QUESTION")).as("game never starts").isEmpty();
    }

    @Test
    void reachingTheWinScoreEndsTheGameAndTheRematchSurvivesTheOldTimer() throws Exception {
        TestPlayer a = connect();
        TestPlayer b = connect();
        long firstRoundStart = 0;

        for(int i = 1; i <= 3; i++) {
            int number = i;
            a.next("QUESTION", m -> m.get("questionNumber").asInt() == number, Duration.ofSeconds(5));
            if(i == 1) {
                firstRoundStart = System.nanoTime();
            }
            a.answerCurrentCorrectly();
        }
        JsonNode won = a.next("GAME_OVER");
        assertThat(won.get("youWon").asBoolean()).isTrue();
        assertThat(won.get("yourScore").asInt()).isEqualTo(3);
        b.next("GAME_OVER");

        a.send(Map.of("type", "REMATCH", "requestRematch", true));
        b.send(Map.of("type", "REMATCH", "requestRematch", true));
        JsonNode rematchQuestion = a.next("QUESTION", m -> m.get("questionNumber").asInt() == 1, Duration.ofSeconds(5));
        assertThat(rematchQuestion.get("remainingSeconds").asLong()).isEqualTo(DURATION);
        assertThat(b.last("GAME_STATE").get("gameStatus").asText()).isEqualTo("RUNNING");

        for(TestPlayer player : List.of(a, b)) {
            List<Boolean> accepted = player.all("REMATCH").stream().map(m -> m.get("opponentAccepted").asBoolean()).toList();
            assertThat(accepted).as("'waiting' must never follow 'starting'").endsWith(true);
            assertThat(accepted.indexOf(true)).isEqualTo(accepted.size() - 1);
        }

        // Wait until just after the first round's timer would have fired
        long untilOldTimer = Duration.ofSeconds(DURATION).toMillis() + 500
                - Duration.ofNanos(System.nanoTime() - firstRoundStart).toMillis();
        Thread.sleep(Math.max(0, untilOldTimer));
        assertThat(a.all("GAME_OVER")).as("old timer must not end the rematch").hasSize(1);

        JsonNode rematchOver = a.next("GAME_OVER", m -> true, Duration.ofSeconds(DURATION + 2));
        assertThat(rematchOver.get("draw").asBoolean()).isTrue();
        Thread.sleep(500);
        assertThat(a.all("GAME_OVER")).hasSize(2);
    }

    @Test
    void forfeitGivesTheOpponentTheWinWithRealScores() throws Exception {
        TestPlayer a = connect();
        TestPlayer b = connect();
        a.next("QUESTION", m -> true, Duration.ofSeconds(5));
        b.next("QUESTION", m -> true, Duration.ofSeconds(5));
        a.answerCurrentCorrectly();
        b.answerCurrentCorrectly();

        b.send(Map.of("type", "FORFEIT"));

        JsonNode winner = a.next("GAME_OVER");
        JsonNode loser = b.next("GAME_OVER");
        assertThat(winner.get("youWon").asBoolean()).isTrue();
        assertThat(winner.get("disconnectMessage").asText()).isEqualTo("Player 2 forfeited");
        assertThat(winner.get("yourScore").asInt()).isEqualTo(1);
        assertThat(winner.get("opponentScore").asInt()).isEqualTo(1);
        assertThat(loser.get("youWon").asBoolean()).isFalse();
        assertThat(loser.get("yourScore").asInt()).isEqualTo(1);
    }

    @Test
    void disconnectingDuringTheGameGivesTheOpponentTheWin() throws Exception {
        TestPlayer a = connect();
        TestPlayer b = connect();
        a.next("QUESTION", m -> true, Duration.ofSeconds(5));

        b.close();

        JsonNode over = a.next("GAME_OVER");
        assertThat(over.get("youWon").asBoolean()).isTrue();
        assertThat(over.get("disconnectMessage").asText()).isEqualTo("Player 2 disconnected");
        assertThat(a.next("ERROR").get("errorMessage").asText()).as("rematch no longer possible").isEqualTo("Opponent left");
    }

    @Test
    void rematchIsRefusedOnceTheOpponentLeft() throws Exception {
        TestPlayer a = connect();
        TestPlayer b = connect();
        a.next("QUESTION", m -> true, Duration.ofSeconds(5));
        b.send(Map.of("type", "FORFEIT"));
        a.next("GAME_OVER");

        b.close();
        assertThat(a.next("ERROR").get("errorMessage").asText()).isEqualTo("Opponent left");

        a.send(Map.of("type", "REMATCH", "requestRematch", true));
        assertThat(a.next("ERROR").get("errorMessage").asText()).isEqualTo("Opponent left");
        assertThat(a.all("REMATCH")).isEmpty();
    }

    @Test
    void floodingMessagesIsRateLimited() throws Exception {
        TestPlayer a = connect();
        for(int i = 0; i < 15; i++) {
            a.send(Map.of("type", "HEARTBEAT"));
        }

        assertThat(a.next("ERROR").get("errorMessage").asText()).contains("Too many messages");
    }

    private TestPlayer connect() throws Exception {
        return connect(null);
    }

    /** Host opens a private game of the given challenge type and a friend joins it */
    private TestPlayer[] privateGame(String challengeType) throws Exception {
        TestPlayer host = connectOnly();
        host.send(Map.of("type", "JOIN_GAME", "createRoom", true, "challengeType", challengeType));
        String code = host.next("ROOM_CREATED").get("roomCode").asText();
        TestPlayer friend = connectOnly();
        friend.send(Map.of("type", "JOIN_GAME", "roomCode", code));
        return new TestPlayer[] {host, friend};
    }

    // ---------- Challenge types ----------

    @Test
    void trueOrFalseGameAsksStatementsAndScoresTrueFalseAnswers() throws Exception {
        TestPlayer[] pair = privateGame("TRUE_FALSE");
        TestPlayer a = pair[0];
        assertThat(a.next("GAME_STATE").get("challengeType").asText()).isEqualTo("TRUE_FALSE");

        JsonNode q = a.next("QUESTION", m -> true, Duration.ofSeconds(5));
        assertThat(q.get("format").asText()).isEqualTo("TRUE_FALSE");
        assertThat(q.get("questionText").asText()).matches("\\d+ [-+×÷] \\d+ = \\d+");
        assertThat(a.answerCurrentCorrectly().get("wasCorrect").asBoolean()).isTrue();

        JsonNode q2 = a.next("QUESTION", m -> m.get("questionNumber").asInt() == 2, Duration.ofSeconds(5));
        int wrong = 1 - TestPlayer.correctAnswer(q2);
        a.answer(wrong);
        a.next("SCORE_UPDATE", m -> m.has("wasCorrect") && !m.get("wasCorrect").isNull() && !m.get("wasCorrect").asBoolean(),
                Duration.ofSeconds(5));
        pair[1].send(Map.of("type", "FORFEIT"));

        JsonNode answers = a.next("GAME_OVER").get("yourStats").get("answers");
        assertThat(answers.get(1).get("answer").asText()).isEqualTo(wrong == 1 ? "True" : "False");
    }

    @Test
    void compareGameOffersTwoOptions() throws Exception {
        TestPlayer[] pair = privateGame("COMPARE");
        TestPlayer a = pair[0];
        JsonNode q = a.next("QUESTION", m -> true, Duration.ofSeconds(5));

        assertThat(q.get("format").asText()).isEqualTo("CHOICE");
        assertThat(q.get("options")).hasSize(2);
        assertThat(a.answerCurrentCorrectly().get("wasCorrect").asBoolean()).isTrue();
    }

    @Test
    void missingNumberGameHidesAnOperand() throws Exception {
        TestPlayer[] pair = privateGame("MISSING_NUMBER");
        TestPlayer a = pair[0];
        JsonNode q = a.next("QUESTION", m -> true, Duration.ofSeconds(5));

        assertThat(q.get("format").asText()).isEqualTo("NUMBER");
        assertThat(q.get("questionText").asText()).contains("?");
        assertThat(a.answerCurrentCorrectly().get("wasCorrect").asBoolean()).isTrue();
    }

    @Test
    void publicGamesAnnounceTheirChallengeType() throws Exception {
        TestPlayer a = connect();
        connect();

        assertThat(a.next("GAME_STATE").get("challengeType").asText())
                .isIn("CLASSIC", "TRUE_FALSE", "MISSING_NUMBER", "COMPARE");
    }

    /** Connects and joins public matchmaking */
    private TestPlayer connect(String name) throws Exception {
        TestPlayer player = connectOnly();
        player.join(name);
        Thread.sleep(150); // keep join order deterministic (first to join becomes Player 1)
        return player;
    }

    private TestPlayer connectOnly() throws Exception {
        TestPlayer player = new TestPlayer(port);
        players.add(player);
        return player;
    }

    // ---------- Nicknames, private games, stats ----------

    @Test
    void nicknamesAreIntroducedBeforeTheCountdown() throws Exception {
        TestPlayer a = connect("Yuko");
        TestPlayer b = connect("Kai <script>");

        JsonNode intro = a.next("GAME_STATE");
        assertThat(intro.get("gameStatus").asText()).isEqualTo("READY");
        assertThat(intro.get("yourName").asText()).isEqualTo("Yuko");
        assertThat(intro.get("opponentName").asText()).as("unsafe characters removed").isEqualTo("Kai script");
        a.next("COUNTDOWN");
        assertThat(a.types().indexOf("GAME_STATE")).as("intro comes before the countdown")
                .isLessThan(a.types().indexOf("COUNTDOWN"));

        a.next("QUESTION", m -> true, Duration.ofSeconds(5));
        b.close();
        assertThat(a.next("GAME_OVER").get("disconnectMessage").asText()).isEqualTo("Kai script disconnected");
    }

    @Test
    void identicalNicknamesStayDistinguishable() throws Exception {
        TestPlayer a = connect("Kai");
        connect("kai");

        assertThat(a.next("GAME_STATE").get("opponentName").asText()).isEqualTo("kai (2)");
    }

    @Test
    void privateGameIsOnlyJoinedThroughItsCode() throws Exception {
        TestPlayer host = connectOnly();
        host.send(Map.of("type", "JOIN_GAME", "playerName", "Host", "createRoom", true));
        String code = host.next("ROOM_CREATED").get("roomCode").asText();
        assertThat(code).matches("[A-Z2-9]{6}");

        TestPlayer stranger = connect("Stranger"); // public matchmaking must not pair with the private game
        TestPlayer friend = connectOnly();
        friend.send(Map.of("type", "JOIN_GAME", "playerName", "Friend", "roomCode", code.toLowerCase()));

        assertThat(host.next("GAME_STATE").get("opponentName").asText()).isEqualTo("Friend");
        friend.next("COUNTDOWN");
        Thread.sleep(500);
        assertThat(stranger.all("COUNTDOWN")).as("stranger keeps waiting").isEmpty();

        TestPlayer late = connectOnly();
        late.send(Map.of("type", "JOIN_GAME", "roomCode", code));
        assertThat(late.next("ERROR").get("errorMessage").asText()).as("a code works once").isEqualTo("Room not found");
    }

    @Test
    void unknownRoomCodeIsRejected() throws Exception {
        TestPlayer a = connectOnly();
        a.send(Map.of("type", "JOIN_GAME", "roomCode", "NOPE42"));

        assertThat(a.next("ERROR").get("errorMessage").asText()).isEqualTo("Room not found");
    }

    @Test
    void privateGameClosesWhenTheHostLeaves() throws Exception {
        TestPlayer host = connectOnly();
        host.send(Map.of("type", "JOIN_GAME", "createRoom", true));
        String code = host.next("ROOM_CREATED").get("roomCode").asText();
        host.close();
        Thread.sleep(300);

        TestPlayer friend = connectOnly();
        friend.send(Map.of("type", "JOIN_GAME", "roomCode", code));
        assertThat(friend.next("ERROR").get("errorMessage").asText()).isEqualTo("Room not found");
    }

    @Test
    void gameOverCarriesAnswerStats() throws Exception {
        TestPlayer[] pair = privateGame("CLASSIC");
        TestPlayer a = pair[0];
        TestPlayer b = pair[1];
        a.next("QUESTION", m -> true, Duration.ofSeconds(5));
        a.answerCurrentCorrectly();
        a.next("QUESTION", m -> m.get("questionNumber").asInt() == 2, Duration.ofSeconds(5));
        a.answer(-999);
        a.next("SCORE_UPDATE", m -> m.has("wasCorrect") && !m.get("wasCorrect").isNull() && !m.get("wasCorrect").asBoolean(), Duration.ofSeconds(5));
        b.send(Map.of("type", "FORFEIT"));

        JsonNode stats = a.next("GAME_OVER").get("yourStats");
        assertThat(stats.get("answered").asInt()).isEqualTo(2);
        assertThat(stats.get("correct").asInt()).isEqualTo(1);
        assertThat(stats.get("averageMillis").asLong()).isGreaterThanOrEqualTo(0);
        JsonNode answers = stats.get("answers");
        assertThat(answers).hasSize(2);
        assertThat(answers.get(0).get("correct").asBoolean()).isTrue();
        assertThat(answers.get(1).get("answer").asText()).isEqualTo("-999");
        assertThat(answers.get(1).get("question").asText()).isNotBlank();

        JsonNode opponent = b.next("GAME_OVER").get("opponentStats");
        assertThat(opponent.get("correct").asInt()).isEqualTo(1);
        assertThat(opponent.has("answers")).as("the opponent's answers stay private").isFalse();
    }
}
