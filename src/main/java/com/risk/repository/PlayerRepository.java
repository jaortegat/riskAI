package com.risk.repository;

import com.risk.model.Player;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Player entities.
 */
@Repository
public interface PlayerRepository extends JpaRepository<Player, String> {

    List<Player> findByGameId(String gameId);

    Optional<Player> findByGameIdAndSessionId(String gameId, String sessionId);

    @Query("SELECT p FROM Player p WHERE p.game.id = :gameId AND p.eliminated = false ORDER BY p.turnOrder")
    List<Player> findActivePlayersByGameId(String gameId);

    @Query("SELECT p FROM Player p LEFT JOIN FETCH p.territories WHERE p.id = :playerId")
    Optional<Player> findByIdWithTerritories(String playerId);

    boolean existsByGameIdAndName(String gameId, String name);
}
