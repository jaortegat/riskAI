package com.risk.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.risk.dto.CreateGameRequest;
import com.risk.dto.JoinGameRequest;
import com.risk.model.CPUDifficulty;
import com.risk.model.Continent;
import com.risk.model.Game;
import com.risk.model.GameMode;
import com.risk.model.GamePhase;
import com.risk.model.GameStatus;
import com.risk.model.Player;
import com.risk.model.PlayerColor;
import com.risk.model.PlayerType;
import com.risk.model.Territory;
import com.risk.repository.ContinentRepository;
import com.risk.repository.GameRepository;
import com.risk.repository.PlayerRepository;
import com.risk.repository.TerritoryRepository;

/**
 * Unit tests for game lifecycle, reinforcements, fortification, and game state retrieval.
 * Tests span GameLifecycleService, ReinforcementService, FortificationService,
 * TurnManagementService, WinConditionService, and GameQueryService.
 */
@ExtendWith(MockitoExtension.class)
class GameServiceLifecycleTest {

    @Mock private GameRepository gameRepository;
    @Mock private PlayerRepository playerRepository;
    @Mock private TerritoryRepository territoryRepository;
    @Mock private ContinentRepository continentRepository;
    @Mock private MapService mapService;

    private GameQueryService gameQueryService;
    private WinConditionService winConditionService;
    private ReinforcementService reinforcementService;
    private TurnManagementService turnManagementService;
    private FortificationService fortificationService;
    private GameLifecycleService lifecycleService;

    private Game game;
    private Player player1;
    private Player player2;

    @BeforeEach
    void setUp() {
        gameQueryService = new GameQueryService(gameRepository, territoryRepository, continentRepository);
        winConditionService = new WinConditionService(gameRepository, playerRepository, territoryRepository);
        reinforcementService = new ReinforcementService(gameRepository, territoryRepository, continentRepository, gameQueryService);
        turnManagementService = new TurnManagementService(gameRepository, gameQueryService, winConditionService, reinforcementService);
        fortificationService = new FortificationService(gameRepository, territoryRepository, gameQueryService, turnManagementService);
        lifecycleService = new GameLifecycleService(gameRepository, playerRepository, territoryRepository, mapService, gameQueryService, reinforcementService);

        player1 = Player.builder()
                .id("p1").name("Alice").color(PlayerColor.RED)
                .type(PlayerType.HUMAN).turnOrder(0).eliminated(false).build();

        player2 = Player.builder()
                .id("p2").name("Bob").color(PlayerColor.BLUE)
                .type(PlayerType.HUMAN).turnOrder(1).eliminated(false).build();

        game = Game.builder()
                .id("game-1").name("Test Game")
                .status(GameStatus.IN_PROGRESS)
                .currentPhase(GamePhase.REINFORCEMENT)
                .currentPlayerIndex(0).turnNumber(1)
                .reinforcementsRemaining(5)
                .maxPlayers(6).minPlayers(2)
                .mapId("classic-world")
                .gameMode(GameMode.CLASSIC)
                .players(new ArrayList<>(List.of(player1, player2)))
                .territories(new HashSet<>())
                .build();

        player1.setGame(game);
        player2.setGame(game);
    }

    @Nested
    @DisplayName("createGame()")
    class CreateGameTests {

        @Test
        @DisplayName("should create game with correct initial state")
        void shouldCreateGameCorrectly() {
            CreateGameRequest request = CreateGameRequest.builder()
                    .gameName("New Game")
                    .playerName("Alice")
                    .maxPlayers(4)
                    .minPlayers(2)
                    .cpuPlayerCount(1)
                    .cpuDifficulty(CPUDifficulty.EASY)
                    .build();

            when(gameRepository.save(any(Game.class))).thenAnswer(inv -> {
                Game g = inv.getArgument(0);
                if (g.getId() == null) g.setId("new-game-id");
                if (g.getPlayers() == null) g.setPlayers(new ArrayList<>());
                return g;
            });
            when(playerRepository.save(any(Player.class))).thenAnswer(inv -> {
                Player p = inv.getArgument(0);
                if (p.getId() == null) p.setId(UUID.randomUUID().toString());
                return p;
            });

            Game result = lifecycleService.createGame(request, "session-1");

            assertNotNull(result);
            assertEquals("New Game", result.getName());
            assertEquals(GameStatus.WAITING_FOR_PLAYERS, result.getStatus());
            assertEquals(GamePhase.SETUP, result.getCurrentPhase());
            assertEquals(1, result.getTurnNumber());
            verify(mapService).initializeMap(any(Game.class));
        }
    }

