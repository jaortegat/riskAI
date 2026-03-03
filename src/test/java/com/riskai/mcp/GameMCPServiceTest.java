package com.riskai.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.riskai.config.MapDefinition;
import com.riskai.config.MapLoader;
import com.riskai.dto.AttackOptionDTO;
import com.riskai.dto.AttackResult;
import com.riskai.dto.GameStateDTO;
import com.riskai.dto.GameSummaryDTO;
import com.riskai.dto.PlayerDTO;
import com.riskai.dto.TerritoryDTO;
import com.riskai.dto.TurnStatusDTO;
import com.riskai.model.CPUDifficulty;
import com.riskai.model.Game;
import com.riskai.model.GameMode;
import com.riskai.model.GamePhase;
import com.riskai.model.GameStatus;
import com.riskai.model.Player;
import com.riskai.model.PlayerColor;
import com.riskai.model.PlayerType;
import com.riskai.model.Territory;
import com.riskai.service.CPUPlayerService;
import com.riskai.service.GameService;
import com.riskai.websocket.GameWebSocketHandler;

/**
 * Unit tests for GameMCPService — verifies MCP tool methods delegate correctly
 * and return proper DTOs.
 */
@ExtendWith(MockitoExtension.class)
class GameMCPServiceTest {

    @Mock private GameService gameService;
    @Mock private CPUPlayerService cpuPlayerService;
    @Mock private GameWebSocketHandler webSocketHandler;
    @Mock private MapLoader mapLoader;

    @InjectMocks
    private GameMCPService mcpService;

    // ── Query Tools ────────────────────────────────────────────────────

    @Nested
    @DisplayName("Query Tools")
    class QueryTools {

        @Test
        @DisplayName("listJoinableGames should return game summaries")
        void listJoinableGamesShouldReturnSummaries() {
            Game game = buildTestGame();
            when(gameService.getJoinableGames()).thenReturn(List.of(game));
            when(mapLoader.getMap("classic-world")).thenReturn(
                    new MapDefinition("classic-world", "Classic World", "", "", 2, 6, List.of()));

            List<GameSummaryDTO> result = mcpService.listJoinableGames();

            assertEquals(1, result.size());
            assertEquals("g1", result.get(0).getId());
            assertTrue(result.get(0).isCanJoin());
        }

        @Test
        @DisplayName("listAllGames should return all game summaries")
        void listAllGamesShouldReturnAll() {
            Game game = buildTestGame();
            when(gameService.getAllGames()).thenReturn(List.of(game));
            when(mapLoader.getMap("classic-world")).thenReturn(
                    new MapDefinition("classic-world", "Classic World", "", "", 2, 6, List.of()));

            List<GameSummaryDTO> result = mcpService.listAllGames();

            assertEquals(1, result.size());
        }

        @Test
        @DisplayName("getGameState should delegate and return DTO")
        void getGameStateShouldDelegate() {
            GameStateDTO expected = GameStateDTO.builder().gameId("g1").build();
            when(gameService.getGameState("g1")).thenReturn(expected);

            GameStateDTO result = mcpService.getGameState("g1");

            assertEquals("g1", result.getGameId());
            verify(gameService).getGameState("g1");
        }

        @Test
        @DisplayName("getPlayerTerritories should filter by player ID")
        void getPlayerTerritoriesShouldFilter() {
            TerritoryDTO owned = TerritoryDTO.builder()
                    .territoryKey("brazil").ownerId("p1").build();
            TerritoryDTO notOwned = TerritoryDTO.builder()
                    .territoryKey("argentina").ownerId("p2").build();
            GameStateDTO state = GameStateDTO.builder()
                    .gameId("g1")
                    .territories(List.of(owned, notOwned))
                    .build();
            when(gameService.getGameState("g1")).thenReturn(state);

            List<TerritoryDTO> result = mcpService.getPlayerTerritories("g1", "p1");

            assertEquals(1, result.size());
            assertEquals("brazil", result.get(0).getTerritoryKey());
        }

