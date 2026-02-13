package com.risk.config;

import java.util.List;

/**
 * A single territory on the map.
 *
 * @param key       unique key within this map, e.g. "WESTERN_EUROPE"
 * @param name      display name, e.g. "Western Europe"
 * @param neighbors list of territory keys this territory connects to
 * @param mapX      X coordinate for rendering on the board
 * @param mapY      Y coordinate for rendering on the board
 */
public record TerritoryDefinition(
        String key,
        String name,
        List<String> neighbors,
        double mapX,
        double mapY
) {}
