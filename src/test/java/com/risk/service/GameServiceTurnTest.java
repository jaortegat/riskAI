package com.risk.service;

import com.risk.model.*;
import com.risk.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for turn management, fortification, and win condition logic.
 * Covers BUG 1 regression (infinite loop in endTurn)
 * and BUG 4 regression (turn number correctness).
 */
@ExtendWith(MockitoExtension.class)
class GameServiceTurnTest {

    @Mock private GameRepository gameRepository;
    @Mock private PlayerRepository playerRepository;
    @Mock private TerritoryRepository territoryRepository;
    @Mock private ContinentRepository continentRepository;

    private GameQueryService gameQueryService;
    private WinConditionService winConditionService;
    private ReinforcementService reinforcementService;
    private TurnManagementService turnManagementService;
    private FortificationService fortificationService;

    private Game game;
    private List<Player> players;

    @BeforeEach
    void setUp() {
        gameQueryService = new GameQueryService(gameRepository, territoryRepository, continentRepository);
        winConditionService = new WinConditionService(gameRepository, playerRepository, territoryRepository);
        reinforcementService = new ReinforcementService(gameRepository, territoryRepository, continentRepository, gameQueryService);
        turnManagementService = new TurnManagementService(gameRepository, gameQueryService, winConditionService, reinforcementService);
        fortificationService = new FortificationService(gameRepository, territoryRepository, gameQueryService, turnManagementService);

        players = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Player p = Player.builder()
                    .id("player-" + i)
                    .name("Player " + i)
                    .color(PlayerColor.values()[i])
                    .type(PlayerType.HUMAN)
                    .turnOrder(i)
                    .eliminated(false)
                    .build();
            players.add(p);
        }

        game = Game.builder()
                .id("game-1")
                .name("Turn Test")
                .status(GameStatus.IN_PROGRESS)
                .currentPhase(GamePhase.FORTIFY)
                .currentPlayerIndex(0)
                .turnNumber(1)
                .reinforcementsRemaining(0)
                .maxPlayers(6)
                .minPlayers(2)
                .mapId("classic-world")
                .gameMode(GameMode.CLASSIC)
                .players(players)
                .build();

