package com.risk.config;

import java.util.List;

/**
 * An area on the map (equivalent to a continent in the classic world map).
 * Controlling all territories in an area grants bonus reinforcements.
 *
 * @param key         unique key within this map, e.g. "EUROPE"
 * @param name        display name, e.g. "Europe"
 * @param bonusArmies reinforcement bonus for controlling the whole area
 * @param color       CSS colour used on the map, e.g. "#4169E1"
 * @param territories territories that belong to this area
 */
public record AreaDefinition(
        String key,
        String name,
        int bonusArmies,
        String color,
        List<TerritoryDefinition> territories
) {}
