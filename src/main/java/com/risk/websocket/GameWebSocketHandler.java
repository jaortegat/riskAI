package com.risk.websocket;

import com.risk.cpu.CPUAction;
import com.risk.dto.*;
import com.risk.service.GameService;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Handler for WebSocket game updates.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GameWebSocketHandler {

    private final SimpMessagingTemplate messagingTemplate;
    private final GameService gameService;

    /**
     * Broadcast game state update to all players in a game.
     */
    public void broadcastGameUpdate(String gameId) {
        try {
            GameStateDTO gameState = gameService.getGameState(gameId);
            messagingTemplate.convertAndSend("/topic/game/" + gameId, 
                    GameMessage.gameUpdate(gameState));
            log.debug("Broadcast game update for game {}", gameId);
        } catch (Exception e) {
            log.error("Error broadcasting game update for game {}", gameId, e);
        }
    }

    /**
     * Broadcast attack result to all players.
     */
    public void broadcastAttackResult(String gameId, CPUAction action, GameService.AttackResult result) {
        broadcastAttackResult(gameId, action.getFromTerritoryKey(), action.getToTerritoryKey(), result);
    }

    /**
     * Broadcast attack result to all players (used for both human and CPU attacks).
     */
    public void broadcastAttackResult(String gameId, String fromTerritoryKey, String toTerritoryKey, GameService.AttackResult result) {
        AttackResultMessage message = AttackResultMessage.builder()
                .fromTerritory(fromTerritoryKey)
                .toTerritory(toTerritoryKey)
                .attackerDice(result.getAttackerDice())
                .defenderDice(result.getDefenderDice())
                .attackerLosses(result.getAttackerLosses())
                .defenderLosses(result.getDefenderLosses())
                .conquered(result.isConquered())
                .eliminatedPlayer(result.getEliminatedPlayer())
                .build();

        messagingTemplate.convertAndSend("/topic/game/" + gameId,
                GameMessage.attackResult(message));
    }

    /**
     * Broadcast CPU fortify action to all players for modal display.
     */
    public void broadcastCPUFortify(String gameId, String playerName,
                                     String fromName, String toName, int armies) {
        CPUFortifyMessage msg = new CPUFortifyMessage(playerName, fromName, toName, armies);
        messagingTemplate.convertAndSend("/topic/game/" + gameId,
                GameMessage.cpuFortify(msg));
    }

    /**
     * Broadcast CPU turn end notification.
     */
    public void broadcastCPUTurnEnd(String gameId, String playerName) {
        messagingTemplate.convertAndSend("/topic/game/" + gameId,
                GameMessage.cpuTurnEnd(playerName));
    }

    /**
     * Broadcast player joined notification.
     */
    public void broadcastPlayerJoined(String gameId, PlayerDTO player) {
        messagingTemplate.convertAndSend("/topic/game/" + gameId,
                GameMessage.playerJoined(player));
    }

    /**
     * Broadcast player left notification.
     */
    public void broadcastPlayerLeft(String gameId, String playerName) {
        messagingTemplate.convertAndSend("/topic/game/" + gameId,
                GameMessage.playerLeft(playerName));
    }

    /**
     * Broadcast game started notification.
     */
    public void broadcastGameStarted(String gameId) {
        GameStateDTO gameState = gameService.getGameState(gameId);
        messagingTemplate.convertAndSend("/topic/game/" + gameId,
                GameMessage.gameStarted(gameState));
    }

    /**
     * Broadcast game over notification.
     */
    public void broadcastGameOver(String gameId, String winnerName) {
        messagingTemplate.convertAndSend("/topic/game/" + gameId,
                GameMessage.gameOver(winnerName));
    }

    /**
     * Broadcast chat message.
     */
    public void broadcastChatMessage(String gameId, String playerName, String message) {
        ChatMessage chat = new ChatMessage(playerName, message);
        messagingTemplate.convertAndSend("/topic/game/" + gameId + "/chat", chat);
    }

    /**
     * Generic game message wrapper.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class GameMessage {
        private String type;
        private Object payload;
        private long timestamp;

        public static GameMessage gameUpdate(GameStateDTO state) {
            return GameMessage.builder()
                    .type("GAME_UPDATE")
                    .payload(state)
                    .timestamp(System.currentTimeMillis())
                    .build();
        }

        public static GameMessage attackResult(AttackResultMessage result) {
            return GameMessage.builder()
                    .type("ATTACK_RESULT")
                    .payload(result)
                    .timestamp(System.currentTimeMillis())
                    .build();
        }

        public static GameMessage playerJoined(PlayerDTO player) {
            return GameMessage.builder()
                    .type("PLAYER_JOINED")
                    .payload(player)
                    .timestamp(System.currentTimeMillis())
                    .build();
        }

        public static GameMessage playerLeft(String playerName) {
            return GameMessage.builder()
                    .type("PLAYER_LEFT")
                    .payload(playerName)
                    .timestamp(System.currentTimeMillis())
                    .build();
        }

        public static GameMessage gameStarted(GameStateDTO state) {
            return GameMessage.builder()
                    .type("GAME_STARTED")
                    .payload(state)
                    .timestamp(System.currentTimeMillis())
                    .build();
        }

        public static GameMessage gameOver(String winnerName) {
            return GameMessage.builder()
                    .type("GAME_OVER")
                    .payload(winnerName)
                    .timestamp(System.currentTimeMillis())
                    .build();
        }

        public static GameMessage cpuFortify(CPUFortifyMessage msg) {
            return GameMessage.builder()
                    .type("CPU_FORTIFY")
                    .payload(msg)
                    .timestamp(System.currentTimeMillis())
                    .build();
        }

        public static GameMessage cpuTurnEnd(String playerName) {
            return GameMessage.builder()
                    .type("CPU_TURN_END")
                    .payload(playerName)
                    .timestamp(System.currentTimeMillis())
                    .build();
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AttackResultMessage {
        private String fromTerritory;
        private String toTerritory;
        private int[] attackerDice;
        private int[] defenderDice;
        private int attackerLosses;
        private int defenderLosses;
        private boolean conquered;
        private String eliminatedPlayer;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CPUFortifyMessage {
        private String playerName;
        private String fromTerritory;
        private String toTerritory;
        private int armies;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChatMessage {
        private String playerName;
        private String message;
    }
}
