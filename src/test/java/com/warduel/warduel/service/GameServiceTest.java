package com.warduel.warduel.service;

import com.warduel.warduel.config.GameConfiguration;
import com.warduel.warduel.model.GameSession;
import com.warduel.warduel.model.GameSession.GameStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GameServiceTest {

    private GameService service;

    @BeforeEach
    void setUp() {
        GameConfiguration config = new GameConfiguration();
        service = new GameService(new QuestionGeneratorService(config), config);
    }

    @Test
    void firstPlayerWaitsAndSecondPlayerFillsTheGame() {
        GameSession first = service.joinGame(session("a"));
        assertThat(first.getStatus()).isEqualTo(GameStatus.WAITING);

        GameSession second = service.joinGame(session("b"));

        assertThat(second).isSameAs(first);
        assertThat(second.isFull()).isTrue();
        // Ready, not running: the clock starts only after the countdown
        assertThat(second.getStatus()).isEqualTo(GameStatus.READY);
        assertThat(second.getEndTime()).isNull();
    }

    @Test
    void thirdPlayerStartsANewGame() {
        GameSession full = join("a", "b");
        GameSession next = service.joinGame(session("c"));

        assertThat(next).isNotSameAs(full);
        assertThat(next.getStatus()).isEqualTo(GameStatus.WAITING);
    }

    @Test
    void joiningTwiceReturnsTheSameGame() {
        GameSession game = service.joinGame(session("a"));
        assertThat(service.joinGame(session("a"))).isSameAs(game);
        assertThat(game.isFull()).isFalse();
    }

    @Test
    void eachPlayerGetsTheirOwnQuestions() {
        GameSession game = join("a", "b");

        assertThat(game.getPlayer1().getQuestions()).hasSize(20);
        assertThat(game.getPlayer2().getQuestions()).hasSize(20);
        assertThat(game.getPlayer1().getQuestions()).isNotSameAs(game.getPlayer2().getQuestions());
        assertThat(game.getDurationSeconds()).isEqualTo(60);
    }

    @Test
    void waitingPlayerWhoLeavesIsNotMatchedLater() {
        GameSession abandoned = service.joinGame(session("a"));
        service.removePlayer("a");

        GameSession game = service.joinGame(session("b"));

        assertThat(game).isNotSameAs(abandoned);
        assertThat(game.getStatus()).isEqualTo(GameStatus.WAITING);
        assertThat(service.getGameByPlayerId("a")).isNull();
    }

    @Test
    void rematchStartsOnlyWhenBothPlayersAskAfterTheGameEnded() {
        GameSession game = join("a", "b");
        game.startGame();
        assertThat(service.requestRematch("a")).as("game still running").isFalse();

        game.endGame();
        assertThat(service.requestRematch("a")).isFalse();
        assertThat(service.requestRematch("b")).isTrue();

        assertThat(game.getStatus()).isEqualTo(GameStatus.READY);
        assertThat(game.getPlayer1().getQuestions()).hasSize(20);
    }

    @Test
    void rematchIsRefusedForUnknownPlayers() {
        assertThat(service.requestRematch("nobody")).isFalse();
    }

    @Test
    void privateGameWaitsForItsFriendNotForStrangers() {
        GameSession room = service.createPrivateGame(session("host"), "Host");
        assertThat(room.getRoomCode()).matches("[A-Z2-9]{6}");

        GameSession publicGame = service.joinGame(session("stranger"), "Stranger");
        assertThat(publicGame).isNotSameAs(room);

        var request = service.requestChallenge(session("friend"), "Friend", room.getRoomCode().toLowerCase(), null);
        assertThat(request.result()).isEqualTo(GameService.ChallengeResult.PENDING);
        assertThat(room.isFull()).as("not a player until accepted").isFalse();
        assertThat(service.isPendingChallenger("friend")).isTrue();

        GameSession game = service.acceptChallenge("host");
        assertThat(game).isSameAs(room);
        assertThat(room.getStatus()).isEqualTo(GameStatus.READY);
        assertThat(room.getPlayer2().getDisplayName()).isEqualTo("Friend");
        assertThat(room.getPlayer2().getQuestions()).hasSize(20);
        assertThat(service.isPendingChallenger("friend")).isFalse();
    }

    @Test
    void declinedChallengeKeepsTheRoomOpen() {
        GameSession room = service.createPrivateGame(session("host"), "Host");
        service.requestChallenge(session("friend"), "Friend", room.getRoomCode(), null);

        assertThat(service.declineChallenge("host").getDisplayName()).isEqualTo("Friend");
        assertThat(room.getPendingChallenger()).isNull();
        assertThat(service.requestChallenge(session("friend"), "Friend", room.getRoomCode(), null).result())
                .as("the same friend may try again").isEqualTo(GameService.ChallengeResult.PENDING);
    }

    @Test
    void onlyOneChallengerAtATime() {
        GameSession room = service.createPrivateGame(session("host"), "Host");
        service.requestChallenge(session("first"), "First", room.getRoomCode(), null);

        assertThat(service.requestChallenge(session("second"), "Second", room.getRoomCode(), null).result())
                .isEqualTo(GameService.ChallengeResult.BUSY);

        assertThat(service.cancelChallenge("first")).isSameAs(room);
        assertThat(service.requestChallenge(session("second"), "Second", room.getRoomCode(), null).result())
                .isEqualTo(GameService.ChallengeResult.PENDING);
    }

    @Test
    void roomCodesWorkOnceAndDieWithTheHost() {
        GameSession room = service.createPrivateGame(session("host"), null);
        service.requestChallenge(session("friend"), null, room.getRoomCode(), null);
        service.acceptChallenge("host");
        assertThat(service.requestChallenge(session("late"), null, room.getRoomCode(), null).result())
                .isEqualTo(GameService.ChallengeResult.NOT_FOUND);

        GameSession abandoned = service.createPrivateGame(session("host2"), null);
        service.requestChallenge(session("waiting"), null, abandoned.getRoomCode(), null);
        service.removePlayer("host2");
        assertThat(service.isPendingChallenger("waiting")).as("waiting challenger let go").isFalse();
        assertThat(service.requestChallenge(session("friend2"), null, abandoned.getRoomCode(), null).result())
                .isEqualTo(GameService.ChallengeResult.NOT_FOUND);
        assertThat(service.requestChallenge(session("friend3"), null, "XXXXXX", null).result())
                .isEqualTo(GameService.ChallengeResult.NOT_FOUND);
    }

    @Test
    void nicknamesAreKeptOrDefaulted() {
        service.joinGame(session("a"), "  Yuko  ");
        GameSession game = service.joinGame(session("b"), "");

        assertThat(game.getPlayer1().getDisplayName()).isEqualTo("Yuko");
        assertThat(game.getPlayer2().getDisplayName()).isEqualTo("Player 2");
    }

    @Test
    void publicChallengeTypeCanBePinned() {
        GameConfiguration config = new GameConfiguration();
        config.setPublicChallengeType("compare");
        GameService pinned = new GameService(new QuestionGeneratorService(config), config);

        pinned.joinGame(session("a"));
        GameSession game = pinned.joinGame(session("b"));

        assertThat(game.getChallengeType()).isEqualTo(com.warduel.warduel.model.ChallengeType.COMPARE);
        assertThat(game.getPlayer1().getQuestions()).allSatisfy(q ->
                assertThat(q.getFormat()).isEqualTo(com.warduel.warduel.model.Question.AnswerFormat.CHOICE));
    }

    @Test
    void findsTheOpponent() {
        GameSession game = join("a", "b");

        assertThat(service.getOpponent(game, "a")).isSameAs(game.getPlayer2());
        assertThat(service.getOpponent(game, "b")).isSameAs(game.getPlayer1());
        assertThat(service.getOpponent(game, "x")).isNull();
    }

    private GameSession join(String id1, String id2) {
        service.joinGame(session(id1));
        return service.joinGame(session(id2));
    }

    private static WebSocketSession session(String id) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        return session;
    }
}