        @Test
        @DisplayName("getAttackableTargets should return enemy neighbors with maxAttackArmies")
        void getAttackableTargetsShouldReturnEnemyNeighbors() {
            TerritoryDTO source = TerritoryDTO.builder()
                    .territoryKey("brazil").ownerId("p1")
                    .armies(5)
                    .neighborKeys(Set.of("argentina", "peru"))
                    .build();
            TerritoryDTO enemyNeighbor = TerritoryDTO.builder()
                    .territoryKey("argentina").ownerId("p2").build();
            TerritoryDTO friendlyNeighbor = TerritoryDTO.builder()
                    .territoryKey("peru").ownerId("p1").build();
            TerritoryDTO farAway = TerritoryDTO.builder()
                    .territoryKey("alaska").ownerId("p2").build();
            GameStateDTO state = GameStateDTO.builder()
                    .gameId("g1")
                    .territories(List.of(source, enemyNeighbor, friendlyNeighbor, farAway))
                    .build();
            when(gameService.getGameState("g1")).thenReturn(state);

            List<AttackOptionDTO> result = mcpService.getAttackableTargets("g1", "brazil");

            assertEquals(1, result.size());
            assertEquals("argentina", result.get(0).target().getTerritoryKey());
            assertEquals(3, result.get(0).maxAttackArmies()); // min(3, 5-1) = 3
        }

        @Test
        @DisplayName("getAttackableTargets maxAttackArmies respects low army counts")
        void getAttackableTargetsShouldCapMaxArmiesAtTwoForSmallForce() {
            TerritoryDTO source = TerritoryDTO.builder()
                    .territoryKey("brazil").ownerId("p1")
                    .armies(2)
                    .neighborKeys(Set.of("argentina"))
                    .build();
            TerritoryDTO enemy = TerritoryDTO.builder()
                    .territoryKey("argentina").ownerId("p2").build();
            GameStateDTO state = GameStateDTO.builder()
                    .gameId("g1")
                    .territories(List.of(source, enemy))
                    .build();
            when(gameService.getGameState("g1")).thenReturn(state);

            List<AttackOptionDTO> result = mcpService.getAttackableTargets("g1", "brazil");

            assertEquals(1, result.size());
            assertEquals(1, result.get(0).maxAttackArmies()); // min(3, 2-1) = 1
        }

        @Test
        @DisplayName("getAttackableTargets should throw for unknown territory")
        void getAttackableTargetsShouldThrowForUnknown() {
            GameStateDTO state = GameStateDTO.builder()
                    .gameId("g1")
                    .territories(List.of())
                    .build();
            when(gameService.getGameState("g1")).thenReturn(state);

            assertThrows(IllegalArgumentException.class,
                    () -> mcpService.getAttackableTargets("g1", "nonexistent"));
        }
    }

    // ── Game Lifecycle Tools ───────────────────────────────────────────

    @Nested
    @DisplayName("Lifecycle Tools")
    class LifecycleTools {

        @Test
        @DisplayName("joinGame should delegate and broadcast")
        void joinGameShouldDelegateAndBroadcast() {
            Player player = Player.builder()
                    .id("p1").name("AgentSmith").color(PlayerColor.BLUE)
                    .type(PlayerType.AI_AGENT).build();
            when(gameService.joinGame(eq("g1"), any(), eq("mcp-AgentSmith"), eq(PlayerType.AI_AGENT))).thenReturn(player);

            PlayerDTO result = mcpService.joinGame("g1", "AgentSmith");

            assertEquals("p1", result.getId());
            assertEquals("AgentSmith", result.getName());
            assertEquals(PlayerType.AI_AGENT, result.getType());
            verify(webSocketHandler).broadcastPlayerJoined(eq("g1"), any(PlayerDTO.class));
            verify(webSocketHandler).broadcastGameUpdate("g1");
        }
    }

    // ── Reinforcement Tools ────────────────────────────────────────────

    @Nested
    @DisplayName("Reinforcement Tools")
    class ReinforcementTools {

        @Test
        @DisplayName("placeArmies should delegate and broadcast")
        void placeArmiesShouldDelegate() {
            TerritoryDTO expectedDTO = TerritoryDTO.builder()
                    .territoryKey("brazil").name("Brazil").armies(5).build();
            GameStateDTO stateWithTerritory = GameStateDTO.builder()
                    .gameId("g1")
                    .territories(List.of(expectedDTO))
                    .build();
            when(gameService.getGameState("g1")).thenReturn(stateWithTerritory);

            TerritoryDTO result = mcpService.placeArmies("g1", "p1", "brazil", 3);

            assertEquals("brazil", result.getTerritoryKey());
            assertEquals(5, result.getArmies());
            verify(gameService).placeArmies("g1", "p1", "brazil", 3);
            verify(webSocketHandler).broadcastGameUpdate("g1");
        }
    }

