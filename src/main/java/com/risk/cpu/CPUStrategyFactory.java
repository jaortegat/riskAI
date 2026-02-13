package com.risk.cpu;

import com.risk.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Factory for creating CPU strategies based on difficulty level.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CPUStrategyFactory {

    private final EasyCPUStrategy easyStrategy;
    private final MediumCPUStrategy mediumStrategy;
    private final HardCPUStrategy hardStrategy;

    /**
     * Get the appropriate strategy for a player's difficulty level.
     */
    public CPUStrategy getStrategy(CPUDifficulty difficulty) {
        if (difficulty == null) {
            difficulty = CPUDifficulty.MEDIUM;
        }

        return switch (difficulty) {
            case EASY -> easyStrategy;
            case MEDIUM -> mediumStrategy;
            case HARD, EXPERT -> hardStrategy;
        };
    }

    /**
     * Get the appropriate strategy for a player.
     */
    public CPUStrategy getStrategy(Player player) {
        return getStrategy(player.getCpuDifficulty());
    }
}
