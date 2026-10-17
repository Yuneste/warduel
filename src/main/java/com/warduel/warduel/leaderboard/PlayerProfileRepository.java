package com.warduel.warduel.leaderboard;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PlayerProfileRepository extends JpaRepository<PlayerProfile, Long> {

    Optional<PlayerProfile> findByTokenHash(String tokenHash);

    /** Ranking: most wins first, then fewer losses, then whoever got there first */
    @Query("select p from PlayerProfile p where p.wins > 0 order by p.wins desc, p.losses asc, p.updatedAt asc")
    List<PlayerProfile> findRanking(Pageable pageable);

    /** How many players rank above someone with these numbers (same ordering as findRanking) */
    @Query("""
            select count(p) from PlayerProfile p
            where p.wins > :wins
               or (p.wins = :wins and p.losses < :losses)
               or (p.wins = :wins and p.losses = :losses and p.updatedAt < :updatedAt)
            """)
    long countRankedAbove(@Param("wins") int wins, @Param("losses") int losses,
                          @Param("updatedAt") java.time.Instant updatedAt);
}
