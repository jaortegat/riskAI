package com.risk.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.risk.dto.AttackResult;
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
 * Unit tests for CombatService attack/combat logic.
 * Covers BUG 3 regression (army miscalculation after conquest).
 */
@ExtendWith(MockitoExtension.class)
class GameServiceCombatTest {

    @Mock private GameRepository gameRepository;
    @Mock private PlayerRepository playerRepository;
    @Mock private TerritoryRepository territoryRepository;
    @Mock private ContinentRepository continentRepository;

    private GameQueryService gameQueryService;
    private WinConditionService winConditionService;
    private CombatService combatService;

    private Game game;
    private Player attacker;
    private Player defender;
    private Territory fromTerritory;
    private Territory toTerritory;

    @BeforeEach
    void setUp() {
        gameQueryService = new GameQueryService(gameRepository, territoryRepository, continentRepository);
        winConditionService = new WinConditionService(gameRepository, playerRepository, territoryRepository);
        combatService = new CombatService(gameRepository, territoryRepository, playerRepository, gameQueryService, winConditionService);

        attacker = Player.builder()
                .id("attacker-1")
                .name("Attacker")
                .color(PlayerColor.RED)
                .type(PlayerType.HUMAN)
                .turnOrder(0)
                .build();

        defender = Player.builder()
                .id("defender-1")
                .name("Defender")
                .color(PlayerColor.BLUE)
                .type(PlayerType.HUMAN)
                .turnOrder(1)
                .build();

        List<Player> players = new ArrayList<>(List.of(attacker, defender));

        game = Game.builder()
                .id("game-1")
                .name("Combat Test")
                .status(GameStatus.IN_PROGRESS)
                .currentPhase(GamePhase.ATTACK)
                .currentPlayerIndex(0)
                .turnNumber(1)
                .reinforcementsRemaining(0)
                .maxPlayers(6)
                .minPlayers(2)
                .mapId("classic-world")
                .gameMode(GameMode.CLASSIC)
                .players(players)
                .build();

        fromTerritory = Territory.builder()
                .id("terr-from")
                .name("Alaska")
                .territoryKey("alaska")
                .game(game)
                .owner(attacker)
                .armies(5)
                .neighborKeys(new HashSet<>(Set.of("kamchatka")))
                .build();

        toTerritory = Territory.builder()
                .id("terr-to")
                .name("Kamchatka")
                .territoryKey("kamchatka")
                .game(game)
                .owner(defender)
                .armies(1)
                .neighborKeys(new HashSet<>(Set.of("alaska")))
                .build();
    }

    @Nested
    @DisplayName("Attack validation")
    class AttackValidationTests {

        @Test
        @DisplayName("should reject attack when not in attack phase")
        void shouldRejectWhenNotAttackPhase() {
            game.setCurrentPhase(GamePhase.REINFORCEMENT);
            when(gameRepository.findById("game-1")).thenReturn(Optional.of(game));

            assertThrows(IllegalStateException.class,
                    () -> combatService.attack("game-1", "attacker-1", "alaska", "kamchatka", 2));
        }

        @Test
        @DisplayName("should reject attack when not current player's turn")
        void shouldRejectWhenNotYourTurn() {
            when(gameRepository.findById("game-1")).thenReturn(Optional.of(game));

            assertThrows(IllegalStateException.class,
                    () -> combatService.attack("game-1", "defender-1", "alaska", "kamchatka", 2));
        }

        @Test
        @DisplayName("should reject attack on own territory")
        void shouldRejectAttackOnOwnTerritory() {
            toTerritory.setOwner(attacker);
            when(gameRepository.findById("game-1")).thenReturn(Optional.of(game));
            when(territoryRepository.findByGameIdAndTerritoryKey("game-1", "alaska"))
                    .thenReturn(Optional.of(fromTerritory));
            when(territoryRepository.findByGameIdAndTerritoryKey("game-1", "kamchatka"))
                    .thenReturn(Optional.of(toTerritory));

            assertThrows(IllegalArgumentException.class,
                    () -> combatService.attack("game-1", "attacker-1", "alaska", "kamchatka", 2));
        }

        @Test
        @DisplayName("should reject attack on non-adjacent territory")
        void shouldRejectNonAdjacent() {
            fromTerritory.setNeighborKeys(new HashSet<>()); // no neighbors
            when(gameRepository.findById("game-1")).thenReturn(Optional.of(game));
            when(territoryRepository.findByGameIdAndTerritoryKey("game-1", "alaska"))
                    .thenReturn(Optional.of(fromTerritory));
            when(territoryRepository.findByGameIdAndTerritoryKey("game-1", "kamchatka"))
                    .thenReturn(Optional.of(toTerritory));

            assertThrows(IllegalArgumentException.class,
                    () -> combatService.attack("game-1", "attacker-1", "alaska", "kamchatka", 2));
        }

