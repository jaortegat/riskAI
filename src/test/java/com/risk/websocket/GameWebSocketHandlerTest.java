package com.risk.websocket;

import com.risk.cpu.CPUAction;
import com.risk.dto.AttackResult;
import com.risk.dto.GameStateDTO;
import com.risk.dto.PlayerDTO;
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
    @DisplayName("broadcastAttackResult()")
    class BroadcastAttackResultTests {

        @Test
        @DisplayName("should send ATTACK_RESULT with territory keys and dice results")
        void shouldSendAttackResult() {
            AttackResult result = AttackResult.builder()
                    .attackerDice(new int[]{6, 5}).defenderDice(new int[]{3})
                    .attackerLosses(0).defenderLosses(1)
                    .conquered(false).build();

            handler.broadcastAttackResult("game-1", "alaska", "kamchatka", result);

            ArgumentCaptor<Object> msgCaptor = ArgumentCaptor.forClass(Object.class);
            verify(messagingTemplate).convertAndSend(eq("/topic/game/game-1"), msgCaptor.capture());

            GameWebSocketHandler.GameMessage msg = (GameWebSocketHandler.GameMessage) msgCaptor.getValue();
            assertEquals("ATTACK_RESULT", msg.getType());

            GameWebSocketHandler.AttackResultMessage payload =
                    (GameWebSocketHandler.AttackResultMessage) msg.getPayload();
            assertEquals("alaska", payload.getFromTerritory());
            assertEquals("kamchatka", payload.getToTerritory());
            assertArrayEquals(new int[]{6, 5}, payload.getAttackerDice());
            assertArrayEquals(new int[]{3}, payload.getDefenderDice());
            assertEquals(0, payload.getAttackerLosses());
            assertEquals(1, payload.getDefenderLosses());
            assertFalse(payload.isConquered());
            assertNull(payload.getEliminatedPlayer());
        }

        @Test
        @DisplayName("should include eliminated player when territory conquered")
        void shouldIncludeEliminatedPlayer() {
            AttackResult result = AttackResult.builder()
                    .attackerDice(new int[]{6}).defenderDice(new int[]{1})
                    .attackerLosses(0).defenderLosses(1)
                    .conquered(true).eliminatedPlayer("Bob").build();

            handler.broadcastAttackResult("game-1", "brazil", "argentina", result);

            ArgumentCaptor<Object> msgCaptor = ArgumentCaptor.forClass(Object.class);
            verify(messagingTemplate).convertAndSend(anyString(), msgCaptor.capture());

            GameWebSocketHandler.GameMessage msg = (GameWebSocketHandler.GameMessage) msgCaptor.getValue();
            GameWebSocketHandler.AttackResultMessage payload =
                    (GameWebSocketHandler.AttackResultMessage) msg.getPayload();
            assertTrue(payload.isConquered());
            assertEquals("Bob", payload.getEliminatedPlayer());
        }

        @Test
        @DisplayName("CPUAction overload should delegate to string overload")
        void shouldDelegateFromCPUAction() {
            CPUAction action = new CPUAction(CPUAction.ActionType.ATTACK, "alaska", "kamchatka", 2);
            AttackResult result = AttackResult.builder()
                    .attackerDice(new int[]{6, 4}).defenderDice(new int[]{5})
                    .attackerLosses(0).defenderLosses(1)
                    .conquered(false).build();

            handler.broadcastAttackResult("game-1", action, result);

            ArgumentCaptor<Object> msgCaptor = ArgumentCaptor.forClass(Object.class);
            verify(messagingTemplate).convertAndSend(eq("/topic/game/game-1"), msgCaptor.capture());

            GameWebSocketHandler.GameMessage msg = (GameWebSocketHandler.GameMessage) msgCaptor.getValue();
            GameWebSocketHandler.AttackResultMessage payload =
                    (GameWebSocketHandler.AttackResultMessage) msg.getPayload();
            assertEquals("alaska", payload.getFromTerritory());
            assertEquals("kamchatka", payload.getToTerritory());
        }
    }

    @Nested
    @DisplayName("broadcastCPUFortify()")
    class BroadcastCPUFortifyTests {

        @Test
        @DisplayName("should send CPU_FORTIFY message with details")
        void shouldSendCPUFortify() {
            handler.broadcastCPUFortify("game-1", "CPU Player", "brazil", "argentina", 5);

            ArgumentCaptor<Object> msgCaptor = ArgumentCaptor.forClass(Object.class);
            verify(messagingTemplate).convertAndSend(eq("/topic/game/game-1"), msgCaptor.capture());

            GameWebSocketHandler.GameMessage msg = (GameWebSocketHandler.GameMessage) msgCaptor.getValue();
            assertEquals("CPU_FORTIFY", msg.getType());

            GameWebSocketHandler.CPUFortifyMessage payload =
                    (GameWebSocketHandler.CPUFortifyMessage) msg.getPayload();
            assertEquals("CPU Player", payload.getPlayerName());
            assertEquals("brazil", payload.getFromTerritory());
            assertEquals("argentina", payload.getToTerritory());
            assertEquals(5, payload.getArmies());
        }
    }

    @Nested
    @DisplayName("broadcastPlayerJoined()")
    class BroadcastPlayerJoinedTests {

        @Test
        @DisplayName("should send PLAYER_JOINED message with player DTO")
        void shouldSendPlayerJoined() {
            PlayerDTO player = PlayerDTO.builder().id("p1").name("Alice").build();

            handler.broadcastPlayerJoined("game-1", player);

            ArgumentCaptor<Object> msgCaptor = ArgumentCaptor.forClass(Object.class);
            verify(messagingTemplate).convertAndSend(eq("/topic/game/game-1"), msgCaptor.capture());

            GameWebSocketHandler.GameMessage msg = (GameWebSocketHandler.GameMessage) msgCaptor.getValue();
            assertEquals("PLAYER_JOINED", msg.getType());
            assertEquals(player, msg.getPayload());
        }
    }

    @Nested
    @DisplayName("broadcastPlayerLeft()")
    class BroadcastPlayerLeftTests {

        @Test
        @DisplayName("should send PLAYER_LEFT message with player name")
        void shouldSendPlayerLeft() {
            handler.broadcastPlayerLeft("game-1", "Bob");

            ArgumentCaptor<Object> msgCaptor = ArgumentCaptor.forClass(Object.class);
            verify(messagingTemplate).convertAndSend(eq("/topic/game/game-1"), msgCaptor.capture());

            GameWebSocketHandler.GameMessage msg = (GameWebSocketHandler.GameMessage) msgCaptor.getValue();
            assertEquals("PLAYER_LEFT", msg.getType());
            assertEquals("Bob", msg.getPayload());
        }
    }

    @Nested
    @DisplayName("broadcastGameStarted()")
    class BroadcastGameStartedTests {

        @Test
        @DisplayName("should send GAME_STARTED message with game state")
        void shouldSendGameStarted() {
            GameStateDTO mockState = new GameStateDTO();
            when(gameService.getGameState("game-1")).thenReturn(mockState);

            handler.broadcastGameStarted("game-1");

            ArgumentCaptor<Object> msgCaptor = ArgumentCaptor.forClass(Object.class);
            verify(messagingTemplate).convertAndSend(eq("/topic/game/game-1"), msgCaptor.capture());

            GameWebSocketHandler.GameMessage msg = (GameWebSocketHandler.GameMessage) msgCaptor.getValue();
            assertEquals("GAME_STARTED", msg.getType());
            assertEquals(mockState, msg.getPayload());
        }
    }

    @Nested
    @DisplayName("broadcastChatMessage()")
    class BroadcastChatMessageTests {

        @Test
        @DisplayName("should send chat message to /chat subtopic")
        void shouldSendChatToSubtopic() {
            handler.broadcastChatMessage("game-1", "Alice", "Hello world!");

            ArgumentCaptor<String> destCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<Object> msgCaptor = ArgumentCaptor.forClass(Object.class);
            verify(messagingTemplate).convertAndSend(destCaptor.capture(), msgCaptor.capture());

            assertEquals("/topic/game/game-1/chat", destCaptor.getValue());

            GameWebSocketHandler.ChatMessage chat =
                    (GameWebSocketHandler.ChatMessage) msgCaptor.getValue();
            assertEquals("Alice", chat.getPlayerName());
            assertEquals("Hello world!", chat.getMessage());
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

        @Test
        @DisplayName("attackResult factory should set correct type and payload")
        void attackResultFactory() {
            var attackMsg = GameWebSocketHandler.AttackResultMessage.builder()
                    .fromTerritory("a").toTerritory("b")
                    .attackerDice(new int[]{6}).defenderDice(new int[]{3})
                    .attackerLosses(0).defenderLosses(1)
                    .conquered(false).build();

            GameWebSocketHandler.GameMessage msg = GameWebSocketHandler.GameMessage.attackResult(attackMsg);
            assertEquals("ATTACK_RESULT", msg.getType());
            assertEquals(attackMsg, msg.getPayload());
            assertTrue(msg.getTimestamp() > 0);
        }

        @Test
        @DisplayName("playerJoined factory should set correct type and payload")
        void playerJoinedFactory() {
            PlayerDTO player = PlayerDTO.builder().id("p1").name("Alice").build();

            GameWebSocketHandler.GameMessage msg = GameWebSocketHandler.GameMessage.playerJoined(player);
            assertEquals("PLAYER_JOINED", msg.getType());
            assertEquals(player, msg.getPayload());
            assertTrue(msg.getTimestamp() > 0);
        }

        @Test
        @DisplayName("cpuFortify factory should set correct type and payload")
        void cpuFortifyFactory() {
            var fortifyMsg = new GameWebSocketHandler.CPUFortifyMessage("CPU", "from", "to", 3);

            GameWebSocketHandler.GameMessage msg = GameWebSocketHandler.GameMessage.cpuFortify(fortifyMsg);
            assertEquals("CPU_FORTIFY", msg.getType());
            assertEquals(fortifyMsg, msg.getPayload());
            assertTrue(msg.getTimestamp() > 0);
        }

        @Test
        @DisplayName("gameStarted factory should set correct type and payload")
        void gameStartedFactory() {
            GameStateDTO state = new GameStateDTO();

            GameWebSocketHandler.GameMessage msg = GameWebSocketHandler.GameMessage.gameStarted(state);
            assertEquals("GAME_STARTED", msg.getType());
            assertEquals(state, msg.getPayload());
            assertTrue(msg.getTimestamp() > 0);
        }

        @Test
        @DisplayName("gameUpdate factory should set correct type")
        void gameUpdateFactory() {
            GameStateDTO state = new GameStateDTO();

            GameWebSocketHandler.GameMessage msg = GameWebSocketHandler.GameMessage.gameUpdate(state);
            assertEquals("GAME_UPDATE", msg.getType());
            assertEquals(state, msg.getPayload());
        }
    }
}
