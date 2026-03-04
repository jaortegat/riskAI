package com.riskai.mcp;

import com.riskai.config.MapDefinition;
import com.riskai.config.MapLoader;
import com.riskai.dto.*;
import com.riskai.model.*;
import com.riskai.service.CPUPlayerService;
import com.riskai.service.GameService;
import com.riskai.websocket.GameWebSocketHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.times;

/**
 * Unit tests for {@link GameMCPService}.
 */
@ExtendWith(MockitoExtension.class)
class GameMCPServiceTest {

    @Mock private GameService gameService;
    @Mock private CPUPlayerService cpuPlayerService;
    @Mock private GameWebSocketHandler webSocketHandler;
    @Mock private MapLoader mapLoader;

    @InjectMocks
    private GameMCPService mcpService;

    private Player testPlayer;
    private Game testGame;

    @BeforeEach
    void setUp() {
        testGame = Game.builder()
                .id("g1")
                .name("Test Game")
                .mapId("classic")
                .status(GameStatus.IN_PROGRESS)
                .maxPlayers(6)
                .minPlayers(2)
                .gameMode(GameMode.CLASSIC)
                .createdAt(LocalDateTime.of(2025, 1, 1, 12, 0))
                .players(List.of())
                .build();

        testPlayer = Player.builder()
                .id("p1")
                .name("Agent Alpha")
                .color(PlayerColor.RED)
                .type(PlayerType.AI_AGENT)
                .turnOrder(0)
                .build();
    }

    // ── Helpers ─────────────────────────────────────────────────────

    /**
     * Mocks {@code gameService.joinGame} and calls {@code mcpService.joinGame},
     * returning the session token.
     */
    private String joinAndGetToken(String gameId, Player player, String playerName) {
        when(gameService.joinGame(eq(gameId), any(JoinGameRequest.class),
                eq("mcp-" + playerName), eq(PlayerType.AI_AGENT)))
                .thenReturn(player);

        MCPSessionResult result = mcpService.joinGame(gameId, playerName);
        return result.sessionToken();
    }

    private GameStateDTO buildGameState(GameStatus status, GamePhase phase,
                                         PlayerDTO currentPlayer,
                                         int reinforcements, int turn,
                                         List<TerritoryDTO> territories) {
        return GameStateDTO.builder()
                .gameId("g1")
                .status(status)
                .currentPhase(phase)
                .currentPlayer(currentPlayer)
                .reinforcementsRemaining(reinforcements)
                .turnNumber(turn)
                .territories(territories != null ? territories : List.of())
                .players(List.of(PlayerDTO.fromPlayer(testPlayer)))
                .build();
    }

    private TerritoryDTO buildTerritory(String key, String ownerId, int armies, Set<String> neighbors) {
        return TerritoryDTO.builder()
                .territoryKey(key)
                .ownerId(ownerId)
                .armies(armies)
                .neighborKeys(neighbors)
                .build();
    }

    // ── Join Game ───────────────────────────────────────────────────

    @Nested
    @DisplayName("joinGame")
    class JoinGameTests {

        @Test
        @DisplayName("should return MCPSessionResult with player info, session token, and hint")
        void shouldReturnSessionResultWithToken() {
            when(gameService.joinGame(eq("g1"), any(JoinGameRequest.class),
                    eq("mcp-Agent Alpha"), eq(PlayerType.AI_AGENT)))
                    .thenReturn(testPlayer);

            MCPSessionResult result = mcpService.joinGame("g1", "Agent Alpha");

            assertThat(result).isNotNull();
            assertThat(result.player().getId()).isEqualTo("p1");
            assertThat(result.player().getName()).isEqualTo("Agent Alpha");
            assertThat(result.sessionToken()).isNotBlank();
            assertThat(result.hint()).contains("g1", "Agent Alpha", "p1");

            verify(webSocketHandler).broadcastPlayerJoined(eq("g1"), any(PlayerDTO.class));
            verify(webSocketHandler).broadcastGameUpdate("g1");
        }