        @Test
        @DisplayName("should reject attack with armies >= source armies")
        void shouldRejectInvalidArmyCount() {
            fromTerritory.setArmies(3);
            when(gameRepository.findById("game-1")).thenReturn(Optional.of(game));
            when(territoryRepository.findByGameIdAndTerritoryKey("game-1", "alaska"))
                    .thenReturn(Optional.of(fromTerritory));
            when(territoryRepository.findByGameIdAndTerritoryKey("game-1", "kamchatka"))
                    .thenReturn(Optional.of(toTerritory));

            // Attacking with 3 armies when source has 3 — must fail (need at least 1 behind)
            assertThrows(IllegalArgumentException.class,
                    () -> combatService.attack("game-1", "attacker-1", "alaska", "kamchatka", 3));
        }

        @Test
        @DisplayName("should reject attack with 0 or negative armies")
        void shouldRejectZeroArmies() {
            when(gameRepository.findById("game-1")).thenReturn(Optional.of(game));
            when(territoryRepository.findByGameIdAndTerritoryKey("game-1", "alaska"))
                    .thenReturn(Optional.of(fromTerritory));
            when(territoryRepository.findByGameIdAndTerritoryKey("game-1", "kamchatka"))
                    .thenReturn(Optional.of(toTerritory));

            assertThrows(IllegalArgumentException.class,
                    () -> combatService.attack("game-1", "attacker-1", "alaska", "kamchatka", 0));
        }

        @Test
        @DisplayName("should reject attack with more than 3 armies")
        void shouldRejectMoreThanThreeArmies() {
            fromTerritory.setArmies(10);
            when(gameRepository.findById("game-1")).thenReturn(Optional.of(game));
            when(territoryRepository.findByGameIdAndTerritoryKey("game-1", "alaska"))
                    .thenReturn(Optional.of(fromTerritory));
            when(territoryRepository.findByGameIdAndTerritoryKey("game-1", "kamchatka"))
                    .thenReturn(Optional.of(toTerritory));

            assertThrows(IllegalArgumentException.class,
                    () -> combatService.attack("game-1", "attacker-1", "alaska", "kamchatka", 4));
        }
    }

    @Nested
    @DisplayName("Attack execution — BUG 3 regression")
    class ConquestArmyTests {

