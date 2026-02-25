package com.riskai.service;

import com.riskai.dto.ContinentDTO;
import com.riskai.dto.GameStateDTO;
import com.riskai.dto.PlayerDTO;
import com.riskai.dto.TerritoryDTO;
import com.riskai.model.Continent;
import com.riskai.model.Game;
import com.riskai.model.Player;
import com.riskai.model.Territory;
import com.riskai.repository.ContinentRepository;
import com.riskai.repository.GameRepository;
import com.riskai.repository.TerritoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service responsible for game state queries and read-only operations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class GameQueryService {

    private final GameRepository gameRepository;
    private final TerritoryRepository territoryRepository;
    private final ContinentRepository continentRepository;

    /**
     * Get game by ID.
     */
    public Game getGame(String gameId) {
        return gameRepository.findById(gameId)
                .orElseThrow(() -> new IllegalArgumentException("Game not found: " + gameId));
    }

    /**
     * Get full game state as DTO.
     */
    public GameStateDTO getGameState(String gameId) {
        Game game = gameRepository.findByIdWithPlayers(gameId);
        if (game == null) {
            throw new IllegalArgumentException("Game not found: " + gameId);
        }
        // Load territories and continents separately to avoid MultipleBagFetchException with Hibernate 7
        List<Territory> territories = territoryRepository.findByGameId(gameId);
        List<Continent> continents = continentRepository.findByGameIdWithTerritories(gameId);

        // Compute player stats from loaded territories (Player.territories may be lazy/empty)
        Map<String, Integer> terrCountByOwner = new HashMap<>();
        Map<String, Integer> armiesByOwner = new HashMap<>();
        for (Territory t : territories) {
            if (t.getOwner() != null) {
                String ownerId = t.getOwner().getId();
                terrCountByOwner.merge(ownerId, 1, Integer::sum);
                armiesByOwner.merge(ownerId, t.getArmies(), Integer::sum);
            }
        }

        GameStateDTO dto = GameStateDTO.fromGame(game);
        dto.setTotalTerritories(territories.size());

        // Fix player stats from territory data
        for (PlayerDTO p : dto.getPlayers()) {
            p.setTerritoryCount(terrCountByOwner.getOrDefault(p.getId(), 0));
            p.setTotalArmies(armiesByOwner.getOrDefault(p.getId(), 0));
        }
        if (dto.getCurrentPlayer() != null) {
            String cpId = dto.getCurrentPlayer().getId();
            dto.getCurrentPlayer().setTerritoryCount(terrCountByOwner.getOrDefault(cpId, 0));
            dto.getCurrentPlayer().setTotalArmies(armiesByOwner.getOrDefault(cpId, 0));
        }

        dto.setTerritories(territories.stream()
                .map(TerritoryDTO::fromTerritory)
                .toList());
        dto.setContinents(continents.stream()
                .map(ContinentDTO::fromContinent)
                .toList());
        return dto;
    }

    /**
     * Get all joinable games.
     */
    public List<Game> getJoinableGames() {
        return gameRepository.findJoinableGames();
    }

    /**
     * Get all games.
     */
    public List<Game> getAllGames() {
        return gameRepository.findAll();
    }

    /**
     * Validate that the given player is the current player.
     */
    public void validateCurrentPlayer(Game game, String playerId) {
        Player currentPlayer = game.getCurrentPlayer();
        if (currentPlayer == null || !currentPlayer.getId().equals(playerId)) {
            throw new IllegalStateException("It's not your turn");
        }
    }
}

