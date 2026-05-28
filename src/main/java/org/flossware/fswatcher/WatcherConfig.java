package org.flossware.fswatcher;

import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Configuration for filesystem deployment watcher.
 * Specifies which directory to watch, file extensions to monitor,
 * and automatic deployment behavior.
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * WatcherConfig config = WatcherConfig.builder()
 *     .watchDirectory(Paths.get("/var/jplatform/apps"))
 *     .autoStart(true)
 *     .autoDeploy(true)
 *     .addFileExtension("yaml")
 *     .addFileExtension("json")
 *     .debounceMillis(500)
 *     .build();
 * }</pre>
 *
 * @see DeploymentWatcher
 */
public class WatcherConfig {
    private final Path watchDirectory;
    private final boolean autoStart;
    private final boolean autoDeploy;
    private final Set<String> fileExtensions;
    private final long debounceMillis;

    private WatcherConfig(Builder builder) {
        this.watchDirectory = builder.watchDirectory;
        this.autoStart = builder.autoStart;
        this.autoDeploy = builder.autoDeploy;
        this.fileExtensions = builder.fileExtensions != null ?
                Set.copyOf(builder.fileExtensions) : Set.of("yaml", "json");
        this.debounceMillis = builder.debounceMillis;
    }

    /**
     * Returns the directory to watch for descriptor files.
     *
     * @return the watch directory path
     */
    public Path getWatchDirectory() {
        return watchDirectory;
    }

    /**
     * Checks if applications should be automatically started after deployment.
     *
     * @return true if auto-start is enabled
     */
    public boolean isAutoStart() {
        return autoStart;
    }

    /**
     * Checks if detected descriptor files should be automatically deployed.
     *
     * @return true if auto-deploy is enabled
     */
    public boolean isAutoDeploy() {
        return autoDeploy;
    }

    /**
     * Returns the file extensions to monitor (without leading dot).
     *
     * @return an unmodifiable set of file extensions
     */
    public Set<String> getFileExtensions() {
        return Collections.unmodifiableSet(fileExtensions);
    }

    /**
     * Returns the debounce delay in milliseconds.
     * File changes are debounced to avoid processing rapid modifications.
     *
     * @return the debounce delay in milliseconds
     */
    public long getDebounceMillis() {
        return debounceMillis;
    }

    /**
     * Creates a new builder for constructing watcher configurations.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for WatcherConfig.
     */
    public static class Builder {
        private Path watchDirectory;
        private boolean autoStart = true;
        private boolean autoDeploy = true;
        private Set<String> fileExtensions = new HashSet<>(Set.of("yaml", "json"));
        private long debounceMillis = 500;

        /**
         * Sets the directory to watch for descriptor files.
         *
         * @param watchDirectory the directory to watch
         * @return this builder
         */
        public Builder watchDirectory(Path watchDirectory) {
            this.watchDirectory = watchDirectory;
            return this;
        }

        /**
         * Sets whether to automatically start applications after deployment.
         *
         * @param autoStart true to enable auto-start
         * @return this builder
         */
        public Builder autoStart(boolean autoStart) {
            this.autoStart = autoStart;
            return this;
        }

        /**
         * Sets whether to automatically deploy detected descriptor files.
         *
         * @param autoDeploy true to enable auto-deploy
         * @return this builder
         */
        public Builder autoDeploy(boolean autoDeploy) {
            this.autoDeploy = autoDeploy;
            return this;
        }

        /**
         * Adds a file extension to monitor (without leading dot).
         *
         * @param extension the file extension (e.g., "yaml", "json")
         * @return this builder
         */
        public Builder addFileExtension(String extension) {
            if (this.fileExtensions == null) {
                this.fileExtensions = new HashSet<>();
            }
            this.fileExtensions.add(extension.toLowerCase());
            return this;
        }

        /**
         * Sets all file extensions to monitor, replacing any previously added.
         *
         * @param extensions the file extensions to monitor
         * @return this builder
         */
        public Builder fileExtensions(Set<String> extensions) {
            this.fileExtensions = new HashSet<>(extensions);
            return this;
        }

        /**
         * Sets the debounce delay for file changes.
         *
         * @param debounceMillis the delay in milliseconds
         * @return this builder
         */
        public Builder debounceMillis(long debounceMillis) {
            this.debounceMillis = debounceMillis;
            return this;
        }

        /**
         * Builds the WatcherConfig instance.
         *
         * @return a new WatcherConfig
         */
        public WatcherConfig build() {
            return new WatcherConfig(this);
        }
    }
}
