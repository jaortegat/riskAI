package com.risk.cpu;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CPUAction value object and factory methods.
 */
class CPUActionTest {

    @Test
    @DisplayName("placeArmies() should create PLACE_ARMIES action with correct fields")
    void placeArmiesShouldCreateCorrectAction() {
        CPUAction action = CPUAction.placeArmies("brazil", 5);

        assertEquals(CPUAction.ActionType.PLACE_ARMIES, action.getType());
        assertNull(action.getFromTerritoryKey());
        assertEquals("brazil", action.getToTerritoryKey());
        assertEquals(5, action.getArmies());
    }

    @Test
    @DisplayName("attack() should create ATTACK action with from, to and armies")
    void attackShouldCreateCorrectAction() {
        CPUAction action = CPUAction.attack("alaska", "kamchatka", 3);

        assertEquals(CPUAction.ActionType.ATTACK, action.getType());
        assertEquals("alaska", action.getFromTerritoryKey());
        assertEquals("kamchatka", action.getToTerritoryKey());
        assertEquals(3, action.getArmies());
    }

    @Test
    @DisplayName("fortify() should create FORTIFY action with from, to and armies")
    void fortifyShouldCreateCorrectAction() {
        CPUAction action = CPUAction.fortify("brazil", "argentina", 2);

        assertEquals(CPUAction.ActionType.FORTIFY, action.getType());
        assertEquals("brazil", action.getFromTerritoryKey());
        assertEquals("argentina", action.getToTerritoryKey());
        assertEquals(2, action.getArmies());
    }

    @Test
    @DisplayName("endAttack() should create END_ATTACK action with no territory data")
    void endAttackShouldCreateCorrectAction() {
        CPUAction action = CPUAction.endAttack();

        assertEquals(CPUAction.ActionType.END_ATTACK, action.getType());
        assertNull(action.getFromTerritoryKey());
        assertNull(action.getToTerritoryKey());
        assertEquals(0, action.getArmies());
    }

    @Test
    @DisplayName("skipFortify() should create SKIP_FORTIFY action with no territory data")
    void skipFortifyShouldCreateCorrectAction() {
        CPUAction action = CPUAction.skipFortify();

        assertEquals(CPUAction.ActionType.SKIP_FORTIFY, action.getType());
        assertNull(action.getFromTerritoryKey());
        assertNull(action.getToTerritoryKey());
        assertEquals(0, action.getArmies());
    }

    @Test
    @DisplayName("default constructor should create action with null fields")
    void defaultConstructorShouldCreateEmptyAction() {
        CPUAction action = new CPUAction();

        assertNull(action.getType());
        assertNull(action.getFromTerritoryKey());
        assertNull(action.getToTerritoryKey());
        assertEquals(0, action.getArmies());
    }

    @Test
    @DisplayName("setters should update all fields")
    void settersShouldUpdateFields() {
        CPUAction action = new CPUAction();
        action.setType(CPUAction.ActionType.ATTACK);
        action.setFromTerritoryKey("from");
        action.setToTerritoryKey("to");
        action.setArmies(3);

        assertEquals(CPUAction.ActionType.ATTACK, action.getType());
        assertEquals("from", action.getFromTerritoryKey());
        assertEquals("to", action.getToTerritoryKey());
        assertEquals(3, action.getArmies());
    }

    @Test
    @DisplayName("ActionType enum should have all expected values")
    void actionTypeEnumShouldHaveAllValues() {
        CPUAction.ActionType[] values = CPUAction.ActionType.values();
        assertEquals(5, values.length);
        assertNotNull(CPUAction.ActionType.valueOf("PLACE_ARMIES"));
        assertNotNull(CPUAction.ActionType.valueOf("ATTACK"));
        assertNotNull(CPUAction.ActionType.valueOf("FORTIFY"));
        assertNotNull(CPUAction.ActionType.valueOf("END_ATTACK"));
        assertNotNull(CPUAction.ActionType.valueOf("SKIP_FORTIFY"));
    }
}
