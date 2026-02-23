package com.risk.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import com.risk.config.MapDefinition;
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
import com.risk.model.GameMode;
import com.risk.model.GamePhase;
import com.risk.model.GameStatus;
import com.risk.model.Player;
import com.risk.model.PlayerColor;
import com.risk.model.PlayerType;
import com.risk.model.Territory;
import com.risk.service.CPUPlayerService;
import com.risk.service.GameService;
import com.risk.websocket.GameWebSocketHandler;

/**
 * Unit tests for GameController REST API.
 */
@ExtendWith(MockitoExtension.class)
class GameControllerTest {

    @Mock private GameService gameService;
    @Mock private CPUPlayerService cpuPlayerService;
    @Mock private GameWebSocketHandler webSocketHandler;
    @Mock private MapLoader mapLoader;

    @InjectMocks
    private GameController controller;

    private Game game;
    private Player humanPlayer;
    private Player cpuPlayer;

    @BeforeEach
    void setUp() {
        humanPlayer = Player.builder()
                .id("player-1").name("Alice").color(PlayerColor.RED)
                .type(PlayerType.HUMAN).turnOrder(0).build();

        cpuPlayer = Player.builder()
                .id("cpu-1").name("CPU Player 1").color(PlayerColor.BLUE)
                .type(PlayerType.CPU).cpuDifficulty(CPUDifficulty.MEDIUM).turnOrder(1).build();

        game = Game.builder()
                .id("game-1").name("Test Game")
                .status(GameStatus.WAITING_FOR_PLAYERS)
                .currentPhase(GamePhase.SETUP)
                .currentPlayerIndex(0).turnNumber(1)
                .reinforcementsRemaining(0)
                .maxPlayers(6).minPlayers(2)
                .mapId("classic-world")
                .gameMode(GameMode.CLASSIC)
                .createdAt(LocalDateTime.now())
                .players(new ArrayList<>(List.of(humanPlayer)))
                .territories(new HashSet<>())
                .build();

        humanPlayer.setGame(game);
        cpuPlayer.setGame(game);
    }

    @Nested
    @DisplayName("GET /api/games/maps")
    class GetAvailableMapsTests {

        @Test
        @DisplayName("should return list of available maps")
        void shouldReturnAvailableMaps() {
            MapDefinition mapDef = new MapDefinition("classic-world", "Classic World",
                    "Standard Risk map", "Author", 2, 6, List.of());

            when(mapLoader.getAvailableMaps()).thenReturn(List.of(mapDef));

            ResponseEntity<List<MapInfoDTO>> response = controller.getAvailableMaps();

            assertEquals(200, response.getStatusCode().value());
            assertNotNull(response.getBody());
            assertEquals(1, response.getBody().size());
            assertEquals("classic-world", response.getBody().get(0).getId());
        }

        @Test
        @DisplayName("should return empty list when no maps available")
        void shouldReturnEmptyListWhenNoMaps() {
            when(mapLoader.getAvailableMaps()).thenReturn(List.of());

            ResponseEntity<List<MapInfoDTO>> response = controller.getAvailableMaps();

            assertEquals(200, response.getStatusCode().value());
            assertTrue(response.getBody().isEmpty());
        }
    }

    @Nested
    @DisplayName("POST /api/games")
    class CreateGameTests {

        @Test
        @DisplayName("should create game and return game state")
        void shouldCreateGame() {
            CreateGameRequest request = CreateGameRequest.builder()
                    .gameName("New Game").playerName("Alice")
                    .maxPlayers(6).minPlayers(2).build();

            GameStateDTO gameState = new GameStateDTO();
            gameState.setGameId("game-1");
            gameState.setGameName("New Game");

            when(gameService.createGame(any(CreateGameRequest.class), eq("session-123")))
                    .thenReturn(game);
            when(gameService.getGameState("game-1")).thenReturn(gameState);

            ResponseEntity<GameStateDTO> response = controller.createGame(request, "session-123");

            assertEquals(200, response.getStatusCode().value());
            assertEquals("game-1", response.getBody().getGameId());
        }
    }

