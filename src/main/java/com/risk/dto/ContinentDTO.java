package com.risk.dto;

import com.risk.model.Continent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for continent representation.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContinentDTO {

    private String id;
    private String name;
    private String continentKey;
    private int bonusArmies;
    private String color;
    private int territoryCount;
    private String controlledBy;

    public static ContinentDTO fromContinent(Continent continent) {
        String controller = null;
        if (!continent.getTerritories().isEmpty()) {
            var firstOwner = continent.getTerritories().get(0).getOwner();
            if (firstOwner != null && continent.isControlledBy(firstOwner)) {
                controller = firstOwner.getName();
            }
        }

        return ContinentDTO.builder()
                .id(continent.getId())
                .name(continent.getName())
                .continentKey(continent.getContinentKey())
                .bonusArmies(continent.getBonusArmies())
                .color(continent.getColor())
                .territoryCount(continent.getTerritories().size())
                .controlledBy(controller)
                .build();
    }
}
