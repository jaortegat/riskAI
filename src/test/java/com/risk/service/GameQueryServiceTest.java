package com.risk.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.risk.dto.GameStateDTO;
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
import com.risk.repository.TerritoryRepository;

/**
 * Unit tests for GameQueryService — focuses on getGameState() and validateCurrentPlayer().
 */
@ExtendWith(MockitoExtension.class)
class GameQueryServiceTest {

    @Mock private GameRepository gameRepository;
    @Mock private TerritoryRepository territoryRepository;
    @Mock private ContinentRepository continentRepository;

    private GameQueryService gameQueryService;
    private Game game;
    private Player player1;
    private Player player2;

    @BeforeEach
    void setUp() {
        gameQueryService = new GameQueryService(gameRepository, territoryRepository, continentRepository);

        player1 = Player.builder()
                .id("p1").name("Alice").color(PlayerColor.RED)
                .type(PlayerType.HUMAN).turnOrder(0).eliminated(false).build();

        player2 = Player.builder()
                .id("p2").name("Bob").color(PlayerColor.BLUE)
                .type(PlayerType.HUMAN).turnOrder(1).eliminated(false).build();

        game = Game.builder()
                .id("game-1").name("Test Game").mapId("classic-world")
                .status(GameStatus.IN_PROGRESS)
                .currentPhase(GamePhase.REINFORCEMENT)
                .currentPlayerIndex(0).turnNumber(1)
                .reinforcementsRemaining(5)
                .maxPlayers(6).minPlayers(2)
                .gameMode(GameMode.CLASSIC)
                .players(new ArrayList<>(List.of(player1, player2)))
                .territories(new HashSet<>())
                .build();

        player1.setGame(game);
        player2.setGame(game);
    }

    @Nested
    @DisplayName("getGameState()")
    class GetGameStateTests {

        @Test
        @DisplayName("should throw when game not found")
        void shouldThrowWhenGameNotFound() {
            when(gameRepository.findByIdWithPlayers("nonexistent")).thenReturn(null);

            assertThrows(IllegalArgumentException.class,
                    () -> gameQueryService.getGameState("nonexistent"));
        }

        @Test
        @DisplayName("should build complete GameStateDTO with player stats")
        void shouldBuildCompleteDTOWithPlayerStats() {
            Territory t1 = Territory.builder()
                    .id("t1").name("Brazil").territoryKey("brazil")
                    .owner(player1).armies(5).game(game)
                    .neighborKeys(new HashSet<>()).build();
            Territory t2 = Territory.builder()
                    .id("t2").name("Argentina").territoryKey("argentina")
                    .owner(player1).armies(3).game(game)
                    .neighborKeys(new HashSet<>()).build();
            Territory t3 = Territory.builder()
                    .id("t3").name("Peru").territoryKey("peru")
                    .owner(player2).armies(2).game(game)
                    .neighborKeys(new HashSet<>()).build();

            Continent continent = Continent.builder()
                    .id("c1").name("South America").continentKey("south-america")
                    .bonusArmies(2).game(game)
                    .territories(new ArrayList<>(List.of(t1, t2, t3))).build();

            when(gameRepository.findByIdWithPlayers("game-1")).thenReturn(game);
            when(territoryRepository.findByGameId("game-1")).thenReturn(List.of(t1, t2, t3));
            when(continentRepository.findByGameIdWithTerritories("game-1")).thenReturn(List.of(continent));

            GameStateDTO dto = gameQueryService.getGameState("game-1");

            assertNotNull(dto);
            assertEquals("game-1", dto.getGameId());
            assertEquals("Test Game", dto.getGameName());
            assertEquals(3, dto.getTotalTerritories());

            // Player stats should be computed from territory data
            assertEquals(2, dto.getPlayers().get(0).getTerritoryCount(), "Alice owns 2 territories");
            assertEquals(8, dto.getPlayers().get(0).getTotalArmies(), "Alice has 5+3=8 armies");
            assertEquals(1, dto.getPlayers().get(1).getTerritoryCount(), "Bob owns 1 territory");
            assertEquals(2, dto.getPlayers().get(1).getTotalArmies(), "Bob has 2 armies");

            // Current player stats
            assertNotNull(dto.getCurrentPlayer());
            assertEquals("p1", dto.getCurrentPlayer().getId());
            assertEquals(2, dto.getCurrentPlayer().getTerritoryCount());
            assertEquals(8, dto.getCurrentPlayer().getTotalArmies());

            // Territories and continents
            assertEquals(3, dto.getTerritories().size());
            assertEquals(1, dto.getContinents().size());
            assertEquals("South America", dto.getContinents().get(0).getName());
        }

