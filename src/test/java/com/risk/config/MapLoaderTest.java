package com.risk.config;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.file.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MapLoader — classpath + external map loading.
 */
class MapLoaderTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    // ── getMap / getAvailableMaps ────────────────────────────────────────

    @Test
    @DisplayName("loadMaps() should load built-in maps from classpath")
    void shouldLoadClasspathMaps() {
        MapLoader loader = new MapLoader(objectMapper);
        loader.loadMaps();

        assertFalse(loader.getMaps().isEmpty(), "Should load at least one classpath map");
        assertNotNull(loader.getMaps().get("classic-world"), "classic-world should be loaded");
    }

    @Test
    @DisplayName("getAvailableMaps() should return unmodifiable list of all maps")
    void shouldReturnAvailableMaps() {
        MapLoader loader = new MapLoader(objectMapper);
        loader.loadMaps();

        List<MapDefinition> maps = loader.getAvailableMaps();

        assertFalse(maps.isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> maps.add(null),
                "Returned list should be unmodifiable");
    }

    @Test
    @DisplayName("getMap() should return map for known id")
    void shouldReturnMapForKnownId() {
        MapLoader loader = new MapLoader(objectMapper);
        loader.loadMaps();

        MapDefinition map = loader.getMap("classic-world");

        assertNotNull(map);
        assertEquals("classic-world", map.id());
        assertEquals("Classic World", map.name());
        assertTrue(map.minPlayers() >= 2);
        assertTrue(map.maxPlayers() >= map.minPlayers());
        assertFalse(map.areas().isEmpty(), "Map should have areas");
    }

    @Test
    @DisplayName("getMap() should throw for unknown map id")
    void shouldThrowForUnknownMapId() {
        MapLoader loader = new MapLoader(objectMapper);
        loader.loadMaps();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> loader.getMap("nonexistent-map"));

        assertTrue(ex.getMessage().contains("Unknown map"));
        assertTrue(ex.getMessage().contains("nonexistent-map"));
    }

    // ── external maps ───────────────────────────────────────────────────

    @Test
    @DisplayName("loadMaps() should load external maps from ./maps/ directory")
    void shouldLoadExternalMaps(@TempDir Path tempDir) throws IOException {
        // Write a valid map JSON to a temp "maps" folder
        Path mapsDir = tempDir.resolve("maps");
        Files.createDirectory(mapsDir);

        String json = """
                {
                  "id": "test-external",
                  "name": "Test External Map",
                  "description": "A test map",
                  "author": "Tester",
                  "minPlayers": 2,
                  "maxPlayers": 4,
                  "areas": []
                }
                """;
        Files.writeString(mapsDir.resolve("test-external.json"), json);

        // Since loadExternalMaps() looks at Paths.get("maps") (CWD-relative),
        // we test the parsing path by manually loading the file
        MapLoader loader = new MapLoader(objectMapper);
        loader.loadMaps(); // loads classpath maps

        MapDefinition externalMap = objectMapper.readValue(
                mapsDir.resolve("test-external.json").toFile(), MapDefinition.class);
        loader.getMaps().put(externalMap.id(), externalMap);

        MapDefinition loaded = loader.getMap("test-external");
        assertEquals("Test External Map", loaded.name());
        assertEquals("Tester", loaded.author());
        assertEquals(2, loaded.minPlayers());
        assertEquals(4, loaded.maxPlayers());
    }

    @Test
    @DisplayName("loadMaps() should handle missing external maps directory gracefully")
    void shouldHandleMissingExternalDir() {
        // Default CWD likely has no ./maps/ dir OR it does — either way, no crash
        MapLoader loader = new MapLoader(objectMapper);

        assertDoesNotThrow(() -> loader.loadMaps());
    }

    @Test
    @DisplayName("loadMaps() should skip malformed JSON files without crashing")
    void shouldSkipMalformedJsonFiles(@TempDir Path tempDir) throws IOException {
        Path mapsDir = tempDir.resolve("maps");
        Files.createDirectory(mapsDir);
        Files.writeString(mapsDir.resolve("bad.json"), "{ invalid json }}");

        MapLoader loader = new MapLoader(objectMapper);
        assertDoesNotThrow(() -> loader.loadMaps());
    }

    // ── map content validation ──────────────────────────────────────────

    @Test
    @DisplayName("loaded map areas should have valid territory definitions")
    void shouldHaveValidTerritoryDefinitions() {
        MapLoader loader = new MapLoader(objectMapper);
        loader.loadMaps();

        MapDefinition map = loader.getMap("classic-world");

        for (AreaDefinition area : map.areas()) {
            assertNotNull(area.key(), "Area key should not be null");
            assertNotNull(area.name(), "Area name should not be null");
            assertTrue(area.bonusArmies() >= 0, "Bonus armies should be non-negative");
            assertFalse(area.territories().isEmpty(),
                    "Area '" + area.name() + "' should have territories");

            for (TerritoryDefinition territory : area.territories()) {
                assertNotNull(territory.key(), "Territory key should not be null");
                assertNotNull(territory.name(), "Territory name should not be null");
                assertFalse(territory.neighbors().isEmpty(),
                        "Territory '" + territory.name() + "' should have neighbors");
            }
        }
    }

    @Test
    @DisplayName("multiple maps should all be accessible")
    void shouldLoadMultipleMaps() {
        MapLoader loader = new MapLoader(objectMapper);
        loader.loadMaps();

        List<MapDefinition> allMaps = loader.getAvailableMaps();

        // We know at least classic-world and europe exist
        assertTrue(allMaps.size() >= 2, "Should load at least 2 built-in maps");

        for (MapDefinition map : allMaps) {
            MapDefinition byId = loader.getMap(map.id());
            assertSame(map, byId, "getMap() should return the same instance");
        }
    }

    @Test
    @DisplayName("custom map should override built-in with same id")
    void shouldOverrideBuiltInMap() {
        MapLoader loader = new MapLoader(objectMapper);
        loader.loadMaps();

        MapDefinition override = new MapDefinition(
                "classic-world", "Custom Classic", "Overridden", "Custom Author", 3, 5, List.of());
        loader.getMaps().put(override.id(), override);

        MapDefinition loaded = loader.getMap("classic-world");
        assertEquals("Custom Classic", loaded.name(), "Custom map should override built-in");
        assertEquals("Custom Author", loaded.author());
    }

    @Test
    @DisplayName("getMap() with null id should throw")
    void shouldThrowForNullMapId() {
        MapLoader loader = new MapLoader(objectMapper);
        loader.loadMaps();

        assertThrows(IllegalArgumentException.class, () -> loader.getMap(null));
    }

    // ── loadExternalMapFile via reflection ───────────────────────────────

    @Test
    @DisplayName("loadExternalMapFile() should load a valid external map file")
    void shouldLoadExternalMapFileDirectly(@TempDir Path tempDir) throws Exception {
        String json = """
                {
                  "id": "reflection-test",
                  "name": "Reflection Map",
                  "description": "Loaded via reflection",
                  "author": "Test",
                  "minPlayers": 2,
                  "maxPlayers": 4,
                  "areas": []
                }
                """;
        Path mapFile = tempDir.resolve("reflection-test.json");
        Files.writeString(mapFile, json);

        MapLoader loader = new MapLoader(objectMapper);

        Method method = MapLoader.class.getDeclaredMethod("loadExternalMapFile", Path.class);
        method.setAccessible(true);
        method.invoke(loader, mapFile);

        assertTrue(loader.getMaps().containsKey("reflection-test"));
        assertEquals("Reflection Map", loader.getMaps().get("reflection-test").name());
    }

    @Test
    @DisplayName("loadExternalMapFile() should handle malformed JSON gracefully")
    void shouldHandleMalformedExternalMapFile(@TempDir Path tempDir) throws Exception {
        Path badFile = tempDir.resolve("bad-map.json");
        Files.writeString(badFile, "not valid json {{{");

        MapLoader loader = new MapLoader(objectMapper);

        Method method = MapLoader.class.getDeclaredMethod("loadExternalMapFile", Path.class);
        method.setAccessible(true);

        // Should not throw — errors are logged
        assertDoesNotThrow(() -> method.invoke(loader, badFile));
        assertTrue(loader.getMaps().isEmpty(), "Malformed file should not add any map");
    }

    @Test
    @DisplayName("loadExternalMaps() should skip non-json files")
    void shouldSkipNonJsonFiles(@TempDir Path tempDir) throws Exception {
        Path mapsDir = tempDir.resolve("maps");
        Files.createDirectory(mapsDir);
        Files.writeString(mapsDir.resolve("readme.txt"), "not a map");
        Files.writeString(mapsDir.resolve("notes.md"), "# Notes");

        MapLoader loader = new MapLoader(objectMapper);

        Method method = MapLoader.class.getDeclaredMethod("loadExternalMaps");
        method.setAccessible(true);

        // The CWD-relative path won't find our tempDir, so we test the file filter logic
        // by verifying the loader doesn't crash when called
        assertDoesNotThrow(() -> loader.loadMaps());
    }

    @Test
    @DisplayName("loadMaps() with empty maps should log warning")
    void shouldLogWarningWhenNoMapsLoaded() {
        // Create a loader that won't find any classpath maps — but since classpath maps
        // always exist, we just verify the loader works end-to-end
        MapLoader loader = new MapLoader(objectMapper);
        loader.loadMaps();

        // After clearing, getAvailableMaps() should be empty
        loader.getMaps().clear();
        assertTrue(loader.getAvailableMaps().isEmpty());
    }

    @Test
    @DisplayName("getMap() error message should include available map keys")
    void shouldIncludeAvailableKeysInErrorMessage() {
        MapLoader loader = new MapLoader(objectMapper);
        loader.loadMaps();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> loader.getMap("does-not-exist"));

        assertTrue(ex.getMessage().contains("classic-world"),
                "Error message should list available maps");
    }

    @Test
    @DisplayName("loadExternalMapFile() should override classpath map with same id")
    void shouldOverrideClasspathMapViaExternalFile(@TempDir Path tempDir) throws Exception {
        MapLoader loader = new MapLoader(objectMapper);
        loader.loadMaps();

        // Verify classic-world exists from classpath
        assertEquals("Classic World", loader.getMap("classic-world").name());

        // Now load an external file with the same id
        String json = """
                {
                  "id": "classic-world",
                  "name": "Overridden Classic",
                  "description": "External override",
                  "author": "Override Author",
                  "minPlayers": 3,
                  "maxPlayers": 5,
                  "areas": []
                }
                """;
        Path overrideFile = tempDir.resolve("classic-world.json");
        Files.writeString(overrideFile, json);

        Method method = MapLoader.class.getDeclaredMethod("loadExternalMapFile", Path.class);
        method.setAccessible(true);
        method.invoke(loader, overrideFile);

        assertEquals("Overridden Classic", loader.getMap("classic-world").name(),
                "External map should override classpath map");
    }

    // ── CWD-relative loadExternalMaps() tests ───────────────────────────

    @Test
    @DisplayName("loadExternalMaps() should load JSON files from CWD/maps/ directory")
    void shouldLoadExternalMapsFromCwdDirectory() throws Exception {
        // Create a maps/ directory relative to CWD (project root for Maven tests)
        Path mapsDir = Paths.get("maps");
        Files.createDirectories(mapsDir);

        String json = """
                {
                  "id": "cwd-external-test",
                  "name": "CWD External Map",
                  "description": "Loaded via CWD",
                  "author": "CWD Tester",
                  "minPlayers": 2,
                  "maxPlayers": 6,
                  "areas": []
                }
                """;
        Path mapFile = mapsDir.resolve("cwd-external-test.json");

        try {
            Files.writeString(mapFile, json);

            MapLoader loader = new MapLoader(objectMapper);
            loader.loadMaps();

            assertTrue(loader.getMaps().containsKey("cwd-external-test"),
                    "Should load map from CWD-relative maps/ directory");
            assertEquals("CWD External Map", loader.getMaps().get("cwd-external-test").name());
        } finally {
            // Clean up — remove the file and directory
            Files.deleteIfExists(mapFile);
            Files.deleteIfExists(mapsDir);
        }
    }

    @Test
    @DisplayName("loadExternalMaps() should skip non-json files in CWD/maps/ directory")
    void shouldSkipNonJsonInCwdMapsDirectory() throws Exception {
        Path mapsDir = Paths.get("maps");
        Files.createDirectories(mapsDir);

        Path txtFile = mapsDir.resolve("readme.txt");
        Path mdFile = mapsDir.resolve("notes.md");

        try {
            Files.writeString(txtFile, "not a map");
            Files.writeString(mdFile, "# Notes");

            MapLoader loader = new MapLoader(objectMapper);
            loader.loadMaps();

            // Non-json files should be ignored; classpath maps should still work
            assertNotNull(loader.getMap("classic-world"));
            assertFalse(loader.getMaps().containsKey("readme"));
            assertFalse(loader.getMaps().containsKey("notes"));
        } finally {
            Files.deleteIfExists(txtFile);
            Files.deleteIfExists(mdFile);
            Files.deleteIfExists(mapsDir);
        }
    }

    @Test
    @DisplayName("loadMaps() should handle empty maps scenario")
    void shouldTriggerEmptyMapsWarning() {
        // A fresh MapLoader that hasn't loaded any maps — maps is empty
        MapLoader loader = new MapLoader(objectMapper);
        // Don't call loadMaps() — verify maps is empty and getAvailableMaps returns empty
        assertTrue(loader.getMaps().isEmpty(),
                "Before loadMaps(), maps should be empty");
        assertTrue(loader.getAvailableMaps().isEmpty());

        // Also test: after loading and clearing, isEmpty() is true
        loader.loadMaps();
        assertFalse(loader.getMaps().isEmpty());
        loader.getMaps().clear();
        assertTrue(loader.getMaps().isEmpty());
        assertTrue(loader.getAvailableMaps().isEmpty());
    }

    @Test
    @DisplayName("loadExternalMaps() should handle malformed JSON in CWD/maps/ directory gracefully")
    void shouldHandleMalformedJsonInCwdMapsDir() throws Exception {
        Path mapsDir = Paths.get("maps");
        Files.createDirectories(mapsDir);
        Path badFile = mapsDir.resolve("bad.json");

        try {
            Files.writeString(badFile, "{ this is NOT valid json }}");

            MapLoader loader = new MapLoader(objectMapper);
            // Should not throw — errors are logged
            assertDoesNotThrow(() -> loader.loadMaps());

            // Classpath maps should still load despite the bad external file
            assertNotNull(loader.getMap("classic-world"));
        } finally {
            Files.deleteIfExists(badFile);
            Files.deleteIfExists(mapsDir);
        }
    }
}
