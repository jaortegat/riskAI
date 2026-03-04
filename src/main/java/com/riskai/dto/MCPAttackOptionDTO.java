package com.riskai.dto;

/**
 * Minimal attack option for MCP tool responses.
 * Contains only the target territory key, its army count, owner name,
 * and the pre-computed max attack armies — no visual/UI fields.
 */
public record MCPAttackOptionDTO(
        String territoryKey,
        String ownerName,
        int armies,
        int maxAttackArmies
) {
}