        @Test
        @DisplayName("two agents joining should get independent session tokens")
        void twoAgentsGetIndependentTokens() {
            Player player2 = Player.builder()
                    .id("p2").name("Agent Beta").color(PlayerColor.BLUE)
                    .type(PlayerType.AI_AGENT).turnOrder(1).build();

            when(gameService.joinGame(eq("g1"), any(JoinGameRequest.class),
                    eq("mcp-Agent Alpha"), eq(PlayerType.AI_AGENT)))
                    .thenReturn(testPlayer);
            when(gameService.joinGame(eq("g1"), any(JoinGameRequest.class),
                    eq("mcp-Agent Beta"), eq(PlayerType.AI_AGENT)))
                    .thenReturn(player2);

            MCPSessionResult r1 = mcpService.joinGame("g1", "Agent Alpha");
            MCPSessionResult r2 = mcpService.joinGame("g1", "Agent Beta");

            assertThat(r1.sessionToken()).isNotEqualTo(r2.sessionToken());
            assertThat(r1.player().getId()).isEqualTo("p1");
            assertThat(r2.player().getId()).isEqualTo("p2");
        }
    }

    // ── Session Token Validation ────────────────────────────────────

    @Nested
    @DisplayName("Session Token Validation")
    class SessionTokenValidation {

        @Test
        @DisplayName("should reject null session token")
        void rejectNullToken() {
            GameStateDTO state = buildGameState(GameStatus.IN_PROGRESS, GamePhase.REINFORCEMENT,
                    PlayerDTO.fromPlayer(testPlayer), 5, 1, List.of());
            // No need to set up getGameState - validation happens before it's called

            assertThatThrownBy(() -> mcpService.getMyTurnStatus("g1", "p1", null))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("Session token is required");
        }

        @Test
        @DisplayName("should reject blank session token")
        void rejectBlankToken() {
            assertThatThrownBy(() -> mcpService.getMyTurnStatus("g1", "p1", "  "))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("Session token is required");
        }

        @Test
        @DisplayName("should reject unknown session token")
        void rejectUnknownToken() {
            assertThatThrownBy(() -> mcpService.getMyTurnStatus("g1", "p1", "fake-token-123"))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("Unknown session token");
        }

        @Test
        @DisplayName("should reject token belonging to a different player")
        void rejectTokenFromWrongPlayer() {
            String token = joinAndGetToken("g1", testPlayer, "Agent Alpha");

            // Token is bound to p1 — using it with p2 must fail
            assertThatThrownBy(() -> mcpService.getMyTurnStatus("g1", "p2", token))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("does not match");
        }

        @Test
        @DisplayName("should accept valid session token for correct player")
        void acceptValidToken() {
            String token = joinAndGetToken("g1", testPlayer, "Agent Alpha");

            GameStateDTO state = buildGameState(GameStatus.IN_PROGRESS, GamePhase.REINFORCEMENT,
                    PlayerDTO.fromPlayer(testPlayer), 5, 1, List.of());
            when(gameService.getGameState("g1")).thenReturn(state);

            TurnStatusDTO status = mcpService.getMyTurnStatus("g1", "p1", token);

            assertThat(status).isNotNull();
            assertThat(status.isYourTurn()).isTrue();
        }
    }

    // ── Query Tools ─────────────────────────────────────────────────

    @Nested
    @DisplayName("Query Tools (no session token required)")
    class QueryTools {

        @Test
        @DisplayName("listJoinableGames - returns joinable games")
        void listJoinableGames() {
            Game joinable = Game.builder()
                    .id("g2").name("Open Game").mapId("classic")
                    .status(GameStatus.WAITING_FOR_PLAYERS)
                    .maxPlayers(6).minPlayers(2)
                    .gameMode(GameMode.CLASSIC)
                    .createdAt(LocalDateTime.of(2025, 1, 1, 12, 0))
                    .players(List.of(testPlayer))
                    .build();
            when(gameService.getJoinableGames()).thenReturn(List.of(joinable));
            when(mapLoader.getMap("classic"))
                    .thenReturn(new MapDefinition("classic", "Classic World", null, null, 2, 6, null));

            List<GameSummaryDTO> result = mcpService.listJoinableGames();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getName()).isEqualTo("Open Game");
            assertThat(result.get(0).isCanJoin()).isTrue();
        }

