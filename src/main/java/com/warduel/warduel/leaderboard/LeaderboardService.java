package com.warduel.warduel.leaderboard;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Wins, losses and draws of public games, keyed by a hashed device token.
 */
@Service
@Slf4j
public class LeaderboardService {

    public static final int TOP = 10;

    // Browsers send crypto.randomUUID(); accept similar random strings, nothing else
    private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9-]{32,64}");

    public enum Outcome { WIN, LOSS, DRAW }

    public record Entry(int rank, String name, int wins, int losses, int draws, int games) {
        static Entry of(int rank, PlayerProfile p) {
            return new Entry(rank, p.getDisplayName(), p.getWins(), p.getLosses(), p.getDraws(), p.getGames());
        }
    }

    public record Board(List<Entry> top, Entry you) {
    }

    private final PlayerProfileRepository repository;
    private final Clock clock;

    @Autowired
    public LeaderboardService(PlayerProfileRepository repository) {
        this(repository, Clock.systemUTC());
    }

    LeaderboardService(PlayerProfileRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * Adds one result for the player with this token. Players without a valid token are ignored.
     */
    @Transactional
    public void record(String token, String displayName, Outcome outcome) {
        String hash = hash(token);
        if(hash == null) {
            return;
        }
        Instant now = clock.instant();
        PlayerProfile profile = repository.findByTokenHash(hash)
                .orElseGet(() -> new PlayerProfile(hash, displayName, now));
        profile.setDisplayName(displayName);
        switch(outcome) {
            case WIN -> profile.setWins(profile.getWins() + 1);
            case LOSS -> profile.setLosses(profile.getLosses() + 1);
            case DRAW -> profile.setDraws(profile.getDraws() + 1);
        }
        profile.setUpdatedAt(now);
        repository.save(profile);
    }

    /**
     * The top players, plus the asking player's own line (null if they haven't played a public game)
     */
    @Transactional(readOnly = true)
    public Board board(String token) {
        List<PlayerProfile> ranking = repository.findRanking(PageRequest.of(0, TOP));
        List<Entry> top = new java.util.ArrayList<>();
        for(int i = 0; i < ranking.size(); i++) {
            top.add(Entry.of(i + 1, ranking.get(i)));
        }

        String hash = hash(token);
        Entry you = hash == null ? null : repository.findByTokenHash(hash)
                .map(p -> Entry.of((int) repository.countRankedAbove(p.getWins(), p.getLosses(), p.getUpdatedAt()) + 1, p))
                .orElse(null);
        return new Board(top, you);
    }

    static String hash(String token) {
        if(token == null || !TOKEN.matcher(token).matches()) {
            return null;
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch(NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
