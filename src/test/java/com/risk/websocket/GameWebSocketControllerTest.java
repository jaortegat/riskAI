package com.risk.websocket;

import com.risk.cpu.CPUAction;
import com.risk.dto.*;
import com.risk.model.*;
import com.risk.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for GameWebSocketController message handlers.
 */
@ExtendWith(MockitoExtension.class)
class GameWebSocketControllerTest {

    @Mock private GameService gameService;
    @Mock private CPUPlayerService cpuPlayerService;
    @Mock private GameWebSocketHandler webSocketHandler;

    @InjectMocks
    private GameWebSocketController controller;

    private Game game;
    private Player player;

    @BeforeEach
    void setUp() {
        player = Player.builder()
                .id("player-1").name("Alice").color(PlayerColor.RED)
                .type(PlayerType.HUMAN).turnOrder(0).build();

        game = Game.builder()
                .id("game-1").name("Test")
                .status(GameStatus.IN_PROGRESS)
                .currentPhase(GamePhase.REINFORCEMENT)
                .currentPlayerIndex(0).turnNumber(1)
                .maxPlayers(6).minPlayers(2)
                .mapId("classic-world")
                .gameMode(GameMode.CLASSIC)
                .players(new ArrayList<>(List.of(player)))
                .territories(new HashSet<>())
                .build();

        player.setGame(game);
    }

    @Nested
    @DisplayName("handleReinforce")
    class ReinforceTests {

        @Test
        @DisplayName("should call placeArmies and broadcast update")
        void shouldReinforceAndBroadcast() {
            var msg = new GameWebSocketController.ReinforceMessage("player-1", "brazil", 3);

            controller.handleReinforce("game-1", msg);

            verify(gameService).placeArmies("game-1", "player-1", "brazil", 3);
            verify(webSocketHandler).broadcastGameUpdate("game-1");
            verify(cpuPlayerService).checkAndTriggerCPUTurn("game-1");
        }

        @Test
        @DisplayName("should broadcast error on exception")
        void shouldBroadcastErrorOnFailure() {
            var msg = new GameWebSocketController.ReinforceMessage("player-1", "brazil", 3);
            doThrow(new IllegalStateException("Not your turn"))
                    .when(gameService).placeArmies(any(), any(), any(), anyInt());

            controller.handleReinforce("game-1", msg);

            verify(webSocketHandler).broadcastError("game-1", "player-1", "Not your turn");
        }
    }

    @Nested
    @DisplayName("handleAttack")
    class AttackTests {

        @Test
        @DisplayName("should call attack, broadcast result and update")
        void shouldAttackAndBroadcast() {
            var msg = new GameWebSocketController.AttackMessage("player-1", "alaska", "kamchatka", 2);
            AttackResult result = AttackResult.builder()
                    .attackerDice(new int[]{6, 5}).defenderDice(new int[]{3})
                    .attackerLosses(0).defenderLosses(1).conquered(false).build();

            when(gameService.attack("game-1", "player-1", "alaska", "kamchatka", 2))
                    .thenReturn(result);
            when(gameService.getGame("game-1")).thenReturn(game);

            controller.handleAttack("game-1", msg);

            verify(webSocketHandler).broadcastAttackResult(eq("game-1"), any(CPUAction.class), eq(result));
            verify(webSocketHandler).broadcastGameUpdate("game-1");
        }

        @Test
        @DisplayName("should broadcast game over when game is finished after attack")
        void shouldBroadcastGameOverAfterConquest() {
            var msg = new GameWebSocketController.AttackMessage("player-1", "alaska", "kamchatka", 2);
            AttackResult result = AttackResult.builder()
                    .attackerDice(new int[]{6}).defenderDice(new int[]{1})
                    .attackerLosses(0).defenderLosses(1).conquered(true).build();

            game.setStatus(GameStatus.FINISHED);
            game.setWinnerId("player-1");

            when(gameService.attack("game-1", "player-1", "alaska", "kamchatka", 2))
                    .thenReturn(result);
            when(gameService.getGame("game-1")).thenReturn(game);

            controller.handleAttack("game-1", msg);

            verify(webSocketHandler).broadcastGameOver("game-1", "Alice");
        }

        @Test
        @DisplayName("should broadcast error on attack exception")
        void shouldBroadcastErrorOnAttackFailure() {
            var msg = new GameWebSocketController.AttackMessage("player-1", "alaska", "kamchatka", 2);
            when(gameService.attack(any(), any(), any(), any(), anyInt()))
                    .thenThrow(new IllegalArgumentException("Not adjacent"));

            controller.handleAttack("game-1", msg);

            verify(webSocketHandler).broadcastError("game-1", "player-1", "Not adjacent");
        }
    }

    @Nested
    @DisplayName("handleEndAttack")
    class EndAttackTests {

