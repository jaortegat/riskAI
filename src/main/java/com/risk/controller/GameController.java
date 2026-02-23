package com.risk.controller;

import com.risk.config.MapLoader;
import com.risk.dto.AttackResult;
import com.risk.dto.CreateGameRequest;
import com.risk.dto.GameStateDTO;
import com.risk.dto.GameSummaryDTO;
import com.risk.dto.JoinGameRequest;
import com.risk.dto.MapInfoDTO;
import com.risk.dto.PlayerDTO;
import com.risk.dto.TerritoryDTO;
import com.risk.model.CPUDifficulty;
import com.risk.model.Game;
import com.risk.model.GameStatus;
import com.risk.model.Player;
import com.risk.model.Territory;
import com.risk.service.CPUPlayerService;
import com.risk.service.GameService;
import com.risk.websocket.GameWebSocketHandler;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST API controller for game management.
 */
@RestController
@RequestMapping("/api/games")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class GameController {

    private final GameService gameService;
    private final CPUPlayerService cpuPlayerService;
    private final GameWebSocketHandler webSocketHandler;
    private final MapLoader mapLoader;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /**
     * List available maps.
     */
    @GetMapping("/maps")
    public ResponseEntity<List<MapInfoDTO>> getAvailableMaps() {
        List<MapInfoDTO> maps = mapLoader.getAvailableMaps().stream()
                .map(MapInfoDTO::fromDefinition)
                .toList();
        return ResponseEntity.ok(maps);
    }

    /**
     * Create a new game.
     */
    @PostMapping
    public ResponseEntity<GameStateDTO> createGame(@Valid @RequestBody CreateGameRequest request,
                                                    @RequestHeader(value = "X-Session-Id", required = false) String sessionId) {
        log.info("Creating new game: {}", request.getGameName());
        Game game = gameService.createGame(request, sessionId);
        return ResponseEntity.ok(gameService.getGameState(game.getId()));
    }

    /**
     * Get all available games.
     */
    @GetMapping
    public ResponseEntity<List<GameSummaryDTO>> getGames(
            @RequestParam(required = false, defaultValue = "false") boolean joinableOnly) {
        
        List<Game> games = joinableOnly ? 
                gameService.getJoinableGames() : 
                gameService.getAllGames();

        List<GameSummaryDTO> summaries = games.stream()
                .map(this::toGameSummary)
                .toList();

        return ResponseEntity.ok(summaries);
    }

    /**
     * Get game details.
     */
    @GetMapping("/{gameId}")
    public ResponseEntity<GameStateDTO> getGame(@PathVariable String gameId) {
        GameStateDTO gameState = gameService.getGameState(gameId);
        return ResponseEntity.ok(gameState);
    }

    /**
     * Join a game.
     */
    @PostMapping("/{gameId}/join")
    public ResponseEntity<PlayerDTO> joinGame(@PathVariable String gameId,
                                               @Valid @RequestBody JoinGameRequest request,
                                               @RequestHeader(value = "X-Session-Id", required = false) String sessionId) {
        log.info("Player {} joining game {}", request.getPlayerName(), gameId);
        Player player = gameService.joinGame(gameId, request, sessionId);
        
        PlayerDTO playerDTO = PlayerDTO.fromPlayer(player);
        webSocketHandler.broadcastPlayerJoined(gameId, playerDTO);
        webSocketHandler.broadcastGameUpdate(gameId);
        
        return ResponseEntity.ok(playerDTO);
    }

    /**
     * Add a CPU player to the game.
     */
    @PostMapping("/{gameId}/cpu")
    public ResponseEntity<PlayerDTO> addCPUPlayer(@PathVariable String gameId,
                                                  @RequestParam(defaultValue = "MEDIUM") CPUDifficulty difficulty) {
        log.info("Adding CPU player with difficulty {} to game {}", difficulty, gameId);
        Player player = gameService.addCPUPlayer(gameId, difficulty);
        
        PlayerDTO playerDTO = PlayerDTO.fromPlayer(player);
        webSocketHandler.broadcastPlayerJoined(gameId, playerDTO);
        webSocketHandler.broadcastGameUpdate(gameId);
        
        return ResponseEntity.ok(playerDTO);
    }

    /**
     * Start the game.
     */
    @PostMapping("/{gameId}/start")
    public ResponseEntity<GameStateDTO> startGame(@PathVariable String gameId) {
        log.info("Starting game {}", gameId);
        gameService.startGame(gameId);
        
        webSocketHandler.broadcastGameStarted(gameId);
        
        // Check if first player is AI
        cpuPlayerService.checkAndTriggerCPUTurn(gameId);
        
        return ResponseEntity.ok(gameService.getGameState(gameId));
    }

    /**
     * Place reinforcement armies.
     */
    @PostMapping("/{gameId}/reinforce")
    public ResponseEntity<TerritoryDTO> placeArmies(@PathVariable String gameId,
                                                     @RequestParam String playerId,
                                                     @RequestParam String territoryKey,
                                                     @RequestParam int armies) {
        Territory territory = gameService.placeArmies(gameId, playerId, territoryKey, armies);
        webSocketHandler.broadcastGameUpdate(gameId);
        
        // Check if CPU turn should start
        cpuPlayerService.checkAndTriggerCPUTurn(gameId);
        
        return ResponseEntity.ok(TerritoryDTO.fromTerritory(territory));
    }

    /**
     * Execute an attack.
     */
    @PostMapping("/{gameId}/attack")
    public ResponseEntity<Map<String, Object>> attack(@PathVariable String gameId,
                                                       @RequestParam String playerId,
                                                       @RequestParam String fromTerritoryKey,
                                                       @RequestParam String toTerritoryKey,
                                                       @RequestParam int armies) {
        AttackResult result = gameService.attack(gameId, playerId, 
                fromTerritoryKey, toTerritoryKey, armies);
        
        // Broadcast attack result to all players (so other humans see it in their game log)
        webSocketHandler.broadcastAttackResult(gameId, fromTerritoryKey, toTerritoryKey, result);
        webSocketHandler.broadcastGameUpdate(gameId);
        
        Map<String, Object> response = new HashMap<>();
        response.put("attackerDice", result.getAttackerDice());
        response.put("defenderDice", result.getDefenderDice());
        response.put("attackerLosses", result.getAttackerLosses());
        response.put("defenderLosses", result.getDefenderLosses());
        response.put("conquered", result.isConquered());
        response.put("eliminatedPlayer", result.getEliminatedPlayer());
        
        return ResponseEntity.ok(response);
    }

    /**
     * End the attack phase.
     */
    @PostMapping("/{gameId}/endAttack")
    public ResponseEntity<GameStateDTO> endAttack(@PathVariable String gameId,
                                                   @RequestParam String playerId) {
        gameService.endAttackPhase(gameId, playerId);
        webSocketHandler.broadcastGameUpdate(gameId);
        return ResponseEntity.ok(gameService.getGameState(gameId));
    }

    /**
     * Fortify (move armies).
     */
    @PostMapping("/{gameId}/fortify")
    public ResponseEntity<GameStateDTO> fortify(@PathVariable String gameId,
                                                 @RequestParam String playerId,
                                                 @RequestParam String fromTerritoryKey,
                                                 @RequestParam String toTerritoryKey,
                                                 @RequestParam int armies) {
        gameService.fortify(gameId, playerId, fromTerritoryKey, toTerritoryKey, armies);
        GameStateDTO state = gameService.getGameState(gameId);
        webSocketHandler.broadcastGameUpdate(gameId);

        // Check if game ended (e.g. turn-limit reached)
        if (state.getStatus() == GameStatus.FINISHED) {
            webSocketHandler.broadcastGameOver(gameId, state.getWinnerName());
        } else {
            // Check if CPU turn should start
            cpuPlayerService.checkAndTriggerCPUTurn(gameId);
        }
        
        return ResponseEntity.ok(state);
    }

    /**
     * Skip fortification.
     */
    @PostMapping("/{gameId}/skipFortify")
    public ResponseEntity<GameStateDTO> skipFortify(@PathVariable String gameId,
                                                     @RequestParam String playerId) {
        gameService.skipFortify(gameId, playerId);
        GameStateDTO state = gameService.getGameState(gameId);
        webSocketHandler.broadcastGameUpdate(gameId);

        // Check if game ended (e.g. turn-limit reached)
        if (state.getStatus() == GameStatus.FINISHED) {
            webSocketHandler.broadcastGameOver(gameId, state.getWinnerName());
        } else {
            // Check if CPU turn should start
            cpuPlayerService.checkAndTriggerCPUTurn(gameId);
        }
        
        return ResponseEntity.ok(state);
    }

    private GameSummaryDTO toGameSummary(Game game) {
        String hostName = game.getPlayers().isEmpty() ? "Unknown" : 
                game.getPlayers().get(0).getName();

        String mapName = "";
        try {
            mapName = mapLoader.getMap(game.getMapId()).name();
        } catch (IllegalArgumentException e) {
            log.debug("Map not found for game {}: {}", game.getId(), e.getMessage());
        }

        return GameSummaryDTO.builder()
                .id(game.getId())
                .name(game.getName())
                .mapId(game.getMapId())
                .mapName(mapName)
                .status(game.getStatus().name())
                .playerCount(game.getPlayers().size())
                .maxPlayers(game.getMaxPlayers())
                .minPlayers(game.getMinPlayers())
                .createdAt(game.getCreatedAt().format(DATE_FORMAT))
                .canJoin(game.getStatus() == GameStatus.WAITING_FOR_PLAYERS && !game.isFull())
                .hostName(hostName)
                .gameMode(game.getGameMode().name())
                .build();
    }
}
