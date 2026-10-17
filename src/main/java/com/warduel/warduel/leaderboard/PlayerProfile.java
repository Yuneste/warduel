package com.warduel.warduel.leaderboard;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A player on the leaderboard. Identified by the SHA-256 hash of a random token that only their
 * browser knows; the token itself is never stored.
 */
@Entity
@Table(name = "player_profile", indexes = @Index(name = "idx_profile_wins", columnList = "wins"))
@Getter
@Setter
@NoArgsConstructor
public class PlayerProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(nullable = false, length = 32)
    private String displayName;

    private int wins;
    private int losses;
    private int draws;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    public PlayerProfile(String tokenHash, String displayName, Instant now) {
        this.tokenHash = tokenHash;
        this.displayName = displayName;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public int getGames() {
        return wins + losses + draws;
    }
}
