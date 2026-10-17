package com.warduel.warduel.leaderboard;

import com.warduel.warduel.leaderboard.LeaderboardService.Board;
import com.warduel.warduel.leaderboard.LeaderboardService.Outcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class LeaderboardServiceTest {

    @Autowired
    private PlayerProfileRepository repository;

    private MutableClock clock;
    private LeaderboardService leaderboard;

    @BeforeEach
    void setUp() {
        clock = new MutableClock();
        leaderboard = new LeaderboardService(repository, clock);
    }

    @Test
    void recordsResultsPerDeviceTokenAndKeepsTheLatestName() {
        String token = token();
        leaderboard.record(token, "Yuko", Outcome.WIN);
        leaderboard.record(token, "Yuko", Outcome.LOSS);
        leaderboard.record(token, "Yuko K.", Outcome.WIN);
        leaderboard.record(token, "Yuko K.", Outcome.DRAW);

        LeaderboardService.Entry you = leaderboard.board(token).you();
        assertThat(you.name()).isEqualTo("Yuko K.");
        assertThat(you.wins()).isEqualTo(2);
        assertThat(you.losses()).isEqualTo(1);
        assertThat(you.draws()).isEqualTo(1);
        assertThat(you.games()).isEqualTo(4);
    }

    @Test
    void storesOnlyAHashOfTheToken() {
        String token = token();
        leaderboard.record(token, "Kai", Outcome.WIN);

        PlayerProfile stored = repository.findAll().get(0);
        assertThat(stored.getTokenHash()).hasSize(64).doesNotContain(token);
        assertThat(stored.getTokenHash()).isEqualTo(LeaderboardService.hash(token));
    }

    @Test
    void ranksByWinsThenFewerLossesThenWhoGotThereFirst() {
        String ada = token(), bob = token(), cy = token(), dee = token();
        win(ada, "Ada", 3);
        win(bob, "Bob", 5);
        win(cy, "Cy", 3);
        leaderboard.record(cy, "Cy", Outcome.LOSS);
        win(dee, "Dee", 3); // same as Ada but later
        leaderboard.record(token(), "Nobody", Outcome.LOSS); // no wins: not on the board

        Board board = leaderboard.board(dee);
        assertThat(board.top()).extracting(LeaderboardService.Entry::name).containsExactly("Bob", "Ada", "Dee", "Cy");
        assertThat(board.top()).extracting(LeaderboardService.Entry::rank).containsExactly(1, 2, 3, 4);
        assertThat(board.you().rank()).isEqualTo(3);
    }

    @Test
    void showsOnlyTheTopTen() {
        for(int i = 0; i < 12; i++) {
            win(token(), "P" + i, i + 1);
        }
        assertThat(leaderboard.board(null).top()).hasSize(LeaderboardService.TOP);
    }

    @Test
    void ignoresMissingOrMalformedTokens() {
        leaderboard.record(null, "Anon", Outcome.WIN);
        leaderboard.record("short", "Anon", Outcome.WIN);
        leaderboard.record("x".repeat(40) + "<script>", "Anon", Outcome.WIN);

        assertThat(repository.count()).isZero();
        assertThat(leaderboard.board("not-a-token").you()).isNull();
    }

    private void win(String token, String name, int times) {
        for(int i = 0; i < times; i++) {
            leaderboard.record(token, name, Outcome.WIN);
            clock.tick();
        }
    }

    private static String token() {
        return UUID.randomUUID().toString();
    }

    /** A clock that moves forward one second per tick, so "who got there first" is deterministic */
    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");

        void tick() {
            now = now.plusSeconds(1);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
