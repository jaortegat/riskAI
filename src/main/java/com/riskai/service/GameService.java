package com.riskai.service;

import com.riskai.dto.AttackResult;
import com.riskai.dto.CreateGameRequest;
import com.riskai.dto.GameStateDTO;
import com.riskai.dto.JoinGameRequest;
import com.riskai.model.CPUDifficulty;
import com.riskai.model.Game;
import com.riskai.model.Player;
import com.riskai.model.PlayerType;
import com.riskai.model.Territory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Facade service delegating to focused service classes.
 * Maintained for backward compatibility during incremental migration of callers.
 *
 * @see GameLifecycleService
 * @see CombatService
 * @see ReinforcementService
 * @see FortificationService
 * @see TurnManagementService
 * @see GameQueryService
 * @see WinConditionService
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class GameService {

    private final GameLifecycleService lifecycleService;
    private final CombatService combatService;
    private final ReinforcementService reinforcementService;
    private final FortificationService fortificationService;
    private final TurnManagementService turnManagementService;
    private final GameQueryService queryService;
    private final WinConditionService winConditionService;

    public Game createGame(CreateGameRequest request, String sessionId) {
        return lifecycleService.createGame(request, sessionId);
    }

    public Player joinGame(String gameId, JoinGameRequest request, String sessionId) {
        return lifecycleService.joinGame(gameId, request, sessionId);
    }

    public Player joinGame(String gameId, JoinGameRequest request, String sessionId, PlayerType playerType) {
        return lifecycleService.joinGame(gameId, request, sessionId, playerType);
    }

    public Player addCPUPlayer(String gameId, CPUDifficulty difficulty) {
        return lifecycleService.addCPUPlayer(gameId, difficulty);
    }

    public Game startGame(String gameId) {
        return lifecycleService.startGame(gameId);
    }

    public int calculateReinforcements(Player player) {
        return reinforcementService.calculateReinforcements(player);
    }

    public Territory placeArmies(String gameId, String playerId, String territoryKey, int armies) {
        return reinforcementService.placeArmies(gameId, playerId, territoryKey, armies);
    }

    public AttackResult attack(String gameId, String playerId, String fromKey, String toKey, int attackingArmies) {
        return combatService.attack(gameId, playerId, fromKey, toKey, attackingArmies);
    }

    public Game endAttackPhase(String gameId, String playerId) {
        return turnManagementService.endAttackPhase(gameId, playerId);
    }

    public Game fortify(String gameId, String playerId, String fromKey, String toKey, int armies) {
        return fortificationService.fortify(gameId, playerId, fromKey, toKey, armies);
    }

    public Game skipFortify(String gameId, String playerId) {
        return fortificationService.skipFortify(gameId, playerId);
    }

    public boolean checkTurnLimit(Game game) {
        return winConditionService.checkTurnLimit(game);
    }

    @Transactional(readOnly = true)
    public Game getGame(String gameId) {
        return queryService.getGame(gameId);
    }

    @Transactional(readOnly = true)
    public Game getGameWithPlayers(String gameId) {
        return queryService.getGameWithPlayers(gameId);
    }

    @Transactional(readOnly = true)
    public GameStateDTO getGameState(String gameId) {
        return queryService.getGameState(gameId);
    }

    @Transactional(readOnly = true)
    public List<Game> getJoinableGames() {
        return queryService.getJoinableGames();
    }

    @Transactional(readOnly = true)
    public List<Game> getAllGames() {
        return queryService.getAllGames();
    }
}

