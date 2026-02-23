package com.risk.service;

import com.risk.model.Game;
import com.risk.model.GamePhase;
import com.risk.repository.GameRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service responsible for turn transitions, phase management, and end-of-turn logic.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class TurnManagementService {

    private final GameRepository gameRepository;
    private final GameQueryService gameQueryService;
    private final WinConditionService winConditionService;
    private final ReinforcementService reinforcementService;

    /**
     * End the current turn: advance to next active player, handle wrap-around,
     * check turn limit, and set up reinforcement phase.
     */
    public void endTurn(Game game) {
        // Move to next active player
        int attempts = 0;
        int maxAttempts = game.getPlayers().size();
        boolean wrapped = false;
        do {
            int previousIndex = game.getCurrentPlayerIndex();
            game.nextPlayer();
            attempts++;
            if (game.getCurrentPlayerIndex() <= previousIndex) {
                wrapped = true;
            }
            if (attempts > maxAttempts) {
                throw new IllegalStateException("No active players remaining");
            }
        } while (game.getCurrentPlayer().isEliminated());

        if (wrapped) {
            game.setTurnNumber(game.getTurnNumber() + 1);
        }

        // Check turn limit before starting next turn
        if (winConditionService.checkTurnLimit(game)) {
            // Game ended, status is now FINISHED
            return;
        }

        game.setCurrentPhase(GamePhase.REINFORCEMENT);
        game.setReinforcementsRemaining(reinforcementService.calculateReinforcements(game.getCurrentPlayer()));
        gameRepository.save(game);
    }

    /**
     * End the attack phase and transition to fortify.
     */
    public Game endAttackPhase(String gameId, String playerId) {
        Game game = gameQueryService.getGame(gameId);
        gameQueryService.validateCurrentPlayer(game, playerId);

        if (game.getCurrentPhase() != GamePhase.ATTACK) {
            throw new IllegalStateException("Not in attack phase");
        }

        game.setCurrentPhase(GamePhase.FORTIFY);
        return gameRepository.save(game);
    }
}