    @Nested
    @DisplayName("joinGame()")
    class JoinGameTests {

        @Test
        @DisplayName("should reject join when game is not waiting for players")
        void shouldRejectWhenNotWaiting() {
            game.setStatus(GameStatus.IN_PROGRESS);
            when(gameRepository.findById("game-1")).thenReturn(Optional.of(game));

            JoinGameRequest request = JoinGameRequest.builder().playerName("Charlie").build();

            assertThrows(IllegalStateException.class,
                    () -> lifecycleService.joinGame("game-1", request, "session-2"));
        }

        @Test
        @DisplayName("should reject join when game is full")
        void shouldRejectWhenFull() {
            game.setStatus(GameStatus.WAITING_FOR_PLAYERS);
            game.setMaxPlayers(2); // Already has 2 players
            when(gameRepository.findById("game-1")).thenReturn(Optional.of(game));

            JoinGameRequest request = JoinGameRequest.builder().playerName("Charlie").build();

            assertThrows(IllegalStateException.class,
                    () -> lifecycleService.joinGame("game-1", request, "session-2"));
        }

        @Test
        @DisplayName("should reject join when name already taken")
        void shouldRejectDuplicateName() {
            game.setStatus(GameStatus.WAITING_FOR_PLAYERS);
            when(gameRepository.findById("game-1")).thenReturn(Optional.of(game));
            when(playerRepository.existsByGameIdAndName("game-1", "Alice")).thenReturn(true);

            JoinGameRequest request = JoinGameRequest.builder().playerName("Alice").build();

            assertThrows(IllegalArgumentException.class,
                    () -> lifecycleService.joinGame("game-1", request, "session-2"));
        }
    }

    @Nested
    @DisplayName("addCPUPlayer()")
    class AddCPUPlayerTests {

        @Test
        @DisplayName("should reject when game is full")
        void shouldRejectWhenFull() {
            game.setMaxPlayers(2); // Already has 2 players
            when(gameRepository.findById("game-1")).thenReturn(Optional.of(game));

            assertThrows(IllegalStateException.class,
                    () -> lifecycleService.addCPUPlayer("game-1", CPUDifficulty.MEDIUM));
        }
    }

    @Nested
    @DisplayName("startGame()")
    class StartGameTests {

        @Test
        @DisplayName("should reject start when not enough players")
        void shouldRejectWhenNotEnoughPlayers() {
            game.setPlayers(new ArrayList<>(List.of(player1))); // Only 1 player
            game.setMinPlayers(2);
            game.setStatus(GameStatus.WAITING_FOR_PLAYERS);
            when(gameRepository.findById("game-1")).thenReturn(Optional.of(game));

            assertThrows(IllegalStateException.class,
                    () -> lifecycleService.startGame("game-1"));
        }
    }

    @Nested
    @DisplayName("placeArmies()")
    class PlaceArmiesTests {