    @Nested
    @DisplayName("GET /api/games")
    class GetGamesTests {

        @Test
        @DisplayName("should return all games when joinableOnly is false")
        void shouldReturnAllGames() {
            MapDefinition mapDef = new MapDefinition("classic-world", "Classic World",
                    "desc", "Author", 2, 6, List.of());
            when(mapLoader.getMap("classic-world")).thenReturn(mapDef);
            when(gameService.getAllGames()).thenReturn(List.of(game));

            ResponseEntity<List<GameSummaryDTO>> response = controller.getGames(false);

            assertEquals(200, response.getStatusCode().value());
            assertEquals(1, response.getBody().size());
            assertEquals("game-1", response.getBody().get(0).getId());
        }

        @Test
        @DisplayName("should return only joinable games when joinableOnly is true")
        void shouldReturnJoinableGames() {
            MapDefinition mapDef = new MapDefinition("classic-world", "Classic World",
                    "desc", "Author", 2, 6, List.of());
            when(mapLoader.getMap("classic-world")).thenReturn(mapDef);
            when(gameService.getJoinableGames()).thenReturn(List.of(game));

            ResponseEntity<List<GameSummaryDTO>> response = controller.getGames(true);

            assertEquals(200, response.getStatusCode().value());
            verify(gameService).getJoinableGames();
            verify(gameService, never()).getAllGames();
        }
    }

    @Nested
    @DisplayName("GET /api/games/{gameId}")
    class GetGameTests {

        @Test
        @DisplayName("should return game state for given id")
        void shouldReturnGameState() {
            GameStateDTO gameState = new GameStateDTO();
            gameState.setGameId("game-1");
            when(gameService.getGameState("game-1")).thenReturn(gameState);

            ResponseEntity<GameStateDTO> response = controller.getGame("game-1");

            assertEquals(200, response.getStatusCode().value());
            assertEquals("game-1", response.getBody().getGameId());
        }
    }

    @Nested
    @DisplayName("POST /{gameId}/join")
    class JoinGameTests {

        @Test
        @DisplayName("should join game and broadcast player joined")
        void shouldJoinGameAndBroadcast() {
            JoinGameRequest request = JoinGameRequest.builder().playerName("Bob").build();
            Player bob = Player.builder()
                    .id("player-2").name("Bob").color(PlayerColor.GREEN)
                    .type(PlayerType.HUMAN).turnOrder(1).build();

            when(gameService.joinGame(eq("game-1"), any(JoinGameRequest.class), eq("session-456")))
                    .thenReturn(bob);

            ResponseEntity<PlayerDTO> response = controller.joinGame("game-1", request, "session-456");

            assertEquals(200, response.getStatusCode().value());
            assertEquals("Bob", response.getBody().getName());
            verify(webSocketHandler).broadcastPlayerJoined(eq("game-1"), any(PlayerDTO.class));
            verify(webSocketHandler).broadcastGameUpdate("game-1");
        }
    }

    @Nested
    @DisplayName("POST /{gameId}/cpu")
    class AddCPUPlayerTests {

        @Test
        @DisplayName("should add CPU player and broadcast")
        void shouldAddCPUAndBroadcast() {
            when(gameService.addCPUPlayer("game-1", CPUDifficulty.HARD)).thenReturn(cpuPlayer);

            ResponseEntity<PlayerDTO> response = controller.addCPUPlayer("game-1", CPUDifficulty.HARD);

            assertEquals(200, response.getStatusCode().value());
            assertEquals(PlayerType.CPU, response.getBody().getType());
            verify(webSocketHandler).broadcastPlayerJoined(eq("game-1"), any(PlayerDTO.class));
            verify(webSocketHandler).broadcastGameUpdate("game-1");
        }
    }

    @Nested
    @DisplayName("POST /{gameId}/start")
    class StartGameTests {

