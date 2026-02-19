package com.risk.cpu;

import com.risk.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CPUStrategyFactory routing logic.
 */
@ExtendWith(MockitoExtension.class)
class CPUStrategyFactoryTest {

    @Mock private EasyCPUStrategy easyStrategy;
    @Mock private MediumCPUStrategy mediumStrategy;
    @Mock private HardCPUStrategy hardStrategy;

    @InjectMocks
    private CPUStrategyFactory factory;

    @Test
    @DisplayName("should return easy strategy for EASY difficulty")
    void shouldReturnEasyForEasy() {
        CPUStrategy result = factory.getStrategy(CPUDifficulty.EASY);
        assertSame(easyStrategy, result);
    }

    @Test
    @DisplayName("should return medium strategy for MEDIUM difficulty")
    void shouldReturnMediumForMedium() {
        CPUStrategy result = factory.getStrategy(CPUDifficulty.MEDIUM);
        assertSame(mediumStrategy, result);
    }

    @Test
    @DisplayName("should return hard strategy for HARD difficulty")
    void shouldReturnHardForHard() {
        CPUStrategy result = factory.getStrategy(CPUDifficulty.HARD);
        assertSame(hardStrategy, result);
    }

    @Test
    @DisplayName("should return hard strategy for EXPERT difficulty (BUG 7 - fallback)")
    void shouldReturnHardForExpert() {
        CPUStrategy result = factory.getStrategy(CPUDifficulty.EXPERT);
        assertSame(hardStrategy, result, "EXPERT should fall back to HARD strategy");
    }

    @Test
    @DisplayName("should default to MEDIUM when difficulty is null")
    void shouldDefaultToMediumWhenNull() {
        CPUStrategy result = factory.getStrategy((CPUDifficulty) null);
        assertSame(mediumStrategy, result, "null difficulty should default to MEDIUM");
    }

    @Test
    @DisplayName("getStrategy(Player) should delegate to player's difficulty")
    void shouldDelegateToPlayerDifficulty() {
        Player cpuPlayer = Player.builder()
                .id("cpu-1")
                .name("CPU Player 1")
                .type(PlayerType.CPU)
                .cpuDifficulty(CPUDifficulty.HARD)
                .build();

        CPUStrategy result = factory.getStrategy(cpuPlayer);
        assertSame(hardStrategy, result);
    }

    @Test
    @DisplayName("getStrategy(Player) should default to MEDIUM when player has null difficulty")
    void shouldDefaultForPlayerWithNullDifficulty() {
        Player cpuPlayer = Player.builder()
                .id("cpu-1")
                .name("CPU Player 1")
                .type(PlayerType.CPU)
                .cpuDifficulty(null)
                .build();

        CPUStrategy result = factory.getStrategy(cpuPlayer);
        assertSame(mediumStrategy, result, "Player with null difficulty should get MEDIUM strategy");
    }
}
