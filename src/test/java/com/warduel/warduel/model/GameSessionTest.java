package com.warduel.warduel.model;

import com.warduel.warduel.model.GameSession.GameStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class GameSessionTest {

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private GameSession game;
    private Player p1;
    private Player p2;

    @BeforeEach
    void setUp() {
        game = new GameSession();
        p1 = player("a");
        p2 = player("b");
    }

    @AfterEach
    void tearDown() {
        scheduler.shutdownNow();
    }

    @Test
    void startsWaitingAndBecomesReadyWhenSecondPlayerJoins() {
        assertThat(game.getStatus()).isEqualTo(GameStatus.WAITING);

        assertThat(game.addPlayer(p1)).isTrue();
        assertThat(game.getStatus()).isEqualTo(GameStatus.WAITING);
        assertThat(game.addPlayer(p2)).isTrue();

        assertThat(game.getStatus()).isEqualTo(GameStatus.READY);
        assertThat(game.isFull()).isTrue();
        assertThat(p1.getDisplayName()).isEqualTo("Player 1");
        assertThat(p2.getDisplayName()).isEqualTo("Player 2");
        assertThat(game.addPlayer(player("c"))).isFalse();
    }

    @Test
    void clockStartsOnlyWhenTheGameStarts() {
        fill();
        assertThat(game.getEndTime()).isNull();
        assertThat(game.getRemainingSeconds()).isEqualTo(game.getDurationSeconds());

        assertThat(game.startGame()).isTrue();

        assertThat(game.getStatus()).isEqualTo(GameStatus.RUNNING);
        assertThat(game.getRound()).isEqualTo(1);
        assertThat(game.getRemainingSeconds()).isBetween((long) game.getDurationSeconds() - 1, (long) game.getDurationSeconds());
        assertThat(game.isTimeUp()).isFalse();
    }

    @Test
    void canOnlyStartFromReady() {
        assertThat(game.startGame()).as("waiting game").isFalse();
        fill();
        game.startGame();
        assertThat(game.startGame()).as("already running").isFalse();
        assertThat(game.getRound()).isEqualTo(1);
    }

    @Test
    void startResetsScoresAndProgress() {
        fill();
        p1.incrementScore();
        p1.nextQuestion();
        p1.markQuestionAnswered(0);

        game.startGame();

        assertThat(p1.getScore()).isZero();
        assertThat(p1.getCurrentQuestionIndex()).isZero();
        assertThat(p1.hasAnsweredQuestion(0)).isFalse();
    }

    @Test
    void endGameTakesEffectOnlyOnce() {
        fill();
        game.startGame();

        assertThat(game.endGame()).isTrue();
        assertThat(game.getStatus()).isEqualTo(GameStatus.FINISHED);
        assertThat(game.endGame()).isFalse();
    }

    @Test
    void endGameCancelsTheRoundsScheduledTasks() {
        fill();
        game.startGame();
        ScheduledFuture<?> timer = scheduler.schedule(() -> { }, 1, TimeUnit.HOURS);
        ScheduledFuture<?> monitor = scheduler.scheduleAtFixedRate(() -> { }, 1, 1, TimeUnit.HOURS);
        game.addScheduledTask(timer);
        game.addScheduledTask(monitor);

        game.endGame();

        assertThat(List.of(timer, monitor)).allSatisfy(task -> assertThat(task.isCancelled()).isTrue());
        assertThat(game.getScheduledTasks()).isEmpty();
    }

    @Test
    void rematchResetsAFinishedGameForAnotherRound() {
        fill();
        game.startGame();
        p1.incrementScore();
        game.setPlayer1Rematch(true);
        game.setPlayer2Rematch(true);
        assertThat(game.resetForRematch()).as("not finished yet").isFalse();

        game.endGame();
        assertThat(game.bothWantRematch()).isTrue();
        assertThat(game.resetForRematch()).isTrue();

        assertThat(game.getStatus()).isEqualTo(GameStatus.READY);
        assertThat(p1.getScore()).isZero();
        assertThat(game.bothWantRematch()).isFalse();
        assertThat(game.getEndTime()).isNull();

        game.startGame();
        assertThat(game.getRound()).isEqualTo(2);
    }

    @Test
    void determinesWinnerOrDraw() {
        fill();
        assertThat(game.determineWinner()).isEqualTo("Draw");
        assertThat(game.isDraw()).isTrue();

        p2.incrementScore();
        assertThat(game.determineWinner()).isEqualTo("Player 2");
        assertThat(game.isDraw()).isFalse();

        p1.incrementScore();
        p1.incrementScore();
        assertThat(game.determineWinner()).isEqualTo("Player 1");
    }

    @Test
    void removingPlayersEmptiesTheirSlot() {
        fill();
        assertThat(game.removePlayer("a")).isTrue();
        assertThat(game.getPlayer1()).isNull();
        assertThat(game.isFull()).isFalse();
        assertThat(game.removePlayer("a")).isFalse();
        assertThat(game.determineWinner()).isEqualTo("Unknown");
    }

    private void fill() {
        game.addPlayer(p1);
        game.addPlayer(p2);
    }

    private static Player player(String id) {
        Player player = new Player(id, null, "");
        player.setQuestions(List.of(new Question("1 + 1", 2, Question.OperationType.ADD)));
        return player;
    }
}
