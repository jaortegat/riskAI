package com.risk.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.risk.dto.AttackResult;
import com.risk.dto.CreateGameRequest;
import com.risk.dto.GameStateDTO;
import com.risk.dto.JoinGameRequest;
import com.risk.model.CPUDifficulty;
import com.risk.model.Game;
import com.risk.model.Player;
import com.risk.model.Territory;

/**
 * Unit tests for GameService facade — verifies all delegation methods.
 */
@ExtendWith(MockitoExtension.class)
class GameServiceFacadeTest {

    @Mock private GameLifecycleService lifecycleService;
    @Mock private CombatService combatService;
    @Mock private ReinforcementService reinforcementService;
    @Mock private FortificationService fortificationService;
    @Mock private TurnManagementService turnManagementService;
    @Mock private GameQueryService queryService;
    @Mock private WinConditionService winConditionService;

    @InjectMocks
    private GameService gameService;

    @Test
    @DisplayName("createGame should delegate to lifecycleService")
    void createGameShouldDelegate() {
        CreateGameRequest request = CreateGameRequest.builder()
                .gameName("Test").playerName("Alice").build();
        Game expected = Game.builder().id("g1").build();
        when(lifecycleService.createGame(request, "session-1")).thenReturn(expected);

        Game result = gameService.createGame(request, "session-1");

        assertEquals(expected, result);
        verify(lifecycleService).createGame(request, "session-1");
    }

    @Test
    @DisplayName("joinGame should delegate to lifecycleService")
    void joinGameShouldDelegate() {
        JoinGameRequest request = JoinGameRequest.builder().playerName("Bob").build();
        Player expected = Player.builder().id("p1").build();
        when(lifecycleService.joinGame("g1", request, "s1")).thenReturn(expected);

        Player result = gameService.joinGame("g1", request, "s1");

        assertEquals(expected, result);
        verify(lifecycleService).joinGame("g1", request, "s1");
    }

    @Test
    @DisplayName("addCPUPlayer should delegate to lifecycleService")
    void addCPUPlayerShouldDelegate() {
        Player expected = Player.builder().id("cpu-1").build();
        when(lifecycleService.addCPUPlayer("g1", CPUDifficulty.HARD)).thenReturn(expected);

        Player result = gameService.addCPUPlayer("g1", CPUDifficulty.HARD);

        assertEquals(expected, result);
        verify(lifecycleService).addCPUPlayer("g1", CPUDifficulty.HARD);
    }

    @Test
    @DisplayName("startGame should delegate to lifecycleService")
    void startGameShouldDelegate() {
        Game expected = Game.builder().id("g1").build();
        when(lifecycleService.startGame("g1")).thenReturn(expected);

        Game result = gameService.startGame("g1");

        assertEquals(expected, result);
        verify(lifecycleService).startGame("g1");
    }

    @Test
    @DisplayName("calculateReinforcements should delegate to reinforcementService")
    void calculateReinforcementsShouldDelegate() {
        Player player = Player.builder().id("p1").build();
        when(reinforcementService.calculateReinforcements(player)).thenReturn(5);

        int result = gameService.calculateReinforcements(player);

        assertEquals(5, result);
        verify(reinforcementService).calculateReinforcements(player);
    }

    @Test
    @DisplayName("placeArmies should delegate to reinforcementService")
    void placeArmiesShouldDelegate() {
        Territory expected = Territory.builder().id("t1").build();
        when(reinforcementService.placeArmies("g1", "p1", "brazil", 3)).thenReturn(expected);

        Territory result = gameService.placeArmies("g1", "p1", "brazil", 3);

        assertEquals(expected, result);
        verify(reinforcementService).placeArmies("g1", "p1", "brazil", 3);
    }

    @Test
    @DisplayName("attack should delegate to combatService")
    void attackShouldDelegate() {
        AttackResult expected = AttackResult.builder().conquered(true).build();
        when(combatService.attack("g1", "p1", "brazil", "argentina", 3)).thenReturn(expected);

        AttackResult result = gameService.attack("g1", "p1", "brazil", "argentina", 3);

        assertEquals(expected, result);
        verify(combatService).attack("g1", "p1", "brazil", "argentina", 3);
    }

    @Test
    @DisplayName("endAttackPhase should delegate to turnManagementService")
    void endAttackPhaseShouldDelegate() {
        Game expected = Game.builder().id("g1").build();
        when(turnManagementService.endAttackPhase("g1", "p1")).thenReturn(expected);

        Game result = gameService.endAttackPhase("g1", "p1");

        assertEquals(expected, result);
        verify(turnManagementService).endAttackPhase("g1", "p1");
    }

    @Test
    @DisplayName("fortify should delegate to fortificationService")
    void fortifyShouldDelegate() {
        Game expected = Game.builder().id("g1").build();
        when(fortificationService.fortify("g1", "p1", "brazil", "argentina", 2)).thenReturn(expected);

        Game result = gameService.fortify("g1", "p1", "brazil", "argentina", 2);

        assertEquals(expected, result);
        verify(fortificationService).fortify("g1", "p1", "brazil", "argentina", 2);
    }

    @Test
    @DisplayName("skipFortify should delegate to fortificationService")
    void skipFortifyShouldDelegate() {
        Game expected = Game.builder().id("g1").build();
        when(fortificationService.skipFortify("g1", "p1")).thenReturn(expected);

        Game result = gameService.skipFortify("g1", "p1");

        assertEquals(expected, result);
        verify(fortificationService).skipFortify("g1", "p1");
    }

    @Test
    @DisplayName("checkTurnLimit should delegate to winConditionService")
    void checkTurnLimitShouldDelegate() {
        Game game = Game.builder().id("g1").build();
        when(winConditionService.checkTurnLimit(game)).thenReturn(true);

        boolean result = gameService.checkTurnLimit(game);

        assertEquals(true, result);
        verify(winConditionService).checkTurnLimit(game);
    }

    @Test
    @DisplayName("getGame should delegate to queryService")
    void getGameShouldDelegate() {
        Game expected = Game.builder().id("g1").build();
        when(queryService.getGame("g1")).thenReturn(expected);

        Game result = gameService.getGame("g1");

        assertEquals(expected, result);
        verify(queryService).getGame("g1");
    }

    @Test
    @DisplayName("getGameState should delegate to queryService")
    void getGameStateShouldDelegate() {
        GameStateDTO expected = GameStateDTO.builder().gameId("g1").build();
        when(queryService.getGameState("g1")).thenReturn(expected);

        GameStateDTO result = gameService.getGameState("g1");

        assertEquals(expected, result);
        verify(queryService).getGameState("g1");
    }

    @Test
    @DisplayName("getJoinableGames should delegate to queryService")
    void getJoinableGamesShouldDelegate() {
        List<Game> expected = List.of(Game.builder().id("g1").build());
        when(queryService.getJoinableGames()).thenReturn(expected);

        List<Game> result = gameService.getJoinableGames();

        assertEquals(expected, result);
        verify(queryService).getJoinableGames();
    }

    @Test
    @DisplayName("getAllGames should delegate to queryService")
    void getAllGamesShouldDelegate() {
        List<Game> expected = List.of(Game.builder().id("g1").build());
        when(queryService.getAllGames()).thenReturn(expected);

        List<Game> result = gameService.getAllGames();

        assertEquals(expected, result);
        verify(queryService).getAllGames();
    }
}
