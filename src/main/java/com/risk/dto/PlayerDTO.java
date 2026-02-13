package com.risk.dto;

import com.risk.model.*;
import lombok.*;

/**
 * DTO for player representation.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlayerDTO {

    private String id;
    private String name;
    private PlayerColor color;
    private String colorHex;
    private PlayerType type;
    private CPUDifficulty cpuDifficulty;
    private int turnOrder;
    private boolean eliminated;
    private int territoryCount;
    private int totalArmies;
    private int cardsHeld;
    private boolean isCurrentPlayer;

    public static PlayerDTO fromPlayer(Player player) {
        return PlayerDTO.builder()
                .id(player.getId())
                .name(player.getName())
                .color(player.getColor())
                .colorHex(player.getColor().getHexCode())
                .type(player.getType())
                .cpuDifficulty(player.getCpuDifficulty())
                .turnOrder(player.getTurnOrder())
                .eliminated(player.isEliminated())
                .territoryCount(player.getTerritoryCount())
                .totalArmies(player.getTotalArmies())
                .cardsHeld(player.getCardsHeld())
                .build();
    }
}
