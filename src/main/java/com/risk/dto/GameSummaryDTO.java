package com.risk.dto;

import lombok.*;

/**
 * DTO for game summary in list views.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GameSummaryDTO {

    private String id;
    private String name;
    private String mapId;
    private String mapName;
    private String status;
    private int playerCount;
    private int maxPlayers;
    private int minPlayers;
    private String createdAt;
    private boolean canJoin;
    private String hostName;
    private String gameMode;
}