        @Test
        @DisplayName("listAllGames - returns all games")
        void listAllGames() {
            when(gameService.getAllGames()).thenReturn(List.of(testGame));
            when(mapLoader.getMap("classic"))
                    .thenReturn(new MapDefinition("classic", "Classic World", null, null, 2, 6, null));

            List<GameSummaryDTO> result = mcpService.listAllGames();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getName()).isEqualTo("Test Game");
        }

        @Test
        @DisplayName("getGameState - returns game state")
        void getGameState() {
            GameStateDTO state = buildGameState(GameStatus.IN_PROGRESS, GamePhase.ATTACK,
                    PlayerDTO.fromPlayer(testPlayer), 0, 3, List.of());
            when(gameService.getGameState("g1")).thenReturn(state);

            GameStateDTO result = mcpService.getGameState("g1");

            assertThat(result.getGameId()).isEqualTo("g1");
            assertThat(result.getStatus()).isEqualTo(GameStatus.IN_PROGRESS);
        }

        @Test
        @DisplayName("getAttackableTargets - returns enemy neighbors with max attack armies")
        void getAttackableTargets() {
            TerritoryDTO brazil = buildTerritory("brazil", "p1", 5,
                    Set.of("argentina", "peru", "north_africa"));
            TerritoryDTO argentina = buildTerritory("argentina", "p2", 2,
                    Set.of("brazil", "peru"));
            TerritoryDTO peru = buildTerritory("peru", "p1", 3,
                    Set.of("brazil", "argentina"));

            GameStateDTO state = buildGameState(GameStatus.IN_PROGRESS, GamePhase.ATTACK,
                    PlayerDTO.fromPlayer(testPlayer), 0, 1,
                    List.of(brazil, argentina, peru));
            when(gameService.getGameState("g1")).thenReturn(state);

            List<MCPAttackOptionDTO> result = mcpService.getAttackableTargets("g1", "brazil");

            assertThat(result).hasSize(1);
            assertThat(result.get(0).territoryKey()).isEqualTo("argentina");
            assertThat(result.get(0).maxAttackArmies()).isEqualTo(3); // min(3, 5-1) = 3
        }
    }

    // ── Turn Status ─────────────────────────────────────────────────

    @Nested
    @DisplayName("getMyTurnStatus")
    class TurnStatusTests {

        private String token;

        @BeforeEach
        void joinFirst() {
            token = joinAndGetToken("g1", testPlayer, "Agent Alpha");
        }

        @Test
        @DisplayName("REINFORCEMENT phase - lists placeArmies action")
        void reinforcementPhase() {
            GameStateDTO state = buildGameState(GameStatus.IN_PROGRESS, GamePhase.REINFORCEMENT,
                    PlayerDTO.fromPlayer(testPlayer), 5, 1, List.of());
            when(gameService.getGameState("g1")).thenReturn(state);

            TurnStatusDTO result = mcpService.getMyTurnStatus("g1", "p1", token);

            assertThat(result.isYourTurn()).isTrue();
            assertThat(result.currentPhase()).isEqualTo(GamePhase.REINFORCEMENT);
            assertThat(result.availableActions()).contains("placeArmies", "getPlayerTerritories");
            assertThat(result.reinforcementsRemaining()).isEqualTo(5);
        }

        @Test
        @DisplayName("ATTACK phase - lists attack, endAttackPhase, getAttackableTargets")
        void attackPhase() {
            GameStateDTO state = buildGameState(GameStatus.IN_PROGRESS, GamePhase.ATTACK,
                    PlayerDTO.fromPlayer(testPlayer), 0, 1, List.of());
            when(gameService.getGameState("g1")).thenReturn(state);

            TurnStatusDTO result = mcpService.getMyTurnStatus("g1", "p1", token);

            assertThat(result.isYourTurn()).isTrue();
            assertThat(result.availableActions()).contains("attack", "endAttackPhase", "getAttackableTargets");
        }

        @Test
        @DisplayName("FORTIFY phase - lists fortify, skipFortify")
        void fortifyPhase() {
            GameStateDTO state = buildGameState(GameStatus.IN_PROGRESS, GamePhase.FORTIFY,
                    PlayerDTO.fromPlayer(testPlayer), 0, 1, List.of());
            when(gameService.getGameState("g1")).thenReturn(state);

            TurnStatusDTO result = mcpService.getMyTurnStatus("g1", "p1", token);

            assertThat(result.isYourTurn()).isTrue();
            assertThat(result.availableActions()).contains("fortify", "skipFortify", "getPlayerTerritories");
        }

        @Test
        @DisplayName("not your turn - isYourTurn is false")
        void notYourTurn() {
            PlayerDTO otherPlayer = PlayerDTO.builder()
                    .id("p2").name("Opponent").color(PlayerColor.BLUE)
                    .type(PlayerType.HUMAN).turnOrder(1).build();
            GameStateDTO state = GameStateDTO.builder()
                    .gameId("g1")
                    .status(GameStatus.IN_PROGRESS)
                    .currentPhase(GamePhase.REINFORCEMENT)
                    .currentPlayer(otherPlayer)
                    .reinforcementsRemaining(3)
                    .turnNumber(1)
                    .territories(List.of())
                    .players(List.of(PlayerDTO.fromPlayer(testPlayer), otherPlayer))
                    .build();
            when(gameService.getGameState("g1")).thenReturn(state);

            TurnStatusDTO result = mcpService.getMyTurnStatus("g1", "p1", token);

            assertThat(result.isYourTurn()).isFalse();
            assertThat(result.hint()).contains("Opponent");
        }

        @Test
        @DisplayName("FINISHED game - reports winner")
        void finishedGame() {
            GameStateDTO state = GameStateDTO.builder()
                    .gameId("g1")
                    .status(GameStatus.FINISHED)
                    .currentPhase(GamePhase.REINFORCEMENT)
                    .currentPlayer(PlayerDTO.fromPlayer(testPlayer))
                    .winnerName("Agent Alpha")
                    .reinforcementsRemaining(0)
                    .turnNumber(10)
                    .territories(List.of())
                    .players(List.of(PlayerDTO.fromPlayer(testPlayer)))
                    .build();
            when(gameService.getGameState("g1")).thenReturn(state);

            TurnStatusDTO result = mcpService.getMyTurnStatus("g1", "p1", token);

            assertThat(result.gameStatus()).isEqualTo(GameStatus.FINISHED);
            assertThat(result.hint()).contains("over", "Agent Alpha");
        }

        @Test
        @DisplayName("WAITING_FOR_PLAYERS - indicates waiting")
        void waitingForPlayers() {
            GameStateDTO state = GameStateDTO.builder()
                    .gameId("g1")
                    .status(GameStatus.WAITING_FOR_PLAYERS)
                    .currentPhase(null)
                    .currentPlayer(null)
                    .reinforcementsRemaining(0)
                    .turnNumber(0)
                    .territories(List.of())
                    .players(List.of(PlayerDTO.fromPlayer(testPlayer)))
                    .build();
            when(gameService.getGameState("g1")).thenReturn(state);

            TurnStatusDTO result = mcpService.getMyTurnStatus("g1", "p1", token);

            assertThat(result.isYourTurn()).isFalse();
            assertThat(result.hint()).contains("waiting");
        }
    }

    // ── waitForMyTurn ───────────────────────────────────────────────

    @Nested
    @DisplayName("waitForMyTurn")
    class WaitForTurnTests {

        private String token;

        @BeforeEach
        void joinFirst() {
            token = joinAndGetToken("g1", testPlayer, "Agent Alpha");
        }

        @Test
        @DisplayName("returns immediately when already your turn")
        void returnsImmediatelyWhenYourTurn() {
            GameStateDTO state = buildGameState(GameStatus.IN_PROGRESS, GamePhase.REINFORCEMENT,
                    PlayerDTO.fromPlayer(testPlayer), 5, 1, List.of());
            when(gameService.getGameState("g1")).thenReturn(state);

            long start = System.currentTimeMillis();
            TurnStatusDTO result = mcpService.waitForMyTurn("g1", "p1", token, 10);
            long elapsed = System.currentTimeMillis() - start;

            assertThat(result.isYourTurn()).isTrue();
            assertThat(elapsed).isLessThan(1000);
        }

        @Test
        @DisplayName("returns immediately when game finished")
        void returnsImmediatelyWhenFinished() {
            GameStateDTO state = GameStateDTO.builder()
                    .gameId("g1")
                    .status(GameStatus.FINISHED)
                    .currentPhase(GamePhase.REINFORCEMENT)
                    .currentPlayer(PlayerDTO.fromPlayer(testPlayer))
                    .winnerName("Agent Alpha")
                    .reinforcementsRemaining(0)
                    .turnNumber(10)
                    .territories(List.of())
                    .players(List.of(PlayerDTO.fromPlayer(testPlayer)))
                    .build();
            when(gameService.getGameState("g1")).thenReturn(state);

            long start = System.currentTimeMillis();
            TurnStatusDTO result = mcpService.waitForMyTurn("g1", "p1", token, 10);
            long elapsed = System.currentTimeMillis() - start;

            assertThat(result.gameStatus()).isEqualTo(GameStatus.FINISHED);
            assertThat(elapsed).isLessThan(1000);
        }

        @Test
        @DisplayName("times out after specified seconds when not your turn")
        void timesOutWhenNotYourTurn() {
            PlayerDTO otherPlayer = PlayerDTO.builder()
                    .id("p2").name("Opponent").color(PlayerColor.BLUE)
                    .type(PlayerType.HUMAN).turnOrder(1).build();
            GameStateDTO state = GameStateDTO.builder()
                    .gameId("g1")
                    .status(GameStatus.IN_PROGRESS)
                    .currentPhase(GamePhase.REINFORCEMENT)
                    .currentPlayer(otherPlayer)
                    .reinforcementsRemaining(3)
                    .turnNumber(1)
                    .territories(List.of())
                    .players(List.of(PlayerDTO.fromPlayer(testPlayer), otherPlayer))
                    .build();
            when(gameService.getGameState("g1")).thenReturn(state);

            long start = System.currentTimeMillis();
            TurnStatusDTO result = mcpService.waitForMyTurn("g1", "p1", token, 2);
            long elapsed = System.currentTimeMillis() - start;

            assertThat(result.isYourTurn()).isFalse();
            // 2-second timeout with 500ms poll → ~2000ms (+/- tolerance)
            assertThat(elapsed).isBetween(1500L, 4000L);
        }

        @Test
        @DisplayName("clamps timeout to valid range (1-60)")
        void clampsTimeout() {
            GameStateDTO state = buildGameState(GameStatus.IN_PROGRESS, GamePhase.REINFORCEMENT,
                    PlayerDTO.fromPlayer(testPlayer), 5, 1, List.of());
            when(gameService.getGameState("g1")).thenReturn(state);

            // 0 should be clamped to 1, but since it's already our turn it returns immediately
            TurnStatusDTO result = mcpService.waitForMyTurn("g1", "p1", token, 0);
            assertThat(result.isYourTurn()).isTrue();
        }
    }

    // ── Player Territories ──────────────────────────────────────────

    @Nested
    @DisplayName("getPlayerTerritories")
    class PlayerTerritoriesTests {

        @Test
        @DisplayName("returns only territories owned by the specified player")
        void returnsOwnedTerritories() {
            String token = joinAndGetToken("g1", testPlayer, "Agent Alpha");

            TerritoryDTO brazil = buildTerritory("brazil", "p1", 5, Set.of("argentina"));
            TerritoryDTO argentina = buildTerritory("argentina", "p2", 2, Set.of("brazil"));
            TerritoryDTO peru = buildTerritory("peru", "p1", 3, Set.of("brazil"));

            GameStateDTO state = buildGameState(GameStatus.IN_PROGRESS, GamePhase.ATTACK,
                    PlayerDTO.fromPlayer(testPlayer), 0, 1,
                    List.of(brazil, argentina, peru));
            when(gameService.getGameState("g1")).thenReturn(state);

            List<MCPTerritoryDTO> result = mcpService.getPlayerTerritories("g1", "p1", token);

            assertThat(result).hasSize(2);
            assertThat(result).extracting(MCPTerritoryDTO::territoryKey)
                    .containsExactlyInAnyOrder("brazil", "peru");
        }
    }

    // ── Action Tools ────────────────────────────────────────────────

    @Nested
    @DisplayName("Action Tools (require session token)")
    class ActionTools {

        private String token;

        @BeforeEach
        void joinFirst() {
            token = joinAndGetToken("g1", testPlayer, "Agent Alpha");
        }

        @Test
        @DisplayName("placeArmies - places armies and broadcasts update")
        void placeArmies() {
            TerritoryDTO brazil = buildTerritory("brazil", "p1", 8, Set.of("argentina"));
            GameStateDTO state = buildGameState(GameStatus.IN_PROGRESS, GamePhase.REINFORCEMENT,
                    PlayerDTO.fromPlayer(testPlayer), 2, 1, List.of(brazil));
            when(gameService.getGameState("g1")).thenReturn(state);

            MCPTerritoryDTO result = mcpService.placeArmies("g1", "p1", token, "brazil", 3);

            assertThat(result.territoryKey()).isEqualTo("brazil");
            verify(gameService).placeArmies("g1", "p1", "brazil", 3);
            verify(webSocketHandler, times(2)).broadcastGameUpdate("g1"); // once from joinAndGetToken, once from placeArmies
            verify(cpuPlayerService).checkAndTriggerCPUTurn("g1");
        }

        @Test
        @DisplayName("attack - returns combat result map")
        void attack() {
            AttackResult attackResult = AttackResult.builder()
                    .attackerDice(new int[]{6, 5, 3})
                    .defenderDice(new int[]{4, 2})
                    .attackerLosses(0)
                    .defenderLosses(2)
                    .conquered(true)
                    .eliminatedPlayer(null)
                    .build();
            when(gameService.attack("g1", "p1", "brazil", "argentina", 3))
                    .thenReturn(attackResult);

            Map<String, Object> result = mcpService.attack("g1", "p1", token,
                    "brazil", "argentina", 3);

            assertThat(result.get("conquered")).isEqualTo(true);
            assertThat(result.get("attackerLosses")).isEqualTo(0);
            assertThat(result.get("defenderLosses")).isEqualTo(2);
            verify(webSocketHandler).broadcastAttackResult("g1", "brazil", "argentina", attackResult);
            verify(webSocketHandler, times(2)).broadcastGameUpdate("g1");
        }

        @Test
        @DisplayName("endAttackPhase - advances to fortify phase")
        void endAttackPhase() {
            GameStateDTO state = buildGameState(GameStatus.IN_PROGRESS, GamePhase.FORTIFY,
                    PlayerDTO.fromPlayer(testPlayer), 0, 1, List.of());
            when(gameService.getGameState("g1")).thenReturn(state);

            MCPPhaseResultDTO result = mcpService.endAttackPhase("g1", "p1", token);

            assertThat(result.currentPhase()).isEqualTo(GamePhase.FORTIFY);
            verify(gameService).endAttackPhase("g1", "p1");
            verify(webSocketHandler, times(2)).broadcastGameUpdate("g1");
        }

        @Test
        @DisplayName("fortify - moves armies and ends turn")
        void fortify() {
            GameStateDTO state = buildGameState(GameStatus.IN_PROGRESS, GamePhase.REINFORCEMENT,
                    PlayerDTO.fromPlayer(testPlayer), 3, 2, List.of());
            when(gameService.getGameState("g1")).thenReturn(state);

            MCPPhaseResultDTO result = mcpService.fortify("g1", "p1", token,
                    "brazil", "argentina", 2);

            verify(gameService).fortify("g1", "p1", "brazil", "argentina", 2);
            verify(webSocketHandler, times(2)).broadcastGameUpdate("g1");
            verify(cpuPlayerService).checkAndTriggerCPUTurn("g1");
        }

        @Test
        @DisplayName("fortify - broadcasts game over when game finishes")
        void fortifyGameOver() {
            GameStateDTO state = GameStateDTO.builder()
                    .gameId("g1")
                    .status(GameStatus.FINISHED)
                    .currentPhase(GamePhase.FORTIFY)
                    .currentPlayer(PlayerDTO.fromPlayer(testPlayer))
                    .winnerName("Agent Alpha")
                    .reinforcementsRemaining(0)
                    .turnNumber(10)
                    .territories(List.of())
                    .players(List.of(PlayerDTO.fromPlayer(testPlayer)))
                    .build();
            when(gameService.getGameState("g1")).thenReturn(state);

            MCPPhaseResultDTO result = mcpService.fortify("g1", "p1", token,
                    "brazil", "argentina", 2);

            assertThat(result.status()).isEqualTo(GameStatus.FINISHED);
            verify(webSocketHandler, times(2)).broadcastGameUpdate("g1");
            verify(webSocketHandler).broadcastGameOver("g1", "Agent Alpha");
            verify(cpuPlayerService, never()).checkAndTriggerCPUTurn(anyString());
        }

        @Test
        @DisplayName("skipFortify - skips and ends turn")
        void skipFortify() {
            GameStateDTO state = buildGameState(GameStatus.IN_PROGRESS, GamePhase.REINFORCEMENT,
                    PlayerDTO.fromPlayer(testPlayer), 3, 2, List.of());
            when(gameService.getGameState("g1")).thenReturn(state);

            MCPPhaseResultDTO result = mcpService.skipFortify("g1", "p1", token);

            verify(gameService).skipFortify("g1", "p1");
            verify(webSocketHandler, times(2)).broadcastGameUpdate("g1");
            verify(cpuPlayerService).checkAndTriggerCPUTurn("g1");
        }

        @Test
        @DisplayName("skipFortify - broadcasts game over when game finishes")
        void skipFortifyGameOver() {
            GameStateDTO state = GameStateDTO.builder()
                    .gameId("g1")
                    .status(GameStatus.FINISHED)
                    .currentPhase(GamePhase.FORTIFY)
                    .currentPlayer(PlayerDTO.fromPlayer(testPlayer))
                    .winnerName("Agent Alpha")
                    .reinforcementsRemaining(0)
                    .turnNumber(10)
                    .territories(List.of())
                    .players(List.of(PlayerDTO.fromPlayer(testPlayer)))
                    .build();
            when(gameService.getGameState("g1")).thenReturn(state);

            MCPPhaseResultDTO result = mcpService.skipFortify("g1", "p1", token);

            assertThat(result.status()).isEqualTo(GameStatus.FINISHED);
            verify(webSocketHandler, times(2)).broadcastGameUpdate("g1");
            verify(webSocketHandler).broadcastGameOver("g1", "Agent Alpha");
            verify(cpuPlayerService, never()).checkAndTriggerCPUTurn(anyString());
        }
    }

    // ── Action Tools Without Token (should fail) ────────────────────

    @Nested
    @DisplayName("Action Tools reject calls without valid session token")
    class ActionToolsRejectWithoutToken {

        @Test
        @DisplayName("placeArmies rejects missing token")
        void placeArmiesRejects() {
            assertThatThrownBy(() -> mcpService.placeArmies("g1", "p1", null, "brazil", 3))
                    .isInstanceOf(SecurityException.class);
        }

        @Test
        @DisplayName("attack rejects missing token")
        void attackRejects() {
            assertThatThrownBy(() -> mcpService.attack("g1", "p1", null, "brazil", "argentina", 3))
                    .isInstanceOf(SecurityException.class);
        }

        @Test
        @DisplayName("endAttackPhase rejects missing token")
        void endAttackPhaseRejects() {
            assertThatThrownBy(() -> mcpService.endAttackPhase("g1", "p1", null))
                    .isInstanceOf(SecurityException.class);
        }

        @Test
        @DisplayName("fortify rejects missing token")
        void fortifyRejects() {
            assertThatThrownBy(() -> mcpService.fortify("g1", "p1", null, "brazil", "argentina", 2))
                    .isInstanceOf(SecurityException.class);
        }

        @Test
        @DisplayName("skipFortify rejects missing token")
        void skipFortifyRejects() {
            assertThatThrownBy(() -> mcpService.skipFortify("g1", "p1", null))
                    .isInstanceOf(SecurityException.class);
        }

        @Test
        @DisplayName("getPlayerTerritories rejects missing token")
        void getPlayerTerritoriesRejects() {
            assertThatThrownBy(() -> mcpService.getPlayerTerritories("g1", "p1", null))
                    .isInstanceOf(SecurityException.class);
        }

        @Test
        @DisplayName("waitForMyTurn rejects missing token")
        void waitForMyTurnRejects() {
            assertThatThrownBy(() -> mcpService.waitForMyTurn("g1", "p1", null, 5))
                    .isInstanceOf(SecurityException.class);
        }
    }
}