        @Test
        @DisplayName("should handle territory with null owner")
        void shouldHandleTerritoryWithNullOwner() {
            Territory unownedTerritory = Territory.builder()
                    .id("t1").name("Unowned").territoryKey("unowned")
                    .owner(null).armies(0).game(game)
                    .neighborKeys(new HashSet<>()).build();

            when(gameRepository.findByIdWithPlayers("game-1")).thenReturn(game);
            when(territoryRepository.findByGameId("game-1")).thenReturn(List.of(unownedTerritory));
            when(continentRepository.findByGameIdWithTerritories("game-1")).thenReturn(List.of());

            GameStateDTO dto = gameQueryService.getGameState("game-1");

            assertNotNull(dto);
            assertEquals(1, dto.getTotalTerritories());
            // Both players should have 0 territories and 0 armies
            assertEquals(0, dto.getPlayers().get(0).getTerritoryCount());
            assertEquals(0, dto.getPlayers().get(0).getTotalArmies());
        }

        @Test
        @DisplayName("should handle game with null currentPlayer")
        void shouldHandleNullCurrentPlayer() {
            // Create a game where getCurrentPlayer() returns null
            Game gameNoCurrentPlayer = Game.builder()
                    .id("game-1").name("Test Game").mapId("classic-world")
                    .status(GameStatus.WAITING_FOR_PLAYERS)
                    .currentPhase(GamePhase.SETUP)
                    .currentPlayerIndex(-1).turnNumber(1)
                    .reinforcementsRemaining(0)
                    .maxPlayers(6).minPlayers(2)
                    .gameMode(GameMode.CLASSIC)
                    .players(new ArrayList<>())
                    .territories(new HashSet<>())
                    .build();

            when(gameRepository.findByIdWithPlayers("game-1")).thenReturn(gameNoCurrentPlayer);
            when(territoryRepository.findByGameId("game-1")).thenReturn(List.of());
            when(continentRepository.findByGameIdWithTerritories("game-1")).thenReturn(List.of());

            GameStateDTO dto = gameQueryService.getGameState("game-1");

            assertNotNull(dto);
            assertNull(dto.getCurrentPlayer());
        }

        @Test
        @DisplayName("should handle empty territories list")
        void shouldHandleEmptyTerritories() {
            when(gameRepository.findByIdWithPlayers("game-1")).thenReturn(game);
            when(territoryRepository.findByGameId("game-1")).thenReturn(List.of());
            when(continentRepository.findByGameIdWithTerritories("game-1")).thenReturn(List.of());

            GameStateDTO dto = gameQueryService.getGameState("game-1");

            assertNotNull(dto);
            assertEquals(0, dto.getTotalTerritories());
            assertEquals(0, dto.getTerritories().size());
            assertEquals(0, dto.getContinents().size());
        }

        @Test
        @DisplayName("should correctly aggregate armies for player with many territories")
        void shouldAggregateArmiesCorrectly() {
            List<Territory> territories = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                territories.add(Territory.builder()
                        .id("t" + i).name("T" + i).territoryKey("key" + i)
                        .owner(player1).armies(3).game(game)
                        .neighborKeys(new HashSet<>()).build());
            }

            when(gameRepository.findByIdWithPlayers("game-1")).thenReturn(game);
            when(territoryRepository.findByGameId("game-1")).thenReturn(territories);
            when(continentRepository.findByGameIdWithTerritories("game-1")).thenReturn(List.of());

            GameStateDTO dto = gameQueryService.getGameState("game-1");

            assertEquals(5, dto.getPlayers().get(0).getTerritoryCount());
            assertEquals(15, dto.getPlayers().get(0).getTotalArmies(), "5 territories × 3 armies = 15");
        }
    }

    @Nested
    @DisplayName("validateCurrentPlayer()")
    class ValidateCurrentPlayerTests {

        @Test
        @DisplayName("should pass when correct player is current")
        void shouldPassForCorrectPlayer() {
            // Should not throw
            gameQueryService.validateCurrentPlayer(game, "p1");
        }

        @Test
        @DisplayName("should throw when wrong player")
        void shouldThrowForWrongPlayer() {
            assertThrows(IllegalStateException.class,
                    () -> gameQueryService.validateCurrentPlayer(game, "p2"));
        }

        @Test
        @DisplayName("should throw when currentPlayer is null")
        void shouldThrowWhenCurrentPlayerIsNull() {
            Game emptyGame = Game.builder()
                    .id("game-1")
                    .players(new ArrayList<>())
                    .currentPlayerIndex(-1)
                    .build();

            assertThrows(IllegalStateException.class,
                    () -> gameQueryService.validateCurrentPlayer(emptyGame, "p1"));
        }
    }
}
