package com.risk.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Player entity helper methods.
 */
class PlayerTest {

    @Nested
    @DisplayName("isCPU() / isHuman()")
    class TypeTests {

        @Test
        @DisplayName("CPU player should return true for isCPU()")
        void cpuPlayerShouldReturnTrueForIsCPU() {
            Player cpu = Player.builder()
                    .id("cpu-1")
                    .name("CPU")
                    .color(PlayerColor.RED)
                    .type(PlayerType.CPU)
                    .turnOrder(0)
                    .build();

            assertTrue(cpu.isCPU());
            assertFalse(cpu.isHuman());
        }

        @Test
        @DisplayName("Human player should return true for isHuman()")
        void humanPlayerShouldReturnTrueForIsHuman() {
            Player human = Player.builder()
                    .id("h-1")
                    .name("Human")
                    .color(PlayerColor.BLUE)
                    .type(PlayerType.HUMAN)
                    .turnOrder(0)
                    .build();

            assertTrue(human.isHuman());
            assertFalse(human.isCPU());
        }
    }

    @Nested
    @DisplayName("eliminate()")
    class EliminateTests {

        @Test
        @DisplayName("should set eliminated flag to true")
        void shouldSetEliminatedFlag() {
            Player p = Player.builder()
                    .id("p-1")
                    .name("Player")
                    .color(PlayerColor.RED)
                    .type(PlayerType.HUMAN)
                    .turnOrder(0)
                    .build();

            assertFalse(p.isEliminated());
            p.eliminate();
            assertTrue(p.isEliminated());
        }
    }

    @Nested
    @DisplayName("Territory stats")
    class TerritoryStatsTests {

        @Test
        @DisplayName("getTerritoryCount() should return number of territories")
        void shouldReturnTerritoryCount() {
            Player p = Player.builder()
                    .id("p-1")
                    .name("Player")
                    .color(PlayerColor.RED)
                    .type(PlayerType.HUMAN)
                    .turnOrder(0)
                    .territories(List.of(
                            Territory.builder().id("t1").armies(3).build(),
                            Territory.builder().id("t2").armies(5).build()))
                    .build();

            assertEquals(2, p.getTerritoryCount());
        }

        @Test
        @DisplayName("getTotalArmies() should sum armies across territories")
        void shouldReturnTotalArmies() {
            Player p = Player.builder()
                    .id("p-1")
                    .name("Player")
                    .color(PlayerColor.RED)
                    .type(PlayerType.HUMAN)
                    .turnOrder(0)
                    .territories(List.of(
                            Territory.builder().id("t1").armies(3).build(),
                            Territory.builder().id("t2").armies(5).build(),
                            Territory.builder().id("t3").armies(2).build()))
                    .build();

            assertEquals(10, p.getTotalArmies());
        }

        @Test
        @DisplayName("should return 0 for empty territory list")
        void shouldReturnZeroForEmpty() {
            Player p = Player.builder()
                    .id("p-1")
                    .name("Player")
                    .color(PlayerColor.RED)
                    .type(PlayerType.HUMAN)
                    .turnOrder(0)
                    .territories(new ArrayList<>())
                    .build();

            assertEquals(0, p.getTerritoryCount());
            assertEquals(0, p.getTotalArmies());
        }
    }
}
