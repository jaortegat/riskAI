package com.risk.dto;

import com.risk.model.Territory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Set;

/**
 * DTO for territory representation.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TerritoryDTO {

    private String id;
    private String name;
    private String territoryKey;
    private String ownerId;
    private String ownerName;
    private String ownerColor;
    private String continentKey;
    private String continentName;
    private String continentColor;
    private int armies;
    private Set<String> neighborKeys;
    private double mapX;
    private double mapY;
    private boolean canAttackFrom;

    public static TerritoryDTO fromTerritory(Territory territory) {
        return TerritoryDTO.builder()
                .id(territory.getId())
                .name(territory.getName())
                .territoryKey(territory.getTerritoryKey())
                .ownerId(territory.getOwner() != null ? territory.getOwner().getId() : null)
                .ownerName(territory.getOwner() != null ? territory.getOwner().getName() : null)
                .ownerColor(territory.getOwner() != null ? 
                    territory.getOwner().getColor().getHexCode() : null)
                .continentKey(territory.getContinent() != null ? 
                    territory.getContinent().getContinentKey() : null)
                .continentName(territory.getContinent() != null ?
                    territory.getContinent().getName() : null)
                .continentColor(territory.getContinent() != null ?
                    territory.getContinent().getColor() : null)
                .armies(territory.getArmies())
                .neighborKeys(territory.getNeighborKeys())
                .mapX(territory.getMapX())
                .mapY(territory.getMapY())
                .canAttackFrom(territory.canAttackFrom())
                .build();
    }
}
