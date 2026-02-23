package com.risk.cpu;

import com.risk.model.CPUDifficulty;
import com.risk.model.Continent;
import com.risk.model.Game;
import com.risk.model.Player;
import com.risk.model.Territory;
import com.risk.repository.TerritoryRepository;
import com.risk.repository.ContinentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * Hard CPU Strategy - Makes strategic decisions focused on continent control.
 */
@Component
@RequiredArgsConstructor
public class HardCPUStrategy implements CPUStrategy {

    private final TerritoryRepository territoryRepository;
    private final ContinentRepository continentRepository;

    @Override
    public CPUDifficulty getDifficulty() {
        return CPUDifficulty.HARD;
    }

    @Override
    public CPUAction decideReinforcement(Game game, Player cpuPlayer, int reinforcementsAvailable) {
        List<Territory> myTerritories = territoryRepository.findByOwnerId(cpuPlayer.getId());
        List<Continent> continents = continentRepository.findByGameIdWithTerritories(game.getId());

        // Find continents we're close to controlling
        Continent targetContinent = null;
        int minMissing = Integer.MAX_VALUE;

        for (Continent continent : continents) {
            int myCount = 0;
            int totalCount = continent.getTerritories().size();

            for (Territory t : continent.getTerritories()) {
                if (t.isOwnedBy(cpuPlayer)) {
                    myCount++;
                }
            }

            int missing = totalCount - myCount;
            if (missing > 0 && missing < minMissing) {
                minMissing = missing;
                targetContinent = continent;
            }
        }

        // Reinforce territories in the target continent that border enemies
        if (targetContinent != null) {
            Territory reinforceTarget = findBestReinforcementTarget(cpuPlayer, targetContinent, myTerritories);
            if (reinforceTarget != null) {
                return CPUAction.placeArmies(reinforceTarget.getTerritoryKey(), reinforcementsAvailable);
            }
        }

        // Default: reinforce weakest border territory
        Territory weakest = findWeakestBorderTerritory(cpuPlayer, game.getId());
        if (weakest != null) {
            return CPUAction.placeArmies(weakest.getTerritoryKey(), reinforcementsAvailable);
        }

        return CPUAction.placeArmies(myTerritories.get(0).getTerritoryKey(), reinforcementsAvailable);
    }

    private Territory findBestReinforcementTarget(Player cpuPlayer, Continent continent, List<Territory> myTerritories) {
        List<Territory> allTerritories = territoryRepository.findByGameId(cpuPlayer.getGame().getId());

        return continent.getTerritories().stream()
                .filter(t -> t.isOwnedBy(cpuPlayer))
                .filter(t -> hasEnemyNeighbor(t, cpuPlayer, allTerritories))
                .min(Comparator.comparingInt(Territory::getArmies))
                .orElse(null);
    }

    private Territory findWeakestBorderTerritory(Player cpuPlayer, String gameId) {
        List<Territory> myTerritories = territoryRepository.findByOwnerId(cpuPlayer.getId());
        List<Territory> allTerritories = territoryRepository.findByGameId(gameId);

        return myTerritories.stream()
                .filter(t -> hasEnemyNeighbor(t, cpuPlayer, allTerritories))
                .min(Comparator.comparingInt(Territory::getArmies))
                .orElse(null);
    }

    private boolean hasEnemyNeighbor(Territory t, Player cpuPlayer, List<Territory> allTerritories) {
        return allTerritories.stream()
                .anyMatch(other -> !other.isOwnedBy(cpuPlayer) && t.isNeighborOf(other.getTerritoryKey()));
    }

    @Override
    public CPUAction decideAttack(Game game, Player cpuPlayer) {
        List<Continent> continents = continentRepository.findByGameIdWithTerritories(game.getId());
        List<Territory> attackCapable = territoryRepository.findAttackCapableTerritories(
                game.getId(), cpuPlayer.getId());

        if (attackCapable.isEmpty()) {
            return CPUAction.endAttack();
        }

        // Priority 1: Complete a continent
        for (Continent continent : continents) {
            CPUAction attack = findContinentCompletionAttack(cpuPlayer, continent, attackCapable);
            if (attack != null) {
                return attack;
            }
        }

        // Priority 2: Attack weak neighbors with strong advantage
        List<Territory> allTerritories = territoryRepository.findByGameId(game.getId());
        Territory bestFrom = null;
        Territory bestTo = null;
        double bestScore = 0;

        for (Territory from : attackCapable) {
            for (Territory to : allTerritories) {
                if (!to.isOwnedBy(cpuPlayer) && from.isNeighborOf(to.getTerritoryKey())) {
                    double ratio = (double) from.getArmies() / to.getArmies();
                    if (ratio > 2.0 && ratio > bestScore) {
                        bestScore = ratio;
                        bestFrom = from;
                        bestTo = to;
                    }
                }
            }
        }

        if (bestFrom != null) {
            int attackArmies = Math.min(3, bestFrom.getArmies() - 1);
            return CPUAction.attack(bestFrom.getTerritoryKey(), bestTo.getTerritoryKey(), attackArmies);
        }

        return CPUAction.endAttack();
    }

    private CPUAction findContinentCompletionAttack(Player cpuPlayer, Continent continent, 
                                                    List<Territory> attackCapable) {
        // Find territories in this continent we don't own
        List<Territory> enemyInContinent = continent.getTerritories().stream()
                .filter(t -> !t.isOwnedBy(cpuPlayer))
                .toList();

        if (enemyInContinent.isEmpty()) {
            return null; // Already own the continent
        }

        // Check if we can attack any of them
        for (Territory target : enemyInContinent) {
            for (Territory from : attackCapable) {
                if (from.isNeighborOf(target.getTerritoryKey())) {
                    // Check if attack is viable
                    if (from.getArmies() > target.getArmies()) {
                        int attackArmies = Math.min(3, from.getArmies() - 1);
                        return CPUAction.attack(from.getTerritoryKey(), target.getTerritoryKey(), attackArmies);
                    }
                }
            }
        }

        return null;
    }

    @Override
    public CPUAction decideFortify(Game game, Player cpuPlayer) {
        List<Territory> myTerritories = territoryRepository.findByOwnerId(cpuPlayer.getId());
        List<Territory> allTerritories = territoryRepository.findByGameId(game.getId());

        // Find interior territories with excess armies
        List<Territory> interior = myTerritories.stream()
                .filter(t -> t.getArmies() > 1)
                .filter(t -> !hasEnemyNeighbor(t, cpuPlayer, allTerritories))
                .toList();

        if (interior.isEmpty()) {
            return CPUAction.skipFortify();
        }

        // Find weakest border territory
        Territory weakestBorder = myTerritories.stream()
                .filter(t -> hasEnemyNeighbor(t, cpuPlayer, allTerritories))
                .min(Comparator.comparingInt(Territory::getArmies))
                .orElse(null);

        if (weakestBorder == null) {
            return CPUAction.skipFortify();
        }

        // Find interior territory that connects (simplified - direct connection only)
        for (Territory from : interior) {
            if (from.isNeighborOf(weakestBorder.getTerritoryKey())) {
                int armies = from.getArmies() - 1;
                return CPUAction.fortify(from.getTerritoryKey(), weakestBorder.getTerritoryKey(), armies);
            }
        }

        return CPUAction.skipFortify();
    }
}
