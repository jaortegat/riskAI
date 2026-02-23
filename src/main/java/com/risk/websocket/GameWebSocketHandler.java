package com.risk.websocket;

import com.risk.cpu.CPUAction;
import com.risk.dto.AttackResult;
import com.risk.dto.GameStateDTO;
import com.risk.dto.PlayerDTO;
import com.risk.service.GameService;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
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

    private static final String TOPIC_PREFIX = "/topic/game/";

    private final SimpMessagingTemplate messagingTemplate;
    private final GameService gameService;

    /**
     * Broadcast game state update to all players in a game.
     */
    public void broadcastGameUpdate(String gameId) {
        try {
            GameStateDTO gameState = gameService.getGameState(gameId);
            messagingTemplate.convertAndSend(TOPIC_PREFIX + gameId, 
                    GameMessage.gameUpdate(gameState));
            log.debug("Broadcast game update for game {}", gameId);
        } catch (RuntimeException e) {
            log.error("Error broadcasting game update for game {}", gameId, e);
        }
    }

    /**
     * Broadcast attack result to all players.
     */
    public void broadcastAttackResult(String gameId, CPUAction action, AttackResult result) {
        broadcastAttackResult(gameId, action.getFromTerritoryKey(), action.getToTerritoryKey(), result);
    }

    /**
     * Broadcast attack result to all players (used for both human and CPU attacks).
     */
    public void broadcastAttackResult(String gameId, String fromTerritoryKey, String toTerritoryKey, AttackResult result) {
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

        messagingTemplate.convertAndSend(TOPIC_PREFIX + gameId,
                GameMessage.attackResult(message));
    }

    /**
     * Broadcast CPU fortify action to all players for modal display.
     */
    public void broadcastCPUFortify(String gameId, String playerName,
                                     String fromName, String toName, int armies) {
        CPUFortifyMessage msg = new CPUFortifyMessage(playerName, fromName, toName, armies);
        messagingTemplate.convertAndSend(TOPIC_PREFIX + gameId,
                GameMessage.cpuFortify(msg));
    }

    /**
     * Broadcast CPU turn end notification.
     */
    public void broadcastCPUTurnEnd(String gameId, String playerName) {
        messagingTemplate.convertAndSend(TOPIC_PREFIX + gameId,
                GameMessage.cpuTurnEnd(playerName));
    }

    /**
     * Broadcast player joined notification.
     */
    public void broadcastPlayerJoined(String gameId, PlayerDTO player) {
        messagingTemplate.convertAndSend(TOPIC_PREFIX + gameId,
                GameMessage.playerJoined(player));
    }

    /**
     * Broadcast player left notification.
     */
    public void broadcastPlayerLeft(String gameId, String playerName) {
        messagingTemplate.convertAndSend(TOPIC_PREFIX + gameId,
                GameMessage.playerLeft(playerName));
    }

    /**
     * Broadcast game started notification.
     */
    public void broadcastGameStarted(String gameId) {
        GameStateDTO gameState = gameService.getGameState(gameId);
        messagingTemplate.convertAndSend(TOPIC_PREFIX + gameId,
                GameMessage.gameStarted(gameState));
    }

    /**
     * Broadcast game over notification.
     */
    public void broadcastGameOver(String gameId, String winnerName) {
        messagingTemplate.convertAndSend(TOPIC_PREFIX + gameId,
                GameMessage.gameOver(winnerName));
    }

    /**
     * Broadcast an error message to all players (clients can filter by playerId).
     */
    public void broadcastError(String gameId, String playerId, String error) {
        GameErrorMessage msg = new GameErrorMessage(playerId, error);
        messagingTemplate.convertAndSend(TOPIC_PREFIX + gameId,
                GameMessage.error(msg));
    }

    /**
     * Broadcast chat message.
     */
    public void broadcastChatMessage(String gameId, String playerName, String message) {
        ChatMessage chat = new ChatMessage(playerName, message);
        messagingTemplate.convertAndSend(TOPIC_PREFIX + gameId + "/chat", chat);
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

        public static GameMessage error(GameErrorMessage error) {
            return GameMessage.builder()
                    .type("ERROR")
                    .payload(error)
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

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GameErrorMessage {
        private String playerId;
        private String error;
    }
}