    // ── Attack Tools ───────────────────────────────────────────────────

    @Nested
    @DisplayName("Attack Tools")
    class AttackTools {

        @Test
        @DisplayName("attack should delegate and return result map")
        void attackShouldDelegate() {
            AttackResult attackResult = AttackResult.builder()
                    .attackerDice(new int[]{6, 5, 3})
                    .defenderDice(new int[]{4, 2})
                    .attackerLosses(0)
                    .defenderLosses(2)
                    .conquered(true)
                    .eliminatedPlayer(null)
                    .build();
            when(gameService.attack("g1", "p1", "brazil", "argentina", 3)).thenReturn(attackResult);

            Map<String, Object> result = mcpService.attack("g1", "p1", "brazil", "argentina", 3);

            assertEquals(true, result.get("conquered"));
            assertEquals(0, result.get("attackerLosses"));
            assertEquals(2, result.get("defenderLosses"));
            verify(webSocketHandler).broadcastAttackResult(eq("g1"), eq("brazil"), eq("argentina"), any());
            verify(webSocketHandler).broadcastGameUpdate("g1");
        }

        @Test
        @DisplayName("endAttackPhase should delegate and broadcast")
        void endAttackPhaseShouldDelegate() {
            GameStateDTO expected = GameStateDTO.builder().gameId("g1").build();
            when(gameService.getGameState("g1")).thenReturn(expected);

            GameStateDTO result = mcpService.endAttackPhase("g1", "p1");

            assertEquals("g1", result.getGameId());
            verify(gameService).endAttackPhase("g1", "p1");
            verify(webSocketHandler).broadcastGameUpdate("g1");
        }
    }

    // ── Fortification Tools ────────────────────────────────────────────

    @Nested
    @DisplayName("Fortification Tools")
    class FortificationTools {

        @Test
        @DisplayName("fortify should delegate, broadcast, and trigger CPU")
        void fortifyShouldDelegate() {
            GameStateDTO state = GameStateDTO.builder()
                    .gameId("g1").status(GameStatus.IN_PROGRESS).build();
            when(gameService.getGameState("g1")).thenReturn(state);

            GameStateDTO result = mcpService.fortify("g1", "p1", "brazil", "argentina", 2);

            assertEquals("g1", result.getGameId());
            verify(gameService).fortify("g1", "p1", "brazil", "argentina", 2);
            verify(webSocketHandler).broadcastGameUpdate("g1");
            verify(cpuPlayerService).checkAndTriggerCPUTurn("g1");
        }

        @Test
        @DisplayName("fortify should broadcast game over when finished")
        void fortifyShouldBroadcastGameOver() {
            GameStateDTO state = GameStateDTO.builder()
                    .gameId("g1").status(GameStatus.FINISHED).winnerName("Alice").build();
            when(gameService.getGameState("g1")).thenReturn(state);

            mcpService.fortify("g1", "p1", "brazil", "argentina", 2);

            verify(webSocketHandler).broadcastGameOver("g1", "Alice");
        }

        @Test
        @DisplayName("skipFortify should delegate and broadcast")
        void skipFortifyShouldDelegate() {
            GameStateDTO state = GameStateDTO.builder()
                    .gameId("g1").status(GameStatus.IN_PROGRESS).build();
            when(gameService.getGameState("g1")).thenReturn(state);

            GameStateDTO result = mcpService.skipFortify("g1", "p1");

            assertEquals("g1", result.getGameId());
            verify(gameService).skipFortify("g1", "p1");
            verify(webSocketHandler).broadcastGameUpdate("g1");
            verify(cpuPlayerService).checkAndTriggerCPUTurn("g1");
        }

        @Test
        @DisplayName("skipFortify should broadcast game over when finished")
        void skipFortifyShouldBroadcastGameOver() {
            GameStateDTO state = GameStateDTO.builder()
                    .gameId("g1").status(GameStatus.FINISHED).winnerName("Bob").build();
            when(gameService.getGameState("g1")).thenReturn(state);

            mcpService.skipFortify("g1", "p1");

            verify(webSocketHandler).broadcastGameOver("g1", "Bob");
        }
    }