        @Test
        @DisplayName("should place armies on owned territory")
        void shouldPlaceArmiesCorrectly() {
            Territory territory = Territory.builder()
                    .id("t1").territoryKey("brazil").owner(player1).armies(3)
                    .game(game).neighborKeys(new HashSet<>()).build();

            when(gameRepository.findById("game-1")).thenReturn(Optional.of(game));
            when(territoryRepository.findByGameIdAndTerritoryKey("game-1", "brazil"))
                    .thenReturn(Optional.of(territory));
            when(territoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(gameRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Territory result = reinforcementService.placeArmies("game-1", "p1", "brazil", 3);

            assertEquals(6, result.getArmies(), "3 original + 3 placed");
            assertEquals(2, game.getReinforcementsRemaining(), "5 - 3 = 2");
        }

        @Test
        @DisplayName("should transition to ATTACK when all reinforcements placed")
        void shouldTransitionToAttackPhase() {
            Territory territory = Territory.builder()
                    .id("t1").territoryKey("brazil").owner(player1).armies(3)
                    .game(game).neighborKeys(new HashSet<>()).build();

            when(gameRepository.findById("game-1")).thenReturn(Optional.of(game));
            when(territoryRepository.findByGameIdAndTerritoryKey("game-1", "brazil"))
                    .thenReturn(Optional.of(territory));
            when(territoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(gameRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            reinforcementService.placeArmies("game-1", "p1", "brazil", 5);

            assertEquals(GamePhase.ATTACK, game.getCurrentPhase());
            assertEquals(0, game.getReinforcementsRemaining());
        }

        @Test
        @DisplayName("should reject when not in reinforcement phase")
        void shouldRejectWrongPhase() {
            game.setCurrentPhase(GamePhase.ATTACK);
            when(gameRepository.findById("game-1")).thenReturn(Optional.of(game));

            assertThrows(IllegalStateException.class,
                    () -> reinforcementService.placeArmies("game-1", "p1", "brazil", 3));
        }

        @Test
        @DisplayName("should reject when requesting more than available reinforcements")
        void shouldRejectTooManyArmies() {
            when(gameRepository.findById("game-1")).thenReturn(Optional.of(game));

            assertThrows(IllegalArgumentException.class,
                    () -> reinforcementService.placeArmies("game-1", "p1", "brazil", 10));
        }

        @Test
        @DisplayName("should reject when territory not owned by current player")
        void shouldRejectNotOwnedTerritory() {
            Territory territory = Territory.builder()
                    .id("t1").territoryKey("brazil").owner(player2).armies(3)
                    .game(game).neighborKeys(new HashSet<>()).build();

            when(gameRepository.findById("game-1")).thenReturn(Optional.of(game));
            when(territoryRepository.findByGameIdAndTerritoryKey("game-1", "brazil"))
                    .thenReturn(Optional.of(territory));

            assertThrows(IllegalArgumentException.class,
                    () -> reinforcementService.placeArmies("game-1", "p1", "brazil", 3));
        }
    }

    @Nested
    @DisplayName("endAttackPhase()")
    class EndAttackPhaseTests {

        @Test
        @DisplayName("should transition from ATTACK to FORTIFY")
        void shouldTransitionToFortify() {
            game.setCurrentPhase(GamePhase.ATTACK);
            when(gameRepository.findById("game-1")).thenReturn(Optional.of(game));
            when(gameRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Game result = turnManagementService.endAttackPhase("game-1", "p1");

            assertEquals(GamePhase.FORTIFY, result.getCurrentPhase());
        }

        @Test
        @DisplayName("should reject when not in attack phase")
        void shouldRejectWrongPhase() {
            game.setCurrentPhase(GamePhase.REINFORCEMENT);
            when(gameRepository.findById("game-1")).thenReturn(Optional.of(game));

            assertThrows(IllegalStateException.class,
                    () -> turnManagementService.endAttackPhase("game-1", "p1"));
        }
    }

    @Nested
    @DisplayName("fortify()")
    class FortifyTests {

        @Test
        @DisplayName("should reject when territories not owned by current player")
        void shouldRejectWhenNotOwnedTerritory() {
            game.setCurrentPhase(GamePhase.FORTIFY);
            Territory from = Territory.builder()
                    .id("t1").territoryKey("brazil").owner(player1).armies(5)
                    .neighborKeys(new HashSet<>(Set.of("argentina"))).build();
            Territory to = Territory.builder()
                    .id("t2").territoryKey("argentina").owner(player2).armies(2)
                    .neighborKeys(new HashSet<>(Set.of("brazil"))).build();

            when(gameRepository.findById("game-1")).thenReturn(Optional.of(game));
            when(territoryRepository.findByGameIdAndTerritoryKey("game-1", "brazil"))
                    .thenReturn(Optional.of(from));
            when(territoryRepository.findByGameIdAndTerritoryKey("game-1", "argentina"))
                    .thenReturn(Optional.of(to));

            assertThrows(IllegalArgumentException.class,
                    () -> fortificationService.fortify("game-1", "p1", "brazil", "argentina", 2));
        }

        @Test
        @DisplayName("should reject when territories not adjacent")
        void shouldRejectWhenNotAdjacent() {
            game.setCurrentPhase(GamePhase.FORTIFY);
            Territory from = Territory.builder()
                    .id("t1").territoryKey("brazil").owner(player1).armies(5)
                    .neighborKeys(new HashSet<>()).build(); // no neighbors
            Territory to = Territory.builder()
                    .id("t2").territoryKey("alaska").owner(player1).armies(2)
                    .neighborKeys(new HashSet<>()).build();

            when(gameRepository.findById("game-1")).thenReturn(Optional.of(game));
            when(territoryRepository.findByGameIdAndTerritoryKey("game-1", "brazil"))
                    .thenReturn(Optional.of(from));
            when(territoryRepository.findByGameIdAndTerritoryKey("game-1", "alaska"))
                    .thenReturn(Optional.of(to));

            assertThrows(IllegalArgumentException.class,
                    () -> fortificationService.fortify("game-1", "p1", "brazil", "alaska", 2));
        }

        @Test
        @DisplayName("should reject when moving all armies (must leave 1)")
        void shouldRejectMovingAllArmies() {
            game.setCurrentPhase(GamePhase.FORTIFY);
            Territory from = Territory.builder()
                    .id("t1").territoryKey("brazil").owner(player1).armies(3)
                    .neighborKeys(new HashSet<>(Set.of("argentina"))).build();
            Territory to = Territory.builder()
                    .id("t2").territoryKey("argentina").owner(player1).armies(2)
                    .neighborKeys(new HashSet<>(Set.of("brazil"))).build();

            when(gameRepository.findById("game-1")).thenReturn(Optional.of(game));
            when(territoryRepository.findByGameIdAndTerritoryKey("game-1", "brazil"))
                    .thenReturn(Optional.of(from));
            when(territoryRepository.findByGameIdAndTerritoryKey("game-1", "argentina"))
                    .thenReturn(Optional.of(to));

            assertThrows(IllegalArgumentException.class,
                    () -> fortificationService.fortify("game-1", "p1", "brazil", "argentina", 3),
                    "Moving 3/3 armies should fail — must leave at least 1");
        }

        @Test
        @DisplayName("should reject fortify when not in fortify phase")
        void shouldRejectWhenNotInFortifyPhase() {
            game.setCurrentPhase(GamePhase.ATTACK);
            when(gameRepository.findById("game-1")).thenReturn(Optional.of(game));

            assertThrows(IllegalStateException.class,
                    () -> fortificationService.fortify("game-1", "p1", "brazil", "argentina", 2));
        }

        @Test
        @DisplayName("should successfully fortify and end turn")
        void shouldFortifySuccessfully() {
            game.setCurrentPhase(GamePhase.FORTIFY);
            Territory from = Territory.builder()
                    .id("t1").territoryKey("brazil").owner(player1).armies(5)
                    .neighborKeys(new HashSet<>(Set.of("argentina"))).build();
            Territory to = Territory.builder()
                    .id("t2").territoryKey("argentina").owner(player1).armies(2)
                    .neighborKeys(new HashSet<>(Set.of("brazil"))).build();

            when(gameRepository.findById("game-1")).thenReturn(Optional.of(game));
            when(territoryRepository.findByGameIdAndTerritoryKey("game-1", "brazil"))
                    .thenReturn(Optional.of(from));
            when(territoryRepository.findByGameIdAndTerritoryKey("game-1", "argentina"))
                    .thenReturn(Optional.of(to));
            when(territoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(gameRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Game result = fortificationService.fortify("game-1", "p1", "brazil", "argentina", 2);

            assertEquals(3, from.getArmies(), "5 - 2 = 3");
            assertEquals(4, to.getArmies(), "2 + 2 = 4");
            verify(territoryRepository, times(2)).save(any());
        }
    }

    @Nested
    @DisplayName("skipFortify()")
    class SkipFortifyTests {

        @Test
        @DisplayName("should skip fortify and end turn")
        void shouldSkipFortifySuccessfully() {
            game.setCurrentPhase(GamePhase.FORTIFY);
            when(gameRepository.findById("game-1")).thenReturn(Optional.of(game));
            when(gameRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Game result = fortificationService.skipFortify("game-1", "p1");

            assertNotNull(result);
            // save called twice: once in endTurn, once in skipFortify
            verify(gameRepository, times(2)).save(any());
        }

        @Test
        @DisplayName("should reject skipFortify when not in fortify phase")
        void shouldRejectSkipFortifyWhenNotInFortifyPhase() {
            game.setCurrentPhase(GamePhase.ATTACK);
            when(gameRepository.findById("game-1")).thenReturn(Optional.of(game));

            assertThrows(IllegalStateException.class,
                    () -> fortificationService.skipFortify("game-1", "p1"));
        }

        @Test
        @DisplayName("should reject skipFortify when wrong player")
        void shouldRejectSkipFortifyForWrongPlayer() {
            game.setCurrentPhase(GamePhase.FORTIFY);
            when(gameRepository.findById("game-1")).thenReturn(Optional.of(game));

            assertThrows(IllegalStateException.class,
                    () -> fortificationService.skipFortify("game-1", "p2"));
        }
    }

    @Nested
    @DisplayName("calculateReinforcements()")
    class CalculateReinforcementsTests {

        @Test
        @DisplayName("should return minimum 3 reinforcements")
        void shouldReturnMinimumThree() {
            player1.setTerritories(new ArrayList<>()); // 0 territories
            when(continentRepository.findByGameIdWithTerritories("game-1")).thenReturn(List.of());

            int reinforcements = reinforcementService.calculateReinforcements(player1);

            assertEquals(3, reinforcements, "Minimum reinforcements is 3");
        }

        @Test
        @DisplayName("should calculate territories / 3 when > 9")
        void shouldCalculateBasedOnTerritoryCount() {
            // Create 12 territories for player
            List<Territory> territories = new ArrayList<>();
            for (int i = 0; i < 12; i++) {
                territories.add(Territory.builder().id("t" + i).owner(player1).build());
            }
            player1.setTerritories(territories);
            when(continentRepository.findByGameIdWithTerritories("game-1")).thenReturn(List.of());

            int reinforcements = reinforcementService.calculateReinforcements(player1);

            assertEquals(4, reinforcements, "12 / 3 = 4");
        }

        @Test
        @DisplayName("should add continent bonus when player controls entire continent")
        void shouldAddContinentBonus() {
            Territory t1 = Territory.builder().id("t1").owner(player1).build();
            Territory t2 = Territory.builder().id("t2").owner(player1).build();

            Continent continent = Continent.builder()
                    .id("c1").name("South America").bonusArmies(2).game(game)
                    .territories(new ArrayList<>(List.of(t1, t2))).build();

            player1.setTerritories(new ArrayList<>(List.of(t1, t2)));
            when(continentRepository.findByGameIdWithTerritories("game-1"))
                    .thenReturn(List.of(continent));

            int reinforcements = reinforcementService.calculateReinforcements(player1);

            // 2 territories / 3 = 0 → min 3, plus 2 bonus = 5
            assertEquals(5, reinforcements);
        }

        @Test
        @DisplayName("should return 0 for null player")
        void shouldReturnZeroForNullPlayer() {
            int reinforcements = reinforcementService.calculateReinforcements(null);
            assertEquals(0, reinforcements);
        }
    }

    @Nested
    @DisplayName("getGame()")
    class GetGameTests {

        @Test
        @DisplayName("should throw when game not found")
        void shouldThrowWhenNotFound() {
            when(gameRepository.findById("nonexistent")).thenReturn(Optional.empty());

            assertThrows(IllegalArgumentException.class,
                    () -> gameQueryService.getGame("nonexistent"));
        }

        @Test
        @DisplayName("should return game when found")
        void shouldReturnGameWhenFound() {
            when(gameRepository.findById("game-1")).thenReturn(Optional.of(game));

            Game result = gameQueryService.getGame("game-1");

            assertEquals("game-1", result.getId());
        }
    }

    @Nested
    @DisplayName("checkGameOver() - Domination mode")
    class DominationTests {

        @Test
        @DisplayName("checkTurnLimit should return false for non-TURN_LIMIT mode")
        void checkTurnLimitShouldReturnFalseForClassic() {
            game.setGameMode(GameMode.CLASSIC);
            assertFalse(winConditionService.checkTurnLimit(game));
        }

        @Test
        @DisplayName("checkTurnLimit should return false when under limit")
        void checkTurnLimitShouldReturnFalseUnderLimit() {
            game.setGameMode(GameMode.TURN_LIMIT);
            game.setTurnLimit(10);
            game.setTurnNumber(5);

            assertFalse(winConditionService.checkTurnLimit(game));
        }

        @Test
        @DisplayName("checkTurnLimit should finish game when over limit")
        void checkTurnLimitShouldFinishGame() {
            game.setGameMode(GameMode.TURN_LIMIT);
            game.setTurnLimit(5);
            game.setTurnNumber(6);

            when(playerRepository.findActivePlayersByGameId("game-1"))
                    .thenReturn(List.of(player1, player2));
            when(territoryRepository.findByOwnerId("p1")).thenReturn(List.of(
                    Territory.builder().id("t1").build(),
                    Territory.builder().id("t2").build()));
            when(territoryRepository.findByOwnerId("p2")).thenReturn(List.of(
                    Territory.builder().id("t3").build()));
            when(gameRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            assertTrue(winConditionService.checkTurnLimit(game));
            assertEquals(GameStatus.FINISHED, game.getStatus());
            assertEquals(5, game.getTurnNumber(),
                    "Turn number should be reset to the limit");
            assertEquals("p1", game.getWinnerId(), "Player with most territories should win");
        }

        @Test
        @DisplayName("checkGameOver should detect domination win")
        void checkGameOverShouldDetectDominationWin() {
            game.setGameMode(GameMode.DOMINATION);
            game.setDominationPercent(60);

            when(playerRepository.findActivePlayersByGameId("game-1"))
                    .thenReturn(List.of(player1, player2));

            // 10 total territories, player1 owns 7 (70% >= 60% threshold)
            List<Territory> allTerritories = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                allTerritories.add(Territory.builder().id("t" + i).build());
            }
            when(territoryRepository.findByGameId("game-1")).thenReturn(allTerritories);

            List<Territory> player1Territories = new ArrayList<>();
            for (int i = 0; i < 7; i++) {
                player1Territories.add(Territory.builder().id("t" + i).build());
            }
            when(territoryRepository.findByOwnerId("p1")).thenReturn(player1Territories);
            when(gameRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            winConditionService.checkGameOver(game);

            assertEquals(GameStatus.FINISHED, game.getStatus());
            assertEquals(GamePhase.GAME_OVER, game.getCurrentPhase());
            assertEquals("p1", game.getWinnerId());
        }

        @Test
        @DisplayName("checkGameOver should not finish when below domination threshold")
        void checkGameOverShouldNotFinishWhenBelowThreshold() {
            game.setGameMode(GameMode.DOMINATION);
            game.setDominationPercent(60);

            when(playerRepository.findActivePlayersByGameId("game-1"))
                    .thenReturn(List.of(player1, player2));

            List<Territory> allTerritories = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                allTerritories.add(Territory.builder().id("t" + i).build());
            }
            when(territoryRepository.findByGameId("game-1")).thenReturn(allTerritories);

            // player1 owns 5 (50% < 60%)
            List<Territory> player1Territories = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                player1Territories.add(Territory.builder().id("t" + i).build());
            }
            when(territoryRepository.findByOwnerId("p1")).thenReturn(player1Territories);
            // player2 owns 5 (50% < 60%)
            List<Territory> player2Territories = new ArrayList<>();
            for (int i = 5; i < 10; i++) {
                player2Territories.add(Territory.builder().id("t" + i).build());
            }
            when(territoryRepository.findByOwnerId("p2")).thenReturn(player2Territories);

            winConditionService.checkGameOver(game);

            assertEquals(GameStatus.IN_PROGRESS, game.getStatus(), "Game should still be in progress");
        }

        @Test
        @DisplayName("checkGameOver should finish when last player standing (Classic)")
        void checkGameOverShouldFinishWhenLastPlayerStanding() {
            game.setGameMode(GameMode.CLASSIC);

            when(playerRepository.findActivePlayersByGameId("game-1"))
                    .thenReturn(List.of(player1)); // only one active player
            when(gameRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            winConditionService.checkGameOver(game);

            assertEquals(GameStatus.FINISHED, game.getStatus());
            assertEquals("p1", game.getWinnerId());
        }

        @Test
        @DisplayName("checkGameOver should not finish when multiple players active (Classic)")
        void checkGameOverShouldNotFinishWithMultiplePlayers() {
            game.setGameMode(GameMode.CLASSIC);

            when(playerRepository.findActivePlayersByGameId("game-1"))
                    .thenReturn(List.of(player1, player2));

            winConditionService.checkGameOver(game);

            assertEquals(GameStatus.IN_PROGRESS, game.getStatus());
        }
    }

    @Nested
    @DisplayName("getJoinableGames() and getAllGames()")
    class GameListTests {

        @Test
        @DisplayName("getAllGames should delegate to repository")
        void shouldDelegateGetAllGames() {
            when(gameRepository.findAll()).thenReturn(List.of(game));

            List<Game> result = gameQueryService.getAllGames();

            assertEquals(1, result.size());
            verify(gameRepository).findAll();
        }

        @Test
        @DisplayName("getJoinableGames should delegate to repository")
        void shouldDelegateGetJoinableGames() {
            when(gameRepository.findJoinableGames()).thenReturn(List.of(game));

            List<Game> result = gameQueryService.getJoinableGames();

            assertEquals(1, result.size());
            verify(gameRepository).findJoinableGames();
        }
    }

    @Nested
    @DisplayName("GameLifecycleService edge cases")
    class LifecycleEdgeCaseTests {

        @Test
        @DisplayName("createGame with null playerName should skip adding human player")
        void shouldSkipHumanWhenPlayerNameIsNull() {
            CreateGameRequest request = CreateGameRequest.builder()
                    .gameName("CPU Only Game")
                    .playerName(null) // No human player
                    .maxPlayers(4)
                    .minPlayers(2)
                    .cpuPlayerCount(2)
                    .cpuDifficulty(CPUDifficulty.EASY)
                    .build();

            when(gameRepository.save(any(Game.class))).thenAnswer(inv -> {
                Game g = inv.getArgument(0);
                if (g.getId() == null) g.setId("new-game-id");
                if (g.getPlayers() == null) g.setPlayers(new ArrayList<>());
                return g;
            });
            when(playerRepository.save(any(Player.class))).thenAnswer(inv -> {
                Player p = inv.getArgument(0);
                if (p.getId() == null) p.setId(java.util.UUID.randomUUID().toString());
                return p;
            });

            Game result = lifecycleService.createGame(request, "session-1");

            // Only CPU players, no human player
            assertEquals(2, result.getPlayers().size());
            assertTrue(result.getPlayers().stream().allMatch(p -> p.getType() == PlayerType.CPU));
        }

        @Test
        @DisplayName("createGame with blank playerName should skip adding human player")
        void shouldSkipHumanWhenPlayerNameIsBlank() {
            CreateGameRequest request = CreateGameRequest.builder()
                    .gameName("CPU Only Game")
                    .playerName("   ") // Blank name
                    .maxPlayers(4)
                    .minPlayers(2)
                    .cpuPlayerCount(1)
                    .cpuDifficulty(CPUDifficulty.MEDIUM)
                    .build();

            when(gameRepository.save(any(Game.class))).thenAnswer(inv -> {
                Game g = inv.getArgument(0);
                if (g.getId() == null) g.setId("new-game-id");
                if (g.getPlayers() == null) g.setPlayers(new ArrayList<>());
                return g;
            });
            when(playerRepository.save(any(Player.class))).thenAnswer(inv -> {
                Player p = inv.getArgument(0);
                if (p.getId() == null) p.setId(java.util.UUID.randomUUID().toString());
                return p;
            });

            Game result = lifecycleService.createGame(request, "session-1");

            assertEquals(1, result.getPlayers().size());
            assertEquals(PlayerType.CPU, result.getPlayers().get(0).getType());
        }

        @Test
        @DisplayName("getInitialArmiesPerPlayer should return correct values for all player counts")
        void shouldReturnCorrectInitialArmies() {
            assertEquals(40, lifecycleService.getInitialArmiesPerPlayer(2));
            assertEquals(35, lifecycleService.getInitialArmiesPerPlayer(3));
            assertEquals(30, lifecycleService.getInitialArmiesPerPlayer(4));
            assertEquals(25, lifecycleService.getInitialArmiesPerPlayer(5));
            assertEquals(20, lifecycleService.getInitialArmiesPerPlayer(6));
            assertEquals(30, lifecycleService.getInitialArmiesPerPlayer(7), "Default case");
            assertEquals(30, lifecycleService.getInitialArmiesPerPlayer(1), "Default case");
        }

        @Test
        @DisplayName("getAvailableColor should return first unused color")
        void shouldReturnFirstUnusedColor() {
            Game emptyGame = Game.builder()
                    .id("g1")
                    .players(new ArrayList<>())
                    .build();

            PlayerColor color = lifecycleService.getAvailableColor(emptyGame);

            assertEquals(PlayerColor.values()[0], color, "First color when none used");
        }

        @Test
        @DisplayName("getAvailableColor should throw when all colors used")
        void shouldThrowWhenAllColorsUsed() {
            List<Player> allPlayers = new ArrayList<>();
            for (PlayerColor color : PlayerColor.values()) {
                allPlayers.add(Player.builder().id("p-" + color).color(color).build());
            }
            Game fullGame = Game.builder()
                    .id("g1")
                    .players(allPlayers)
                    .build();

            assertThrows(IllegalStateException.class,
                    () -> lifecycleService.getAvailableColor(fullGame));
        }

        @Test
        @DisplayName("joinGame should succeed with valid request")
        void shouldJoinGameSuccessfully() {
            game.setStatus(GameStatus.WAITING_FOR_PLAYERS);
            game.setMaxPlayers(6);
            when(gameRepository.findById("game-1")).thenReturn(Optional.of(game));
            when(playerRepository.existsByGameIdAndName("game-1", "Charlie")).thenReturn(false);
            when(playerRepository.save(any(Player.class))).thenAnswer(inv -> {
                Player p = inv.getArgument(0);
                if (p.getId() == null) p.setId("p3");
                return p;
            });

            JoinGameRequest request = JoinGameRequest.builder().playerName("Charlie").build();
            Player result = lifecycleService.joinGame("game-1", request, "session-3");

            assertNotNull(result);
            assertEquals("Charlie", result.getName());
            assertEquals(PlayerType.HUMAN, result.getType());
            assertEquals(2, result.getTurnOrder());
        }

        @Test
        @DisplayName("addCPUPlayer should add with correct name increment")
        void shouldAddCPUWithCorrectName() {
            game.setMaxPlayers(6);
            // player1 and player2 are both HUMAN, so CPU count is 0
            when(gameRepository.findById("game-1")).thenReturn(Optional.of(game));
            when(playerRepository.save(any(Player.class))).thenAnswer(inv -> {
                Player p = inv.getArgument(0);
                if (p.getId() == null) p.setId("cpu-1");
                return p;
            });

            Player result = lifecycleService.addCPUPlayer("game-1", CPUDifficulty.HARD);

            assertNotNull(result);
            assertEquals("CPU Player 1", result.getName());
            assertEquals(PlayerType.CPU, result.getType());
            assertEquals(CPUDifficulty.HARD, result.getCpuDifficulty());
        }
    }
}
