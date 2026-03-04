package com.riskai.dto;

import com.riskai.model.GamePhase;
import com.riskai.model.GameStatus;

import java.util.List;

/**
 * Lightweight result returned by MCP phase-transition tools
 * (endAttackPhase, fortify, skipFortify).
 * Contains only the essential state an AI agent needs after a phase change,
 * avoiding the full 42-territory payload of {@link GameStateDTO}.
 */
public record MCPPhaseResultDTO(
        GameStatus status,
        GamePhase currentPhase,
        int turnNumber,
        String currentPlayerName,
        List<PlayerScoreDTO> scores,
        String winnerName,
        String hint
) {

    /**
     * Compact player score summary.
     */
    public record PlayerScoreDTO(
            String name,
            int territories,
            int armies,
            boolean eliminated
    ) {}
}
