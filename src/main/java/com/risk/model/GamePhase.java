package com.risk.model;

/**
 * Represents the current phase within a player's turn.
 */
public enum GamePhase {
    SETUP,              // Initial territory distribution
    REINFORCEMENT,      // Player receives and places reinforcements
    ATTACK,             // Player may attack territories
    FORTIFY,            // Player may move armies between territories
    GAME_OVER           // Game has ended
}