        @Test
        @DisplayName("source territory must always keep at least 1 army after conquest")
        void sourceShouldKeepAtLeastOneArmyAfterConquest() {
            // Scenario: attacker has 2 armies, attacks with 1, defender has 1 army
            // If conquest occurs, source must keep >= 1
            fromTerritory.setArmies(2);
            toTerritory.setArmies(1);

            when(gameRepository.findById("game-1")).thenReturn(Optional.of(game));
            when(territoryRepository.findByGameIdAndTerritoryKey("game-1", "alaska"))
                    .thenReturn(Optional.of(fromTerritory));
            when(territoryRepository.findByGameIdAndTerritoryKey("game-1", "kamchatka"))
                    .thenReturn(Optional.of(toTerritory));
            when(territoryRepository.findByOwnerId("defender-1")).thenReturn(List.of());
            when(playerRepository.findActivePlayersByGameId("game-1")).thenReturn(List.of(attacker));
            when(territoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(gameRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(playerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // Run many attacks — some will conquer due to random dice
            int conquests = 0;
            for (int i = 0; i < 200; i++) {
                fromTerritory.setArmies(2);
                fromTerritory.setOwner(attacker);
                toTerritory.setArmies(1);
                toTerritory.setOwner(defender);

                try {
                    AttackResult result = combatService.attack(
                            "game-1", "attacker-1", "alaska", "kamchatka", 1);

                    if (result.isConquered()) {
                        conquests++;
                        assertTrue(fromTerritory.getArmies() >= 1,
                                "BUG 3 regression: source territory has " + fromTerritory.getArmies() +
                                " armies after conquest — must be >= 1");
                        assertTrue(toTerritory.getArmies() >= 1,
                                "Conquered territory must have at least 1 army");
                        assertEquals(attacker, toTerritory.getOwner(),
                                "Conquered territory must belong to attacker");
                    }
                } catch (IllegalStateException e) {
                    // This could happen if moveArmies < 1 — that's the bug guard working
                    // But with 2 armies attacking with 1, after 0 attacker losses, from=2, 
                    // moveArmies = min(1, 2-1) = 1, from = 2-1 = 1 ✓
                }
            }

            assertTrue(conquests > 0, "Expected at least some conquests in 200 attempts");
        }

        @Test
        @DisplayName("conquered territory should have correct army count")
        void conqueredTerritoryShouldHaveCorrectArmies() {
            // With 5 armies, attack with 3. If conquest: 
            //   moveArmies = min(3, from.armies - 1)
            //   to.armies = moveArmies
            //   from.armies = from.armies - moveArmies
            fromTerritory.setArmies(5);
            toTerritory.setArmies(1);

            when(gameRepository.findById("game-1")).thenReturn(Optional.of(game));
            when(territoryRepository.findByGameIdAndTerritoryKey("game-1", "alaska"))
                    .thenReturn(Optional.of(fromTerritory));
            when(territoryRepository.findByGameIdAndTerritoryKey("game-1", "kamchatka"))
                    .thenReturn(Optional.of(toTerritory));
            when(territoryRepository.findByOwnerId("defender-1")).thenReturn(List.of());
            when(playerRepository.findActivePlayersByGameId("game-1")).thenReturn(List.of(attacker));
            when(territoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(gameRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(playerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            for (int i = 0; i < 200; i++) {
                fromTerritory.setArmies(5);
                fromTerritory.setOwner(attacker);
                toTerritory.setArmies(1);
                toTerritory.setOwner(defender);

                AttackResult result = combatService.attack(
                        "game-1", "attacker-1", "alaska", "kamchatka", 3);

                if (result.isConquered()) {
                    int totalAfter = fromTerritory.getArmies() + toTerritory.getArmies();
                    int totalBefore = 5 - result.getAttackerLosses(); // armies after attacker losses
                    assertEquals(totalBefore, totalAfter,
                            "Total armies must be conserved after conquest (minus attacker losses)");
                    assertTrue(fromTerritory.getArmies() >= 1,
                            "Source must keep at least 1 army");
                    break; // One successful conquest is enough
                }
            }
        }
    }

    @Nested
    @DisplayName("Attack result dice")
    class AttackDiceTests {

        @Test
        @DisplayName("attack result should contain valid dice values")
        void shouldReturnValidDice() {
            when(gameRepository.findById("game-1")).thenReturn(Optional.of(game));
            when(territoryRepository.findByGameIdAndTerritoryKey("game-1", "alaska"))
                    .thenReturn(Optional.of(fromTerritory));
            when(territoryRepository.findByGameIdAndTerritoryKey("game-1", "kamchatka"))
                    .thenReturn(Optional.of(toTerritory));
            // May or may not be called depending on whether conquest happens
            lenient().when(territoryRepository.findByOwnerId(anyString())).thenReturn(List.of(toTerritory));
            when(territoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(gameRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            AttackResult result = combatService.attack(
                    "game-1", "attacker-1", "alaska", "kamchatka", 3);

            // Check dice counts
            assertEquals(3, result.getAttackerDice().length);
            assertTrue(result.getDefenderDice().length >= 1 && result.getDefenderDice().length <= 2);

            // Check dice values 1-6
            for (int die : result.getAttackerDice()) {
                assertTrue(die >= 1 && die <= 6, "Die value must be 1-6, got " + die);
            }
            for (int die : result.getDefenderDice()) {
                assertTrue(die >= 1 && die <= 6, "Die value must be 1-6, got " + die);
            }

            // Losses should be consistent
            int totalLosses = result.getAttackerLosses() + result.getDefenderLosses();
            int comparisons = Math.min(result.getAttackerDice().length, result.getDefenderDice().length);
            assertEquals(comparisons, totalLosses, "Total losses must equal number of dice comparisons");
        }
    }

    @Nested
    @DisplayName("Player elimination")
    class PlayerEliminationTests {

        @Test
        @DisplayName("defender should be eliminated when they lose their last territory")
        void shouldEliminateDefenderWhenLastTerritoryConquered() {
            fromTerritory.setArmies(5);
            toTerritory.setArmies(1);

            when(gameRepository.findById("game-1")).thenReturn(Optional.of(game));
            when(territoryRepository.findByGameIdAndTerritoryKey("game-1", "alaska"))
                    .thenReturn(Optional.of(fromTerritory));
            when(territoryRepository.findByGameIdAndTerritoryKey("game-1", "kamchatka"))
                    .thenReturn(Optional.of(toTerritory));
            // Defender has NO territories left after conquest
            when(territoryRepository.findByOwnerId("defender-1")).thenReturn(List.of());
            when(playerRepository.findActivePlayersByGameId("game-1")).thenReturn(List.of(attacker));
            when(territoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(gameRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(playerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            for (int i = 0; i < 200; i++) {
                fromTerritory.setArmies(5);
                fromTerritory.setOwner(attacker);
                toTerritory.setArmies(1);
                toTerritory.setOwner(defender);
                defender.setEliminated(false);

                AttackResult result = combatService.attack(
                        "game-1", "attacker-1", "alaska", "kamchatka", 3);

                if (result.isConquered()) {
                    assertNotNull(result.getEliminatedPlayer());
                    assertEquals("Defender", result.getEliminatedPlayer());
                    assertTrue(defender.isEliminated());
                    return; // Test passed
                }
            }
            fail("Expected at least one conquest in 200 attempts");
        }
    }
}
