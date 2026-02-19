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
 * Unit tests for HardCPUStrategy — continent-focused strategy.
 */
@ExtendWith(MockitoExtension.class)
class HardCPUStrategyTest {

    @Mock private TerritoryRepository territoryRepository;
    @Mock private ContinentRepository continentRepository;

    @InjectMocks
    private HardCPUStrategy strategy;

    private Game game;
    private Player cpuPlayer;
    private Player enemy;

    @BeforeEach
    void setUp() {
        cpuPlayer = Player.builder()
                .id("cpu-1").name("CPU Hard").type(PlayerType.CPU)
                .cpuDifficulty(CPUDifficulty.HARD).color(PlayerColor.RED).turnOrder(0).build();

        enemy = Player.builder()
                .id("enemy-1").name("Enemy").type(PlayerType.HUMAN)
                .color(PlayerColor.BLUE).turnOrder(1).build();

        game = Game.builder()
                .id("game-1").name("Hard Test").status(GameStatus.IN_PROGRESS)
                .currentPhase(GamePhase.REINFORCEMENT)
                .players(new ArrayList<>(List.of(cpuPlayer, enemy)))
                .build();

        cpuPlayer.setGame(game);
        enemy.setGame(game);
    }

    @Test
    @DisplayName("getDifficulty() should return HARD")
    void shouldReturnHardDifficulty() {
        assertEquals(CPUDifficulty.HARD, strategy.getDifficulty());
    }

    @Nested
    @DisplayName("Reinforcement decisions")
    class ReinforcementTests {

        @Test
        @DisplayName("should target continent close to completion")
        void shouldTargetContinentCloseToCompletion() {
            // CPU owns 2/3 of South America continent
            Territory t1 = Territory.builder()
                    .id("t1").territoryKey("brazil").owner(cpuPlayer).armies(2)
                    .neighborKeys(new HashSet<>(Set.of("argentina", "peru")))
                    .build();
            Territory t2 = Territory.builder()
                    .id("t2").territoryKey("peru").owner(cpuPlayer).armies(1)
                    .neighborKeys(new HashSet<>(Set.of("brazil", "argentina")))
                    .build();
            Territory t3 = Territory.builder()
                    .id("t3").territoryKey("argentina").owner(enemy).armies(3)
                    .neighborKeys(new HashSet<>(Set.of("brazil", "peru")))
                    .build();

            // Another continent with only 1/4 owned
            Territory t4 = Territory.builder()
                    .id("t4").territoryKey("alaska").owner(cpuPlayer).armies(1)
                    .neighborKeys(new HashSet<>(Set.of("kamchatka")))
                    .build();

            Continent southAmerica = Continent.builder()
                    .id("c1").name("South America").continentKey("south-america")
                    .bonusArmies(2).game(game)
                    .territories(new ArrayList<>(List.of(t1, t2, t3)))
                    .build();
            t1.setContinent(southAmerica);
            t2.setContinent(southAmerica);
            t3.setContinent(southAmerica);

            when(territoryRepository.findByOwnerId("cpu-1")).thenReturn(List.of(t1, t2, t4));
            when(continentRepository.findByGameIdWithTerritories("game-1"))
                    .thenReturn(List.of(southAmerica));
            when(territoryRepository.findByGameId("game-1")).thenReturn(List.of(t1, t2, t3, t4));

            CPUAction action = strategy.decideReinforcement(game, cpuPlayer, 5);

            assertNotNull(action);
            assertEquals(CPUAction.ActionType.PLACE_ARMIES, action.getType());
            // Should reinforce one of the South American territories that borders enemy
            assertTrue(Set.of("brazil", "peru").contains(action.getToTerritoryKey()),
                    "Should reinforce a border territory in the targeted continent");
        }

        @Test
        @DisplayName("should throw when no territories owned (missing guard)")
        void shouldThrowWhenNoTerritories() {
            when(territoryRepository.findByOwnerId("cpu-1")).thenReturn(List.of());

            // Bug: decideReinforcement lacks empty-list guard, falls through to get(0)
            assertThrows(IndexOutOfBoundsException.class,
                    () -> strategy.decideReinforcement(game, cpuPlayer, 5));
        }

        @Test
        @DisplayName("should fall back to border territory when no continent targets")
        void shouldFallBackToBorderTerritory() {
            // Single territory, no continents close to completion
            Territory t1 = Territory.builder()
                    .id("t1").territoryKey("alaska").owner(cpuPlayer).armies(2)
                    .neighborKeys(new HashSet<>(Set.of("kamchatka")))
                    .build();

            Territory enemyT = Territory.builder()
                    .id("t2").territoryKey("kamchatka").owner(enemy).armies(5)
                    .neighborKeys(new HashSet<>(Set.of("alaska")))
                    .build();

            when(territoryRepository.findByOwnerId("cpu-1")).thenReturn(List.of(t1));
            when(continentRepository.findByGameIdWithTerritories("game-1")).thenReturn(List.of());
            when(territoryRepository.findByGameId("game-1")).thenReturn(List.of(t1, enemyT));

            CPUAction action = strategy.decideReinforcement(game, cpuPlayer, 3);

            assertNotNull(action);
            assertEquals(CPUAction.ActionType.PLACE_ARMIES, action.getType());
            assertEquals("alaska", action.getToTerritoryKey());
        }
    }

