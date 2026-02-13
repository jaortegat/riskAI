package com.risk.cpu;

/**
 * Represents an action decided by a CPU player.
 */
public class CPUAction {

    private ActionType type;
    private String fromTerritoryKey;
    private String toTerritoryKey;
    private int armies;

    public CPUAction() {}

    public CPUAction(ActionType type, String fromTerritoryKey, String toTerritoryKey, int armies) {
        this.type = type;
        this.fromTerritoryKey = fromTerritoryKey;
        this.toTerritoryKey = toTerritoryKey;
        this.armies = armies;
    }

    public ActionType getType() { return type; }
    public void setType(ActionType type) { this.type = type; }
    public String getFromTerritoryKey() { return fromTerritoryKey; }
    public void setFromTerritoryKey(String fromTerritoryKey) { this.fromTerritoryKey = fromTerritoryKey; }
    public String getToTerritoryKey() { return toTerritoryKey; }
    public void setToTerritoryKey(String toTerritoryKey) { this.toTerritoryKey = toTerritoryKey; }
    public int getArmies() { return armies; }
    public void setArmies(int armies) { this.armies = armies; }

    public enum ActionType {
        PLACE_ARMIES,
        ATTACK,
        FORTIFY,
        END_ATTACK,
        SKIP_FORTIFY
    }

    public static CPUAction placeArmies(String territoryKey, int armies) {
        return new CPUAction(ActionType.PLACE_ARMIES, null, territoryKey, armies);
    }

    public static CPUAction attack(String from, String to, int armies) {
        return new CPUAction(ActionType.ATTACK, from, to, armies);
    }

    public static CPUAction fortify(String from, String to, int armies) {
        return new CPUAction(ActionType.FORTIFY, from, to, armies);
    }

    public static CPUAction endAttack() {
        return new CPUAction(ActionType.END_ATTACK, null, null, 0);
    }

    public static CPUAction skipFortify() {
        return new CPUAction(ActionType.SKIP_FORTIFY, null, null, 0);
    }
}
