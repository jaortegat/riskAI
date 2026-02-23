package com.risk.service;

import com.risk.dto.CreateGameRequest;
import com.risk.model.*;
import com.risk.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class GameServiceTest {

    @Autowired
    private GameService gameService;

    @Autowired
    private TerritoryRepository territoryRepository;

    private Game testGame;

    @BeforeEach
    void setUp() {
        CreateGameRequest request = CreateGameRequest.builder()
                .gameName("Test Game")
                .playerName("Test Player")
                .maxPlayers(6)
                .minPlayers(2)
                .cpuPlayerCount(1)
                .cpuDifficulty(CPUDifficulty.MEDIUM)
                .build();

        testGame = gameService.createGame(request, "test-session");
    }

    @Test
    void createGame_shouldCreateGameWithCorrectSettings() {
        assertNotNull(testGame.getId());
        assertEquals("Test Game", testGame.getName());
        assertEquals(GameStatus.WAITING_FOR_PLAYERS, testGame.getStatus());
        assertEquals(2, testGame.getPlayers().size()); // 1 human + 1 AI
    }

    @Test
    void createGame_shouldInitializeMap() {
        var territories = territoryRepository.findByGameId(testGame.getId());
        assertEquals(42, territories.size()); // RiskAI has 42 territories
    }

    @Test
    void addCPUPlayer_shouldAddNewCPUPlayer() {
        Player ai = gameService.addCPUPlayer(testGame.getId(), CPUDifficulty.HARD);
        
        assertNotNull(ai);
        assertEquals(PlayerType.CPU, ai.getType());
        assertEquals(CPUDifficulty.HARD, ai.getCpuDifficulty());
    }

    @Test
    void startGame_shouldDistributeTerritories() {
        // Add minimum players
        gameService.addCPUPlayer(testGame.getId(), CPUDifficulty.EASY);
        
        Game startedGame = gameService.startGame(testGame.getId());
        
        assertEquals(GameStatus.IN_PROGRESS, startedGame.getStatus());
        assertEquals(GamePhase.REINFORCEMENT, startedGame.getCurrentPhase());
        
        // All territories should be owned
        var territories = territoryRepository.findByGameId(testGame.getId());
        assertTrue(territories.stream().allMatch(t -> t.getOwner() != null));
    }

    @Test
    void calculateReinforcements_shouldReturnMinimumThree() {
        Player player = testGame.getPlayers().get(0);
        int reinforcements = gameService.calculateReinforcements(player);
        assertTrue(reinforcements >= 3);
    }
}
