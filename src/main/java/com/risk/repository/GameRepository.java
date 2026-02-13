package com.risk.repository;

import com.risk.model.Game;
import com.risk.model.GameStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for Game entities.
 */
@Repository
public interface GameRepository extends JpaRepository<Game, String> {

    List<Game> findByStatus(GameStatus status);

    List<Game> findByStatusIn(List<GameStatus> statuses);

    @Query("SELECT g FROM Game g WHERE g.status = 'WAITING_FOR_PLAYERS' AND SIZE(g.players) < g.maxPlayers")
    List<Game> findJoinableGames();

    @Query("SELECT g FROM Game g LEFT JOIN FETCH g.players WHERE g.id = :gameId")
    Game findByIdWithPlayers(String gameId);
}
