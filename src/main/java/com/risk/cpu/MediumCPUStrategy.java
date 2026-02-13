package com.risk.cpu;

import com.risk.model.*;
import com.risk.repository.TerritoryRepository;
import com.risk.repository.ContinentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Medium CPU Strategy - Makes somewhat strategic decisions.
 */
@Component
@RequiredArgsConstructor
public class MediumCPUStrategy implements CPUStrategy {

    private final TerritoryRepository territoryRepository;
    private final ContinentRepository continentRepository;
    private final Random random = new Random();

    @Override
    public CPUDifficulty getDifficulty() {
        return CPUDifficulty.MEDIUM;
    }

    @Override
    public CPUAction decideReinforcement(Game game, Player cpuPlayer, int reinforcementsAvailable) {
        List<Territory> myTerritories = territoryRepository.findByOwnerId(cpuPlayer.getId());

        if (myTerritories.isEmpty()) {
            return null;
        }

        // Find territories on borders (have enemy neighbors)
        Map<String, Integer> enemyNeighborCount = new HashMap<>();
        List<Territory> allTerritories = territoryRepository.findByGameId(game.getId());

        for (Territory t : myTerritories) {
            int enemyCount = 0;
            for (Territory other : allTerritories) {
                if (!other.isOwnedBy(cpuPlayer) && t.isNeighborOf(other.getTerritoryKey())) {
                    enemyCount++;
                }
            }
            if (enemyCount > 0) {
                enemyNeighborCount.put(t.getTerritoryKey(), enemyCount);
            }
        }

        // Reinforce the territory with most enemy neighbors
        String targetKey = enemyNeighborCount.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(myTerritories.get(0).getTerritoryKey());

        return CPUAction.placeArmies(targetKey, reinforcementsAvailable);
    }

    @Override
    public CPUAction decideAttack(Game game, Player cpuPlayer) {
        List<Territory> attackCapable = territoryRepository.findAttackCapableTerritories(
                game.getId(), cpuPlayer.getId());

        if (attackCapable.isEmpty()) {
            return CPUAction.endAttack();
        }

        // Find the best attack opportunity
        Territory bestFrom = null;
        Territory bestTo = null;
        int bestAdvantage = 0;

        List<Territory> allTerritories = territoryRepository.findByGameId(game.getId());

        for (Territory from : attackCapable) {
            for (Territory to : allTerritories) {
                if (!to.isOwnedBy(cpuPlayer) && from.isNeighborOf(to.getTerritoryKey())) {
                    int advantage = from.getArmies() - to.getArmies();
                    if (advantage > bestAdvantage) {
                        bestAdvantage = advantage;
                        bestFrom = from;
                        bestTo = to;
                    }
                }
            }
        }

        // Only attack if we have an advantage
        if (bestFrom != null && bestAdvantage >= 2) {
            int attackArmies = Math.min(3, bestFrom.getArmies() - 1);
            return CPUAction.attack(bestFrom.getTerritoryKey(), bestTo.getTerritoryKey(), attackArmies);
        }

        return CPUAction.endAttack();
    }

    @Override
    public CPUAction decideFortify(Game game, Player cpuPlayer) {
        List<Territory> myTerritories = territoryRepository.findByOwnerId(cpuPlayer.getId());
        List<Territory> allTerritories = territoryRepository.findByGameId(game.getId());

        // Find interior territories (no enemy neighbors) with armies
        List<Territory> interior = new ArrayList<>();
        List<Territory> border = new ArrayList<>();

        for (Territory t : myTerritories) {
            boolean hasBorder = false;
            for (Territory other : allTerritories) {
                if (!other.isOwnedBy(cpuPlayer) && t.isNeighborOf(other.getTerritoryKey())) {
                    hasBorder = true;
                    break;
                }
            }
            if (hasBorder) {
                border.add(t);
            } else if (t.getArmies() > 1) {
                interior.add(t);
            }
        }

        // Move armies from interior to border
        if (!interior.isEmpty() && !border.isEmpty()) {
            Territory from = interior.stream()
                    .max(Comparator.comparingInt(Territory::getArmies))
                    .orElse(null);

            if (from != null) {
                // Find a connected border territory
                for (Territory to : border) {
                    if (from.isNeighborOf(to.getTerritoryKey())) {
                        int armies = from.getArmies() - 1;
                        return CPUAction.fortify(from.getTerritoryKey(), to.getTerritoryKey(), armies);
                    }
                }
            }
        }

        return CPUAction.skipFortify();
    }
}
