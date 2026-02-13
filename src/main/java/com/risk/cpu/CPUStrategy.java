package com.risk.cpu;

import com.risk.model.*;
import java.util.List;

/**
 * Strategy interface for CPU players.
 * Implements the Strategy pattern for different CPU difficulty levels.
 */
public interface CPUStrategy {

    /**
     * Decide where to place reinforcement armies.
     */
    CPUAction decideReinforcement(Game game, Player cpuPlayer, int reinforcementsAvailable);

    /**
     * Decide whether to attack and where.
     */
    CPUAction decideAttack(Game game, Player cpuPlayer);

    /**
     * Decide fortification move.
     */
    CPUAction decideFortify(Game game, Player cpuPlayer);

    /**
     * Get the difficulty level this strategy represents.
     */
    CPUDifficulty getDifficulty();
}
