package com.risk.service;

import com.risk.model.Game;
import com.risk.model.GameMode;
import com.risk.model.GamePhase;
import com.risk.model.GameStatus;
import com.risk.model.Player;
import com.risk.repository.GameRepository;
import com.risk.repository.PlayerRepository;
import com.risk.repository.TerritoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service responsible for checking win conditions across all game modes.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class WinConditionService {

    private final GameRepository gameRepository;
    private final PlayerRepository playerRepository;
    private final TerritoryRepository territoryRepository;

    /**
     * Check if the game is over after an attack.
     * Handles Classic (last player standing) and Domination (territory percentage) modes.
     */
    public void checkGameOver(Game game) {
        List<Player> activePlayers = playerRepository.findActivePlayersByGameId(game.getId());

        // Classic: last player standing
        if (activePlayers.size() == 1) {
            finishGame(game, activePlayers.get(0));
            return;
        }

        // Domination: check if any player controls enough territories
        if (game.getGameMode() == GameMode.DOMINATION) {
            int totalTerritories = territoryRepository.findByGameId(game.getId()).size();
            int threshold = (int) Math.ceil(totalTerritories * game.getDominationPercent() / 100.0);
            for (Player player : activePlayers) {
                int owned = territoryRepository.findByOwnerId(player.getId()).size();
                if (owned >= threshold) {
                    finishGame(game, player);
                    return;
                }
            }
        }
    }

    /**
     * Check if the turn limit has been reached (called at end of each turn round).
     */
    public boolean checkTurnLimit(Game game) {
        if (game.getGameMode() != GameMode.TURN_LIMIT) return false;
        if (game.getTurnNumber() > game.getTurnLimit()) {
            // Reset turn number to the limit (this turn never actually started)
            game.setTurnNumber(game.getTurnLimit());
            // Find player with most territories
            List<Player> activePlayers = playerRepository.findActivePlayersByGameId(game.getId());
            Player winner = null;
            int maxTerritories = 0;
            for (Player p : activePlayers) {
                int count = territoryRepository.findByOwnerId(p.getId()).size();
                if (count > maxTerritories) {
                    maxTerritories = count;
                    winner = p;
                }
            }
            if (winner != null) {
                finishGame(game, winner);
            }
            return true;
        }
        return false;
    }

    /**
     * Mark a game as finished with the given winner.
     */
    public void finishGame(Game game, Player winner) {
        game.setStatus(GameStatus.FINISHED);
        game.setCurrentPhase(GamePhase.GAME_OVER);
        game.setWinnerId(winner.getId());
        game.setEndedAt(LocalDateTime.now());
        gameRepository.save(game);
        log.info("Game {} won by {} (mode: {})", game.getName(), winner.getName(), game.getGameMode());
    }
}