        @Test
        @DisplayName("should start game, broadcast, and trigger CPU turn check")
        void shouldStartAndBroadcast() {
            game.setStatus(GameStatus.IN_PROGRESS);
            GameStateDTO gameState = new GameStateDTO();
            gameState.setGameId("game-1");

            when(gameService.startGame("game-1")).thenReturn(game);
            when(gameService.getGameState("game-1")).thenReturn(gameState);

            ResponseEntity<GameStateDTO> response = controller.startGame("game-1");

            assertEquals(200, response.getStatusCode().value());
            verify(webSocketHandler).broadcastGameStarted("game-1");
            verify(cpuPlayerService).checkAndTriggerCPUTurn("game-1");
        }
    }

    @Nested
    @DisplayName("POST /{gameId}/reinforce")
    class ReinforceTests {

        @Test
        @DisplayName("should place armies and broadcast update")
        void shouldPlaceArmies() {
            Territory territory = Territory.builder()
                    .id("t1").territoryKey("brazil").owner(humanPlayer).armies(5)
                    .neighborKeys(new HashSet<>()).build();

            when(gameService.placeArmies("game-1", "player-1", "brazil", 3))
                    .thenReturn(territory);

            ResponseEntity<TerritoryDTO> response = controller.placeArmies(
                    "game-1", "player-1", "brazil", 3);

            assertEquals(200, response.getStatusCode().value());
            assertEquals("brazil", response.getBody().getTerritoryKey());
            verify(webSocketHandler).broadcastGameUpdate("game-1");
            verify(cpuPlayerService).checkAndTriggerCPUTurn("game-1");
        }
    }

    @Nested
    @DisplayName("POST /{gameId}/attack")
    class AttackTests {

        @Test
        @DisplayName("should execute attack and return result map")
        void shouldExecuteAttack() {
            AttackResult result = AttackResult.builder()
                    .attackerDice(new int[]{6, 5})
                    .defenderDice(new int[]{4})
                    .attackerLosses(0)
                    .defenderLosses(1)
                    .conquered(false)
                    .build();

            when(gameService.attack("game-1", "player-1", "alaska", "kamchatka", 2))
                    .thenReturn(result);

            ResponseEntity<Map<String, Object>> response = controller.attack(
                    "game-1", "player-1", "alaska", "kamchatka", 2);

            assertEquals(200, response.getStatusCode().value());
            Map<String, Object> body = response.getBody();
            assertNotNull(body);
            assertEquals(0, body.get("attackerLosses"));
            assertEquals(1, body.get("defenderLosses"));
            assertEquals(false, body.get("conquered"));
            verify(webSocketHandler).broadcastAttackResult(eq("game-1"), eq("alaska"), eq("kamchatka"), eq(result));
        }

        @Test
        @DisplayName("should return conquered=true and eliminatedPlayer when territory is conquered")
        void shouldReturnConquestWithElimination() {
            AttackResult result = AttackResult.builder()
                    .attackerDice(new int[]{6, 5, 4})
                    .defenderDice(new int[]{3, 2})
                    .attackerLosses(0)
                    .defenderLosses(2)
                    .conquered(true)
                    .eliminatedPlayer("player-2")
                    .build();

            when(gameService.attack("game-1", "player-1", "brazil", "argentina", 3))
                    .thenReturn(result);

            ResponseEntity<Map<String, Object>> response = controller.attack(
                    "game-1", "player-1", "brazil", "argentina", 3);

            assertEquals(200, response.getStatusCode().value());
            Map<String, Object> body = response.getBody();
            assertEquals(true, body.get("conquered"));
            assertEquals("player-2", body.get("eliminatedPlayer"));
            assertEquals(0, body.get("attackerLosses"));
            assertEquals(2, body.get("defenderLosses"));
            verify(webSocketHandler).broadcastAttackResult(eq("game-1"), eq("brazil"), eq("argentina"), eq(result));
            verify(webSocketHandler).broadcastGameUpdate("game-1");
        }

