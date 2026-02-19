package com.risk.controller;

import com.risk.config.MapDefinition;
import com.risk.config.MapLoader;
import com.risk.dto.MapInfoDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for WebController page rendering.
 */
@ExtendWith(MockitoExtension.class)
class WebControllerTest {

    @Mock
    private MapLoader mapLoader;

    @InjectMocks
    private WebController controller;

    @Test
    @DisplayName("GET / should return index view")
    void homeShouldReturnIndexView() {
        Model model = new ConcurrentModel();
        String view = controller.home(model);
        assertEquals("index", view);
    }

    @Test
    @DisplayName("GET /create should return create view with maps")
    void createGameShouldReturnCreateViewWithMaps() {
        MapDefinition mapDef = new MapDefinition("classic-world", "Classic World",
                "desc", "Author", 2, 6, List.of());
        when(mapLoader.getAvailableMaps()).thenReturn(List.of(mapDef));

        Model model = new ConcurrentModel();
        String view = controller.createGame(model);

        assertEquals("create", view);
        assertTrue(model.containsAttribute("maps"));
        @SuppressWarnings("unchecked")
        List<MapInfoDTO> maps = (List<MapInfoDTO>) model.getAttribute("maps");
        assertNotNull(maps);
        assertEquals(1, maps.size());
        assertEquals("classic-world", maps.get(0).getId());
    }

    @Test
    @DisplayName("GET /game/{gameId} should return game view with gameId")
    void gameShouldReturnGameViewWithId() {
        Model model = new ConcurrentModel();
        String view = controller.game("game-123", model);

        assertEquals("game", view);
        assertEquals("game-123", model.getAttribute("gameId"));
    }

    @Test
    @DisplayName("GET /join/{gameId} should return join view with gameId")
    void joinGameShouldReturnJoinViewWithId() {
        Model model = new ConcurrentModel();
        String view = controller.joinGame("game-456", model);

        assertEquals("join", view);
        assertEquals("game-456", model.getAttribute("gameId"));
    }
}