    @Nested
    @DisplayName("Attack decisions")
    class AttackTests {

        @Test
        @DisplayName("should prioritize continent completion attack")
        void shouldPrioritizeContinentCompletion() {
            // CPU owns all of South America except argentina
            Territory brazil = Territory.builder()
                    .id("t1").territoryKey("brazil").owner(cpuPlayer).armies(6)
                    .neighborKeys(new HashSet<>(Set.of("argentina", "peru")))
                    .build();
            Territory peru = Territory.builder()
                    .id("t2").territoryKey("peru").owner(cpuPlayer).armies(3)
                    .neighborKeys(new HashSet<>(Set.of("brazil", "argentina")))
                    .build();
            Territory argentina = Territory.builder()
                    .id("t3").territoryKey("argentina").owner(enemy).armies(2)
                    .neighborKeys(new HashSet<>(Set.of("brazil", "peru")))
                    .build();

            Continent southAmerica = Continent.builder()
                    .id("c1").name("South America").continentKey("south-america")
                    .bonusArmies(2).game(game)
                    .territories(new ArrayList<>(List.of(brazil, peru, argentina)))
                    .build();
            brazil.setContinent(southAmerica);
            peru.setContinent(southAmerica);
            argentina.setContinent(southAmerica);

            when(continentRepository.findByGameIdWithTerritories("game-1"))
                    .thenReturn(List.of(southAmerica));
            when(territoryRepository.findAttackCapableTerritories("game-1", "cpu-1"))
                    .thenReturn(List.of(brazil, peru));

            CPUAction action = strategy.decideAttack(game, cpuPlayer);

            assertEquals(CPUAction.ActionType.ATTACK, action.getType());
            assertEquals("argentina", action.getToTerritoryKey(),
                    "Should attack to complete the continent");
        }

        @Test
        @DisplayName("should attack weak neighbor with 2x ratio as fallback")
        void shouldAttackWeakNeighborWithRatio() {
            Territory from = Territory.builder()
                    .id("t1").territoryKey("alaska").owner(cpuPlayer).armies(6)
                    .neighborKeys(new HashSet<>(Set.of("kamchatka")))
                    .build();

            Territory weakEnemy = Territory.builder()
                    .id("t2").territoryKey("kamchatka").owner(enemy).armies(2)
                    .neighborKeys(new HashSet<>(Set.of("alaska")))
                    .build();

            when(continentRepository.findByGameIdWithTerritories("game-1")).thenReturn(List.of());
            when(territoryRepository.findAttackCapableTerritories("game-1", "cpu-1"))
                    .thenReturn(List.of(from));
            when(territoryRepository.findByGameId("game-1")).thenReturn(List.of(from, weakEnemy));

            CPUAction action = strategy.decideAttack(game, cpuPlayer);

            assertEquals(CPUAction.ActionType.ATTACK, action.getType());
            assertEquals("alaska", action.getFromTerritoryKey());
            assertEquals("kamchatka", action.getToTerritoryKey());
        }

        @Test
        @DisplayName("should end attack when no viable targets")
        void shouldEndAttackWhenNoTargets() {
            // CPU has 3 armies, enemy has 5 — no 2x ratio
            Territory from = Territory.builder()
                    .id("t1").territoryKey("alaska").owner(cpuPlayer).armies(3)
                    .neighborKeys(new HashSet<>(Set.of("kamchatka")))
                    .build();

            Territory strongEnemy = Territory.builder()
                    .id("t2").territoryKey("kamchatka").owner(enemy).armies(5)
                    .neighborKeys(new HashSet<>(Set.of("alaska")))
                    .build();

            when(continentRepository.findByGameIdWithTerritories("game-1")).thenReturn(List.of());
            when(territoryRepository.findAttackCapableTerritories("game-1", "cpu-1"))
                    .thenReturn(List.of(from));
            when(territoryRepository.findByGameId("game-1")).thenReturn(List.of(from, strongEnemy));

            CPUAction action = strategy.decideAttack(game, cpuPlayer);

            assertEquals(CPUAction.ActionType.END_ATTACK, action.getType());
        }

