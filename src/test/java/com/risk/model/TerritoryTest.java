package com.risk.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Territory entity helper methods.
 */
class TerritoryTest {

    private Territory territory;
    private Player owner;

    @BeforeEach
    void setUp() {
        owner = Player.builder()
                .id("player-1")
                .name("Owner")
                .color(PlayerColor.RED)
                .type(PlayerType.HUMAN)
                .turnOrder(0)
                .build();

        territory = Territory.builder()
                .id("terr-1")
                .name("Alaska")
                .territoryKey("alaska")
                .owner(owner)
                .armies(3)
                .neighborKeys(new HashSet<>(Set.of("kamchatka", "northwest_territory", "alberta")))
                .build();
    }

    @Nested
    @DisplayName("isOwnedBy()")
    class IsOwnedByTests {

        @Test
        @DisplayName("should return true for the owner")
        void shouldReturnTrueForOwner() {
            assertTrue(territory.isOwnedBy(owner));
        }

        @Test
        @DisplayName("should return false for a different player")
        void shouldReturnFalseForDifferentPlayer() {
            Player other = Player.builder().id("player-2").build();
            assertFalse(territory.isOwnedBy(other));
        }

        @Test
        @DisplayName("should return false when territory has no owner")
        void shouldReturnFalseWhenNoOwner() {
            territory.setOwner(null);
            assertFalse(territory.isOwnedBy(owner));
        }
    }

    @Nested
    @DisplayName("canAttackFrom()")
    class CanAttackFromTests {

        @Test
        @DisplayName("should return true when armies > 1")
        void shouldReturnTrueWithMultipleArmies() {
            territory.setArmies(2);
            assertTrue(territory.canAttackFrom());
        }

        @Test
        @DisplayName("should return false when exactly 1 army")
        void shouldReturnFalseWithOneArmy() {
            territory.setArmies(1);
            assertFalse(territory.canAttackFrom());
        }
    }

    @Nested
    @DisplayName("isNeighborOf()")
    class IsNeighborOfTests {

        @Test
        @DisplayName("should return true for a neighbor")
        void shouldReturnTrueForNeighbor() {
            assertTrue(territory.isNeighborOf("kamchatka"));
        }

        @Test
        @DisplayName("should return false for a non-neighbor")
        void shouldReturnFalseForNonNeighbor() {
            assertFalse(territory.isNeighborOf("brazil"));
        }
    }
}