    // ── Turn Status Tools ───────────────────────────────────────────────

    @Nested
    @DisplayName("Turn Status Tools")
    class TurnStatusTools {

        @Test
        @DisplayName("getMyTurnStatus should report REINFORCEMENT phase when it's my turn")
        void shouldReportReinforcementPhase() {
            GameStateDTO state = GameStateDTO.builder()
                    .gameId("g1")
                    .status(GameStatus.IN_PROGRESS)
                    .currentPhase(GamePhase.REINFORCEMENT)
                    .reinforcementsRemaining(5)
                    .turnNumber(1)
                    .currentPlayer(PlayerDTO.builder().id("p1").name("Agent").build())
                    .players(List.of(PlayerDTO.builder().id("p1").name("Agent").build()))
                    .build();
            when(gameService.getGameState("g1")).thenReturn(state);

            TurnStatusDTO result = mcpService.getMyTurnStatus("g1", "p1");

            assertTrue(result.isYourTurn());
            assertEquals(GamePhase.REINFORCEMENT, result.currentPhase());
            assertEquals(5, result.reinforcementsRemaining());
            assertTrue(result.availableActions().contains("placeArmies"));
            assertTrue(result.hint().contains("REINFORCEMENT"));
        }

        @Test
        @DisplayName("getMyTurnStatus should report ATTACK phase when it's my turn")
        void shouldReportAttackPhase() {
            GameStateDTO state = GameStateDTO.builder()
                    .gameId("g1")
                    .status(GameStatus.IN_PROGRESS)
                    .currentPhase(GamePhase.ATTACK)
                    .turnNumber(1)
                    .currentPlayer(PlayerDTO.builder().id("p1").name("Agent").build())
                    .players(List.of(PlayerDTO.builder().id("p1").name("Agent").build()))
                    .build();
            when(gameService.getGameState("g1")).thenReturn(state);

            TurnStatusDTO result = mcpService.getMyTurnStatus("g1", "p1");

            assertTrue(result.isYourTurn());
            assertEquals(GamePhase.ATTACK, result.currentPhase());
            assertTrue(result.availableActions().contains("attack"));
            assertTrue(result.availableActions().contains("endAttackPhase"));
            assertTrue(result.availableActions().contains("getAttackableTargets"));
        }

        @Test
        @DisplayName("getMyTurnStatus should report FORTIFY phase when it's my turn")
        void shouldReportFortifyPhase() {
            GameStateDTO state = GameStateDTO.builder()
                    .gameId("g1")
                    .status(GameStatus.IN_PROGRESS)
                    .currentPhase(GamePhase.FORTIFY)
                    .turnNumber(1)
                    .currentPlayer(PlayerDTO.builder().id("p1").name("Agent").build())
                    .players(List.of(PlayerDTO.builder().id("p1").name("Agent").build()))
                    .build();
            when(gameService.getGameState("g1")).thenReturn(state);

            TurnStatusDTO result = mcpService.getMyTurnStatus("g1", "p1");

            assertTrue(result.isYourTurn());
            assertEquals(GamePhase.FORTIFY, result.currentPhase());
            assertTrue(result.availableActions().contains("fortify"));
            assertTrue(result.availableActions().contains("skipFortify"));
        }

        @Test
        @DisplayName("getMyTurnStatus should report not my turn when another player is active")
        void shouldReportNotMyTurn() {
            GameStateDTO state = GameStateDTO.builder()
                    .gameId("g1")
                    .status(GameStatus.IN_PROGRESS)
                    .currentPhase(GamePhase.REINFORCEMENT)
                    .turnNumber(1)
                    .currentPlayer(PlayerDTO.builder().id("p2").name("Opponent").build())
                    .players(List.of(
                            PlayerDTO.builder().id("p1").name("Agent").build(),
                            PlayerDTO.builder().id("p2").name("Opponent").build()))
                    .build();
            when(gameService.getGameState("g1")).thenReturn(state);

            TurnStatusDTO result = mcpService.getMyTurnStatus("g1", "p1");

            assertFalse(result.isYourTurn());
            assertTrue(result.availableActions().isEmpty());
            assertTrue(result.hint().contains("Opponent"));
        }

