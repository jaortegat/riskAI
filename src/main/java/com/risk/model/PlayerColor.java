package com.risk.model;

/**
 * Available colors for players.
 */
public enum PlayerColor {
    RED("#e74c3c"),
    BLUE("#3498db"),
    GREEN("#2ecc71"),
    YELLOW("#f1c40f"),
    PURPLE("#9b59b6"),
    ORANGE("#e67e22");

    private final String hexCode;

    PlayerColor(String hexCode) {
        this.hexCode = hexCode;
    }

    public String getHexCode() {
        return hexCode;
    }
}
