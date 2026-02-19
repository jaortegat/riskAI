package com.risk.cpu;

import com.risk.model.*;
import com.risk.repository.TerritoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for EasyCPUStrategy random decision-making.
 */
@ExtendWith(MockitoExtension.class)
class EasyCPUStrategyTest {

    @Mock
    private TerritoryRepository territoryRepository;

    @InjectMocks
    private EasyCPUStrategy strategy;

    private Game game;
    private Player cpuPlayer;
    private Player enemy;

    @BeforeEach
    void setUp() {
        cpuPlayer = Player.builder()
                .id("cpu-1")
                .name("CPU Easy")
                .type(PlayerType.CPU)
                .cpuDifficulty(CPUDifficulty.EASY)
                .color(PlayerColor.RED)
                .turnOrder(0)
                .build();

        enemy = Player.builder()
                .id("enemy-1")
                .name("Enemy")
                .type(PlayerType.HUMAN)
                .color(PlayerColor.BLUE)
                .turnOrder(1)
                .build();

        game = Game.builder()
                .id("game-1")
                .name("Easy Test")
                .status(GameStatus.IN_PROGRESS)
                .currentPhase(GamePhase.REINFORCEMENT)
                .players(new ArrayList<>(List.of(cpuPlayer, enemy)))
                .build();

        cpuPlayer.setGame(game);
        enemy.setGame(game);
    }

    @Test
    @DisplayName("getDifficulty() should return EASY")
    void shouldReturnEasyDifficulty() {
        assertEquals(CPUDifficulty.EASY, strategy.getDifficulty());
    }

    @Nested
    @DisplayName("Reinforcement decisions")
    class ReinforcementTests {

        @Test
        @DisplayName("should place all armies on a territory")
        void shouldPlaceAllArmiesOnTerritory() {
            Territory t1 = Territory.builder()
                    .id("t1").territoryKey("brazil").owner(cpuPlayer).armies(3).build();
            when(territoryRepository.findByOwnerId("cpu-1")).thenReturn(List.of(t1));

            CPUAction action = strategy.decideReinforcement(game, cpuPlayer, 5);

            assertNotNull(action);
            assertEquals(CPUAction.ActionType.PLACE_ARMIES, action.getType());
            assertEquals("brazil", action.getToTerritoryKey());
            assertEquals(5, action.getArmies());
        }

        @Test
        @DisplayName("should return null when no territories owned")
        void shouldReturnNullWhenNoTerritories() {
            when(territoryRepository.findByOwnerId("cpu-1")).thenReturn(List.of());

            CPUAction action = strategy.decideReinforcement(game, cpuPlayer, 5);

            assertNull(action);
        }
    }

    @Nested
    @DisplayName("Attack decisions")
    class AttackTests {

        @Test
        @DisplayName("should return endAttack when no attack-capable territories")
        void shouldEndAttackWhenNoneCapable() {
            when(territoryRepository.findAttackCapableTerritories("game-1", "cpu-1"))
                    .thenReturn(List.of());

            // Run multiple times to avoid 50% chance
            boolean allEndAttack = true;
            for (int i = 0; i < 20; i++) {
                CPUAction action = strategy.decideAttack(game, cpuPlayer);
                if (action.getType() != CPUAction.ActionType.END_ATTACK) {
                    allEndAttack = false;
                    break;
                }
            }
            assertTrue(allEndAttack, "Should always END_ATTACK with no capable territories");
        }