        @Test
        @DisplayName("should return conquered=true without elimination when defender has other territories")
        void shouldReturnConquestWithoutElimination() {
            AttackResult result = AttackResult.builder()
                    .attackerDice(new int[]{6})
                    .defenderDice(new int[]{3})
                    .attackerLosses(0)
                    .defenderLosses(1)
                    .conquered(true)
                    .eliminatedPlayer(null)
                    .build();

            when(gameService.attack("game-1", "player-1", "alaska", "kamchatka", 1))
                    .thenReturn(result);

            ResponseEntity<Map<String, Object>> response = controller.attack(
                    "game-1", "player-1", "alaska", "kamchatka", 1);

            Map<String, Object> body = response.getBody();
            assertEquals(true, body.get("conquered"));
            assertNull(body.get("eliminatedPlayer"));
        }
    }

    @Nested
    @DisplayName("POST /{gameId}/endAttack")
    class EndAttackTests {

        @Test
        @DisplayName("should end attack phase and return game state")
        void shouldEndAttack() {
            GameStateDTO gameState = new GameStateDTO();
            gameState.setGameId("game-1");

            when(gameService.getGameState("game-1")).thenReturn(gameState);

            ResponseEntity<GameStateDTO> response = controller.endAttack("game-1", "player-1");

            assertEquals(200, response.getStatusCode().value());
            verify(gameService).endAttackPhase("game-1", "player-1");
            verify(webSocketHandler).broadcastGameUpdate("game-1");
        }
    }

    @Nested
    @DisplayName("POST /{gameId}/fortify")
    class FortifyTests {

        @Test
        @DisplayName("should fortify and trigger CPU turn check")
        void shouldFortify() {
            GameStateDTO gameState = new GameStateDTO();
            gameState.setGameId("game-1");

            when(gameService.getGameState("game-1")).thenReturn(gameState);

            ResponseEntity<GameStateDTO> response = controller.fortify(
                    "game-1", "player-1", "brazil", "argentina", 2);

            assertEquals(200, response.getStatusCode().value());
            verify(gameService).fortify("game-1", "player-1", "brazil", "argentina", 2);
            verify(webSocketHandler).broadcastGameUpdate("game-1");
            verify(cpuPlayerService).checkAndTriggerCPUTurn("game-1");
        }

        @Test
        @DisplayName("should broadcast game over and NOT trigger CPU when game is finished")
        void shouldBroadcastGameOverWhenFinished() {
            GameStateDTO gameState = new GameStateDTO();
            gameState.setGameId("game-1");
            gameState.setStatus(GameStatus.FINISHED);
            gameState.setWinnerName("Player 1");

            when(gameService.getGameState("game-1")).thenReturn(gameState);

            ResponseEntity<GameStateDTO> response = controller.fortify(
                    "game-1", "player-1", "brazil", "argentina", 2);

            assertEquals(200, response.getStatusCode().value());
            verify(webSocketHandler).broadcastGameOver("game-1", "Player 1");
            verify(cpuPlayerService, never()).checkAndTriggerCPUTurn(any());
        }
    }

    @Nested
    @DisplayName("POST /{gameId}/skipFortify")
    class SkipFortifyTests {

        @Test
        @DisplayName("should skip fortify and trigger CPU turn check")
        void shouldSkipFortify() {
            GameStateDTO gameState = new GameStateDTO();
            gameState.setGameId("game-1");

            when(gameService.getGameState("game-1")).thenReturn(gameState);

            ResponseEntity<GameStateDTO> response = controller.skipFortify("game-1", "player-1");

            assertEquals(200, response.getStatusCode().value());
            verify(gameService).skipFortify("game-1", "player-1");
            verify(webSocketHandler).broadcastGameUpdate("game-1");
            verify(cpuPlayerService).checkAndTriggerCPUTurn("game-1");
        }