        @Test
        @DisplayName("getMyTurnStatus should report game finished")
        void shouldReportGameFinished() {
            GameStateDTO state = GameStateDTO.builder()
                    .gameId("g1")
                    .status(GameStatus.FINISHED)
                    .currentPhase(GamePhase.GAME_OVER)
                    .winnerName("Agent")
                    .players(List.of(PlayerDTO.builder().id("p1").name("Agent").build()))
                    .build();
            when(gameService.getGameState("g1")).thenReturn(state);

            TurnStatusDTO result = mcpService.getMyTurnStatus("g1", "p1");

            assertFalse(result.isYourTurn());
            assertTrue(result.hint().contains("over"));
            assertTrue(result.hint().contains("Agent"));
        }

        @Test
        @DisplayName("getMyTurnStatus should report waiting for players")
        void shouldReportWaitingForPlayers() {
            GameStateDTO state = GameStateDTO.builder()
                    .gameId("g1")
                    .status(GameStatus.WAITING_FOR_PLAYERS)
                    .currentPhase(GamePhase.SETUP)
                    .players(List.of(PlayerDTO.builder().id("p1").name("Agent").build()))
                    .build();
            when(gameService.getGameState("g1")).thenReturn(state);

            TurnStatusDTO result = mcpService.getMyTurnStatus("g1", "p1");

            assertFalse(result.isYourTurn());
            assertTrue(result.availableActions().contains("startGame"));
            assertTrue(result.hint().contains("waiting"));
        }

        @Test
        @DisplayName("getMyTurnStatus should throw for unknown player")
        void shouldThrowForUnknownPlayer() {
            GameStateDTO state = GameStateDTO.builder()
                    .gameId("g1")
                    .status(GameStatus.IN_PROGRESS)
                    .players(List.of(PlayerDTO.builder().id("p1").name("Agent").build()))
                    .build();
            when(gameService.getGameState("g1")).thenReturn(state);

            assertThrows(IllegalArgumentException.class,
                    () -> mcpService.getMyTurnStatus("g1", "unknown"));
        }

        @Test
        @DisplayName("waitForMyTurn should return immediately when it is already my turn")
        void waitForMyTurnShouldReturnImmediately() {
            GameStateDTO state = GameStateDTO.builder()
                    .gameId("g1")
                    .status(GameStatus.IN_PROGRESS)
                    .currentPhase(GamePhase.ATTACK)
                    .turnNumber(1)
                    .currentPlayer(PlayerDTO.builder().id("p1").name("Agent").build())
                    .players(List.of(PlayerDTO.builder().id("p1").name("Agent").build()))
                    .build();
            when(gameService.getGameState("g1")).thenReturn(state);

            long start = System.currentTimeMillis();
            TurnStatusDTO result = mcpService.waitForMyTurn("g1", "p1", 10);
            long elapsed = System.currentTimeMillis() - start;

            assertTrue(result.isYourTurn());
            assertEquals(GamePhase.ATTACK, result.currentPhase());
            assertTrue(elapsed < 2000, "Should return immediately, took " + elapsed + "ms");
        }

        @Test
        @DisplayName("waitForMyTurn should return immediately when game is finished")
        void waitForMyTurnShouldReturnOnGameOver() {
            GameStateDTO state = GameStateDTO.builder()
                    .gameId("g1")
                    .status(GameStatus.FINISHED)
                    .currentPhase(GamePhase.GAME_OVER)
                    .winnerName("Agent")
                    .players(List.of(PlayerDTO.builder().id("p1").name("Agent").build()))
                    .build();
            when(gameService.getGameState("g1")).thenReturn(state);

            long start = System.currentTimeMillis();
            TurnStatusDTO result = mcpService.waitForMyTurn("g1", "p1", 10);
            long elapsed = System.currentTimeMillis() - start;

            assertFalse(result.isYourTurn());
            assertEquals(GameStatus.FINISHED, result.gameStatus());
            assertTrue(elapsed < 2000, "Should return immediately on game over, took " + elapsed + "ms");
        }

