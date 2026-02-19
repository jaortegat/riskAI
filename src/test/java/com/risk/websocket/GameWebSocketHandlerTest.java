package com.risk.websocket;

import com.risk.dto.GameStateDTO;
import com.risk.service.GameService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for GameWebSocketHandler.
 * Covers BUG 6 regression (broadcastError actually sends messages).
 */
@ExtendWith(MockitoExtension.class)
class GameWebSocketHandlerTest {

    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private GameService gameService;

    private GameWebSocketHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GameWebSocketHandler(messagingTemplate, gameService);
    }

    @Nested
    @DisplayName("broadcastError() — BUG 6 regression")
    class BroadcastErrorTests {

        @Test
        @DisplayName("should send error message to game topic via STOMP")
        void shouldSendErrorViaSTOMP() {
            handler.broadcastError("game-1", "player-1", "Invalid action");

            ArgumentCaptor<String> destCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<Object> msgCaptor = ArgumentCaptor.forClass(Object.class);

            verify(messagingTemplate).convertAndSend(destCaptor.capture(), msgCaptor.capture());

            assertEquals("/topic/game/game-1", destCaptor.getValue());

            GameWebSocketHandler.GameMessage msg = (GameWebSocketHandler.GameMessage) msgCaptor.getValue();
            assertEquals("ERROR", msg.getType());
            assertNotNull(msg.getPayload());
            assertTrue(msg.getTimestamp() > 0);
        }

        @Test
        @DisplayName("error payload should contain playerId and error message")
        void errorPayloadShouldContainDetails() {
            handler.broadcastError("game-1", "player-42", "Not your turn");

            ArgumentCaptor<Object> msgCaptor = ArgumentCaptor.forClass(Object.class);
            verify(messagingTemplate).convertAndSend(anyString(), msgCaptor.capture());

            GameWebSocketHandler.GameMessage msg = (GameWebSocketHandler.GameMessage) msgCaptor.getValue();
            GameWebSocketHandler.GameErrorMessage errorPayload =
                    (GameWebSocketHandler.GameErrorMessage) msg.getPayload();

            assertEquals("player-42", errorPayload.getPlayerId());
            assertEquals("Not your turn", errorPayload.getError());
        }

        @Test
        @DisplayName("should not throw when called with null playerId")
        void shouldHandleNullPlayerId() {
            assertDoesNotThrow(() -> handler.broadcastError("game-1", null, "Some error"));
            verify(messagingTemplate).convertAndSend(anyString(), any(GameWebSocketHandler.GameMessage.class));
        }
    }

    @Nested
    @DisplayName("broadcastGameUpdate()")
    class BroadcastGameUpdateTests {

        @Test
        @DisplayName("should send GAME_UPDATE message with game state")
        void shouldSendGameUpdate() {
            GameStateDTO mockState = new GameStateDTO();
            when(gameService.getGameState("game-1")).thenReturn(mockState);

            handler.broadcastGameUpdate("game-1");

            ArgumentCaptor<Object> msgCaptor = ArgumentCaptor.forClass(Object.class);
            verify(messagingTemplate).convertAndSend(eq("/topic/game/game-1"), msgCaptor.capture());

            GameWebSocketHandler.GameMessage msg = (GameWebSocketHandler.GameMessage) msgCaptor.getValue();
            assertEquals("GAME_UPDATE", msg.getType());
            assertEquals(mockState, msg.getPayload());
        }

        @Test
        @DisplayName("should handle exception without propagating")
        void shouldNotPropagateException() {
            when(gameService.getGameState("game-1")).thenThrow(new RuntimeException("DB error"));

            assertDoesNotThrow(() -> handler.broadcastGameUpdate("game-1"));
        }
    }

    @Nested
    @DisplayName("broadcastGameOver()")
    class BroadcastGameOverTests {

        @Test
        @DisplayName("should send GAME_OVER message with winner name")
        void shouldSendGameOver() {
            handler.broadcastGameOver("game-1", "Champion");

            ArgumentCaptor<Object> msgCaptor = ArgumentCaptor.forClass(Object.class);
            verify(messagingTemplate).convertAndSend(eq("/topic/game/game-1"), msgCaptor.capture());

            GameWebSocketHandler.GameMessage msg = (GameWebSocketHandler.GameMessage) msgCaptor.getValue();
            assertEquals("GAME_OVER", msg.getType());
            assertEquals("Champion", msg.getPayload());
        }
    }

    @Nested
    @DisplayName("broadcastCPUTurnEnd()")
    class BroadcastCPUTurnEndTests {

        @Test
        @DisplayName("should send CPU_TURN_END message")
        void shouldSendCPUTurnEnd() {
            handler.broadcastCPUTurnEnd("game-1", "CPU Player 1");

            ArgumentCaptor<Object> msgCaptor = ArgumentCaptor.forClass(Object.class);
            verify(messagingTemplate).convertAndSend(eq("/topic/game/game-1"), msgCaptor.capture());

            GameWebSocketHandler.GameMessage msg = (GameWebSocketHandler.GameMessage) msgCaptor.getValue();
            assertEquals("CPU_TURN_END", msg.getType());
            assertEquals("CPU Player 1", msg.getPayload());
        }
    }

    @Nested
    @DisplayName("GameMessage factory methods")
    class GameMessageTests {

        @Test
        @DisplayName("all factory methods should set timestamp")
        void allFactoryMethodsShouldSetTimestamp() {
            long before = System.currentTimeMillis();

            GameWebSocketHandler.GameMessage msg1 = GameWebSocketHandler.GameMessage.gameOver("winner");
            GameWebSocketHandler.GameMessage msg2 = GameWebSocketHandler.GameMessage.cpuTurnEnd("cpu");
            GameWebSocketHandler.GameMessage msg3 = GameWebSocketHandler.GameMessage.playerLeft("player");

            long after = System.currentTimeMillis();

            assertTrue(msg1.getTimestamp() >= before && msg1.getTimestamp() <= after);
            assertTrue(msg2.getTimestamp() >= before && msg2.getTimestamp() <= after);
            assertTrue(msg3.getTimestamp() >= before && msg3.getTimestamp() <= after);
        }

        @Test
        @DisplayName("error message should have correct type")
        void errorMessageType() {
            var errorMsg = new GameWebSocketHandler.GameErrorMessage("p1", "bad move");
            GameWebSocketHandler.GameMessage msg = GameWebSocketHandler.GameMessage.error(errorMsg);

            assertEquals("ERROR", msg.getType());
            assertEquals(errorMsg, msg.getPayload());
        }
    }
}
