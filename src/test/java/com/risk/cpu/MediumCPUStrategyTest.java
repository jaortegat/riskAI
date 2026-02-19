package com.risk.cpu;

import com.risk.model.*;
import com.risk.repository.ContinentRepository;
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
 * Unit tests for MediumCPUStrategy — border reinforcement & advantage attacks.
 */
@ExtendWith(MockitoExtension.class)
class MediumCPUStrategyTest {

    @Mock private TerritoryRepository territoryRepository;
    @Mock private ContinentRepository continentRepository;

    @InjectMocks
    private MediumCPUStrategy strategy;

    private Game game;
    private Player cpuPlayer;
    private Player enemy;

    @BeforeEach
    void setUp() {
        cpuPlayer = Player.builder()
                .id("cpu-1").name("CPU Medium").type(PlayerType.CPU)
                .cpuDifficulty(CPUDifficulty.MEDIUM).color(PlayerColor.RED).turnOrder(0).build();

        enemy = Player.builder()
                .id("enemy-1").name("Enemy").type(PlayerType.HUMAN)
                .color(PlayerColor.BLUE).turnOrder(1).build();

        game = Game.builder()
                .id("game-1").name("Medium Test").status(GameStatus.IN_PROGRESS)
                .currentPhase(GamePhase.REINFORCEMENT)
                .players(new ArrayList<>(List.of(cpuPlayer, enemy)))
                .build();

        cpuPlayer.setGame(game);
        enemy.setGame(game);
    }

    @Test
    @DisplayName("getDifficulty() should return MEDIUM")
    void shouldReturnMediumDifficulty() {
        assertEquals(CPUDifficulty.MEDIUM, strategy.getDifficulty());
    }

    @Nested
    @DisplayName("Reinforcement decisions")
    class ReinforcementTests {

        @Test
        @DisplayName("should reinforce territory with most enemy neighbors")
        void shouldReinforceMostExposedTerritory() {
            Territory t1 = Territory.builder()
                    .id("t1").territoryKey("brazil").owner(cpuPlayer).armies(2)
                    .neighborKeys(new HashSet<>(Set.of("argentina", "peru")))
                    .build();
            Territory t2 = Territory.builder()
                    .id("t2").territoryKey("peru").owner(cpuPlayer).armies(1)
                    .neighborKeys(new HashSet<>(Set.of("brazil", "venezuela")))
                    .build();
            Territory enemyT1 = Territory.builder()
                    .id("t3").territoryKey("argentina").owner(enemy).armies(3)
                    .neighborKeys(new HashSet<>(Set.of("brazil")))
                    .build();
            Territory enemyT2 = Territory.builder()
                    .id("t4").territoryKey("venezuela").owner(enemy).armies(2)
                    .neighborKeys(new HashSet<>(Set.of("peru")))
                    .build();

            when(territoryRepository.findByOwnerId("cpu-1")).thenReturn(List.of(t1, t2));
            when(territoryRepository.findByGameId("game-1")).thenReturn(List.of(t1, t2, enemyT1, enemyT2));

            CPUAction action = strategy.decideReinforcement(game, cpuPlayer, 5);

            assertNotNull(action);
            assertEquals(CPUAction.ActionType.PLACE_ARMIES, action.getType());
            // brazil has 1 enemy neighbor (argentina), peru has 1 enemy neighbor (venezuela)
            // both equal, so either is acceptable
            assertTrue(Set.of("brazil", "peru").contains(action.getToTerritoryKey()));
            assertEquals(5, action.getArmies());
        }

        @Test
        @DisplayName("should return null when no territories owned")
        void shouldReturnNullWhenNoTerritories() {
            when(territoryRepository.findByOwnerId("cpu-1")).thenReturn(List.of());

            CPUAction action = strategy.decideReinforcement(game, cpuPlayer, 5);

            assertNull(action);
        }