        @Test
        @DisplayName("waitForMyTurn should timeout when not my turn")
        void waitForMyTurnShouldTimeout() {
            GameStateDTO state = GameStateDTO.builder()
                    .gameId("g1")
                    .status(GameStatus.IN_PROGRESS)
                    .currentPhase(GamePhase.REINFORCEMENT)
                    .turnNumber(1)
                    .currentPlayer(PlayerDTO.builder().id("p2").name("Opponent").build())
                    .players(List.of(
                            PlayerDTO.builder().id("p1").name("Agent").build(),
                            PlayerDTO.builder().id("p2").name("Opponent").build()))
                    .build();
            when(gameService.getGameState("g1")).thenReturn(state);

            long start = System.currentTimeMillis();
            TurnStatusDTO result = mcpService.waitForMyTurn("g1", "p1", 2);
            long elapsed = System.currentTimeMillis() - start;

            assertFalse(result.isYourTurn());
            assertTrue(elapsed >= 1500, "Should wait near timeout, took " + elapsed + "ms");
            assertTrue(elapsed < 4000, "Should not overshoot timeout, took " + elapsed + "ms");
        }

        @Test
        @DisplayName("waitForMyTurn should clamp negative timeout to 1 second")
        void waitForMyTurnShouldClampNegativeTimeout() {
            GameStateDTO state = GameStateDTO.builder()
                    .gameId("g1")
                    .status(GameStatus.IN_PROGRESS)
                    .currentPhase(GamePhase.REINFORCEMENT)
                    .turnNumber(1)
                    .currentPlayer(PlayerDTO.builder().id("p2").name("Opponent").build())
                    .players(List.of(
                            PlayerDTO.builder().id("p1").name("Agent").build(),
                            PlayerDTO.builder().id("p2").name("Opponent").build()))
                    .build();
            when(gameService.getGameState("g1")).thenReturn(state);

            long start = System.currentTimeMillis();
            mcpService.waitForMyTurn("g1", "p1", -5);
            long elapsed = System.currentTimeMillis() - start;

            assertTrue(elapsed < 3000, "Negative timeout should clamp to 1s, took " + elapsed + "ms");
        }

        @Test
        @DisplayName("waitForMyTurn should return when turn changes mid-wait")
        void waitForMyTurnShouldReturnWhenTurnChanges() {
            // First call: not my turn; second call: it's my turn
            GameStateDTO notMyTurn = GameStateDTO.builder()
                    .gameId("g1")
                    .status(GameStatus.IN_PROGRESS)
                    .currentPhase(GamePhase.REINFORCEMENT)
                    .turnNumber(1)
                    .currentPlayer(PlayerDTO.builder().id("p2").name("Opponent").build())
                    .players(List.of(
                            PlayerDTO.builder().id("p1").name("Agent").build(),
                            PlayerDTO.builder().id("p2").name("Opponent").build()))
                    .build();
            GameStateDTO myTurn = GameStateDTO.builder()
                    .gameId("g1")
                    .status(GameStatus.IN_PROGRESS)
                    .currentPhase(GamePhase.REINFORCEMENT)
                    .reinforcementsRemaining(5)
                    .turnNumber(2)
                    .currentPlayer(PlayerDTO.builder().id("p1").name("Agent").build())
                    .players(List.of(
                            PlayerDTO.builder().id("p1").name("Agent").build(),
                            PlayerDTO.builder().id("p2").name("Opponent").build()))
                    .build();
            when(gameService.getGameState("g1"))
                    .thenReturn(notMyTurn)
                    .thenReturn(myTurn);

            long start = System.currentTimeMillis();
            TurnStatusDTO result = mcpService.waitForMyTurn("g1", "p1", 30);
            long elapsed = System.currentTimeMillis() - start;

            assertTrue(result.isYourTurn());
            assertEquals(5, result.reinforcementsRemaining());
            assertTrue(elapsed >= 800, "Should have waited at least one poll cycle, took " + elapsed + "ms");
            assertTrue(elapsed < 5000, "Should return quickly after turn change, took " + elapsed + "ms");
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private Game buildTestGame() {
        Player player = Player.builder()
                .id("p1").name("Alice").color(PlayerColor.RED)
                .type(PlayerType.HUMAN).build();
        return Game.builder()
                .id("g1")
                .name("Test Game")
                .mapId("classic-world")
                .status(GameStatus.WAITING_FOR_PLAYERS)
                .maxPlayers(6)
                .minPlayers(2)
                .gameMode(GameMode.CLASSIC)
                .createdAt(LocalDateTime.of(2026, 3, 1, 12, 0))
                .players(List.of(player))
                .build();
    }
}
