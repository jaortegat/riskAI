package com.risk.service;

import com.risk.dto.CreateGameRequest;
import com.risk.dto.JoinGameRequest;
import com.risk.model.CPUDifficulty;
import com.risk.model.Game;
import com.risk.model.GamePhase;
import com.risk.model.GameStatus;
import com.risk.model.Player;
import com.risk.model.PlayerColor;
import com.risk.model.PlayerType;
import com.risk.model.Territory;
import com.risk.repository.GameRepository;
import com.risk.repository.PlayerRepository;
import com.risk.repository.TerritoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Service responsible for game lifecycle: creation, joining, starting, and setup.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class GameLifecycleService {

    private final GameRepository gameRepository;
    private final PlayerRepository playerRepository;
    private final TerritoryRepository territoryRepository;
    private final MapService mapService;
    private final GameQueryService gameQueryService;
    private final ReinforcementService reinforcementService;

    /**
     * Create a new game.
     */
    public Game createGame(CreateGameRequest request, String sessionId) {
        log.info("Creating new game: {}", request.getGameName());

        Game game = Game.builder()
                .name(request.getGameName())
                .mapId(request.getMapId())
                .status(GameStatus.WAITING_FOR_PLAYERS)
                .currentPhase(GamePhase.SETUP)
                .currentPlayerIndex(0)
                .turnNumber(1)
                .reinforcementsRemaining(0)
                .maxPlayers(request.getMaxPlayers())
                .minPlayers(request.getMinPlayers())
                .gameMode(request.getGameMode())
                .dominationPercent(request.getDominationPercent())
                .turnLimit(request.getTurnLimit())
                .build();

        game = gameRepository.save(game);

        // Initialize map
        mapService.initializeMap(game);

        // Add the creating player (if a name was provided)
        boolean hasHuman = request.getPlayerName() != null && !request.getPlayerName().isBlank();
        if (hasHuman) {
            addPlayer(game, request.getPlayerName(), PlayerType.HUMAN, sessionId, null);
        }

        // Add CPU players if requested (capped to max players)
        int maxCpu = hasHuman ? request.getMaxPlayers() - 1 : request.getMaxPlayers();
        int cpuCount = Math.min(request.getCpuPlayerCount(), maxCpu);
        for (int i = 0; i < cpuCount; i++) {
            addPlayer(game, "CPU Player " + (i + 1), PlayerType.CPU, null, request.getCpuDifficulty());
        }

        return gameRepository.save(game);
    }

    /**
     * Join an existing game.
     */
    public Player joinGame(String gameId, JoinGameRequest request, String sessionId) {
        Game game = gameQueryService.getGame(gameId);

        if (game.getStatus() != GameStatus.WAITING_FOR_PLAYERS) {
            throw new IllegalStateException("Game is not accepting new players");
        }

        if (game.isFull()) {
            throw new IllegalStateException("Game is full");
        }

        if (playerRepository.existsByGameIdAndName(gameId, request.getPlayerName())) {
            throw new IllegalArgumentException("Player name already taken");
        }

        return addPlayer(game, request.getPlayerName(), PlayerType.HUMAN, sessionId, null);
    }

    /**
     * Add a CPU player to the game.
     */
    public Player addCPUPlayer(String gameId, CPUDifficulty difficulty) {
        Game game = gameQueryService.getGame(gameId);

        if (game.isFull()) {
            throw new IllegalStateException("Game is full");
        }

        int cpuCount = (int) game.getPlayers().stream().filter(Player::isCPU).count();
        return addPlayer(game, "CPU Player " + (cpuCount + 1), PlayerType.CPU, null, difficulty);
    }

    /**
     * Start the game.
     */
    public Game startGame(String gameId) {
        Game game = gameQueryService.getGame(gameId);

        if (!game.canStart()) {
            throw new IllegalStateException("Game cannot start - need at least " + game.getMinPlayers() + " players");
        }

        game.setStatus(GameStatus.IN_PROGRESS);
        game.setStartedAt(LocalDateTime.now());

        // Distribute territories randomly
        distributeTerritories(game);

        // Set initial phase
        game.setCurrentPhase(GamePhase.REINFORCEMENT);
        game.setReinforcementsRemaining(reinforcementService.calculateReinforcements(game.getCurrentPlayer()));

        log.info("Game {} started with {} players", game.getName(), game.getPlayers().size());
        return gameRepository.save(game);
    }

    Player addPlayer(Game game, String name, PlayerType type, String sessionId, CPUDifficulty difficulty) {
        int turnOrder = game.getPlayers().size();
        PlayerColor color = getAvailableColor(game);

        Player player = Player.builder()
                .name(name)
                .color(color)
                .type(type)
                .cpuDifficulty(difficulty)
                .game(game)
                .turnOrder(turnOrder)
                .sessionId(sessionId)
                .lastActiveAt(LocalDateTime.now())
                .build();

        player = playerRepository.save(player);
        game.getPlayers().add(player);

        log.info("Player {} joined game {}", name, game.getName());
        return player;
    }

    PlayerColor getAvailableColor(Game game) {
        Set<PlayerColor> usedColors = new HashSet<>();
        game.getPlayers().forEach(p -> usedColors.add(p.getColor()));

        for (PlayerColor color : PlayerColor.values()) {
            if (!usedColors.contains(color)) {
                return color;
            }
        }
        throw new IllegalStateException("No colors available");
    }

    void distributeTerritories(Game game) {
        List<Territory> territories = new ArrayList<>(territoryRepository.findByGameId(game.getId()));
        Collections.shuffle(territories);

        List<Player> players = game.getPlayers();
        int initialArmies = getInitialArmiesPerPlayer(players.size());

        int playerIndex = 0;
        for (Territory territory : territories) {
            Player player = players.get(playerIndex);
            territory.setOwner(player);
            territory.setArmies(1);
            territoryRepository.save(territory);
            playerIndex = (playerIndex + 1) % players.size();
        }

        // Distribute remaining armies
        for (Player player : players) {
            List<Territory> playerTerritories = territoryRepository.findByOwnerId(player.getId());
            int armiesPlaced = playerTerritories.size();
            int remaining = initialArmies - armiesPlaced;

            if (remaining <= 0) {
                continue;
            }

            if (playerTerritories.isEmpty()) {
                throw new IllegalStateException("No territories assigned to player " + player.getName());
            }

            Random random = new Random();
            for (int i = 0; i < remaining; i++) {
                Territory t = playerTerritories.get(random.nextInt(playerTerritories.size()));
                t.setArmies(t.getArmies() + 1);
                territoryRepository.save(t);
            }
        }
    }

    int getInitialArmiesPerPlayer(int playerCount) {
        return switch (playerCount) {
            case 2 -> 40;
            case 3 -> 35;
            case 4 -> 30;
            case 5 -> 25;
            case 6 -> 20;
            default -> 30;
        };
    }
}
