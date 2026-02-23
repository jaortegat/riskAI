package com.risk.dto;

import com.risk.model.CPUDifficulty;
import com.risk.model.GameMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for creating a new game.
 * Uses Integer wrappers so Jackson 3 leaves absent fields as null.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateGameRequest {

    @NotBlank(message = "Game name is required")
    @Size(min = 3, max = 50, message = "Game name must be between 3 and 50 characters")
    private String gameName;

    @Size(max = 30, message = "Player name must be at most 30 characters")
    private String playerName;

    private String mapId;

    public String getMapId() {
        return mapId != null && !mapId.isBlank() ? mapId : "classic-world";
    }

    private Integer maxPlayers;
    private Integer minPlayers;
    private Integer cpuPlayerCount;
    private CPUDifficulty cpuDifficulty;
    private GameMode gameMode;
    private Integer dominationPercent;
    private Integer turnLimit;

    public int getMaxPlayers() {
        return maxPlayers != null ? maxPlayers : 6;
    }

    public int getMinPlayers() {
        return minPlayers != null ? minPlayers : 2;
    }

    public int getCpuPlayerCount() {
        return cpuPlayerCount != null ? cpuPlayerCount : 0;
    }

    public CPUDifficulty getCpuDifficulty() {
        return cpuDifficulty != null ? cpuDifficulty : CPUDifficulty.MEDIUM;
    }

    public GameMode getGameMode() {
        return gameMode != null ? gameMode : GameMode.CLASSIC;
    }

    public int getDominationPercent() {
        return dominationPercent != null ? dominationPercent : 70;
    }

    public int getTurnLimit() {
        return turnLimit != null ? turnLimit : 20;
    }
}
