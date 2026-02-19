package com.risk.websocket;

import com.risk.dto.*;
import com.risk.model.*;
import com.risk.service.*;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

/**
 * WebSocket controller for real-time game interactions.
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class GameWebSocketController {

    private final GameService gameService;
    private final CPUPlayerService cpuPlayerService;
    private final GameWebSocketHandler webSocketHandler;

    /**
     * Handle reinforcement placement.
     */
    @MessageMapping("/game/{gameId}/reinforce")
    public void handleReinforce(@DestinationVariable String gameId,
                                @Payload ReinforceMessage message,
                                SimpMessageHeaderAccessor headerAccessor) {
        log.debug("Reinforce request: {} armies to {} in game {}",
                message.getArmies(), message.getTerritoryKey(), gameId);

        try {
            gameService.placeArmies(gameId, message.getPlayerId(),
                    message.getTerritoryKey(), message.getArmies());
            webSocketHandler.broadcastGameUpdate(gameId);
            
            // Check if CPU turn should start
            cpuPlayerService.checkAndTriggerCPUTurn(gameId);
        } catch (Exception e) {
            log.error("Error processing reinforce", e);
            sendError(gameId, message.getPlayerId(), e.getMessage());
        }
    }

    /**
     * Handle attack action.
     */
    @MessageMapping("/game/{gameId}/attack")
    public void handleAttack(@DestinationVariable String gameId,
                            @Payload AttackMessage message,
                            SimpMessageHeaderAccessor headerAccessor) {
        log.debug("Attack request: {} -> {} with {} armies in game {}",
                message.getFromTerritoryKey(), message.getToTerritoryKey(),
                message.getArmies(), gameId);

        try {
            GameService.AttackResult result = gameService.attack(
                    gameId, message.getPlayerId(),
                    message.getFromTerritoryKey(), message.getToTerritoryKey(),
                    message.getArmies());

            // Broadcast attack result
            webSocketHandler.broadcastAttackResult(gameId,
                    com.risk.cpu.CPUAction.attack(message.getFromTerritoryKey(),
                            message.getToTerritoryKey(), message.getArmies()),
                    result);

            webSocketHandler.broadcastGameUpdate(gameId);
            
            // Check for game over
            Game game = gameService.getGame(gameId);
            if (game.getStatus() == GameStatus.FINISHED) {
                webSocketHandler.broadcastGameOver(gameId,
                        game.getPlayers().stream()
                                .filter(p -> p.getId().equals(game.getWinnerId()))
                                .findFirst()
                                .map(Player::getName)
                                .orElse("Unknown"));
            }
        } catch (Exception e) {
            log.error("Error processing attack", e);
            sendError(gameId, message.getPlayerId(), e.getMessage());
        }
    }

    /**
     * Handle end attack phase.
     */
    @MessageMapping("/game/{gameId}/endAttack")
    public void handleEndAttack(@DestinationVariable String gameId,
                                @Payload PlayerIdMessage message) {
        log.debug("End attack request in game {}", gameId);

        try {
            gameService.endAttackPhase(gameId, message.getPlayerId());
            webSocketHandler.broadcastGameUpdate(gameId);
        } catch (Exception e) {
            log.error("Error ending attack phase", e);
            sendError(gameId, message.getPlayerId(), e.getMessage());
        }
    }

    /**
     * Handle fortify action.
     */
    @MessageMapping("/game/{gameId}/fortify")
    public void handleFortify(@DestinationVariable String gameId,
                             @Payload FortifyMessage message) {
        log.debug("Fortify request: {} -> {} with {} armies in game {}",
                message.getFromTerritoryKey(), message.getToTerritoryKey(),
                message.getArmies(), gameId);

        try {
            gameService.fortify(gameId, message.getPlayerId(),
                    message.getFromTerritoryKey(), message.getToTerritoryKey(),
                    message.getArmies());
            webSocketHandler.broadcastGameUpdate(gameId);

            // Check if game ended (e.g. turn-limit reached)
            Game game = gameService.getGame(gameId);
            if (game.getStatus() == GameStatus.FINISHED) {
                webSocketHandler.broadcastGameOver(gameId,
                        game.getPlayers().stream()
                                .filter(p -> p.getId().equals(game.getWinnerId()))
                                .findFirst()
                                .map(Player::getName)
                                .orElse("Unknown"));
                return;
            }
            
            // Check if CPU turn should start
            cpuPlayerService.checkAndTriggerCPUTurn(gameId);
        } catch (Exception e) {
            log.error("Error processing fortify", e);
            sendError(gameId, message.getPlayerId(), e.getMessage());
        }
    }

    /**
     * Handle skip fortify.
     */
    @MessageMapping("/game/{gameId}/skipFortify")
    public void handleSkipFortify(@DestinationVariable String gameId,
                                  @Payload PlayerIdMessage message) {
        log.debug("Skip fortify request in game {}", gameId);

        try {
            gameService.skipFortify(gameId, message.getPlayerId());
            webSocketHandler.broadcastGameUpdate(gameId);

            // Check if game ended (e.g. turn-limit reached)
            Game game = gameService.getGame(gameId);
            if (game.getStatus() == GameStatus.FINISHED) {
                webSocketHandler.broadcastGameOver(gameId,
                        game.getPlayers().stream()
                                .filter(p -> p.getId().equals(game.getWinnerId()))
                                .findFirst()
                                .map(Player::getName)
                                .orElse("Unknown"));
                return;
            }
            
            // Check if CPU turn should start
            cpuPlayerService.checkAndTriggerCPUTurn(gameId);
        } catch (Exception e) {
            log.error("Error skipping fortify", e);
            sendError(gameId, message.getPlayerId(), e.getMessage());
        }
    }

    /**
     * Handle chat message.
     */
    @MessageMapping("/game/{gameId}/chat")
    public void handleChat(@DestinationVariable String gameId,
                          @Payload ChatMessageRequest message) {
        webSocketHandler.broadcastChatMessage(gameId, message.getPlayerName(), message.getMessage());
    }

    private void sendError(String gameId, String playerId, String error) {
        // Send error to specific player
        log.warn("Error in game {}: {}", gameId, error);
        webSocketHandler.broadcastError(gameId, playerId, error);
    }

    // Message DTOs
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReinforceMessage {
        private String playerId;
        private String territoryKey;
        private int armies;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AttackMessage {
        private String playerId;
        private String fromTerritoryKey;
        private String toTerritoryKey;
        private int armies;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FortifyMessage {
        private String playerId;
        private String fromTerritoryKey;
        private String toTerritoryKey;
        private int armies;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlayerIdMessage {
        private String playerId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChatMessageRequest {
        private String playerName;
        private String message;
    }
}
