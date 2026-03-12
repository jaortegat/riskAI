package com.riskai.dto;

import com.riskai.model.GamePhase;
import com.riskai.model.GameStatus;

import java.util.List;

/**
 * Concise turn status designed for AI agent polling.
 * Tells the agent whether it's their turn, what phase they're in,
 * and exactly which actions are available right now.
 *
 * @param gameId                the game identifier
 * @param gameStatus            current game status (WAITING_FOR_PLAYERS, IN_PROGRESS, FINISHED)
 * @param isYourTurn            true if it is currently the agent's turn
 * @param currentPhase          the active game phase (REINFORCEMENT, ATTACK, FORTIFY, etc.)
 * @param currentPlayerName     name of the player whose turn it is
 * @param yourPlayerId          the agent's player ID (echoed back for convenience)
 * @param yourPlayerName        the agent's player name
 * @param reinforcementsRemaining armies left to place (only relevant in REINFORCEMENT phase)
 * @param turnNumber            the current turn number
 * @param availableActions      list of MCP tool names the agent can call right now
 * @param hint                  human-readable guidance on what to do next
 */
public record TurnStatusDTO(
        String gameId,
        GameStatus gameStatus,
        boolean isYourTurn,
        GamePhase currentPhase,
        String currentPlayerName,
        String yourPlayerId,
        String yourPlayerName,
        int reinforcementsRemaining,
        int turnNumber,
        List<String> availableActions,
        String hint
) {}
