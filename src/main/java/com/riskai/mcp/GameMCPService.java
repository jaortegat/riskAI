package com.riskai.mcp;

import com.riskai.dto.AttackOptionDTO;
import com.riskai.dto.AttackResult;
import com.riskai.dto.GameStateDTO;
import com.riskai.dto.GameSummaryDTO;
import com.riskai.dto.JoinGameRequest;
import com.riskai.dto.PlayerDTO;
import com.riskai.dto.TerritoryDTO;
import com.riskai.dto.TurnStatusDTO;
import com.riskai.config.MapLoader;
import com.riskai.model.Game;
import com.riskai.model.GameStatus;
import com.riskai.model.Player;
import com.riskai.model.PlayerType;
import com.riskai.service.CPUPlayerService;
import com.riskai.service.GameService;
import com.riskai.websocket.GameWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP tool service that exposes RiskAI game operations for AI agents.
 * <p>
 * Each public method annotated with {@code @Tool} becomes an MCP-callable tool,
 * allowing AI agent clients (e.g., GitHub Copilot Chat) to join and play the game
 * via the Model Context Protocol.
 * <p>
 * This service is intentionally thin — all business logic is delegated to existing
 * service classes. Tool methods return DTOs or records, never JPA entities.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GameMCPService {

    private final GameService gameService;
    private final CPUPlayerService cpuPlayerService;
    private final GameWebSocketHandler webSocketHandler;
    private final MapLoader mapLoader;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    // ── Query Tools ────────────────────────────────────────────────────

    @Tool(description = "List all games that are currently available to join (status WAITING_FOR_PLAYERS and not full). "
            + "Use this to find a game to join as an AI player.")
    @Transactional(readOnly = true)
    public List<GameSummaryDTO> listJoinableGames() {
        log.info("[MCP] Listing joinable games");
        return gameService.getJoinableGames().stream()
                .map(this::toGameSummary)
                .toList();
    }

    @Tool(description = "List all games regardless of status. "
            + "Returns a summary of each game including id, name, status, player count, and game mode.")
    @Transactional(readOnly = true)
    public List<GameSummaryDTO> listAllGames() {
        log.info("[MCP] Listing all games");
        return gameService.getAllGames().stream()
                .map(this::toGameSummary)
                .toList();
    }

    @Tool(description = "Get the full current state of a game including all players, territories, continents, "
            + "current phase, turn number, and reinforcements remaining. "
            + "Use this to understand the board before making a move.")
    public GameStateDTO getGameState(
            @ToolParam(description = "The unique ID of the game") String gameId) {
        log.info("[MCP] Getting game state for game {}", gameId);
        return gameService.getGameState(gameId);
    }

    @Tool(description = "Get the list of territories owned by a specific player in a game, "
            + "including army counts and neighbor information. "
            + "Useful for planning reinforcements, attacks, and fortifications.")
    public List<TerritoryDTO> getPlayerTerritories(
            @ToolParam(description = "The unique ID of the game") String gameId,
            @ToolParam(description = "The unique ID of the player") String playerId) {
        log.info("[MCP] Getting territories for player {} in game {}", playerId, gameId);
        GameStateDTO state = gameService.getGameState(gameId);
        return state.getTerritories().stream()
                .filter(t -> playerId.equals(t.getOwnerId()))
                .toList();
    }

    @Tool(description = "Get territories that can be attacked from a given territory. "
            + "Returns a list of attack options, each containing the target territory "
            + "and maxAttackArmies — the exact number of armies to pass to the attack tool. "
            + "IMPORTANT: always use maxAttackArmies from this response when calling attack; "
            + "never hard-code or guess the army count.")
    public List<AttackOptionDTO> getAttackableTargets(
            @ToolParam(description = "The unique ID of the game") String gameId,
            @ToolParam(description = "The territory key of the source territory to attack from") String territoryKey) {
        log.info("[MCP] Getting attackable targets from {} in game {}", territoryKey, gameId);
        GameStateDTO state = gameService.getGameState(gameId);

        TerritoryDTO source = state.getTerritories().stream()
                .filter(t -> territoryKey.equals(t.getTerritoryKey()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Territory not found: " + territoryKey));

        int maxAttackArmies = Math.min(3, source.getArmies() - 1);

        return state.getTerritories().stream()
                .filter(t -> source.getNeighborKeys().contains(t.getTerritoryKey()))
                .filter(t -> !source.getOwnerId().equals(t.getOwnerId()))
                .map(t -> new AttackOptionDTO(t, maxAttackArmies))
                .toList();
    }

    // ── Turn Status Tool ───────────────────────────────────────────────

    @Tool(description = "Check if it is your turn and what you should do next. "
            + "This is the MOST IMPORTANT tool for AI agents — call it to know: "
            + "(1) whether it is currently your turn, "
            + "(2) which game phase is active (REINFORCEMENT, ATTACK, FORTIFY), "
            + "(3) which tool(s) you should call next, and "
            + "(4) a human-readable hint with guidance. "
            + "WORKFLOW: After joining/starting a game, poll this tool to wait for your turn. "
            + "When isYourTurn=true, follow the availableActions list. "
            + "After each action, call this again to see if your phase changed or your turn ended.")
    public TurnStatusDTO getMyTurnStatus(
            @ToolParam(description = "The unique ID of the game") String gameId,
            @ToolParam(description = "Your player ID (received when joining the game)") String playerId) {
        log.debug("[MCP] Checking turn status for player {} in game {}", playerId, gameId);
        GameStateDTO state = gameService.getGameState(gameId);

        // Find the agent's player info
        PlayerDTO myPlayer = state.getPlayers().stream()
                .filter(p -> playerId.equals(p.getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Player not found: " + playerId));

        boolean isMyTurn = state.getCurrentPlayer() != null
                && playerId.equals(state.getCurrentPlayer().getId());

        List<String> actions = new ArrayList<>();
        String hint;

        if (state.getStatus() == GameStatus.FINISHED) {
            hint = "Game is over. " + (state.getWinnerName() != null
                    ? "Winner: " + state.getWinnerName() : "No winner.");
        } else if (state.getStatus() == GameStatus.WAITING_FOR_PLAYERS) {
            hint = "Game is waiting for players to join. The game will start once enough players have joined.";
        } else if (!isMyTurn) {
            hint = "It is " + state.getCurrentPlayer().getName() + "'s turn. "
                    + "Wait and call getMyTurnStatus again to check when it's your turn.";
        } else {
            switch (state.getCurrentPhase()) {
                case REINFORCEMENT -> {
                    actions.add("placeArmies");
                    actions.add("getPlayerTerritories");
                    hint = "REINFORCEMENT phase — you have " + state.getReinforcementsRemaining()
                            + " armies to place. Call placeArmies to distribute them on your territories. "
                            + "Once all are placed, you automatically move to the ATTACK phase.";
                }
                case ATTACK -> {
                    actions.add("attack");
                    actions.add("endAttackPhase");
                    actions.add("getAttackableTargets");
                    hint = "ATTACK phase — you may attack enemy neighbors or call endAttackPhase to move "
                            + "to FORTIFY. Use getAttackableTargets to find valid targets.";
                }
                case FORTIFY -> {
                    actions.add("fortify");
                    actions.add("skipFortify");
                    actions.add("getPlayerTerritories");
                    hint = "FORTIFY phase — move armies between two adjacent territories you own, "
                            + "or call skipFortify to end your turn without moving.";
                }
                default -> {
                    hint = "Current phase: " + state.getCurrentPhase() + ". No actions available.";
                }
            }
        }

        return new TurnStatusDTO(
                gameId,
                state.getStatus(),
                isMyTurn,
                state.getCurrentPhase(),
                state.getCurrentPlayer() != null ? state.getCurrentPlayer().getName() : null,
                playerId,
                myPlayer.getName(),
                state.getReinforcementsRemaining(),
                state.getTurnNumber(),
                actions,
                hint
        );
    }

    @Tool(description = "Wait (long-poll) until it is your turn or the timeout expires. "
            + "Use this instead of repeatedly calling getMyTurnStatus in a loop — it blocks "
            + "server-side and checks every second, saving token cost and round-trips. "
            + "Returns immediately if it is already your turn or the game has finished. "
            + "After it returns with isYourTurn=true, follow the availableActions list. "
            + "If it returns with isYourTurn=false, the timeout expired — call it again to keep waiting.")
    public TurnStatusDTO waitForMyTurn(
            @ToolParam(description = "The unique ID of the game") String gameId,
            @ToolParam(description = "Your player ID (received when joining the game)") String playerId,
            @ToolParam(description = "Maximum seconds to wait before returning (1-60, default 30)") int timeoutSeconds) {
        int clampedTimeout = Math.clamp(timeoutSeconds, 1, 60);
        log.info("[MCP] Player {} waiting for turn in game {} (timeout {}s)", playerId, gameId, clampedTimeout);

        long deadline = System.currentTimeMillis() + (clampedTimeout * 1000L);

        while (System.currentTimeMillis() < deadline) {
            TurnStatusDTO status = getMyTurnStatus(gameId, playerId);
            if (status.isYourTurn() || status.gameStatus() == GameStatus.FINISHED) {
                return status;
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return status;
            }
        }

        // Timeout expired — return current status (isYourTurn will be false)
        return getMyTurnStatus(gameId, playerId);
    }

    // ── Game Lifecycle Tools ───────────────────────────────────────────

    @Tool(description = "Join an existing game as an AI player. "
            + "The game must be in WAITING_FOR_PLAYERS status and not full. "
            + "Returns your player details including your player ID (needed for all subsequent actions).")
    public PlayerDTO joinGame(
            @ToolParam(description = "The unique ID of the game to join") String gameId,
            @ToolParam(description = "Your player name (2-30 characters, must be unique in the game)") String playerName) {
        log.info("[MCP] Player '{}' joining game {}", playerName, gameId);

        JoinGameRequest request = JoinGameRequest.builder()
                .playerName(playerName)
                .build();

        Player player = gameService.joinGame(gameId, request, "mcp-" + playerName, PlayerType.AI_AGENT);
        PlayerDTO dto = PlayerDTO.fromPlayer(player);
        webSocketHandler.broadcastPlayerJoined(gameId, dto);
        webSocketHandler.broadcastGameUpdate(gameId);
        return dto;
    }

    // ── Reinforcement Tools ────────────────────────────────────────────

    @Tool(description = "Place reinforcement armies on one of your territories during the REINFORCEMENT phase. "
            + "You receive armies based on territories owned (territories/3, min 3) plus continent bonuses. "
            + "Call getGameState to check reinforcementsRemaining. You can split reinforcements across "
            + "multiple territories by calling this tool multiple times. "
            + "Once all reinforcements are placed, the phase automatically advances to ATTACK.")
    public TerritoryDTO placeArmies(
            @ToolParam(description = "The unique ID of the game") String gameId,
            @ToolParam(description = "Your player ID (received when joining the game)") String playerId,
            @ToolParam(description = "The territory key where you want to place armies (must be a territory you own)") String territoryKey,
            @ToolParam(description = "Number of armies to place (1 to reinforcementsRemaining)") int armies) {
        log.info("[MCP] Player {} placing {} armies on {} in game {}", playerId, armies, territoryKey, gameId);

        gameService.placeArmies(gameId, playerId, territoryKey, armies);
        webSocketHandler.broadcastGameUpdate(gameId);
        cpuPlayerService.checkAndTriggerCPUTurn(gameId);
        // Fetch from game state to avoid lazy-proxy "no session" error on the detached Territory entity
        return gameService.getGameState(gameId).getTerritories().stream()
                .filter(t -> territoryKey.equals(t.getTerritoryKey()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Territory not found: " + territoryKey));
    }

    // ── Attack Tools ───────────────────────────────────────────────────

    @Tool(description = "Attack an adjacent enemy territory during the ATTACK phase. "
            + "You must attack from a territory you own with at least 2 armies. "
            + "You can attack with 1-3 armies (must leave at least 1 army behind). "
            + "Combat is resolved with dice rolls — attacker rolls up to 3 dice, defender up to 2. "
            + "If you conquer the territory, armies are automatically moved in. "
            + "You can attack multiple times per turn. Use endAttackPhase when done attacking.")
    public Map<String, Object> attack(
            @ToolParam(description = "The unique ID of the game") String gameId,
            @ToolParam(description = "Your player ID") String playerId,
            @ToolParam(description = "Territory key you are attacking FROM (must own, must have 2+ armies)") String fromTerritoryKey,
            @ToolParam(description = "Territory key you are attacking (must be adjacent, must be owned by opponent)") String toTerritoryKey,
            @ToolParam(description = "Number of armies to attack with (1-3, must be less than armies on source territory)") int armies) {
        log.info("[MCP] Player {} attacking from {} to {} with {} armies in game {}",
                playerId, fromTerritoryKey, toTerritoryKey, armies, gameId);

        AttackResult result = gameService.attack(gameId, playerId, fromTerritoryKey, toTerritoryKey, armies);

        webSocketHandler.broadcastAttackResult(gameId, fromTerritoryKey, toTerritoryKey, result);
        webSocketHandler.broadcastGameUpdate(gameId);

        Map<String, Object> response = new HashMap<>();
        response.put("attackerDice", result.getAttackerDice());
        response.put("defenderDice", result.getDefenderDice());
        response.put("attackerLosses", result.getAttackerLosses());
        response.put("defenderLosses", result.getDefenderLosses());
        response.put("conquered", result.isConquered());
        response.put("eliminatedPlayer", result.getEliminatedPlayer());
        return response;
    }

    @Tool(description = "End the attack phase and move to the FORTIFICATION phase. "
            + "Call this when you are done attacking for this turn.")
    public GameStateDTO endAttackPhase(
            @ToolParam(description = "The unique ID of the game") String gameId,
            @ToolParam(description = "Your player ID") String playerId) {
        log.info("[MCP] Player {} ending attack phase in game {}", playerId, gameId);
        gameService.endAttackPhase(gameId, playerId);
        webSocketHandler.broadcastGameUpdate(gameId);
        return gameService.getGameState(gameId);
    }

    // ── Fortification Tools ────────────────────────────────────────────

    @Tool(description = "Move armies between two of your connected territories during the FORTIFICATION phase. "
            + "You can only fortify once per turn. The territories must both be yours and adjacent. "
            + "After fortifying, your turn ends and the next player begins.")
    public GameStateDTO fortify(
            @ToolParam(description = "The unique ID of the game") String gameId,
            @ToolParam(description = "Your player ID") String playerId,
            @ToolParam(description = "Territory key to move armies FROM (must own, must have 2+ armies)") String fromTerritoryKey,
            @ToolParam(description = "Territory key to move armies TO (must own, must be adjacent)") String toTerritoryKey,
            @ToolParam(description = "Number of armies to move (must leave at least 1 behind)") int armies) {
        log.info("[MCP] Player {} fortifying {} -> {} ({} armies) in game {}",
                playerId, fromTerritoryKey, toTerritoryKey, armies, gameId);

        gameService.fortify(gameId, playerId, fromTerritoryKey, toTerritoryKey, armies);
        GameStateDTO state = gameService.getGameState(gameId);
        webSocketHandler.broadcastGameUpdate(gameId);

        if (state.getStatus() == GameStatus.FINISHED) {
            webSocketHandler.broadcastGameOver(gameId, state.getWinnerName());
        } else {
            cpuPlayerService.checkAndTriggerCPUTurn(gameId);
        }

        return state;
    }

    @Tool(description = "Skip the fortification phase and end your turn without moving any armies. "
            + "Use this when you don't want to or can't fortify.")
    public GameStateDTO skipFortify(
            @ToolParam(description = "The unique ID of the game") String gameId,
            @ToolParam(description = "Your player ID") String playerId) {
        log.info("[MCP] Player {} skipping fortification in game {}", playerId, gameId);

        gameService.skipFortify(gameId, playerId);
        GameStateDTO state = gameService.getGameState(gameId);
        webSocketHandler.broadcastGameUpdate(gameId);

        if (state.getStatus() == GameStatus.FINISHED) {
            webSocketHandler.broadcastGameOver(gameId, state.getWinnerName());
        } else {
            cpuPlayerService.checkAndTriggerCPUTurn(gameId);
        }

        return state;
    }

    // ── Helpers ─────────────────────────────────────────────────────────

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