        @Test
        @DisplayName("should attack enemy neighbor with correct army count")
        void shouldAttackEnemyNeighbor() {
            Territory from = Territory.builder()
                    .id("t1").territoryKey("alaska").owner(cpuPlayer).armies(4)
                    .neighborKeys(new HashSet<>(Set.of("kamchatka")))
                    .build();

            Territory enemyTerritory = Territory.builder()
                    .id("t2").territoryKey("kamchatka").owner(enemy).armies(2)
                    .neighborKeys(new HashSet<>(Set.of("alaska")))
                    .build();

            when(territoryRepository.findAttackCapableTerritories("game-1", "cpu-1"))
                    .thenReturn(List.of(from));
            when(territoryRepository.findByGameId("game-1"))
                    .thenReturn(List.of(from, enemyTerritory));

            // Run until we get an ATTACK action (50% chance each time)
            CPUAction attackAction = null;
            for (int i = 0; i < 50; i++) {
                CPUAction action = strategy.decideAttack(game, cpuPlayer);
                if (action.getType() == CPUAction.ActionType.ATTACK) {
                    attackAction = action;
                    break;
                }
            }

            assertNotNull(attackAction, "Should eventually decide to attack");
            assertEquals("alaska", attackAction.getFromTerritoryKey());
            assertEquals("kamchatka", attackAction.getToTerritoryKey());
            assertEquals(3, attackAction.getArmies(), "Should use min(3, armies-1)");
        }

        @Test
        @DisplayName("should limit attack armies to source armies - 1")
        void shouldLimitAttackArmies() {
            Territory from = Territory.builder()
                    .id("t1").territoryKey("alaska").owner(cpuPlayer).armies(2)
                    .neighborKeys(new HashSet<>(Set.of("kamchatka")))
                    .build();

            Territory enemyTerritory = Territory.builder()
                    .id("t2").territoryKey("kamchatka").owner(enemy).armies(1)
                    .neighborKeys(new HashSet<>(Set.of("alaska")))
                    .build();

            when(territoryRepository.findAttackCapableTerritories("game-1", "cpu-1"))
                    .thenReturn(List.of(from));
            when(territoryRepository.findByGameId("game-1"))
                    .thenReturn(List.of(from, enemyTerritory));

            CPUAction attackAction = null;
            for (int i = 0; i < 50; i++) {
                CPUAction action = strategy.decideAttack(game, cpuPlayer);
                if (action.getType() == CPUAction.ActionType.ATTACK) {
                    attackAction = action;
                    break;
                }
            }

            assertNotNull(attackAction);
            assertEquals(1, attackAction.getArmies(), "With 2 armies, can only attack with 1");
        }
    }

    @Nested
    @DisplayName("Fortify decisions")
    class FortifyTests {

        @Test
        @DisplayName("should skip fortify when no movable territories")
        void shouldSkipFortifyWhenNoneMovable() {
            Territory t1 = Territory.builder()
                    .id("t1").territoryKey("brazil").owner(cpuPlayer).armies(1)
                    .neighborKeys(new HashSet<>(Set.of("argentina")))
                    .build();

            // Use lenient() because random.nextInt(3) may skip before calling findByOwnerId
            lenient().when(territoryRepository.findByOwnerId("cpu-1")).thenReturn(List.of(t1));

            CPUAction action = strategy.decideFortify(game, cpuPlayer);

            assertEquals(CPUAction.ActionType.SKIP_FORTIFY, action.getType());
        }

        @Test
        @DisplayName("should skip fortify when territory has no friendly neighbor")
        void shouldSkipWhenNoFriendlyNeighbor() {
            Territory t1 = Territory.builder()
                    .id("t1").territoryKey("brazil").owner(cpuPlayer).armies(5)
                    .neighborKeys(new HashSet<>(Set.of("kamchatka"))) // no friendly neighbor
                    .build();

            when(territoryRepository.findByOwnerId("cpu-1")).thenReturn(List.of(t1));

            // The fortify logic only fires 1/3 of the time, but with only one territory
            // with no neighbor among owned territories, it should always skip
            for (int i = 0; i < 20; i++) {
                CPUAction action = strategy.decideFortify(game, cpuPlayer);
                assertEquals(CPUAction.ActionType.SKIP_FORTIFY, action.getType());
            }
        }
    }
}
