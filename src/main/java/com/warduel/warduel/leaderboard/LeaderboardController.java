package com.warduel.warduel.leaderboard;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /api/leaderboard - the top players; with an X-Player-Token header also the caller's own line.
 * The token travels in a header rather than the URL so it doesn't end up in access logs.
 */
@RestController
public class LeaderboardController {

    private final LeaderboardService leaderboard;

    public LeaderboardController(LeaderboardService leaderboard) {
        this.leaderboard = leaderboard;
    }

    @GetMapping("/api/leaderboard")
    public ResponseEntity<LeaderboardService.Board> board(
            @RequestHeader(value = "X-Player-Token", required = false) String token) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(leaderboard.board(token));
    }
}
