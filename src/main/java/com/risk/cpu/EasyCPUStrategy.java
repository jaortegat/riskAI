package com.risk.cpu;

import com.risk.model.*;
import com.risk.repository.TerritoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Easy CPU Strategy - Makes random decisions.
 */
@Component
@RequiredArgsConstructor
public class EasyCPUStrategy implements CPUStrategy {

    private final TerritoryRepository territoryRepository;
    private final Random random = new Random();

    @Override
    public CPUDifficulty getDifficulty() {
        return CPUDifficulty.EASY;
    }

    @Override
    public CPUAction decideReinforcement(Game game, Player cpuPlayer, int reinforcementsAvailable) {
        List<Territory> myTerritories = territoryRepository.findByOwnerId(cpuPlayer.getId());

        if (myTerritories.isEmpty()) {
            return null;
        }

        // Randomly pick a territory
        Territory target = myTerritories.get(random.nextInt(myTerritories.size()));
        return CPUAction.placeArmies(target.getTerritoryKey(), reinforcementsAvailable);
    }

    @Override
    public CPUAction decideAttack(Game game, Player cpuPlayer) {
        // 50% chance to attack
        if (random.nextBoolean()) {
            return CPUAction.endAttack();
        }

        List<Territory> attackCapable = territoryRepository.findAttackCapableTerritories(
                game.getId(), cpuPlayer.getId());

        if (attackCapable.isEmpty()) {
            return CPUAction.endAttack();
        }

        // Find a territory with an enemy neighbor
        for (Territory from : attackCapable) {
            List<Territory> allTerritories = territoryRepository.findByGameId(game.getId());
            for (Territory to : allTerritories) {
                if (!to.isOwnedBy(cpuPlayer) && from.isNeighborOf(to.getTerritoryKey())) {
                    int attackArmies = Math.min(3, from.getArmies() - 1);
                    return CPUAction.attack(from.getTerritoryKey(), to.getTerritoryKey(), attackArmies);
                }
            }
        }

        return CPUAction.endAttack();
    }

    @Override
    public CPUAction decideFortify(Game game, Player cpuPlayer) {
        // Easy CPU doesn't fortify much
        if (random.nextInt(3) != 0) {
            return CPUAction.skipFortify();
        }

        List<Territory> myTerritories = territoryRepository.findByOwnerId(cpuPlayer.getId());
        List<Territory> canMove = myTerritories.stream()
                .filter(t -> t.getArmies() > 1)
                .toList();

        if (canMove.isEmpty()) {
            return CPUAction.skipFortify();
        }

        Territory from = canMove.get(random.nextInt(canMove.size()));

        // Find a connected territory
        for (Territory to : myTerritories) {
            if (!to.getId().equals(from.getId()) && from.isNeighborOf(to.getTerritoryKey())) {
                int armies = random.nextInt(from.getArmies() - 1) + 1;
                return CPUAction.fortify(from.getTerritoryKey(), to.getTerritoryKey(), armies);
            }
        }

        return CPUAction.skipFortify();
    }
}
