package com.riskai.service;

import com.riskai.model.Continent;
import com.riskai.model.Game;
import com.riskai.model.GamePhase;
import com.riskai.model.Player;
import com.riskai.model.Territory;
import com.riskai.repository.ContinentRepository;
import com.riskai.repository.GameRepository;
import com.riskai.repository.TerritoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service responsible for reinforcement calculation and placement.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ReinforcementService {

    private final GameRepository gameRepository;
    private final TerritoryRepository territoryRepository;
    private final ContinentRepository continentRepository;
    private final GameQueryService gameQueryService;

    /**
     * Calculate reinforcements for a player.
     */
    public int calculateReinforcements(Player player) {
        if (player == null) return 0;

        // Base reinforcements: territories / 3, minimum 3
        int territories = player.getTerritoryCount();
        int reinforcements = Math.max(3, territories / 3);

        // Continent bonuses
        List<Continent> continents = continentRepository.findByGameIdWithTerritories(player.getGame().getId());
        for (Continent continent : continents) {
            if (continent.isControlledBy(player)) {
                reinforcements += continent.getBonusArmies();
            }
        }

        return reinforcements;
    }

    /**
     * Place reinforcements on a territory.
     */
    public Territory placeArmies(String gameId, String playerId, String territoryKey, int armies) {
        Game game = gameQueryService.getGame(gameId);
        gameQueryService.validateCurrentPlayer(game, playerId);

        if (game.getCurrentPhase() != GamePhase.REINFORCEMENT) {
            throw new IllegalStateException("Not in reinforcement phase");
        }

        if (armies > game.getReinforcementsRemaining()) {
            throw new IllegalArgumentException("Not enough reinforcements available");
        }

        Territory territory = territoryRepository.findByGameIdAndTerritoryKey(gameId, territoryKey)
                .orElseThrow(() -> new IllegalArgumentException("Territory not found"));

        if (!territory.isOwnedBy(game.getCurrentPlayer())) {
            throw new IllegalArgumentException("You don't own this territory");
        }

        territory.setArmies(territory.getArmies() + armies);
        territoryRepository.save(territory);

        game.setReinforcementsRemaining(game.getReinforcementsRemaining() - armies);

        if (game.getReinforcementsRemaining() == 0) {
            game.setCurrentPhase(GamePhase.ATTACK);
        }

        gameRepository.save(game);
        return territory;
    }
}

