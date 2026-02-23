package com.risk.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the Game entity.
 * Covers the nextPlayer() fix (BUG 4) and helper methods.
 */
class GameTest {

    private Game game;
    private List<Player> players;

    @BeforeEach
    void setUp() {
        players = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Player p = Player.builder()
                    .id("player-" + i)
                    .name("Player " + i)
                    .color(PlayerColor.values()[i])
                    .type(PlayerType.HUMAN)
                    .turnOrder(i)
                    .build();
            players.add(p);
        }

        game = Game.builder()
                .id("game-1")
                .name("Test Game")
                .status(GameStatus.IN_PROGRESS)
                .currentPhase(GamePhase.REINFORCEMENT)
                .currentPlayerIndex(0)
                .turnNumber(1)
                .reinforcementsRemaining(5)
                .maxPlayers(6)
                .minPlayers(2)
                .mapId("classic-world")
                .players(players)
                .build();
    }

    @Nested
    @DisplayName("nextPlayer()")
    class NextPlayerTests {

        @Test
        @DisplayName("should advance to next player index")
        void shouldAdvanceToNextPlayer() {
            assertEquals(0, game.getCurrentPlayerIndex());
            game.nextPlayer();
            assertEquals(1, game.getCurrentPlayerIndex());
        }

        @Test
        @DisplayName("should wrap around to index 0 after last player")
        void shouldWrapAroundAfterLastPlayer() {
            game.setCurrentPlayerIndex(3); // last player (0-indexed, 4 players)
            game.nextPlayer();
            assertEquals(0, game.getCurrentPlayerIndex());
        }

        @Test
        @DisplayName("should NOT increment turn number — that is handled by endTurn()")
        void shouldNotIncrementTurnNumber() {
            int turnBefore = game.getTurnNumber();
            // Cycle through all players back to index 0
            for (int i = 0; i < 4; i++) {
                game.nextPlayer();
            }
            assertEquals(0, game.getCurrentPlayerIndex());
            assertEquals(turnBefore, game.getTurnNumber(),
                    "nextPlayer() must not modify turnNumber — BUG 4 regression");
        }

        @Test
        @DisplayName("should not change turn number even when called many times")
        void shouldNotChangeTurnNumberOnMultipleCalls() {
            int turnBefore = game.getTurnNumber();
            for (int i = 0; i < 20; i++) {
                game.nextPlayer();
            }
            assertEquals(turnBefore, game.getTurnNumber(),
                    "nextPlayer() must never touch turnNumber");
        }
    }

    @Nested
    @DisplayName("getCurrentPlayer()")
    class GetCurrentPlayerTests {

        @Test
        @DisplayName("should return player at current index")
        void shouldReturnCorrectPlayer() {
            assertEquals(players.get(0), game.getCurrentPlayer());
            game.setCurrentPlayerIndex(2);
            assertEquals(players.get(2), game.getCurrentPlayer());
        }

        @Test
        @DisplayName("should return null when no players")
        void shouldReturnNullWhenNoPlayers() {
            game.setPlayers(new ArrayList<>());
            assertNull(game.getCurrentPlayer());
        }
    }

    @Nested
    @DisplayName("canStart()")
    class CanStartTests {

        @Test
        @DisplayName("should return true when enough players and waiting status")
        void shouldReturnTrueWhenReady() {
            game.setStatus(GameStatus.WAITING_FOR_PLAYERS);
            game.setMinPlayers(2);
            assertTrue(game.canStart());
        }

        @Test
        @DisplayName("should return false when game already in progress")
        void shouldReturnFalseWhenInProgress() {
            game.setStatus(GameStatus.IN_PROGRESS);
            assertFalse(game.canStart());
        }

        @Test
        @DisplayName("should return false when not enough players")
        void shouldReturnFalseWhenNotEnoughPlayers() {
            game.setStatus(GameStatus.WAITING_FOR_PLAYERS);
            game.setMinPlayers(10);
            assertFalse(game.canStart());
        }
    }

    @Nested
    @DisplayName("isFull()")
    class IsFullTests {

        @Test
        @DisplayName("should return true when at max players")
        void shouldReturnTrueAtMax() {
            game.setMaxPlayers(4); // 4 players already
            assertTrue(game.isFull());
        }

        @Test
        @DisplayName("should return false when below max")
        void shouldReturnFalseWhenBelow() {
            game.setMaxPlayers(6);
            assertFalse(game.isFull());
        }

        @Test
        @DisplayName("should return true when exceeding max players")
        void shouldReturnTrueWhenExceedingMax() {
            game.setMaxPlayers(2); // 4 players > 2 max
            assertTrue(game.isFull());
        }
    }

    @Nested
    @DisplayName("prePersist()")
    class PrePersistTests {

        @Test
        @DisplayName("should set defaults when fields are null")
        void shouldSetDefaultsWhenNull() {
            Game newGame = new Game();
            newGame.prePersist();

            assertNotNull(newGame.getCreatedAt(), "createdAt should be set");
            assertEquals(GameStatus.WAITING_FOR_PLAYERS, newGame.getStatus());
            assertEquals(GamePhase.SETUP, newGame.getCurrentPhase());
        }

        @Test
        @DisplayName("should not overwrite createdAt if already set")
        void shouldNotOverwriteCreatedAt() {
            Game newGame = new Game();
            LocalDateTime fixedTime = LocalDateTime.of(2025, 1, 1, 12, 0);
            newGame.setCreatedAt(fixedTime);
            newGame.prePersist();

            assertEquals(fixedTime, newGame.getCreatedAt(),
                    "prePersist should not overwrite existing createdAt");
        }

        @Test
        @DisplayName("should not overwrite status if already set")
        void shouldNotOverwriteStatus() {
            Game newGame = new Game();
            newGame.setStatus(GameStatus.IN_PROGRESS);
            newGame.prePersist();

            assertEquals(GameStatus.IN_PROGRESS, newGame.getStatus(),
                    "prePersist should not overwrite existing status");
        }

        @Test
        @DisplayName("should not overwrite currentPhase if already set")
        void shouldNotOverwriteCurrentPhase() {
            Game newGame = new Game();
            newGame.setCurrentPhase(GamePhase.ATTACK);
            newGame.prePersist();

            assertEquals(GamePhase.ATTACK, newGame.getCurrentPhase(),
                    "prePersist should not overwrite existing currentPhase");
        }

        @Test
        @DisplayName("should preserve all pre-existing values")
        void shouldPreserveAllPreExistingValues() {
            Game newGame = new Game();
            LocalDateTime fixedTime = LocalDateTime.of(2025, 6, 15, 10, 30);
            newGame.setCreatedAt(fixedTime);
            newGame.setStatus(GameStatus.FINISHED);
            newGame.setCurrentPhase(GamePhase.FORTIFY);
            newGame.prePersist();

            assertEquals(fixedTime, newGame.getCreatedAt());
            assertEquals(GameStatus.FINISHED, newGame.getStatus());
            assertEquals(GamePhase.FORTIFY, newGame.getCurrentPhase());
        }
    }

    @Nested
    @DisplayName("Builder defaults")
    class BuilderDefaultTests {

        @Test
        @DisplayName("should use default gameMode CLASSIC")
        void shouldDefaultToClassicMode() {
            Game built = Game.builder()
                    .id("g1").name("Test").mapId("m")
                    .status(GameStatus.WAITING_FOR_PLAYERS)
                    .currentPhase(GamePhase.SETUP)
                    .maxPlayers(6).minPlayers(2)
                    .build();

            assertEquals(GameMode.CLASSIC, built.getGameMode());
        }

        @Test
        @DisplayName("should use default dominationPercent 70")
        void shouldDefaultDominationPercent() {
            Game built = Game.builder()
                    .id("g1").name("Test").mapId("m")
                    .status(GameStatus.WAITING_FOR_PLAYERS)
                    .currentPhase(GamePhase.SETUP)
                    .maxPlayers(6).minPlayers(2)
                    .build();

            assertEquals(70, built.getDominationPercent());
        }

        @Test
        @DisplayName("should use default turnLimit 20")
        void shouldDefaultTurnLimit() {
            Game built = Game.builder()
                    .id("g1").name("Test").mapId("m")
                    .status(GameStatus.WAITING_FOR_PLAYERS)
                    .currentPhase(GamePhase.SETUP)
                    .maxPlayers(6).minPlayers(2)
                    .build();

            assertEquals(20, built.getTurnLimit());
        }

        @Test
        @DisplayName("should initialize empty players list")
        void shouldInitializeEmptyPlayersList() {
            Game built = Game.builder()
                    .id("g1").name("Test").mapId("m")
                    .status(GameStatus.WAITING_FOR_PLAYERS)
                    .currentPhase(GamePhase.SETUP)
                    .maxPlayers(6).minPlayers(2)
                    .build();

            assertNotNull(built.getPlayers());
            assertTrue(built.getPlayers().isEmpty());
        }

        @Test
        @DisplayName("should initialize empty territories set")
        void shouldInitializeEmptyTerritoriesSet() {
            Game built = Game.builder()
                    .id("g1").name("Test").mapId("m")
                    .status(GameStatus.WAITING_FOR_PLAYERS)
                    .currentPhase(GamePhase.SETUP)
                    .maxPlayers(6).minPlayers(2)
                    .build();

            assertNotNull(built.getTerritories());
            assertTrue(built.getTerritories().isEmpty());
        }
    }

    @Nested
    @DisplayName("canStart() edge cases")
    class CanStartEdgeCases {

        @Test
        @DisplayName("should return false when FINISHED")
        void shouldReturnFalseWhenFinished() {
            game.setStatus(GameStatus.FINISHED);
            assertFalse(game.canStart());
        }

        @Test
        @DisplayName("should return true with exactly minPlayers")
        void shouldReturnTrueAtExactlyMinPlayers() {
            game.setStatus(GameStatus.WAITING_FOR_PLAYERS);
            game.setMinPlayers(4); // exactly 4 players in setup
            assertTrue(game.canStart());
        }
    }

    @Nested
    @DisplayName("nextPlayer() edge cases")
    class NextPlayerEdgeCases {

        @Test
        @DisplayName("should work with single player")
        void shouldWorkWithSinglePlayer() {
            game.setPlayers(new ArrayList<>(List.of(players.get(0))));
            game.setCurrentPlayerIndex(0);

            game.nextPlayer();

            assertEquals(0, game.getCurrentPlayerIndex(),
                    "With single player, index should wrap back to 0");
        }

        @Test
        @DisplayName("should work with two players")
        void shouldWorkWithTwoPlayers() {
            game.setPlayers(new ArrayList<>(List.of(players.get(0), players.get(1))));
            game.setCurrentPlayerIndex(0);

            game.nextPlayer();
            assertEquals(1, game.getCurrentPlayerIndex());

            game.nextPlayer();
            assertEquals(0, game.getCurrentPlayerIndex());
        }
    }
}
