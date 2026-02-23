package com.risk.service;

import com.risk.dto.AttackResult;
import com.risk.model.Game;
import com.risk.model.GamePhase;
import com.risk.model.Player;
import com.risk.model.Territory;
import com.risk.repository.GameRepository;
import com.risk.repository.PlayerRepository;
import com.risk.repository.TerritoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Arrays;

/**
 * Service responsible for attack/combat mechanics including dice resolution.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CombatService {

    private final GameRepository gameRepository;
    private final TerritoryRepository territoryRepository;
    private final PlayerRepository playerRepository;
    private final GameQueryService gameQueryService;
    private final WinConditionService winConditionService;

    private final SecureRandom random = new SecureRandom();

    /**
     * Execute an attack.
     */
    public AttackResult attack(String gameId, String playerId, String fromKey, String toKey, int attackingArmies) {
        Game game = gameQueryService.getGame(gameId);
        gameQueryService.validateCurrentPlayer(game, playerId);

        if (game.getCurrentPhase() != GamePhase.ATTACK) {
            throw new IllegalStateException("Not in attack phase");
        }

        Territory from = territoryRepository.findByGameIdAndTerritoryKey(gameId, fromKey)
                .orElseThrow(() -> new IllegalArgumentException("Source territory not found"));
        Territory to = territoryRepository.findByGameIdAndTerritoryKey(gameId, toKey)
                .orElseThrow(() -> new IllegalArgumentException("Target territory not found"));

        if (!from.isOwnedBy(game.getCurrentPlayer())) {
            throw new IllegalArgumentException("You don't own the attacking territory");
        }

        if (to.isOwnedBy(game.getCurrentPlayer())) {
            throw new IllegalArgumentException("Cannot attack your own territory");
        }

        if (!from.isNeighborOf(toKey)) {
            throw new IllegalArgumentException("Territories are not adjacent");
        }

        if (attackingArmies < 1 || attackingArmies > 3 || attackingArmies >= from.getArmies()) {
            throw new IllegalArgumentException("Invalid number of attacking armies");
        }

        return executeAttack(game, from, to, attackingArmies);
    }

    AttackResult executeAttack(Game game, Territory from, Territory to, int attackingArmies) {
        int defendingArmies = Math.min(2, to.getArmies());

        int[] attackDice = rollDice(attackingArmies);
        int[] defendDice = rollDice(defendingArmies);

        Arrays.sort(attackDice);
        Arrays.sort(defendDice);
        reverseArray(attackDice);
        reverseArray(defendDice);

        int attackerLosses = 0;
        int defenderLosses = 0;

        int comparisons = Math.min(attackDice.length, defendDice.length);
        for (int i = 0; i < comparisons; i++) {
            if (attackDice[i] > defendDice[i]) {
                defenderLosses++;
            } else {
                attackerLosses++;
            }
        }

        from.setArmies(from.getArmies() - attackerLosses);
        to.setArmies(to.getArmies() - defenderLosses);

        boolean conquered = to.getArmies() <= 0;
        Player eliminatedPlayer = null;

        if (conquered) {
            Player previousOwner = to.getOwner();
            to.setOwner(from.getOwner());
            int moveArmies = Math.min(attackingArmies, from.getArmies() - 1);
            if (moveArmies < 1) {
                throw new IllegalStateException("Not enough armies to occupy conquered territory");
            }
            to.setArmies(moveArmies);
            from.setArmies(from.getArmies() - moveArmies);

            // Check if player was eliminated
            if (territoryRepository.findByOwnerId(previousOwner.getId()).isEmpty()) {
                previousOwner.eliminate();
                playerRepository.save(previousOwner);
                eliminatedPlayer = previousOwner;
            }

            // Check for game over
            winConditionService.checkGameOver(game);
        }

        territoryRepository.save(from);
        territoryRepository.save(to);
        gameRepository.save(game);

        return AttackResult.builder()
                .attackerDice(attackDice)
                .defenderDice(defendDice)
                .attackerLosses(attackerLosses)
                .defenderLosses(defenderLosses)
                .conquered(conquered)
                .eliminatedPlayer(eliminatedPlayer != null ? eliminatedPlayer.getName() : null)
                .build();
    }

    int[] rollDice(int count) {
        int[] dice = new int[count];
        for (int i = 0; i < count; i++) {
            dice[i] = random.nextInt(6) + 1;
        }
        return dice;
    }

    void reverseArray(int[] arr) {
        for (int i = 0; i < arr.length / 2; i++) {
            int temp = arr[i];
            arr[i] = arr[arr.length - 1 - i];
            arr[arr.length - 1 - i] = temp;
        }
    }
}
