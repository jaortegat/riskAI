package com.risk.service;

import com.risk.model.Game;
import com.risk.model.GamePhase;
import com.risk.model.Player;
import com.risk.model.Territory;
import com.risk.repository.GameRepository;
import com.risk.repository.TerritoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service responsible for fortification (army movement) between territories.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class FortificationService {

    private final GameRepository gameRepository;
    private final TerritoryRepository territoryRepository;
    private final GameQueryService gameQueryService;
    private final TurnManagementService turnManagementService;

    /**
     * Fortify - move armies between owned adjacent territories, then end turn.
     */
    public Game fortify(String gameId, String playerId, String fromKey, String toKey, int armies) {
        Game game = gameQueryService.getGame(gameId);
        gameQueryService.validateCurrentPlayer(game, playerId);

        if (game.getCurrentPhase() != GamePhase.FORTIFY) {
            throw new IllegalStateException("Not in fortify phase");
        }

        Territory from = territoryRepository.findByGameIdAndTerritoryKey(gameId, fromKey)
                .orElseThrow(() -> new IllegalArgumentException("Source territory not found"));
        Territory to = territoryRepository.findByGameIdAndTerritoryKey(gameId, toKey)
                .orElseThrow(() -> new IllegalArgumentException("Target territory not found"));

        Player currentPlayer = game.getCurrentPlayer();

        if (!from.isOwnedBy(currentPlayer) || !to.isOwnedBy(currentPlayer)) {
            throw new IllegalArgumentException("You must own both territories");
        }

        if (!from.isNeighborOf(toKey)) {
            throw new IllegalArgumentException("Territories must be adjacent for fortification");
        }

        if (armies >= from.getArmies()) {
            throw new IllegalArgumentException("Must leave at least 1 army behind");
        }

        from.setArmies(from.getArmies() - armies);
        to.setArmies(to.getArmies() + armies);

        territoryRepository.save(from);
        territoryRepository.save(to);

        turnManagementService.endTurn(game);
        return game;
    }

    /**
     * Skip fortification and end turn.
     */
    public Game skipFortify(String gameId, String playerId) {
        Game game = gameQueryService.getGame(gameId);
        gameQueryService.validateCurrentPlayer(game, playerId);

        if (game.getCurrentPhase() != GamePhase.FORTIFY) {
            throw new IllegalStateException("Not in fortify phase");
        }

        turnManagementService.endTurn(game);
        return gameRepository.save(game);
    }
}