        // Set bidirectional relationship
        for (Player p : players) {
            p.setGame(game);
        }
    }

    @Nested
    @DisplayName("skipFortify() — endTurn path")
    class SkipFortifyTests {

        @Test
        @DisplayName("should advance to next player and set REINFORCEMENT phase")
        void shouldAdvanceToNextPlayer() {
            when(gameRepository.findById("game-1")).thenReturn(Optional.of(game));
            when(gameRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(continentRepository.findByGameIdWithTerritories("game-1")).thenReturn(List.of());

            Game result = fortificationService.skipFortify("game-1", "player-0");

            assertEquals(1, result.getCurrentPlayerIndex());
            assertEquals(GamePhase.REINFORCEMENT, result.getCurrentPhase());
        }

        @Test
        @DisplayName("should reject when not current player")
        void shouldRejectWrongPlayer() {
            when(gameRepository.findById("game-1")).thenReturn(Optional.of(game));

            assertThrows(IllegalStateException.class,
                    () -> fortificationService.skipFortify("game-1", "player-2"));
        }

        @Test
        @DisplayName("should reject when not in fortify phase")
        void shouldRejectWrongPhase() {
            game.setCurrentPhase(GamePhase.ATTACK);
            when(gameRepository.findById("game-1")).thenReturn(Optional.of(game));

            assertThrows(IllegalStateException.class,
                    () -> fortificationService.skipFortify("game-1", "player-0"));
        }
    }

    @Nested
    @DisplayName("endTurn() — BUG 1 regression: skip eliminated players")
    class SkipEliminatedPlayersTests {

        @Test
        @DisplayName("should skip eliminated players and land on next active player")
        void shouldSkipEliminatedPlayers() {
            // Player 1 is eliminated; expect skip from 0 → 2
            players.get(1).setEliminated(true);

            when(gameRepository.findById("game-1")).thenReturn(Optional.of(game));
            when(gameRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(continentRepository.findByGameIdWithTerritories("game-1")).thenReturn(List.of());

            Game result = fortificationService.skipFortify("game-1", "player-0");

            assertEquals(2, result.getCurrentPlayerIndex(), "Should skip eliminated player 1");
            assertFalse(result.getCurrentPlayer().isEliminated());
        }

        @Test
        @DisplayName("should skip multiple consecutive eliminated players")
        void shouldSkipMultipleEliminated() {
            // Players 1 and 2 are eliminated; expect skip from 0 → 3
            players.get(1).setEliminated(true);
            players.get(2).setEliminated(true);

            when(gameRepository.findById("game-1")).thenReturn(Optional.of(game));
            when(gameRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(continentRepository.findByGameIdWithTerritories("game-1")).thenReturn(List.of());

            Game result = fortificationService.skipFortify("game-1", "player-0");

            assertEquals(3, result.getCurrentPlayerIndex(), "Should skip to player 3");
        }

        @Test
        @DisplayName("should throw when all players are eliminated — circuit breaker (BUG 1)")
        void shouldThrowWhenAllEliminated() {
            // All other players eliminated — should NOT infinite loop
            players.get(1).setEliminated(true);
            players.get(2).setEliminated(true);
            players.get(3).setEliminated(true);
            // Player 0 is current and calls endTurn, wraps around, 
            // but player 0 is still active so it should land on player 0
            // Actually, let's eliminate ALL players to test the circuit breaker
            players.get(0).setEliminated(true);

            when(gameRepository.findById("game-1")).thenReturn(Optional.of(game));

            assertThrows(IllegalStateException.class,
                    () -> fortificationService.skipFortify("game-1", "player-0"),
                    "BUG 1 regression: should throw instead of infinite looping");
        }

        @Test
        @DisplayName("should handle wrap-around with eliminated players at the boundary")
        void shouldHandleWrapAroundWithEliminated() {
            // Current player is 3 (last), players 0 and 1 eliminated
            // Should wrap around: 3 → 0(skip) → 1(skip) → 2
            game.setCurrentPlayerIndex(3);
            players.get(0).setEliminated(true);
            players.get(1).setEliminated(true);

            when(gameRepository.findById("game-1")).thenReturn(Optional.of(game));
            when(gameRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(continentRepository.findByGameIdWithTerritories("game-1")).thenReturn(List.of());

            Game result = fortificationService.skipFortify("game-1", "player-3");

            assertEquals(2, result.getCurrentPlayerIndex(), "Should wrap and skip to player 2");
        }
    }

    @Nested
    @DisplayName("endTurn() — BUG 4 regression: turn number")
    class TurnNumberTests {

        @Test
        @DisplayName("turn number should increment exactly once when wrapping around")
        void turnShouldIncrementOnceOnWrap() {
            // Player 3 is current (last), all others active → should wrap to player 0
            game.setCurrentPlayerIndex(3);
            game.setTurnNumber(1);

            when(gameRepository.findById("game-1")).thenReturn(Optional.of(game));
            when(gameRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(continentRepository.findByGameIdWithTerritories("game-1")).thenReturn(List.of());

            Game result = fortificationService.skipFortify("game-1", "player-3");

            assertEquals(0, result.getCurrentPlayerIndex());
            assertEquals(2, result.getTurnNumber(),
                    "Turn number should increment by exactly 1 on wrap");
        }

        @Test
        @DisplayName("turn number should NOT increment when not wrapping")
        void turnShouldNotIncrementWithoutWrap() {
            // Player 0 → Player 1 — no wrap
            game.setTurnNumber(5);

            when(gameRepository.findById("game-1")).thenReturn(Optional.of(game));
            when(gameRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(continentRepository.findByGameIdWithTerritories("game-1")).thenReturn(List.of());

            Game result = fortificationService.skipFortify("game-1", "player-0");

            assertEquals(1, result.getCurrentPlayerIndex());
            assertEquals(5, result.getTurnNumber(),
                    "Turn number should NOT change when not wrapping");
        }

        @Test
        @DisplayName("turn number should increment once even when skipping eliminated players across wrap boundary (BUG 4)")
        void turnShouldIncrementOnceEvenWhenSkippingAcrossWrap() {
            // Player 3 is current, players 0 and 1 eliminated
            // Path: 3 → 0(skip, wrap!) → 1(skip) → 2
            // Turn should increment exactly once despite multiple skips across the wrap
            game.setCurrentPlayerIndex(3);
            game.setTurnNumber(1);
            players.get(0).setEliminated(true);
            players.get(1).setEliminated(true);

            when(gameRepository.findById("game-1")).thenReturn(Optional.of(game));
            when(gameRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(continentRepository.findByGameIdWithTerritories("game-1")).thenReturn(List.of());

            Game result = fortificationService.skipFortify("game-1", "player-3");

            assertEquals(2, result.getCurrentPlayerIndex());
            assertEquals(2, result.getTurnNumber(),
                    "BUG 4 regression: turn should increment exactly once, not per skipped player");
        }
    }

    @Nested
    @DisplayName("Turn limit game mode")
    class TurnLimitTests {

        @Test
        @DisplayName("game should end when turn limit is exceeded")
        void shouldEndGameOnTurnLimit() {
            game.setGameMode(GameMode.TURN_LIMIT);
            game.setTurnLimit(5);
            game.setTurnNumber(5); // Will become 6 after wrap → exceeds limit
            game.setCurrentPlayerIndex(3); // Last player, will wrap

            Player playerWithMost = players.get(2);

            when(gameRepository.findById("game-1")).thenReturn(Optional.of(game));
            when(gameRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(playerRepository.findActivePlayersByGameId("game-1")).thenReturn(players);
            // Player 2 has the most territories
            when(territoryRepository.findByOwnerId("player-0")).thenReturn(List.of());
            when(territoryRepository.findByOwnerId("player-1")).thenReturn(List.of());
            when(territoryRepository.findByOwnerId("player-2")).thenReturn(
                    List.of(Territory.builder().id("t1").build(), Territory.builder().id("t2").build(),
                            Territory.builder().id("t3").build()));
            when(territoryRepository.findByOwnerId("player-3")).thenReturn(List.of());

            Game result = fortificationService.skipFortify("game-1", "player-3");

            assertEquals(GameStatus.FINISHED, result.getStatus());
            assertEquals(GamePhase.GAME_OVER, result.getCurrentPhase());
            assertEquals(5, result.getTurnNumber(),
                    "Turn number should stay at the limit, not exceed it");
            assertEquals("player-2", result.getWinnerId(),
                    "Player with most territories should win");
        }
    }

    @Nested
    @DisplayName("Reinforcement calculation")
    class ReinforcementTests {

        @Test
        @DisplayName("should return minimum 3 reinforcements")
        void shouldReturnMinThree() {
            Player p = players.get(0);
            p.setGame(game);
            p.setTerritories(List.of()); // 0 territories
            when(continentRepository.findByGameIdWithTerritories("game-1")).thenReturn(List.of());

            int result = reinforcementService.calculateReinforcements(p);
            assertEquals(3, result, "Minimum reinforcements should be 3");
        }

        @Test
        @DisplayName("should calculate territories / 3 when > 9 territories")
        void shouldCalculateFromTerritoryCount() {
            Player p = players.get(0);
            p.setGame(game);
            List<Territory> territories = new ArrayList<>();
            for (int i = 0; i < 12; i++) {
                territories.add(Territory.builder().id("t" + i).build());
            }
            p.setTerritories(territories);
            when(continentRepository.findByGameIdWithTerritories("game-1")).thenReturn(List.of());

            int result = reinforcementService.calculateReinforcements(p);
            assertEquals(4, result, "12 territories / 3 = 4");
        }

        @Test
        @DisplayName("should return 0 for null player")
        void shouldReturnZeroForNull() {
            assertEquals(0, reinforcementService.calculateReinforcements(null));
        }
    }
}
