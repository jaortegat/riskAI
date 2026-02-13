package com.risk.dto;

import lombok.*;

/**
 * DTO for game actions sent via WebSocket.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GameActionDTO {

    private ActionType actionType;
    private String playerId;
    private String fromTerritoryKey;
    private String toTerritoryKey;
    private int armies;
    private int[] attackerDice;
    private int[] defenderDice;

    public enum ActionType {
        PLACE_ARMIES,
        ATTACK,
        ATTACK_RESULT,
        FORTIFY,
        END_ATTACK,
        END_TURN,
        SKIP_FORTIFY
    }
}
