package com.risk.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Result of an attack action containing dice rolls, losses, and conquest info.
 */
@Data
@Builder
public class AttackResult {
    private int[] attackerDice;
    private int[] defenderDice;
    private int attackerLosses;
    private int defenderLosses;
    private boolean conquered;
    private String eliminatedPlayer;
}