        @Test
        @DisplayName("should broadcast game over and NOT trigger CPU when game is finished")
        void shouldBroadcastGameOverWhenFinished() {
            GameStateDTO gameState = new GameStateDTO();
            gameState.setGameId("game-1");
            gameState.setStatus(GameStatus.FINISHED);
            gameState.setWinnerName("Player 1");

            when(gameService.getGameState("game-1")).thenReturn(gameState);

            ResponseEntity<GameStateDTO> response = controller.skipFortify("game-1", "player-1");

            assertEquals(200, response.getStatusCode().value());
            verify(webSocketHandler).broadcastGameOver("game-1", "Player 1");
            verify(cpuPlayerService, never()).checkAndTriggerCPUTurn(any());
        }
    }

    @Nested
    @DisplayName("toGameSummary() helper")
    class ToGameSummaryTests {

        @Test
        @DisplayName("should handle unknown map gracefully (swallow exception)")
        void shouldHandleUnknownMapGracefully() {
            when(mapLoader.getMap("unknown-map")).thenThrow(new IllegalArgumentException("Unknown map"));
            game.setMapId("unknown-map");

            when(gameService.getAllGames()).thenReturn(List.of(game));

            ResponseEntity<List<GameSummaryDTO>> response = controller.getGames(false);

            assertEquals(200, response.getStatusCode().value());
            assertEquals("", response.getBody().get(0).getMapName());
        }

        @Test
        @DisplayName("should show Unknown host when game has no players")
        void shouldShowUnknownHostWhenNoPlayers() {
            Game emptyGame = Game.builder()
                    .id("game-2").name("Empty").status(GameStatus.WAITING_FOR_PLAYERS)
                    .currentPhase(GamePhase.SETUP).currentPlayerIndex(0).turnNumber(1)
                    .maxPlayers(6).minPlayers(2).mapId("classic-world")
                    .gameMode(GameMode.CLASSIC).createdAt(LocalDateTime.now())
                    .players(new ArrayList<>()).territories(new HashSet<>())
                    .build();

            MapDefinition mapDef = new MapDefinition("classic-world", "Classic World",
                    "desc", "Author", 2, 6, List.of());
            when(mapLoader.getMap("classic-world")).thenReturn(mapDef);
            when(gameService.getAllGames()).thenReturn(List.of(emptyGame));

            ResponseEntity<List<GameSummaryDTO>> response = controller.getGames(false);

            assertEquals("Unknown", response.getBody().get(0).getHostName());
        }

        @Test
        @DisplayName("should populate all summary fields correctly")
        void shouldPopulateAllSummaryFields() {
            MapDefinition mapDef = new MapDefinition("classic-world", "Classic World",
                    "desc", "Author", 2, 6, List.of());
            when(mapLoader.getMap("classic-world")).thenReturn(mapDef);
            when(gameService.getAllGames()).thenReturn(List.of(game));

            ResponseEntity<List<GameSummaryDTO>> response = controller.getGames(false);

            GameSummaryDTO summary = response.getBody().get(0);
            assertEquals("game-1", summary.getId());
            assertEquals("Test Game", summary.getName());
            assertEquals("classic-world", summary.getMapId());
            assertEquals("Classic World", summary.getMapName());
            assertEquals("WAITING_FOR_PLAYERS", summary.getStatus());
            assertEquals(1, summary.getPlayerCount());
            assertEquals(6, summary.getMaxPlayers());
            assertEquals(2, summary.getMinPlayers());
            assertTrue(summary.isCanJoin(), "Game with WAITING status and not full should be joinable");
            assertEquals("Alice", summary.getHostName());
            assertEquals("CLASSIC", summary.getGameMode());
            assertNotNull(summary.getCreatedAt());
        }

        @Test
        @DisplayName("should mark canJoin=false when game is full")
        void shouldNotBeJoinableWhenFull() {
            game.setMaxPlayers(1); // Only 1 max, already has 1 player = full
            MapDefinition mapDef = new MapDefinition("classic-world", "Classic World",
                    "desc", "Author", 2, 6, List.of());
            when(mapLoader.getMap("classic-world")).thenReturn(mapDef);
            when(gameService.getAllGames()).thenReturn(List.of(game));

            ResponseEntity<List<GameSummaryDTO>> response = controller.getGames(false);

            assertFalse(response.getBody().get(0).isCanJoin());
        }