        @Test
        @DisplayName("should end attack and broadcast")
        void shouldEndAttackAndBroadcast() {
            var msg = new GameWebSocketController.PlayerIdMessage("player-1");

            controller.handleEndAttack("game-1", msg);

            verify(gameService).endAttackPhase("game-1", "player-1");
            verify(webSocketHandler).broadcastGameUpdate("game-1");
        }

        @Test
        @DisplayName("should broadcast error on exception")
        void shouldBroadcastErrorOnEndAttackFailure() {
            var msg = new GameWebSocketController.PlayerIdMessage("player-1");
            doThrow(new RuntimeException("phase error"))
                    .when(gameService).endAttackPhase("game-1", "player-1");

            controller.handleEndAttack("game-1", msg);

            verify(webSocketHandler).broadcastError("game-1", "player-1", "phase error");
        }
    }

    @Nested
    @DisplayName("handleFortify")
    class FortifyTests {

        @Test
        @DisplayName("should fortify and check CPU turn")
        void shouldFortifyAndCheckCPU() {
            var msg = new GameWebSocketController.FortifyMessage("player-1", "brazil", "argentina", 2);
            when(gameService.fortify("game-1", "player-1", "brazil", "argentina", 2))
                    .thenReturn(game);

            controller.handleFortify("game-1", msg);

            verify(webSocketHandler).broadcastGameUpdate("game-1");
            verify(cpuPlayerService).checkAndTriggerCPUTurn("game-1");
        }

        @Test
        @DisplayName("should broadcast game over when game ends after fortify (turn limit)")
        void shouldBroadcastGameOverAfterFortify() {
            var msg = new GameWebSocketController.FortifyMessage("player-1", "brazil", "argentina", 2);
            game.setStatus(GameStatus.FINISHED);
            game.setWinnerId("player-1");

            when(gameService.fortify("game-1", "player-1", "brazil", "argentina", 2))
                    .thenReturn(game);

            controller.handleFortify("game-1", msg);

            verify(webSocketHandler).broadcastGameOver("game-1", "Alice");
            verify(cpuPlayerService, never()).checkAndTriggerCPUTurn(any());
        }

        @Test
        @DisplayName("should broadcast error on fortify exception")
        void shouldBroadcastErrorOnFortifyFailure() {
            var msg = new GameWebSocketController.FortifyMessage("player-1", "brazil", "argentina", 2);
            when(gameService.fortify("game-1", "player-1", "brazil", "argentina", 2))
                    .thenThrow(new RuntimeException("Not adjacent"));

            controller.handleFortify("game-1", msg);

            verify(webSocketHandler).broadcastError("game-1", "player-1", "Not adjacent");
        }
    }

    @Nested
    @DisplayName("handleSkipFortify")
    class SkipFortifyTests {

        @Test
        @DisplayName("should skip fortify and check CPU turn")
        void shouldSkipFortifyAndCheckCPU() {
            var msg = new GameWebSocketController.PlayerIdMessage("player-1");
            when(gameService.skipFortify("game-1", "player-1")).thenReturn(game);

            controller.handleSkipFortify("game-1", msg);

            verify(webSocketHandler).broadcastGameUpdate("game-1");
            verify(cpuPlayerService).checkAndTriggerCPUTurn("game-1");
        }

        @Test
        @DisplayName("should broadcast game over when game ends after skip fortify")
        void shouldBroadcastGameOverAfterSkipFortify() {
            var msg = new GameWebSocketController.PlayerIdMessage("player-1");
            game.setStatus(GameStatus.FINISHED);
            game.setWinnerId("player-1");

            when(gameService.skipFortify("game-1", "player-1")).thenReturn(game);

            controller.handleSkipFortify("game-1", msg);

            verify(webSocketHandler).broadcastGameOver("game-1", "Alice");
        }

        @Test
        @DisplayName("should broadcast error on skip fortify exception")
        void shouldBroadcastErrorOnSkipFortifyFailure() {
            var msg = new GameWebSocketController.PlayerIdMessage("player-1");
            when(gameService.skipFortify("game-1", "player-1"))
                    .thenThrow(new RuntimeException("Not your turn"));

            controller.handleSkipFortify("game-1", msg);

            verify(webSocketHandler).broadcastError("game-1", "player-1", "Not your turn");
        }
    }

    @Nested
    @DisplayName("handleChat")
    class ChatTests {

        @Test
        @DisplayName("should broadcast chat message")
        void shouldBroadcastChat() {
            var msg = new GameWebSocketController.ChatMessageRequest("Alice", "Hello!");

            controller.handleChat("game-1", msg);

            verify(webSocketHandler).broadcastChatMessage("game-1", "Alice", "Hello!");
        }
    }
}