        @Test
        @DisplayName("should fall back to first territory when all are interior")
        void shouldFallBackToFirstTerritory() {
            // All territories only neighbor each other (no enemies)
            Territory t1 = Territory.builder()
                    .id("t1").territoryKey("brazil").owner(cpuPlayer).armies(2)
                    .neighborKeys(new HashSet<>(Set.of("peru")))
                    .build();
            Territory t2 = Territory.builder()
                    .id("t2").territoryKey("peru").owner(cpuPlayer).armies(1)
                    .neighborKeys(new HashSet<>(Set.of("brazil")))
                    .build();

            when(territoryRepository.findByOwnerId("cpu-1")).thenReturn(List.of(t1, t2));
            when(territoryRepository.findByGameId("game-1")).thenReturn(List.of(t1, t2));

            CPUAction action = strategy.decideReinforcement(game, cpuPlayer, 3);

            assertNotNull(action);
            assertEquals(CPUAction.ActionType.PLACE_ARMIES, action.getType());
            assertEquals("brazil", action.getToTerritoryKey(), "Should fall back to first territory");
        }
    }

    @Nested
    @DisplayName("Attack decisions")
    class AttackTests {

        @Test
        @DisplayName("should attack target with best advantage")
        void shouldAttackBestAdvantage() {
            Territory from = Territory.builder()
                    .id("t1").territoryKey("alaska").owner(cpuPlayer).armies(8)
                    .neighborKeys(new HashSet<>(Set.of("kamchatka", "nw-territory")))
                    .build();

            Territory weakEnemy = Territory.builder()
                    .id("t2").territoryKey("kamchatka").owner(enemy).armies(2)
                    .neighborKeys(new HashSet<>(Set.of("alaska")))
                    .build();

            Territory strongEnemy = Territory.builder()
                    .id("t3").territoryKey("nw-territory").owner(enemy).armies(6)
                    .neighborKeys(new HashSet<>(Set.of("alaska")))
                    .build();

            when(territoryRepository.findAttackCapableTerritories("game-1", "cpu-1"))
                    .thenReturn(List.of(from));
            when(territoryRepository.findByGameId("game-1"))
                    .thenReturn(List.of(from, weakEnemy, strongEnemy));

            CPUAction action = strategy.decideAttack(game, cpuPlayer);

            assertEquals(CPUAction.ActionType.ATTACK, action.getType());
            assertEquals("alaska", action.getFromTerritoryKey());
            assertEquals("kamchatka", action.getToTerritoryKey(), "Should choose weaker target");
            assertEquals(3, action.getArmies());
        }

        @Test
        @DisplayName("should end attack when no advantage >= 2")
        void shouldEndAttackWhenNoAdvantage() {
            Territory from = Territory.builder()
                    .id("t1").territoryKey("alaska").owner(cpuPlayer).armies(3)
                    .neighborKeys(new HashSet<>(Set.of("kamchatka")))
                    .build();

            Territory enemyT = Territory.builder()
                    .id("t2").territoryKey("kamchatka").owner(enemy).armies(3)
                    .neighborKeys(new HashSet<>(Set.of("alaska")))
                    .build();

            when(territoryRepository.findAttackCapableTerritories("game-1", "cpu-1"))
                    .thenReturn(List.of(from));
            when(territoryRepository.findByGameId("game-1"))
                    .thenReturn(List.of(from, enemyT));

            CPUAction action = strategy.decideAttack(game, cpuPlayer);

            assertEquals(CPUAction.ActionType.END_ATTACK, action.getType(),
                    "Advantage is 0 (3-3), should end attack since < 2");
        }

        @Test
        @DisplayName("should end attack when no attack-capable territories")
        void shouldEndAttackWhenNoneCapable() {
            when(territoryRepository.findAttackCapableTerritories("game-1", "cpu-1"))
                    .thenReturn(List.of());

            CPUAction action = strategy.decideAttack(game, cpuPlayer);

            assertEquals(CPUAction.ActionType.END_ATTACK, action.getType());
        }
    }

    @Nested
    @DisplayName("Fortify decisions")
    class FortifyTests {

