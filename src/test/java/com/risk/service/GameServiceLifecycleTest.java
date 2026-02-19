package com.risk.service;

import com.risk.dto.*;
import com.risk.model.*;
import com.risk.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Additional GameService unit tests for game lifecycle, reinforcements,
 * fortification, and game state retrieval.
 */
@ExtendWith(MockitoExtension.class)
class GameServiceLifecycleTest {

    @Mock private GameRepository gameRepository;
    @Mock private PlayerRepository playerRepository;
    @Mock private TerritoryRepository territoryRepository;
    @Mock private ContinentRepository continentRepository;
    @Mock private MapService mapService;

    @InjectMocks
    private GameService gameService;

    private Game game;
    private Player player1;
    private Player player2;

    @BeforeEach
    void setUp() {
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

            Game result = gameService.createGame(request, "session-1");

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
                    () -> gameService.joinGame("game-1", request, "session-2"));
        }

        @Test
        @DisplayName("should reject join when game is full")
        void shouldRejectWhenFull() {
            game.setStatus(GameStatus.WAITING_FOR_PLAYERS);
            game.setMaxPlayers(2); // Already has 2 players
            when(gameRepository.findById("game-1")).thenReturn(Optional.of(game));

            JoinGameRequest request = JoinGameRequest.builder().playerName("Charlie").build();

            assertThrows(IllegalStateException.class,
                    () -> gameService.joinGame("game-1", request, "session-2"));
        }

        @Test
        @DisplayName("should reject join when name already taken")
        void shouldRejectDuplicateName() {
            game.setStatus(GameStatus.WAITING_FOR_PLAYERS);
            when(gameRepository.findById("game-1")).thenReturn(Optional.of(game));
            when(playerRepository.existsByGameIdAndName("game-1", "Alice")).thenReturn(true);

            JoinGameRequest request = JoinGameRequest.builder().playerName("Alice").build();

            assertThrows(IllegalArgumentException.class,
                    () -> gameService.joinGame("game-1", request, "session-2"));
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
                    () -> gameService.addCPUPlayer("game-1", CPUDifficulty.MEDIUM));
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
                    () -> gameService.startGame("game-1"));
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

            Territory result = gameService.placeArmies("game-1", "p1", "brazil", 3);

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

            gameService.placeArmies("game-1", "p1", "brazil", 5);

            assertEquals(GamePhase.ATTACK, game.getCurrentPhase());
            assertEquals(0, game.getReinforcementsRemaining());
        }

        @Test
        @DisplayName("should reject when not in reinforcement phase")
        void shouldRejectWrongPhase() {
            game.setCurrentPhase(GamePhase.ATTACK);
            when(gameRepository.findById("game-1")).thenReturn(Optional.of(game));

            assertThrows(IllegalStateException.class,
                    () -> gameService.placeArmies("game-1", "p1", "brazil", 3));
        }

        @Test
        @DisplayName("should reject when requesting more than available reinforcements")
        void shouldRejectTooManyArmies() {
            when(gameRepository.findById("game-1")).thenReturn(Optional.of(game));

            assertThrows(IllegalArgumentException.class,
                    () -> gameService.placeArmies("game-1", "p1", "brazil", 10));
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
                    () -> gameService.placeArmies("game-1", "p1", "brazil", 3));
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

            Game result = gameService.endAttackPhase("game-1", "p1");

            assertEquals(GamePhase.FORTIFY, result.getCurrentPhase());
        }

        @Test
        @DisplayName("should reject when not in attack phase")
        void shouldRejectWrongPhase() {
            game.setCurrentPhase(GamePhase.REINFORCEMENT);
            when(gameRepository.findById("game-1")).thenReturn(Optional.of(game));

            assertThrows(IllegalStateException.class,
                    () -> gameService.endAttackPhase("game-1", "p1"));
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
                    () -> gameService.fortify("game-1", "p1", "brazil", "argentina", 2));
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
                    () -> gameService.fortify("game-1", "p1", "brazil", "alaska", 2));
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
                    () -> gameService.fortify("game-1", "p1", "brazil", "argentina", 3),
                    "Moving 3/3 armies should fail — must leave at least 1");
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

            int reinforcements = gameService.calculateReinforcements(player1);

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

            int reinforcements = gameService.calculateReinforcements(player1);

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

            int reinforcements = gameService.calculateReinforcements(player1);

            // 2 territories / 3 = 0 → min 3, plus 2 bonus = 5
            assertEquals(5, reinforcements);
        }

        @Test
        @DisplayName("should return 0 for null player")
        void shouldReturnZeroForNullPlayer() {
            int reinforcements = gameService.calculateReinforcements(null);
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
                    () -> gameService.getGame("nonexistent"));
        }

        @Test
        @DisplayName("should return game when found")
        void shouldReturnGameWhenFound() {
            when(gameRepository.findById("game-1")).thenReturn(Optional.of(game));

            Game result = gameService.getGame("game-1");

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
            assertFalse(gameService.checkTurnLimit(game));
        }

        @Test
        @DisplayName("checkTurnLimit should return false when under limit")
        void checkTurnLimitShouldReturnFalseUnderLimit() {
            game.setGameMode(GameMode.TURN_LIMIT);
            game.setTurnLimit(10);
            game.setTurnNumber(5);

            assertFalse(gameService.checkTurnLimit(game));
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

            assertTrue(gameService.checkTurnLimit(game));
            assertEquals(GameStatus.FINISHED, game.getStatus());
            assertEquals("p1", game.getWinnerId(), "Player with most territories should win");
        }
    }

    @Nested
    @DisplayName("getJoinableGames() and getAllGames()")
    class GameListTests {

        @Test
        @DisplayName("getAllGames should delegate to repository")
        void shouldDelegateGetAllGames() {
            when(gameRepository.findAll()).thenReturn(List.of(game));

            List<Game> result = gameService.getAllGames();

            assertEquals(1, result.size());
            verify(gameRepository).findAll();
        }

        @Test
        @DisplayName("getJoinableGames should delegate to repository")
        void shouldDelegateGetJoinableGames() {
            when(gameRepository.findJoinableGames()).thenReturn(List.of(game));

            List<Game> result = gameService.getJoinableGames();

            assertEquals(1, result.size());
            verify(gameRepository).findJoinableGames();
        }
    }
}
