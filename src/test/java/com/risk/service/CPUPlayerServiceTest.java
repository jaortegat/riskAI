package com.risk.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import com.risk.cpu.CPUAction;
import com.risk.cpu.CPUStrategy;
import com.risk.cpu.CPUStrategyFactory;
import com.risk.dto.AttackResult;
import com.risk.model.CPUDifficulty;
import com.risk.model.Game;
import com.risk.model.GameMode;
import com.risk.model.GamePhase;
import com.risk.model.GameStatus;
import com.risk.model.Player;
import com.risk.model.PlayerColor;
import com.risk.model.PlayerType;
import com.risk.model.Territory;
import com.risk.websocket.GameWebSocketHandler;

/**
 * Unit tests for CPUPlayerService — covers executeCPUTurn flow and helper methods.
 * Uses LENIENT strictness because the complex multi-phase flow makes exact stubbing impractical.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CPUPlayerServiceTest {

    @Mock private GameService gameService;
    @Mock private CPUStrategyFactory strategyFactory;
    @Mock private GameWebSocketHandler webSocketHandler;
    @Mock private CPUStrategy cpuStrategy;

    private CPUPlayerService cpuPlayerService;
    private Player cpuPlayer;
    private Player humanPlayer;

    @BeforeEach
    void setUp() {
        cpuPlayerService = new CPUPlayerService(gameService, strategyFactory, webSocketHandler);
        ReflectionTestUtils.setField(cpuPlayerService, "thinkDelayMs", 0L);

        cpuPlayer = Player.builder()
                .id("cpu-1").name("CPU Player 1")
                .color(PlayerColor.RED).type(PlayerType.CPU)
                .cpuDifficulty(CPUDifficulty.MEDIUM).turnOrder(0)
                .eliminated(false).build();

        humanPlayer = Player.builder()
                .id("human-1").name("Human")
                .color(PlayerColor.BLUE).type(PlayerType.HUMAN)
                .turnOrder(1).eliminated(false).build();
    }

    @Nested
    @DisplayName("executeCPUTurn() — full flow")
    class FullFlowTests {

        @Test
        @DisplayName("should execute complete turn: reinforce → attack → fortify")
        void shouldExecuteCompleteTurn() {
            Game initial = makeGame(GamePhase.REINFORCEMENT, GameStatus.IN_PROGRESS, 3);
            Game noReinf = makeGame(GamePhase.ATTACK, GameStatus.IN_PROGRESS, 0);
            Game fortifyPhase = makeGame(GamePhase.FORTIFY, GameStatus.IN_PROGRESS, 0);
            Game afterTurn = makeGame(GamePhase.REINFORCEMENT, GameStatus.IN_PROGRESS, 0);
            afterTurn.setCurrentPlayerIndex(1);

            when(gameService.getGame("game-1")).thenReturn(
                    initial,       // executeCPUTurn initial
                    noReinf,       // after placeArmies in reinforce loop
                    noReinf,       // attack loop entry
                    fortifyPhase,  // post-attack-loop check (FORTIFY → skip fallback)
                    fortifyPhase,  // post-attack game-ended check
                    fortifyPhase,  // executeFortifyPhase reload
                    afterTurn,     // post-fortify game-ended check
                    afterTurn,     // next player check
                    afterTurn      // checkAndTriggerCPUTurn
            );

            when(strategyFactory.getStrategy(any(Player.class))).thenReturn(cpuStrategy);
            when(cpuStrategy.decideReinforcement(any(), any(), eq(3)))
                    .thenReturn(CPUAction.placeArmies("brazil", 3));
            when(cpuStrategy.decideAttack(any(), any())).thenReturn(CPUAction.endAttack());
            when(cpuStrategy.decideFortify(any(), any())).thenReturn(CPUAction.skipFortify());

            cpuPlayerService.executeCPUTurn("game-1", "cpu-1");

            verify(gameService).placeArmies("game-1", "cpu-1", "brazil", 3);
            verify(gameService).endAttackPhase("game-1", "cpu-1");
            verify(gameService).skipFortify("game-1", "cpu-1");
            verify(webSocketHandler).broadcastCPUTurnEnd(eq("game-1"), eq("CPU Player 1"));
        }

        @Test
        @DisplayName("should stop when game ends during attack phase")
        void shouldStopWhenGameEndsDuringAttack() {
            Game initial = makeGame(GamePhase.REINFORCEMENT, GameStatus.IN_PROGRESS, 0);
            Game attackPhase = makeGame(GamePhase.ATTACK, GameStatus.IN_PROGRESS, 0);
            Game finished = makeGame(GamePhase.GAME_OVER, GameStatus.FINISHED, 0);
            finished.setWinnerId("cpu-1");

            when(gameService.getGame("game-1")).thenReturn(
                    initial,       // initial
                    attackPhase,   // attack loop entry
                    finished,      // after attack → check game over → FINISHED
                    finished,      // post-loop (GAME_OVER → skip fallback)
                    finished       // post-attack check → FINISHED → return
            );

            when(strategyFactory.getStrategy(any(Player.class))).thenReturn(cpuStrategy);
            when(cpuStrategy.decideReinforcement(any(), any(), anyInt())).thenReturn(null);

            AttackResult result = AttackResult.builder().conquered(true).build();
            when(cpuStrategy.decideAttack(any(), any()))
                    .thenReturn(CPUAction.attack("brazil", "argentina", 3));
            when(gameService.attack(eq("game-1"), eq("cpu-1"), eq("brazil"), eq("argentina"), eq(3)))
                    .thenReturn(result);

            cpuPlayerService.executeCPUTurn("game-1", "cpu-1");

            verify(webSocketHandler).broadcastGameOver(eq("game-1"), eq("CPU Player 1"));
            verify(gameService, never()).skipFortify(anyString(), anyString());
        }

        @Test
        @DisplayName("should broadcast game over when game finishes during fortify (turn limit)")
        void shouldHandleTurnLimitDuringFortify() {
            Game initial = makeGame(GamePhase.REINFORCEMENT, GameStatus.IN_PROGRESS, 0);
            Game attackPhase = makeGame(GamePhase.ATTACK, GameStatus.IN_PROGRESS, 0);
            Game fortifyPhase = makeGame(GamePhase.FORTIFY, GameStatus.IN_PROGRESS, 0);
            Game inProgress = makeGame(GamePhase.FORTIFY, GameStatus.IN_PROGRESS, 0);
            Game finished = makeGame(GamePhase.GAME_OVER, GameStatus.FINISHED, 0);
            finished.setWinnerId("cpu-1");

            when(gameService.getGame("game-1")).thenReturn(
                    initial,       // initial
                    attackPhase,   // attack loop (END_ATTACK)
                    fortifyPhase,  // post-loop (FORTIFY → skip fallback)
                    inProgress,    // post-attack check (not finished)
                    fortifyPhase,  // executeFortifyPhase reload
                    finished       // post-fortify check → FINISHED
            );

            when(strategyFactory.getStrategy(any(Player.class))).thenReturn(cpuStrategy);
            when(cpuStrategy.decideReinforcement(any(), any(), anyInt())).thenReturn(null);
            when(cpuStrategy.decideAttack(any(), any())).thenReturn(CPUAction.endAttack());
            when(cpuStrategy.decideFortify(any(), any())).thenReturn(CPUAction.skipFortify());

            cpuPlayerService.executeCPUTurn("game-1", "cpu-1");

            verify(webSocketHandler).broadcastGameOver(eq("game-1"), eq("CPU Player 1"));
        }

        @Test
        @DisplayName("should handle RuntimeException during attack gracefully")
        void shouldHandleAttackException() {
            Game initial = makeGame(GamePhase.REINFORCEMENT, GameStatus.IN_PROGRESS, 0);
            Game attackPhase = makeGame(GamePhase.ATTACK, GameStatus.IN_PROGRESS, 0);
            Game fortifyPhase = makeGame(GamePhase.FORTIFY, GameStatus.IN_PROGRESS, 0);
            Game afterTurn = makeGame(GamePhase.REINFORCEMENT, GameStatus.IN_PROGRESS, 0);
            afterTurn.setCurrentPlayerIndex(1);

            when(gameService.getGame("game-1")).thenReturn(
                    initial,       // initial
                    attackPhase,   // attack loop entry
                    attackPhase,   // post-loop (still ATTACK → fallback endAttackPhase)
                    fortifyPhase,  // post-attack check (not finished)
                    fortifyPhase,  // executeFortifyPhase
                    afterTurn,     // post-fortify check
                    afterTurn,     // next player check
                    afterTurn      // checkAndTriggerCPUTurn
            );

            when(strategyFactory.getStrategy(any(Player.class))).thenReturn(cpuStrategy);
            when(cpuStrategy.decideReinforcement(any(), any(), anyInt())).thenReturn(null);
            when(cpuStrategy.decideAttack(any(), any()))
                    .thenReturn(CPUAction.attack("brazil", "argentina", 3));
            when(gameService.attack(eq("game-1"), eq("cpu-1"), eq("brazil"), eq("argentina"), eq(3)))
                    .thenThrow(new RuntimeException("Invalid attack"));
            when(cpuStrategy.decideFortify(any(), any())).thenReturn(CPUAction.skipFortify());

            cpuPlayerService.executeCPUTurn("game-1", "cpu-1");

            verify(gameService).endAttackPhase("game-1", "cpu-1");
            verify(webSocketHandler).broadcastCPUTurnEnd(eq("game-1"), eq("CPU Player 1"));
        }
    }

    @Nested
    @DisplayName("executeCPUTurn() — reinforcement phase")
    class ReinforcementPhaseTests {

        @Test
        @DisplayName("should place armies in multiple rounds until reinforcements exhausted")
        void shouldPlaceArmiesInMultipleRounds() {
            Game withReinf = makeGame(GamePhase.REINFORCEMENT, GameStatus.IN_PROGRESS, 5);
            Game lessReinf = makeGame(GamePhase.REINFORCEMENT, GameStatus.IN_PROGRESS, 2);
            Game noReinf = makeGame(GamePhase.ATTACK, GameStatus.IN_PROGRESS, 0);
            Game fortifyPhase = makeGame(GamePhase.FORTIFY, GameStatus.IN_PROGRESS, 0);
            Game afterTurn = makeGame(GamePhase.REINFORCEMENT, GameStatus.IN_PROGRESS, 0);
            afterTurn.setCurrentPlayerIndex(1);

            when(gameService.getGame("game-1")).thenReturn(
                    withReinf,     // initial
                    lessReinf,     // after first placeArmies
                    noReinf,       // after second placeArmies (0 remaining → exit)
                    noReinf,       // attack loop
                    fortifyPhase,  // post-loop
                    fortifyPhase,  // post-attack check
                    fortifyPhase,  // fortify phase
                    afterTurn,     // post-fortify check
                    afterTurn,     // next player check
                    afterTurn      // checkAndTriggerCPUTurn
            );

            when(strategyFactory.getStrategy(any(Player.class))).thenReturn(cpuStrategy);
            when(cpuStrategy.decideReinforcement(any(), any(), eq(5)))
                    .thenReturn(CPUAction.placeArmies("brazil", 3));
            when(cpuStrategy.decideReinforcement(any(), any(), eq(2)))
                    .thenReturn(CPUAction.placeArmies("argentina", 2));
            when(cpuStrategy.decideAttack(any(), any())).thenReturn(CPUAction.endAttack());
            when(cpuStrategy.decideFortify(any(), any())).thenReturn(CPUAction.skipFortify());

            cpuPlayerService.executeCPUTurn("game-1", "cpu-1");

            verify(gameService).placeArmies("game-1", "cpu-1", "brazil", 3);
            verify(gameService).placeArmies("game-1", "cpu-1", "argentina", 2);
        }
    }

    @Nested
    @DisplayName("executeCPUTurn() — fortify phase")
    class FortifyPhaseTests {

        @Test
        @DisplayName("should execute fortify action with territory names")
        void shouldExecuteFortifyAction() {
            Game initial = makeGame(GamePhase.REINFORCEMENT, GameStatus.IN_PROGRESS, 0);
            Game attackPhase = makeGame(GamePhase.ATTACK, GameStatus.IN_PROGRESS, 0);
            Game fortifyPhase = makeGame(GamePhase.FORTIFY, GameStatus.IN_PROGRESS, 0);
            Game afterTurn = makeGame(GamePhase.REINFORCEMENT, GameStatus.IN_PROGRESS, 0);
            afterTurn.setCurrentPlayerIndex(1);

            when(gameService.getGame("game-1")).thenReturn(
                    initial,       // initial
                    attackPhase,   // attack loop
                    fortifyPhase,  // post-loop (FORTIFY → skip fallback)
                    fortifyPhase,  // post-attack check
                    fortifyPhase,  // fortify reload
                    afterTurn,     // post-fortify check
                    afterTurn,     // next player check
                    afterTurn      // checkAndTriggerCPUTurn
            );

            when(strategyFactory.getStrategy(any(Player.class))).thenReturn(cpuStrategy);
            when(cpuStrategy.decideReinforcement(any(), any(), anyInt())).thenReturn(null);
            when(cpuStrategy.decideAttack(any(), any())).thenReturn(CPUAction.endAttack());
            when(cpuStrategy.decideFortify(any(), any()))
                    .thenReturn(CPUAction.fortify("brazil", "argentina", 2));

            cpuPlayerService.executeCPUTurn("game-1", "cpu-1");

            verify(gameService).fortify("game-1", "cpu-1", "brazil", "argentina", 2);
            verify(webSocketHandler).broadcastCPUFortify(eq("game-1"), eq("CPU Player 1"),
                    eq("Brazil"), eq("Argentina"), eq(2));
        }

        @Test
        @DisplayName("should use territory key as fallback name when territory not found")
        void shouldUseTerritoryKeyAsFallback() {
            Game initial = makeGameNoTerritories(GamePhase.REINFORCEMENT, GameStatus.IN_PROGRESS, 0);
            Game attackPhase = makeGameNoTerritories(GamePhase.ATTACK, GameStatus.IN_PROGRESS, 0);
            Game fortifyPhase = makeGameNoTerritories(GamePhase.FORTIFY, GameStatus.IN_PROGRESS, 0);
            Game afterTurn = makeGameNoTerritories(GamePhase.REINFORCEMENT, GameStatus.IN_PROGRESS, 0);
            afterTurn.setCurrentPlayerIndex(1);

            when(gameService.getGame("game-1")).thenReturn(
                    initial,       // initial
                    attackPhase,   // attack loop
                    fortifyPhase,  // post-loop
                    fortifyPhase,  // post-attack check
                    fortifyPhase,  // fortify reload
                    afterTurn,     // post-fortify check
                    afterTurn,     // next player check
                    afterTurn      // checkAndTriggerCPUTurn
            );

            when(strategyFactory.getStrategy(any(Player.class))).thenReturn(cpuStrategy);
            when(cpuStrategy.decideReinforcement(any(), any(), anyInt())).thenReturn(null);
            when(cpuStrategy.decideAttack(any(), any())).thenReturn(CPUAction.endAttack());
            when(cpuStrategy.decideFortify(any(), any()))
                    .thenReturn(CPUAction.fortify("unknown-from", "unknown-to", 1));

            cpuPlayerService.executeCPUTurn("game-1", "cpu-1");

            verify(webSocketHandler).broadcastCPUFortify(eq("game-1"), eq("CPU Player 1"),
                    eq("unknown-from"), eq("unknown-to"), eq(1));
        }

        @Test
        @DisplayName("should skip fortify when game phase is not FORTIFY")
        void shouldSkipFortifyWhenNotInFortifyPhase() {
            Game initial = makeGame(GamePhase.REINFORCEMENT, GameStatus.IN_PROGRESS, 0);
            Game attackPhase = makeGame(GamePhase.ATTACK, GameStatus.IN_PROGRESS, 0);
            Game nextTurn = makeGame(GamePhase.REINFORCEMENT, GameStatus.IN_PROGRESS, 0);
            nextTurn.setCurrentPlayerIndex(1);

            when(gameService.getGame("game-1")).thenReturn(
                    initial,    // initial
                    attackPhase, // attack loop
                    nextTurn,    // post-loop (REINFORCEMENT → skip fallback)
                    nextTurn,    // post-attack check
                    nextTurn,    // executeFortifyPhase → not FORTIFY → return early
                    nextTurn,    // post-fortify check
                    nextTurn,    // next player check
                    nextTurn     // checkAndTriggerCPUTurn
            );

            when(strategyFactory.getStrategy(any(Player.class))).thenReturn(cpuStrategy);
            when(cpuStrategy.decideReinforcement(any(), any(), anyInt())).thenReturn(null);
            when(cpuStrategy.decideAttack(any(), any())).thenReturn(CPUAction.endAttack());

            cpuPlayerService.executeCPUTurn("game-1", "cpu-1");

            verify(gameService, never()).skipFortify(anyString(), anyString());
            verify(gameService, never()).fortify(anyString(), anyString(), anyString(), anyString(), anyInt());
        }
    }

    @Nested
    @DisplayName("executeCPUTurn() — error handling")
    class ErrorHandlingTests {

        @Test
        @DisplayName("should handle RuntimeException in executeCPUTurn")
        void shouldHandleRuntimeException() {
            when(gameService.getGame("game-1"))
                    .thenThrow(new RuntimeException("DB error"));

            cpuPlayerService.executeCPUTurn("game-1", "cpu-1");

            verifyNoInteractions(strategyFactory);
        }

        @Test
        @DisplayName("should handle InterruptedException")
        void shouldHandleInterruptedException() {
            ReflectionTestUtils.setField(cpuPlayerService, "thinkDelayMs", 10000L);

            Game initial = makeGame(GamePhase.REINFORCEMENT, GameStatus.IN_PROGRESS, 5);
            when(gameService.getGame("game-1")).thenReturn(initial);
            when(strategyFactory.getStrategy(any(Player.class))).thenReturn(cpuStrategy);

            Thread testThread = Thread.currentThread();
            Thread interrupter = new Thread(() -> {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                testThread.interrupt();
            });
            interrupter.start();

            cpuPlayerService.executeCPUTurn("game-1", "cpu-1");

            Thread.interrupted();
        }
    }

    @Nested
    @DisplayName("getWinnerName()")
    class GetWinnerNameTests {

        @Test
        @DisplayName("should return Unknown when winner not found in players")
        void shouldReturnUnknownWhenWinnerNotFound() {
            Game initial = makeGame(GamePhase.REINFORCEMENT, GameStatus.IN_PROGRESS, 0);
            Game attackPhase = makeGame(GamePhase.ATTACK, GameStatus.IN_PROGRESS, 0);
            Game finished = makeGame(GamePhase.GAME_OVER, GameStatus.FINISHED, 0);
            finished.setWinnerId("nonexistent-player");

            when(gameService.getGame("game-1")).thenReturn(
                    initial,       // initial
                    attackPhase,   // attack loop
                    finished,      // after attack → FINISHED
                    finished,      // post-loop (GAME_OVER → skip fallback)
                    finished       // post-attack → FINISHED → broadcastGameOver
            );

            when(strategyFactory.getStrategy(any(Player.class))).thenReturn(cpuStrategy);
            when(cpuStrategy.decideReinforcement(any(), any(), anyInt())).thenReturn(null);

            AttackResult result = AttackResult.builder().conquered(true).build();
            when(cpuStrategy.decideAttack(any(), any()))
                    .thenReturn(CPUAction.attack("brazil", "argentina", 3));
            when(gameService.attack(eq("game-1"), eq("cpu-1"), anyString(), anyString(), anyInt()))
                    .thenReturn(result);

            cpuPlayerService.executeCPUTurn("game-1", "cpu-1");

            verify(webSocketHandler).broadcastGameOver(eq("game-1"), eq("Unknown"));
        }
    }

    @Nested
    @DisplayName("checkAndTriggerCPUTurn()")
    class CheckAndTriggerTests {

        @Test
        @DisplayName("should handle RuntimeException in checkAndTriggerCPUTurn")
        void shouldHandleRuntimeExceptionInCheck() {
            when(gameService.getGame("game-1"))
                    .thenThrow(new RuntimeException("DB error"));

            cpuPlayerService.checkAndTriggerCPUTurn("game-1");

            verifyNoInteractions(strategyFactory);
        }

        @Test
        @DisplayName("should not trigger when game is finished")
        void shouldNotTriggerWhenFinished() {
            Game finished = makeGame(GamePhase.GAME_OVER, GameStatus.FINISHED, 0);
            when(gameService.getGame("game-1")).thenReturn(finished);

            cpuPlayerService.checkAndTriggerCPUTurn("game-1");

            verifyNoInteractions(strategyFactory);
        }
    }

    private Game makeGame(GamePhase phase, GameStatus status, int reinforcements) {
        Territory t1 = Territory.builder()
                .id("t1").name("Brazil").territoryKey("brazil")
                .armies(5).neighborKeys(new HashSet<>()).build();
        Territory t2 = Territory.builder()
                .id("t2").name("Argentina").territoryKey("argentina")
                .armies(3).neighborKeys(new HashSet<>()).build();

        return Game.builder()
                .id("game-1").name("Test Game")
                .status(status).currentPhase(phase)
                .currentPlayerIndex(0).turnNumber(1)
                .reinforcementsRemaining(reinforcements)
                .maxPlayers(6).minPlayers(2)
                .mapId("classic-world")
                .gameMode(GameMode.CLASSIC)
                .players(new ArrayList<>(List.of(cpuPlayer, humanPlayer)))
                .territories(new HashSet<>(List.of(t1, t2)))
                .build();
    }

    private Game makeGameNoTerritories(GamePhase phase, GameStatus status, int reinforcements) {
        return Game.builder()
                .id("game-1").name("Test Game")
                .status(status).currentPhase(phase)
                .currentPlayerIndex(0).turnNumber(1)
                .reinforcementsRemaining(reinforcements)
                .maxPlayers(6).minPlayers(2)
                .mapId("classic-world")
                .gameMode(GameMode.CLASSIC)
                .players(new ArrayList<>(List.of(cpuPlayer, humanPlayer)))
                .territories(new HashSet<>())
                .build();
    }
}
