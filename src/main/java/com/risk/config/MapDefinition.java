package com.risk.config;

import java.util.List;

/**
 * Root definition of a playable map, loaded from a JSON file.
 *
 * @param id          unique slug, e.g. "classic-world"
 * @param name        human-readable name shown in the UI
 * @param description short description of the map
 * @param author      map author / credit
 * @param minPlayers  recommended minimum players
 * @param maxPlayers  recommended maximum players
 * @param areas       the regions (continents in the classic map)
 */
public record MapDefinition(
        String id,
        String name,
        String description,
        String author,
        int minPlayers,
        int maxPlayers,
        List<AreaDefinition> areas
) {}
