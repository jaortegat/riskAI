package com.risk.dto;

import com.risk.model.Game;
import com.risk.model.GamePhase;
import com.risk.model.GameStatus;
import com.risk.model.Player;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO for game state representation.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GameStateDTO {

    private String gameId;
    private String gameName;
    private String mapId;
    private String mapName;
    private GameStatus status;
    private GamePhase currentPhase;
    private int turnNumber;
    private int reinforcementsRemaining;
    private PlayerDTO currentPlayer;
    private List<PlayerDTO> players;
    private List<TerritoryDTO> territories;
    private List<ContinentDTO> continents;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private String winnerId;
    private String winnerName;
    private int maxPlayers;
    private String gameMode;
    private int dominationPercent;
    private int turnLimit;
    private int totalTerritories;

    public static GameStateDTO fromGame(Game game) {
        return GameStateDTO.builder()
                .gameId(game.getId())
                .gameName(game.getName())
                .mapId(game.getMapId())
                .status(game.getStatus())
                .currentPhase(game.getCurrentPhase())
                .turnNumber(game.getTurnNumber())
                .reinforcementsRemaining(game.getReinforcementsRemaining())
                .maxPlayers(game.getMaxPlayers())
                .gameMode(game.getGameMode().name())
                .dominationPercent(game.getDominationPercent())
                .turnLimit(game.getTurnLimit())
                .currentPlayer(game.getCurrentPlayer() != null ? 
                    PlayerDTO.fromPlayer(game.getCurrentPlayer()) : null)
                .players(game.getPlayers().stream()
                    .map(PlayerDTO::fromPlayer)
                    .toList())
                .territories(game.getTerritories().stream()
                    .map(TerritoryDTO::fromTerritory)
                    .toList())
                .createdAt(game.getCreatedAt())
                .startedAt(game.getStartedAt())
                .winnerId(game.getWinnerId())
                .winnerName(game.getWinnerId() != null
                    ? game.getPlayers().stream()
                        .filter(p -> p.getId().equals(game.getWinnerId()))
                        .map(Player::getName)
                        .findFirst().orElse(null)
                    : null)
                .build();
    }
}