        @Test
        @DisplayName("should move armies from interior to border territory")
        void shouldFortifyBorderFromInterior() {
            Territory interiorTerritory = Territory.builder()
                    .id("t1").territoryKey("central-africa").owner(cpuPlayer).armies(5)
                    .neighborKeys(new HashSet<>(Set.of("north-africa", "south-africa")))
                    .build();

            Territory borderTerritory = Territory.builder()
                    .id("t2").territoryKey("north-africa").owner(cpuPlayer).armies(1)
                    .neighborKeys(new HashSet<>(Set.of("central-africa", "egypt")))
                    .build();

            Territory enemyT = Territory.builder()
                    .id("t3").territoryKey("egypt").owner(enemy).armies(3)
                    .neighborKeys(new HashSet<>(Set.of("north-africa")))
                    .build();

            Territory friendlyT = Territory.builder()
                    .id("t4").territoryKey("south-africa").owner(cpuPlayer).armies(1)
                    .neighborKeys(new HashSet<>(Set.of("central-africa")))
                    .build();

            when(territoryRepository.findByOwnerId("cpu-1"))
                    .thenReturn(List.of(interiorTerritory, borderTerritory, friendlyT));
            when(territoryRepository.findByGameId("game-1"))
                    .thenReturn(List.of(interiorTerritory, borderTerritory, enemyT, friendlyT));

            CPUAction action = strategy.decideFortify(game, cpuPlayer);

            assertEquals(CPUAction.ActionType.FORTIFY, action.getType());
            assertEquals("central-africa", action.getFromTerritoryKey());
            assertEquals("north-africa", action.getToTerritoryKey());
            assertEquals(4, action.getArmies(), "Should move armies - 1");
        }

        @Test
        @DisplayName("should skip fortify when no interior territories")
        void shouldSkipWhenNoInterior() {
            // All territories border enemies
            Territory t1 = Territory.builder()
                    .id("t1").territoryKey("brazil").owner(cpuPlayer).armies(5)
                    .neighborKeys(new HashSet<>(Set.of("argentina")))
                    .build();

            Territory enemyT = Territory.builder()
                    .id("t2").territoryKey("argentina").owner(enemy).armies(3)
                    .neighborKeys(new HashSet<>(Set.of("brazil")))
                    .build();

            when(territoryRepository.findByOwnerId("cpu-1")).thenReturn(List.of(t1));
            when(territoryRepository.findByGameId("game-1")).thenReturn(List.of(t1, enemyT));

            CPUAction action = strategy.decideFortify(game, cpuPlayer);

            assertEquals(CPUAction.ActionType.SKIP_FORTIFY, action.getType());
        }

        @Test
        @DisplayName("should skip fortify when interior has only 1 army")
        void shouldSkipWhenInteriorHasOneArmy() {
            Territory interiorTerritory = Territory.builder()
                    .id("t1").territoryKey("central-africa").owner(cpuPlayer).armies(1)
                    .neighborKeys(new HashSet<>(Set.of("north-africa")))
                    .build();

            Territory borderTerritory = Territory.builder()
                    .id("t2").territoryKey("north-africa").owner(cpuPlayer).armies(1)
                    .neighborKeys(new HashSet<>(Set.of("central-africa", "egypt")))
                    .build();

            Territory enemyT = Territory.builder()
                    .id("t3").territoryKey("egypt").owner(enemy).armies(3)
                    .neighborKeys(new HashSet<>(Set.of("north-africa")))
                    .build();

            when(territoryRepository.findByOwnerId("cpu-1"))
                    .thenReturn(List.of(interiorTerritory, borderTerritory));
            when(territoryRepository.findByGameId("game-1"))
                    .thenReturn(List.of(interiorTerritory, borderTerritory, enemyT));

            CPUAction action = strategy.decideFortify(game, cpuPlayer);

            assertEquals(CPUAction.ActionType.SKIP_FORTIFY, action.getType(),
                    "Interior with 1 army can't move, so should skip");
        }
    }
}
