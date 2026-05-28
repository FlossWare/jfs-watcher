package org.flossware.fswatcher;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Edge case tests for WatcherConfig to achieve 100% coverage.
 */
class WatcherConfigEdgeCasesTest {

    @Test
    @DisplayName("Should use default file extensions when builder extensions is null")
    void testDefaultFileExtensionsWhenNull() throws Exception {
        WatcherConfig.Builder builder = WatcherConfig.builder()
                .watchDirectory(Paths.get("/test"));

        // Set fileExtensions to null via reflection
        java.lang.reflect.Field extensionsField = WatcherConfig.Builder.class.getDeclaredField("fileExtensions");
        extensionsField.setAccessible(true);
        extensionsField.set(builder, null);

        WatcherConfig config = builder.build();

        // Should use default extensions
        assertTrue(config.getFileExtensions().contains("yaml"));
        assertTrue(config.getFileExtensions().contains("json"));
        assertEquals(2, config.getFileExtensions().size());
    }

    @Test
    @DisplayName("Should handle case-insensitive file extension")
    void testCaseInsensitiveFileExtension() {
        WatcherConfig config = WatcherConfig.builder()
                .watchDirectory(Paths.get("/test"))
                .addFileExtension("YAML")
                .addFileExtension("Json")
                .build();

        // Extensions should be normalized to lowercase
        assertTrue(config.getFileExtensions().contains("yaml"));
        assertTrue(config.getFileExtensions().contains("json"));
    }

    @Test
    @DisplayName("Should create new HashSet when fileExtensions is null in addFileExtension")
    void testAddFileExtensionWhenNull() throws Exception {
        WatcherConfig.Builder builder = WatcherConfig.builder()
                .watchDirectory(Paths.get("/test"));

        // Set fileExtensions to null
        java.lang.reflect.Field extensionsField = WatcherConfig.Builder.class.getDeclaredField("fileExtensions");
        extensionsField.setAccessible(true);
        extensionsField.set(builder, null);

        // Now call addFileExtension - should create new HashSet
        builder.addFileExtension("xml");

        WatcherConfig config = builder.build();

        assertTrue(config.getFileExtensions().contains("xml"));
    }

    @Test
    @DisplayName("Should replace extensions with fileExtensions setter")
    void testFileExtensionsSetter() {
        Set<String> customExtensions = new HashSet<>();
        customExtensions.add("yml");
        customExtensions.add("conf");

        WatcherConfig config = WatcherConfig.builder()
                .watchDirectory(Paths.get("/test"))
                .addFileExtension("yaml") // This should be replaced
                .fileExtensions(customExtensions)
                .build();

        // Should only have the custom extensions
        assertEquals(2, config.getFileExtensions().size());
        assertTrue(config.getFileExtensions().contains("yml"));
        assertTrue(config.getFileExtensions().contains("conf"));
        assertFalse(config.getFileExtensions().contains("yaml"));
    }

    @Test
    @DisplayName("Should return unmodifiable set of file extensions")
    void testUnmodifiableFileExtensions() {
        WatcherConfig config = WatcherConfig.builder()
                .watchDirectory(Paths.get("/test"))
                .build();

        Set<String> extensions = config.getFileExtensions();

        assertThrows(UnsupportedOperationException.class, () -> {
            extensions.add("invalid");
        });
    }

    @Test
    @DisplayName("Should build with all custom settings")
    void testFullyCustomConfig() {
        Path customPath = Paths.get("/custom/path");
        Set<String> customExtensions = Set.of("yml", "conf", "properties");

        WatcherConfig config = WatcherConfig.builder()
                .watchDirectory(customPath)
                .autoStart(false)
                .autoDeploy(false)
                .fileExtensions(customExtensions)
                .debounceMillis(1000)
                .build();

        assertEquals(customPath, config.getWatchDirectory());
        assertFalse(config.isAutoStart());
        assertFalse(config.isAutoDeploy());
        assertEquals(3, config.getFileExtensions().size());
        assertTrue(config.getFileExtensions().contains("yml"));
        assertEquals(1000, config.getDebounceMillis());
    }

    @Test
    @DisplayName("Should use defaults when only watch directory is set")
    void testDefaultsExceptWatchDirectory() {
        WatcherConfig config = WatcherConfig.builder()
                .watchDirectory(Paths.get("/test"))
                .build();

        assertTrue(config.isAutoStart());
        assertTrue(config.isAutoDeploy());
        assertEquals(2, config.getFileExtensions().size());
        assertEquals(500, config.getDebounceMillis());
    }

    @Test
    @DisplayName("Should allow multiple calls to addFileExtension")
    void testMultipleAddFileExtension() {
        WatcherConfig config = WatcherConfig.builder()
                .watchDirectory(Paths.get("/test"))
                .addFileExtension("xml")
                .addFileExtension("properties")
                .addFileExtension("conf")
                .build();

        // Should have defaults plus added extensions
        assertTrue(config.getFileExtensions().contains("yaml"));
        assertTrue(config.getFileExtensions().contains("json"));
        assertTrue(config.getFileExtensions().contains("xml"));
        assertTrue(config.getFileExtensions().contains("properties"));
        assertTrue(config.getFileExtensions().contains("conf"));
        assertEquals(5, config.getFileExtensions().size());
    }

    @Test
    @DisplayName("Should support builder chaining")
    void testBuilderChaining() {
        WatcherConfig.Builder builder = WatcherConfig.builder();

        assertSame(builder, builder.watchDirectory(Paths.get("/test")));
        assertSame(builder, builder.autoStart(false));
        assertSame(builder, builder.autoDeploy(false));
        assertSame(builder, builder.addFileExtension("xml"));
        assertSame(builder, builder.fileExtensions(Set.of("yml")));
        assertSame(builder, builder.debounceMillis(1000));
    }

    @Test
    @DisplayName("Should handle empty fileExtensions set")
    void testEmptyFileExtensions() {
        WatcherConfig config = WatcherConfig.builder()
                .watchDirectory(Paths.get("/test"))
                .fileExtensions(Set.of())
                .build();

        assertTrue(config.getFileExtensions().isEmpty());
    }

    @Test
    @DisplayName("Should handle zero debounce millis")
    void testZeroDebounceMillis() {
        WatcherConfig config = WatcherConfig.builder()
                .watchDirectory(Paths.get("/test"))
                .debounceMillis(0)
                .build();

        assertEquals(0, config.getDebounceMillis());
    }

    @Test
    @DisplayName("Should handle large debounce millis")
    void testLargeDebounceMillis() {
        long largeValue = Long.MAX_VALUE;
        WatcherConfig config = WatcherConfig.builder()
                .watchDirectory(Paths.get("/test"))
                .debounceMillis(largeValue)
                .build();

        assertEquals(largeValue, config.getDebounceMillis());
    }
}
