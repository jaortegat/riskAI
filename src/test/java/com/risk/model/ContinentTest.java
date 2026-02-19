package com.risk.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Continent entity — isControlledBy() logic.
 */
class ContinentTest {

    private Player playerA;
    private Player playerB;
    private Continent continent;

    @BeforeEach
    void setUp() {
        playerA = Player.builder()
                .id("player-a").name("Alice").color(PlayerColor.RED)
                .type(PlayerType.HUMAN).turnOrder(0).build();

        playerB = Player.builder()
                .id("player-b").name("Bob").color(PlayerColor.BLUE)
                .type(PlayerType.HUMAN).turnOrder(1).build();

        continent = Continent.builder()
                .id("c1").name("South America").continentKey("south-america")
                .bonusArmies(2).build();
    }

    @Test
    @DisplayName("should return true when player controls all territories")
    void shouldReturnTrueWhenFullyControlled() {
        Territory t1 = Territory.builder()
                .id("t1").territoryKey("brazil").owner(playerA).armies(3).build();
        Territory t2 = Territory.builder()
                .id("t2").territoryKey("argentina").owner(playerA).armies(2).build();
        Territory t3 = Territory.builder()
                .id("t3").territoryKey("peru").owner(playerA).armies(1).build();

        continent.setTerritories(new ArrayList<>(List.of(t1, t2, t3)));

        assertTrue(continent.isControlledBy(playerA));
    }

    @Test
    @DisplayName("should return false when another player owns a territory")
    void shouldReturnFalseWhenNotFullyControlled() {
        Territory t1 = Territory.builder()
                .id("t1").territoryKey("brazil").owner(playerA).armies(3).build();
        Territory t2 = Territory.builder()
                .id("t2").territoryKey("argentina").owner(playerB).armies(2).build();

        continent.setTerritories(new ArrayList<>(List.of(t1, t2)));

        assertFalse(continent.isControlledBy(playerA));
    }

    @Test
    @DisplayName("should return false when a territory has no owner")
    void shouldReturnFalseWhenTerritoryUnowned() {
        Territory t1 = Territory.builder()
                .id("t1").territoryKey("brazil").owner(playerA).armies(3).build();
        Territory t2 = Territory.builder()
                .id("t2").territoryKey("argentina").owner(null).armies(0).build();

        continent.setTerritories(new ArrayList<>(List.of(t1, t2)));

        assertFalse(continent.isControlledBy(playerA));
    }

    @Test
    @DisplayName("should return false when continent has no territories")
    void shouldReturnFalseWhenEmpty() {
        continent.setTerritories(new ArrayList<>());
        assertFalse(continent.isControlledBy(playerA));
    }

    @Test
    @DisplayName("should return true for single-territory continent controlled by player")
    void shouldReturnTrueForSingleTerritory() {
        Territory t1 = Territory.builder()
                .id("t1").territoryKey("iceland").owner(playerA).armies(1).build();

        continent.setTerritories(new ArrayList<>(List.of(t1)));

        assertTrue(continent.isControlledBy(playerA));
    }

    @Test
    @DisplayName("should distinguish between different players")
    void shouldDistinguishPlayers() {
        Territory t1 = Territory.builder()
                .id("t1").territoryKey("brazil").owner(playerB).armies(3).build();

        continent.setTerritories(new ArrayList<>(List.of(t1)));

        assertFalse(continent.isControlledBy(playerA));
        assertTrue(continent.isControlledBy(playerB));
    }
}
