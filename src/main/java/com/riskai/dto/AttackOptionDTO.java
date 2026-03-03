package com.riskai.dto;

/**
 * Represents a single attack option returned by getAttackableTargets.
 * Bundles the target territory with the pre-computed maximum number of armies
 * the agent may use for this attack. Always pass {@code maxAttackArmies} directly
 * to the attack tool — never guess or hard-code the army count.
 */
public record AttackOptionDTO(
        TerritoryDTO target,
        int maxAttackArmies) {
}