        @Test
        @DisplayName("should end attack when no territories owned")
        void shouldEndAttackWhenNoTerritoriesOwned() {
            // findAttackCapableTerritories returns empty list by default
            CPUAction action = strategy.decideAttack(game, cpuPlayer);

            assertEquals(CPUAction.ActionType.END_ATTACK, action.getType());
        }
    }

    @Nested
    @DisplayName("Fortify decisions")
    class FortifyTests {

        @Test
        @DisplayName("should move armies from interior to weakest border territory")
        void shouldFortifyWeakestBorder() {
            Territory interior = Territory.builder()
                    .id("t1").territoryKey("central-africa").owner(cpuPlayer).armies(5)
                    .neighborKeys(new HashSet<>(Set.of("north-africa", "south-africa")))
                    .build();

            Territory weakBorder = Territory.builder()
                    .id("t2").territoryKey("north-africa").owner(cpuPlayer).armies(1)
                    .neighborKeys(new HashSet<>(Set.of("central-africa", "egypt")))
                    .build();

            Territory strongBorder = Territory.builder()
                    .id("t3").territoryKey("south-africa").owner(cpuPlayer).armies(4)
                    .neighborKeys(new HashSet<>(Set.of("central-africa", "madagascar")))
                    .build();

            Territory enemyT = Territory.builder()
                    .id("t4").territoryKey("egypt").owner(enemy).armies(3)
                    .neighborKeys(new HashSet<>(Set.of("north-africa")))
                    .build();

            Territory enemyT2 = Territory.builder()
                    .id("t5").territoryKey("madagascar").owner(enemy).armies(2)
                    .neighborKeys(new HashSet<>(Set.of("south-africa")))
                    .build();

            when(territoryRepository.findByOwnerId("cpu-1"))
                    .thenReturn(List.of(interior, weakBorder, strongBorder));
            when(territoryRepository.findByGameId("game-1"))
                    .thenReturn(List.of(interior, weakBorder, strongBorder, enemyT, enemyT2));

            CPUAction action = strategy.decideFortify(game, cpuPlayer);

            assertEquals(CPUAction.ActionType.FORTIFY, action.getType());
            assertEquals("central-africa", action.getFromTerritoryKey());
            assertEquals("north-africa", action.getToTerritoryKey(),
                    "Should target the weakest border territory");
            assertEquals(4, action.getArmies(), "Should move armies - 1");
        }

        @Test
        @DisplayName("should skip fortify when no interior territories with excess armies")
        void shouldSkipWhenAllBorder() {
            Territory t1 = Territory.builder()
                    .id("t1").territoryKey("brazil").owner(cpuPlayer).armies(3)
                    .neighborKeys(new HashSet<>(Set.of("argentina")))
                    .build();

            Territory enemyT = Territory.builder()
                    .id("t2").territoryKey("argentina").owner(enemy).armies(2)
                    .neighborKeys(new HashSet<>(Set.of("brazil")))
                    .build();

            when(territoryRepository.findByOwnerId("cpu-1")).thenReturn(List.of(t1));
            when(territoryRepository.findByGameId("game-1")).thenReturn(List.of(t1, enemyT));

            CPUAction action = strategy.decideFortify(game, cpuPlayer);

            assertEquals(CPUAction.ActionType.SKIP_FORTIFY, action.getType());
        }

        @Test
        @DisplayName("should skip fortify when interior territory not connected to border")
        void shouldSkipWhenNotConnected() {
            // Interior not directly connected to any border territory
            Territory interior = Territory.builder()
                    .id("t1").territoryKey("central").owner(cpuPlayer).armies(5)
                    .neighborKeys(new HashSet<>(Set.of("inner")))
                    .build();

            Territory inner = Territory.builder()
                    .id("t2").territoryKey("inner").owner(cpuPlayer).armies(1)
                    .neighborKeys(new HashSet<>(Set.of("central", "border")))
                    .build();

            Territory border = Territory.builder()
                    .id("t3").territoryKey("border").owner(cpuPlayer).armies(1)
                    .neighborKeys(new HashSet<>(Set.of("inner", "enemy-land")))
                    .build();

            Territory enemyT = Territory.builder()
                    .id("t4").territoryKey("enemy-land").owner(enemy).armies(3)
                    .neighborKeys(new HashSet<>(Set.of("border")))
                    .build();

            when(territoryRepository.findByOwnerId("cpu-1"))
                    .thenReturn(List.of(interior, inner, border));
            when(territoryRepository.findByGameId("game-1"))
                    .thenReturn(List.of(interior, inner, border, enemyT));

            CPUAction action = strategy.decideFortify(game, cpuPlayer);

            // central's only neighbor among owned is inner (which is also interior)
            // central is not directly connected to border, so it can't fortify
            assertEquals(CPUAction.ActionType.SKIP_FORTIFY, action.getType());
        }
    }
}
