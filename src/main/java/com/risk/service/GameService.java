package com.risk.service;

import com.risk.dto.*;
import com.risk.model.*;
import com.risk.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Core game service handling game lifecycle and operations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class GameService {

    private final GameRepository gameRepository;
    private final PlayerRepository playerRepository;
    private final TerritoryRepository territoryRepository;
    private final ContinentRepository continentRepository;
    private final MapService mapService;

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
        Game game = getGame(gameId);

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
        Game game = getGame(gameId);

        if (game.isFull()) {
            throw new IllegalStateException("Game is full");
        }

        int cpuCount = (int) game.getPlayers().stream().filter(Player::isCPU).count();
        return addPlayer(game, "CPU Player " + (cpuCount + 1), PlayerType.CPU, null, difficulty);
    }

    private Player addPlayer(Game game, String name, PlayerType type, String sessionId, CPUDifficulty difficulty) {
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

    private PlayerColor getAvailableColor(Game game) {
        Set<PlayerColor> usedColors = new HashSet<>();
        game.getPlayers().forEach(p -> usedColors.add(p.getColor()));

        for (PlayerColor color : PlayerColor.values()) {
            if (!usedColors.contains(color)) {
                return color;
            }
        }
        throw new IllegalStateException("No colors available");
    }

    /**
     * Start the game.
     */
    public Game startGame(String gameId) {
        Game game = getGame(gameId);

        if (!game.canStart()) {
            throw new IllegalStateException("Game cannot start - need at least " + game.getMinPlayers() + " players");
        }

        game.setStatus(GameStatus.IN_PROGRESS);
        game.setStartedAt(LocalDateTime.now());

        // Distribute territories randomly
        distributeTerritories(game);

        // Set initial phase
        game.setCurrentPhase(GamePhase.REINFORCEMENT);
        game.setReinforcementsRemaining(calculateReinforcements(game.getCurrentPlayer()));

        log.info("Game {} started with {} players", game.getName(), game.getPlayers().size());
        return gameRepository.save(game);
    }

    private void distributeTerritories(Game game) {
        List<Territory> territories = new ArrayList<>(game.getTerritories());
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

            Random random = new Random();
            for (int i = 0; i < remaining; i++) {
                Territory t = playerTerritories.get(random.nextInt(playerTerritories.size()));
                t.setArmies(t.getArmies() + 1);
                territoryRepository.save(t);
            }
        }
    }

    private int getInitialArmiesPerPlayer(int playerCount) {
        return switch (playerCount) {
            case 2 -> 40;
            case 3 -> 35;
            case 4 -> 30;
            case 5 -> 25;
            case 6 -> 20;
            default -> 30;
        };
    }

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
        Game game = getGame(gameId);
        validateCurrentPlayer(game, playerId);

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

    /**
     * Execute an attack.
     */
    public AttackResult attack(String gameId, String playerId, String fromKey, String toKey, int attackingArmies) {
        Game game = getGame(gameId);
        validateCurrentPlayer(game, playerId);

        if (game.getCurrentPhase() != GamePhase.ATTACK) {
            throw new IllegalStateException("Not in attack phase");
        }

        Territory from = territoryRepository.findByGameIdAndTerritoryKey(gameId, fromKey)
                .orElseThrow(() -> new IllegalArgumentException("Source territory not found"));
        Territory to = territoryRepository.findByGameIdAndTerritoryKey(gameId, toKey)
                .orElseThrow(() -> new IllegalArgumentException("Target territory not found"));

        if (!from.isOwnedBy(game.getCurrentPlayer())) {
            throw new IllegalArgumentException("You don't own the attacking territory");
        }

        if (to.isOwnedBy(game.getCurrentPlayer())) {
            throw new IllegalArgumentException("Cannot attack your own territory");
        }

        if (!from.isNeighborOf(toKey)) {
            throw new IllegalArgumentException("Territories are not adjacent");
        }

        if (attackingArmies < 1 || attackingArmies > 3 || attackingArmies >= from.getArmies()) {
            throw new IllegalArgumentException("Invalid number of attacking armies");
        }

        return executeAttack(game, from, to, attackingArmies);
    }

    private AttackResult executeAttack(Game game, Territory from, Territory to, int attackingArmies) {
        int defendingArmies = Math.min(2, to.getArmies());

        int[] attackDice = rollDice(attackingArmies);
        int[] defendDice = rollDice(defendingArmies);

        Arrays.sort(attackDice);
        Arrays.sort(defendDice);
        reverseArray(attackDice);
        reverseArray(defendDice);

        int attackerLosses = 0;
        int defenderLosses = 0;

        int comparisons = Math.min(attackDice.length, defendDice.length);
        for (int i = 0; i < comparisons; i++) {
            if (attackDice[i] > defendDice[i]) {
                defenderLosses++;
            } else {
                attackerLosses++;
            }
        }

        from.setArmies(from.getArmies() - attackerLosses);
        to.setArmies(to.getArmies() - defenderLosses);

        boolean conquered = to.getArmies() <= 0;
        Player eliminatedPlayer = null;

        if (conquered) {
            Player previousOwner = to.getOwner();
            to.setOwner(from.getOwner());
            to.setArmies(attackingArmies);
            from.setArmies(from.getArmies() - attackingArmies);

            // Check if player was eliminated
            if (territoryRepository.findByOwnerId(previousOwner.getId()).isEmpty()) {
                previousOwner.eliminate();
                playerRepository.save(previousOwner);
                eliminatedPlayer = previousOwner;
            }

            // Check for game over
            checkGameOver(game);
        }

        territoryRepository.save(from);
        territoryRepository.save(to);
        gameRepository.save(game);

        return AttackResult.builder()
                .attackerDice(attackDice)
                .defenderDice(defendDice)
                .attackerLosses(attackerLosses)
                .defenderLosses(defenderLosses)
                .conquered(conquered)
                .eliminatedPlayer(eliminatedPlayer != null ? eliminatedPlayer.getName() : null)
                .build();
    }

    private int[] rollDice(int count) {
        Random random = new Random();
        int[] dice = new int[count];
        for (int i = 0; i < count; i++) {
            dice[i] = random.nextInt(6) + 1;
        }
        return dice;
    }

    private void reverseArray(int[] arr) {
        for (int i = 0; i < arr.length / 2; i++) {
            int temp = arr[i];
            arr[i] = arr[arr.length - 1 - i];
            arr[arr.length - 1 - i] = temp;
        }
    }

    /**
     * End the attack phase.
     */
    public Game endAttackPhase(String gameId, String playerId) {
        Game game = getGame(gameId);
        validateCurrentPlayer(game, playerId);

        if (game.getCurrentPhase() != GamePhase.ATTACK) {
            throw new IllegalStateException("Not in attack phase");
        }

        game.setCurrentPhase(GamePhase.FORTIFY);
        return gameRepository.save(game);
    }

    /**
     * Fortify - move armies between territories.
     */
    public void fortify(String gameId, String playerId, String fromKey, String toKey, int armies) {
        Game game = getGame(gameId);
        validateCurrentPlayer(game, playerId);

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

        endTurn(game);
    }

    /**
     * Skip fortification and end turn.
     */
    public Game skipFortify(String gameId, String playerId) {
        Game game = getGame(gameId);
        validateCurrentPlayer(game, playerId);

        if (game.getCurrentPhase() != GamePhase.FORTIFY) {
            throw new IllegalStateException("Not in fortify phase");
        }

        endTurn(game);
        return gameRepository.save(game);
    }

    private void endTurn(Game game) {
        // Move to next active player
        do {
            game.nextPlayer();
        } while (game.getCurrentPlayer().isEliminated());

        // Check turn limit before starting next turn
        if (checkTurnLimit(game)) {
            return;
        }

        game.setCurrentPhase(GamePhase.REINFORCEMENT);
        game.setReinforcementsRemaining(calculateReinforcements(game.getCurrentPlayer()));
        gameRepository.save(game);
    }

    private void checkGameOver(Game game) {
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

    private void finishGame(Game game, Player winner) {
        game.setStatus(GameStatus.FINISHED);
        game.setCurrentPhase(GamePhase.GAME_OVER);
        game.setWinnerId(winner.getId());
        game.setEndedAt(LocalDateTime.now());
        gameRepository.save(game);
        log.info("Game {} won by {} (mode: {})", game.getName(), winner.getName(), game.getGameMode());
    }

    private void validateCurrentPlayer(Game game, String playerId) {
        Player currentPlayer = game.getCurrentPlayer();
        if (currentPlayer == null || !currentPlayer.getId().equals(playerId)) {
            throw new IllegalStateException("It's not your turn");
        }
    }

    /**
     * Get game by ID.
     */
    @Transactional(readOnly = true)
    public Game getGame(String gameId) {
        return gameRepository.findById(gameId)
                .orElseThrow(() -> new IllegalArgumentException("Game not found: " + gameId));
    }

    /**
     * Get full game state.
     */
    @Transactional(readOnly = true)
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
    @Transactional(readOnly = true)
    public List<Game> getJoinableGames() {
        return gameRepository.findJoinableGames();
    }

    /**
     * Get all games.
     */
    @Transactional(readOnly = true)
    public List<Game> getAllGames() {
        return gameRepository.findAll();
    }

    /**
     * Attack result data class.
     */
    @lombok.Data
    @lombok.Builder
    public static class AttackResult {
        private int[] attackerDice;
        private int[] defenderDice;
        private int attackerLosses;
        private int defenderLosses;
        private boolean conquered;
        private String eliminatedPlayer;
    }
}
