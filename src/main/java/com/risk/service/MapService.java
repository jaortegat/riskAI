package com.risk.service;

import com.risk.config.*;
import com.risk.model.*;
import com.risk.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Service for initializing game maps from JSON-based {@link MapDefinition}s.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class MapService {

    private final ContinentRepository continentRepository;
    private final TerritoryRepository territoryRepository;
    private final MapLoader mapLoader;

    /**
     * Initialize the map for a game using the map definition identified by
     * {@code game.getMapId()}.
     */
    public void initializeMap(Game game) {
        String mapId = game.getMapId();
        MapDefinition mapDef = mapLoader.getMap(mapId);
        log.info("Initializing map '{}' for game: {}", mapDef.name(), game.getId());

        for (AreaDefinition areaDef : mapDef.areas()) {
            Continent continent = createContinent(game, areaDef);
            for (TerritoryDefinition terrDef : areaDef.territories()) {
                createTerritory(game, continent, terrDef);
            }
        }

        log.info("Map '{}' initialized with {} territories", mapDef.name(),
                territoryRepository.findByGameId(game.getId()).size());
    }

    private Continent createContinent(Game game, AreaDefinition areaDef) {
        Continent continent = Continent.builder()
                .continentKey(areaDef.key())
                .name(areaDef.name())
                .bonusArmies(areaDef.bonusArmies())
                .color(areaDef.color())
                .game(game)
                .build();
        return continentRepository.save(continent);
    }

    private Territory createTerritory(Game game, Continent continent, TerritoryDefinition terrDef) {
        Territory territory = Territory.builder()
                .territoryKey(terrDef.key())
                .name(terrDef.name())
                .game(game)
                .continent(continent)
                .mapX(terrDef.mapX())
                .mapY(terrDef.mapY())
                .neighborKeys(new HashSet<>(terrDef.neighbors()))
                .build();
        return territoryRepository.save(territory);
    }
}
