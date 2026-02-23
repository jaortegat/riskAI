package com.risk.dto;

import com.risk.config.MapDefinition;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for exposing available map information to the UI.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MapInfoDTO {

    private String id;
    private String name;
    private String description;
    private String author;
    private int minPlayers;
    private int maxPlayers;
    private int areaCount;
    private int territoryCount;

    public static MapInfoDTO fromDefinition(MapDefinition def) {
        int territories = def.areas().stream()
                .mapToInt(a -> a.territories().size())
                .sum();
        return MapInfoDTO.builder()
                .id(def.id())
                .name(def.name())
                .description(def.description())
                .author(def.author())
                .minPlayers(def.minPlayers())
                .maxPlayers(def.maxPlayers())
                .areaCount(def.areas().size())
                .territoryCount(territories)
                .build();
    }
}
