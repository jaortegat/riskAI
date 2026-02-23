package com.risk.service;

import com.risk.cpu.CPUAction;
import com.risk.cpu.CPUStrategy;
import com.risk.cpu.CPUStrategyFactory;
import com.risk.dto.AttackResult;
import com.risk.model.Game;
import com.risk.model.GamePhase;
import com.risk.model.GameStatus;
import com.risk.model.Player;
import com.risk.model.Territory;
import com.risk.websocket.GameWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Service for executing CPU player turns.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CPUPlayerService {

    private static final String UNKNOWN_PLAYER = "Unknown";

    private final GameService gameService;
    private final CPUStrategyFactory strategyFactory;
    private final GameWebSocketHandler webSocketHandler;

    private final ConcurrentHashMap<String, ReentrantLock> gameLocks = new ConcurrentHashMap<>();

    @Value("${game.cpu.think-delay-ms:1000}")
    private long thinkDelayMs;

    /**
     * Execute a turn for a CPU player.
     */
    @Async
    public void executeCPUTurn(String gameId, String playerId) {
        ReentrantLock lock = gameLocks.computeIfAbsent(gameId, id -> new ReentrantLock());
        if (!lock.tryLock()) {
            log.info("CPU turn already running for game {}", gameId);
            return;
        }
        try {
            Game game = gameService.getGame(gameId);
            Player cpuPlayer = game.getCurrentPlayer();

            if (cpuPlayer == null || !cpuPlayer.getId().equals(playerId) || !cpuPlayer.isCPU()) {
                log.warn("Invalid CPU turn request for game {} player {}", gameId, playerId);
                return;
            }

            log.info("{} starting turn in game {}", cpuPlayer.getName(), game.getName());

            CPUStrategy strategy = strategyFactory.getStrategy(cpuPlayer);

            // Reinforcement phase
            executeReinforcementPhase(game, cpuPlayer, strategy);

            // Attack phase
            executeAttackPhase(game, cpuPlayer, strategy);

            // Stop if game ended during attack phase
            game = gameService.getGame(gameId);
            if (game.getStatus() == GameStatus.FINISHED) {
                log.info("Game {} ended during {}'s attack phase", game.getName(), cpuPlayer.getName());
                return;
            }

            // Fortify phase
            executeFortifyPhase(game, cpuPlayer, strategy);

            // Check if game ended during endTurn (turn limit reached)
            game = gameService.getGame(gameId);
            if (game.getStatus() == GameStatus.FINISHED) {
                webSocketHandler.broadcastGameOver(game.getId(), getWinnerName(game));
                log.info("Game {} ended (turn limit) during {}'s turn", game.getName(), cpuPlayer.getName());
                return;
            }

            // Notify clients that CPU turn has ended
            webSocketHandler.broadcastCPUTurnEnd(game.getId(), cpuPlayer.getName());

            log.info("{} completed turn in game {}", cpuPlayer.getName(), game.getName());

            // Check if the next player is also a CPU and trigger their turn
            Game updatedGame = gameService.getGame(gameId);
            if (updatedGame.getCurrentPlayer() != null &&
                    !updatedGame.getCurrentPlayer().getId().equals(playerId) &&
                    updatedGame.getStatus() == GameStatus.IN_PROGRESS) {
                checkAndTriggerCPUTurn(gameId);
            }

        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.warn("CPU turn interrupted for game {} player {}", gameId, playerId);
        } catch (RuntimeException e) {
            log.error("Error executing CPU turn for game {} player {}", gameId, playerId, e);
        } finally {
            lock.unlock();
            if (!lock.hasQueuedThreads()) {
                gameLocks.remove(gameId, lock);
            }
        }
    }

    private void executeReinforcementPhase(Game game, Player cpuPlayer, CPUStrategy strategy) throws InterruptedException {
        int reinforcements = game.getReinforcementsRemaining();

        while (reinforcements > 0) {
            Thread.sleep(thinkDelayMs);

            CPUAction action = strategy.decideReinforcement(game, cpuPlayer, reinforcements);

            if (action == null || action.getType() != CPUAction.ActionType.PLACE_ARMIES) {
                break;
            }

            gameService.placeArmies(game.getId(), cpuPlayer.getId(),
                    action.getToTerritoryKey(), action.getArmies());

            webSocketHandler.broadcastGameUpdate(game.getId());

            game = gameService.getGame(game.getId());
            reinforcements = game.getReinforcementsRemaining();
        }
    }

    private void executeAttackPhase(Game game, Player cpuPlayer, CPUStrategy strategy) throws InterruptedException {
        int maxAttacks = 10; // Prevent infinite loops
        int attacks = 0;

        while (attacks < maxAttacks) {
            Thread.sleep(thinkDelayMs);

            game = gameService.getGame(game.getId());

            if (game.getCurrentPhase() != GamePhase.ATTACK) {
                break;
            }

            CPUAction action = strategy.decideAttack(game, cpuPlayer);

            if (action == null || action.getType() == CPUAction.ActionType.END_ATTACK) {
                gameService.endAttackPhase(game.getId(), cpuPlayer.getId());
                webSocketHandler.broadcastGameUpdate(game.getId());
                break;
            }

            if (action.getType() == CPUAction.ActionType.ATTACK) {
                try {
                    AttackResult result = gameService.attack(
                            game.getId(), cpuPlayer.getId(),
                            action.getFromTerritoryKey(), action.getToTerritoryKey(),
                            action.getArmies());

                    webSocketHandler.broadcastAttackResult(game.getId(), action, result);
                    // Broadcast updated game state so clients see ownership changes (especially after conquests)
                    webSocketHandler.broadcastGameUpdate(game.getId());
                    attacks++;

                    // Check if game is over after this attack
                    game = gameService.getGame(game.getId());
                    if (game.getStatus() == GameStatus.FINISHED) {
                        webSocketHandler.broadcastGameOver(game.getId(), getWinnerName(game));
                        return;
                    }
                } catch (RuntimeException e) {
                    log.debug("CPU attack failed: {}", e.getMessage());
                    break;
                }
            }
        }

        // Ensure we transition out of ATTACK phase (handles exception breaks and maxAttacks exit)
        game = gameService.getGame(game.getId());
        if (game.getCurrentPhase() == GamePhase.ATTACK) {
            gameService.endAttackPhase(game.getId(), cpuPlayer.getId());
            webSocketHandler.broadcastGameUpdate(game.getId());
        }
    }

    private void executeFortifyPhase(Game game, Player cpuPlayer, CPUStrategy strategy) throws InterruptedException {
        Thread.sleep(thinkDelayMs);

        game = gameService.getGame(game.getId());
        // Refresh cpuPlayer reference from the reloaded game
        final String cpuId = cpuPlayer.getId();
        cpuPlayer = game.getPlayers().stream()
                .filter(p -> p.getId().equals(cpuId))
                .findFirst().orElse(cpuPlayer);

        if (game.getCurrentPhase() != GamePhase.FORTIFY) {
            return;
        }

        CPUAction action = strategy.decideFortify(game, cpuPlayer);

        if (action == null || action.getType() == CPUAction.ActionType.SKIP_FORTIFY) {
            gameService.skipFortify(game.getId(), cpuPlayer.getId());
        } else if (action.getType() == CPUAction.ActionType.FORTIFY) {
            gameService.fortify(game.getId(), cpuPlayer.getId(),
                    action.getFromTerritoryKey(), action.getToTerritoryKey(),
                    action.getArmies());

            // Broadcast CPU fortify details for modal display
            String fromName = game.getTerritories().stream()
                    .filter(t -> t.getTerritoryKey().equals(action.getFromTerritoryKey()))
                    .map(Territory::getName).findFirst().orElse(action.getFromTerritoryKey());
            String toName = game.getTerritories().stream()
                    .filter(t -> t.getTerritoryKey().equals(action.getToTerritoryKey()))
                    .map(Territory::getName).findFirst().orElse(action.getToTerritoryKey());
            webSocketHandler.broadcastCPUFortify(game.getId(), cpuPlayer.getName(),
                    fromName, toName, action.getArmies());
        }

        webSocketHandler.broadcastGameUpdate(game.getId());
    }

    private String getWinnerName(Game game) {
        String winnerId = game.getWinnerId();
        return game.getPlayers().stream()
                .filter(p -> p.getId().equals(winnerId))
                .findFirst()
                .map(Player::getName)
                .orElse(UNKNOWN_PLAYER);
    }

    /**
     * Check if the current player is a CPU and trigger their turn.
     */
    public void checkAndTriggerCPUTurn(String gameId) {
        try {
            Game game = gameService.getGame(gameId);
            Player currentPlayer = game.getCurrentPlayer();

            if (currentPlayer != null && currentPlayer.isCPU() && 
                game.getStatus() == GameStatus.IN_PROGRESS) {
                executeCPUTurn(gameId, currentPlayer.getId());
            }
        } catch (RuntimeException e) {
            log.error("Error checking CPU turn for game {}", gameId, e);
        }
    }
}
