package com.risk.config;

import tools.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

/**
 * Loads all available map definitions at startup.
 * <p>
 * Maps are loaded from two locations (in order):
 * <ol>
 *   <li>Classpath: {@code classpath:maps/*.json} – built-in maps shipped with the app</li>
 *   <li>External folder: {@code ./maps/} next to the running jar – user-created custom maps</li>
 * </ol>
 * If a custom map has the same {@code id} as a built-in map, the custom one wins.
 */
@Component
@Slf4j
public class MapLoader {

    private final ObjectMapper objectMapper;

    /** All loaded maps keyed by their id. */
    @Getter
    private final Map<String, MapDefinition> maps = new LinkedHashMap<>();

    public MapLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void loadMaps() {
        loadClasspathMaps();
        loadExternalMaps();

        if (maps.isEmpty()) {
            log.warn("No map definitions found! The game will not work without at least one map.");
        } else {
            log.info("Loaded {} map(s): {}", maps.size(),
                    maps.values().stream().map(MapDefinition::name).toList());
        }
    }

    /**
     * Returns an unmodifiable list of every loaded map definition.
     */
    public List<MapDefinition> getAvailableMaps() {
        return List.copyOf(maps.values());
    }

    /**
     * Get a specific map by its id.
     *
     * @throws IllegalArgumentException if the map id is unknown
     */
    public MapDefinition getMap(String mapId) {
        MapDefinition map = maps.get(mapId);
        if (map == null) {
            throw new IllegalArgumentException("Unknown map: " + mapId
                    + ". Available maps: " + maps.keySet());
        }
        return map;
    }

    // ── classpath maps ──────────────────────────────────────────────────

    private void loadClasspathMaps() {
        try {
            var resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath:maps/*.json");

            for (Resource resource : resources) {
                try (InputStream is = resource.getInputStream()) {
                    MapDefinition map = objectMapper.readValue(is, MapDefinition.class);
                    maps.put(map.id(), map);
                    log.info("Loaded built-in map '{}' ({}) from classpath",
                            map.name(), map.id());
                } catch (IOException e) {
                    log.error("Failed to load classpath map: {}", resource.getFilename(), e);
                }
            }
        } catch (IOException e) {
            log.warn("Could not scan classpath for maps: {}", e.getMessage());
        }
    }

    // ── external maps (./maps/ folder) ──────────────────────────────────

    private void loadExternalMaps() {
        Path externalDir = Paths.get("maps");
        if (!Files.isDirectory(externalDir)) {
            log.debug("No external maps directory found at '{}'", externalDir.toAbsolutePath());
            return;
        }

        try (Stream<Path> files = Files.list(externalDir)) {
            files.filter(p -> p.toString().endsWith(".json"))
                 .sorted()
                 .forEach(this::loadExternalMapFile);
        } catch (IOException e) {
            log.error("Error reading external maps directory", e);
        }
    }

    private void loadExternalMapFile(Path path) {
        try {
            MapDefinition map = objectMapper.readValue(path.toFile(), MapDefinition.class);
            maps.put(map.id(), map);
            log.info("Loaded custom map '{}' ({}) from {}", map.name(), map.id(), path);
        } catch (Exception e) {
            log.error("Failed to load custom map: {}", path, e);
        }
    }
}
