package com.riskai.dto;

import java.util.Set;

/**
 * Minimal territory representation for MCP tool responses.
 * Strips visual/UI fields (mapX, mapY, colors, continent names)
 * that AI agents don't need, significantly reducing response size.
 */
public record MCPTerritoryDTO(
        String territoryKey,
        String continentKey,
        int armies,
        Set<String> neighborKeys
) {

    public static MCPTerritoryDTO fromTerritoryDTO(TerritoryDTO t) {
        return new MCPTerritoryDTO(
                t.getTerritoryKey(),
                t.getContinentKey(),
                t.getArmies(),
                t.getNeighborKeys()
        );
    }
}