        @Test
        @DisplayName("should mark canJoin=false when game is IN_PROGRESS")
        void shouldNotBeJoinableWhenInProgress() {
            game.setStatus(GameStatus.IN_PROGRESS);
            MapDefinition mapDef = new MapDefinition("classic-world", "Classic World",
                    "desc", "Author", 2, 6, List.of());
            when(mapLoader.getMap("classic-world")).thenReturn(mapDef);
            when(gameService.getAllGames()).thenReturn(List.of(game));

            ResponseEntity<List<GameSummaryDTO>> response = controller.getGames(false);

            assertFalse(response.getBody().get(0).isCanJoin());
        }
    }

    @Nested
    @DisplayName("POST /api/games (create) edge cases")
    class CreateGameEdgeCases {

        @Test
        @DisplayName("should create game with null session id")
        void shouldCreateGameWithNullSession() {
            CreateGameRequest request = CreateGameRequest.builder()
                    .gameName("No Session Game").playerName("Alice")
                    .maxPlayers(6).minPlayers(2).build();

            GameStateDTO gameState = new GameStateDTO();
            gameState.setGameId("game-1");

            when(gameService.createGame(any(CreateGameRequest.class), eq(null)))
                    .thenReturn(game);
            when(gameService.getGameState("game-1")).thenReturn(gameState);

            ResponseEntity<GameStateDTO> response = controller.createGame(request, null);

            assertEquals(200, response.getStatusCode().value());
            verify(gameService).createGame(any(), eq(null));
        }
    }

    @Nested
    @DisplayName("POST /{gameId}/join edge cases")
    class JoinGameEdgeCases {

        @Test
        @DisplayName("should join game with null session id")
        void shouldJoinGameWithNullSession() {
            JoinGameRequest request = JoinGameRequest.builder().playerName("Charlie").build();
            Player charlie = Player.builder()
                    .id("player-3").name("Charlie").color(PlayerColor.YELLOW)
                    .type(PlayerType.HUMAN).turnOrder(2).build();

            when(gameService.joinGame(eq("game-1"), any(JoinGameRequest.class), eq(null)))
                    .thenReturn(charlie);

            ResponseEntity<PlayerDTO> response = controller.joinGame("game-1", request, null);

            assertEquals(200, response.getStatusCode().value());
            assertEquals("Charlie", response.getBody().getName());
        }
    }

    @Nested
    @DisplayName("POST /{gameId}/cpu default difficulty")
    class AddCPUDefaultDifficulty {

        @Test
        @DisplayName("should add CPU player with MEDIUM default difficulty")
        void shouldAddCPUWithDefaultDifficulty() {
            when(gameService.addCPUPlayer("game-1", CPUDifficulty.MEDIUM)).thenReturn(cpuPlayer);

            ResponseEntity<PlayerDTO> response = controller.addCPUPlayer("game-1", CPUDifficulty.MEDIUM);

            assertEquals(200, response.getStatusCode().value());
            assertEquals("CPU Player 1", response.getBody().getName());
        }

        @Test
        @DisplayName("should add CPU player with EASY difficulty")
        void shouldAddCPUWithEasyDifficulty() {
            Player easyCpu = Player.builder()
                    .id("cpu-easy").name("CPU Easy").color(PlayerColor.GREEN)
                    .type(PlayerType.CPU).cpuDifficulty(CPUDifficulty.EASY).turnOrder(1).build();

            when(gameService.addCPUPlayer("game-1", CPUDifficulty.EASY)).thenReturn(easyCpu);

            ResponseEntity<PlayerDTO> response = controller.addCPUPlayer("game-1", CPUDifficulty.EASY);

            assertEquals(200, response.getStatusCode().value());
            assertEquals(CPUDifficulty.EASY, response.getBody().getCpuDifficulty());
        }
    }
}
