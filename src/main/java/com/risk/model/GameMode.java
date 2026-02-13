package com.risk.model;

/**
 * Game mode determines the win condition.
 */
public enum GameMode {
    CLASSIC,        // Conquer all territories to win
    DOMINATION,     // Control a percentage of territories to win
    TURN_LIMIT      // Most territories after N turns wins
}
