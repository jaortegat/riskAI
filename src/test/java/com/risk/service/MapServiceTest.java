package com.risk.service;

import com.risk.config.*;
import com.risk.model.*;
import com.risk.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MapService map initialization logic.
 */
@ExtendWith(MockitoExtension.class)
class MapServiceTest {

    @Mock private ContinentRepository continentRepository;
    @Mock private TerritoryRepository territoryRepository;
    @Mock private MapLoader mapLoader;

    @InjectMocks
    private MapService mapService;

    private Game game;

    @BeforeEach
    void setUp() {
        game = Game.builder()
                .id("game-1")
                .name("Map Test")
                .mapId("classic-world")
                .status(GameStatus.WAITING_FOR_PLAYERS)
                .players(new ArrayList<>())
                .territories(new HashSet<>())
                .build();
    }

    @Test
    @DisplayName("should create continents and territories from map definition")
    void shouldInitializeMapFromDefinition() {
        TerritoryDefinition terr1 = new TerritoryDefinition("alaska", "Alaska",
                List.of("kamchatka", "nw-territory"), 10.0, 20.0);
        TerritoryDefinition terr2 = new TerritoryDefinition("kamchatka", "Kamchatka",
                List.of("alaska"), 30.0, 40.0);

        AreaDefinition area = new AreaDefinition("north-america", "North America",
                5, "#FF0000", List.of(terr1, terr2));

        MapDefinition mapDef = new MapDefinition("classic-world", "Classic World",
                "Standard Risk map", "Author", 2, 6, List.of(area));

        when(mapLoader.getMap("classic-world")).thenReturn(mapDef);
        when(continentRepository.save(any(Continent.class))).thenAnswer(inv -> {
            Continent c = inv.getArgument(0);
            c.setId("cont-1");
            return c;
        });
        when(territoryRepository.save(any(Territory.class))).thenAnswer(inv -> {
            Territory t = inv.getArgument(0);
            t.setId("terr-" + t.getTerritoryKey());
            return t;
        });
        when(territoryRepository.findByGameId("game-1")).thenReturn(List.of());

        mapService.initializeMap(game);

        // Verify continent created
        ArgumentCaptor<Continent> continentCaptor = ArgumentCaptor.forClass(Continent.class);
        verify(continentRepository).save(continentCaptor.capture());
        Continent saved = continentCaptor.getValue();
        assertEquals("north-america", saved.getContinentKey());
        assertEquals("North America", saved.getName());
        assertEquals(5, saved.getBonusArmies());
        assertEquals("#FF0000", saved.getColor());

        // Verify 2 territories created
        verify(territoryRepository, times(2)).save(any(Territory.class));
    }

    @Test
    @DisplayName("should set neighbor keys on created territories")
    void shouldSetNeighborKeys() {
        TerritoryDefinition terr1 = new TerritoryDefinition("alaska", "Alaska",
                List.of("kamchatka", "nw-territory"), 10.0, 20.0);

        AreaDefinition area = new AreaDefinition("na", "NA", 5, "#FF0000", List.of(terr1));
        MapDefinition mapDef = new MapDefinition("classic-world", "CW", "d", "a", 2, 6, List.of(area));

        when(mapLoader.getMap("classic-world")).thenReturn(mapDef);
        when(continentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(territoryRepository.save(any(Territory.class))).thenAnswer(inv -> inv.getArgument(0));
        when(territoryRepository.findByGameId("game-1")).thenReturn(List.of());

        mapService.initializeMap(game);

        ArgumentCaptor<Territory> captor = ArgumentCaptor.forClass(Territory.class);
        verify(territoryRepository).save(captor.capture());

        Territory territory = captor.getValue();
        assertEquals("alaska", territory.getTerritoryKey());
        assertTrue(territory.getNeighborKeys().contains("kamchatka"));
        assertTrue(territory.getNeighborKeys().contains("nw-territory"));
    }

    @Test
    @DisplayName("should handle map with multiple areas")
    void shouldHandleMultipleAreas() {
        TerritoryDefinition t1 = new TerritoryDefinition("brazil", "Brazil",
                List.of("argentina"), 10.0, 20.0);
        AreaDefinition area1 = new AreaDefinition("south-america", "South America",
                2, "#00FF00", List.of(t1));

        TerritoryDefinition t2 = new TerritoryDefinition("egypt", "Egypt",
                List.of("north-africa"), 30.0, 40.0);
        AreaDefinition area2 = new AreaDefinition("africa", "Africa",
                3, "#0000FF", List.of(t2));

        MapDefinition mapDef = new MapDefinition("classic-world", "CW", "d", "a", 2, 6,
                List.of(area1, area2));

        when(mapLoader.getMap("classic-world")).thenReturn(mapDef);
        when(continentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(territoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(territoryRepository.findByGameId("game-1")).thenReturn(List.of());

        mapService.initializeMap(game);

        verify(continentRepository, times(2)).save(any(Continent.class));
        verify(territoryRepository, times(2)).save(any(Territory.class));
    }
}
