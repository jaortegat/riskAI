package com.risk.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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
    }
}
