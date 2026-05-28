package org.flossware.fswatcher;

import org.flossware.fswatcher.DeploymentEventListener;
import org.flossware.fswatcher.DeploymentWatcher;
import org.flossware.fswatcher.WatcherConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;

import static java.nio.file.StandardWatchEventKinds.*;

/**
 * Filesystem-based implementation of DeploymentWatcher that monitors a directory
 * for application descriptor files and triggers deployment events.
 *
 * <p>This implementation uses Java NIO's WatchService to efficiently monitor
 * filesystem changes. It includes the following features:</p>
 * <ul>
 *   <li>Thread-safe listener management using CopyOnWriteArrayList</li>
 *   <li>Debouncing of file changes to avoid processing rapid modifications</li>
 *   <li>File extension filtering based on WatcherConfig</li>
 *   <li>Dedicated thread for watching via ExecutorService</li>
 *   <li>Proper lifecycle management (start/stop/close)</li>
 * </ul>
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
 *
 * DeploymentWatcher watcher = new FileSystemDeploymentWatcher(config);
 * watcher.addListener(new AutoDeploymentHandler(applicationManager, parsers));
 * watcher.start();
 *
 * // Later...
 * watcher.stop();
 * watcher.close();
 * }</pre>
 *
 * <p>File events are debounced to prevent duplicate processing when files are
 * modified multiple times in quick succession (e.g., during a text editor save).
 * The debounce delay is configurable via WatcherConfig.</p>
 *
 * @see DeploymentWatcher
 * @see WatcherConfig
 * @see DeploymentEventListener
 */
public class FileSystemDeploymentWatcher implements DeploymentWatcher {

    private static final Logger logger = LoggerFactory.getLogger(FileSystemDeploymentWatcher.class);

    private final WatcherConfig config;
    private final CopyOnWriteArrayList<DeploymentEventListener> listeners;
    private final Map<Path, ScheduledFuture<?>> pendingEvents;

    private WatchService watchService;
    private ExecutorService watcherExecutor;
    private ScheduledExecutorService debounceScheduler;
    private volatile boolean running;

    /**
     * Creates a new filesystem deployment watcher with the given configuration.
     *
     * @param config the watcher configuration
     * @throws NullPointerException if config is null
     */
    public FileSystemDeploymentWatcher(WatcherConfig config) {
        if (config == null) {
            throw new NullPointerException("config cannot be null");
        }
        this.config = config;
        this.listeners = new CopyOnWriteArrayList<>();
        this.pendingEvents = new ConcurrentHashMap<>();
        this.running = false;
    }

    @Override
    public void start() throws Exception {
        if (running) {
            logger.warn("Watcher is already running");
            return;
        }

        Path watchDir = config.getWatchDirectory();
        if (watchDir == null) {
            throw new IllegalStateException("Watch directory is not configured");
        }

        if (!Files.exists(watchDir)) {
            throw new IllegalStateException("Watch directory does not exist: " + watchDir);
        }

        if (!Files.isDirectory(watchDir)) {
            throw new IllegalStateException("Watch path is not a directory: " + watchDir);
        }

        logger.info("Starting filesystem watcher on directory: {}", watchDir);

        try {
            // Create watch service
            watchService = FileSystems.getDefault().newWatchService();

            // Register directory
            watchDir.register(
                watchService,
                ENTRY_CREATE,
                ENTRY_MODIFY,
                ENTRY_DELETE
            );

            // Create thread pools
            watcherExecutor = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "fs-watcher");
                t.setDaemon(true);
                return t;
            });

            debounceScheduler = Executors.newScheduledThreadPool(2, r -> {
                Thread t = new Thread(r, "fs-watcher-debounce");
                t.setDaemon(true);
                return t;
            });

            // Start watching
            running = true;
            watcherExecutor.submit(this::watchLoop);

            logger.info("Filesystem watcher started successfully");

            // Scan existing files
            scanExistingFiles(watchDir);

        } catch (Exception e) {
            logger.error("Failed to start filesystem watcher", e);
            cleanup();
            throw e;
        }
    }

    @Override
    public void stop() throws Exception {
        if (!running) {
            logger.warn("Watcher is not running");
            return;
        }

        logger.info("Stopping filesystem watcher");
        running = false;

        cleanup();

        logger.info("Filesystem watcher stopped");
    }

    @Override
    public void close() throws Exception {
        stop();
    }

    @Override
    public void addListener(DeploymentEventListener listener) {
        if (listener == null) {
            throw new NullPointerException("listener cannot be null");
        }
        listeners.add(listener);
        logger.debug("Added deployment listener: {}", listener.getClass().getName());
    }

    @Override
    public void removeListener(DeploymentEventListener listener) {
        if (listener == null) {
            throw new NullPointerException("listener cannot be null");
        }
        listeners.remove(listener);
        logger.debug("Removed deployment listener: {}", listener.getClass().getName());
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    /**
     * Main watch loop that processes filesystem events.
     */
    private void watchLoop() {
        logger.debug("Watch loop started");

        while (running) {
            WatchKey key;
            try {
                // Wait for events (blocks until event or interrupt)
                key = watchService.poll(1, TimeUnit.SECONDS);
                if (key == null) {
                    continue;
                }
            } catch (InterruptedException e) {
                logger.debug("Watch loop interrupted");
                Thread.currentThread().interrupt();
                break;
            } catch (ClosedWatchServiceException e) {
                logger.debug("Watch service closed");
                break;
            }

            // Process events
            for (WatchEvent<?> event : key.pollEvents()) {
                WatchEvent.Kind<?> kind = event.kind();

                // Handle overflow
                if (kind == StandardWatchEventKinds.OVERFLOW) {
                    logger.warn("Watch event overflow - some events may have been lost");
                    continue;
                }

                // Get the filename
                @SuppressWarnings("unchecked")
                WatchEvent<Path> pathEvent = (WatchEvent<Path>) event;
                Path filename = pathEvent.context();
                Path fullPath = config.getWatchDirectory().resolve(filename);

                // Filter by extension
                if (!matchesExtension(filename)) {
                    logger.trace("Ignoring file (extension mismatch): {}", filename);
                    continue;
                }

                logger.debug("Detected {} event for: {}", kind.name(), filename);

                // Schedule debounced event
                if (kind == ENTRY_CREATE) {
                    scheduleEvent(fullPath, () -> notifyDescriptorDetected(fullPath));
                } else if (kind == ENTRY_MODIFY) {
                    scheduleEvent(fullPath, () -> notifyDescriptorModified(fullPath));
                } else if (kind == ENTRY_DELETE) {
                    scheduleEvent(fullPath, () -> notifyDescriptorRemoved(fullPath));
                }
            }

            // Reset the key
            boolean valid = key.reset();
            if (!valid) {
                logger.error("Watch key is no longer valid - stopping watcher");
                running = false;
                break;
            }
        }

        logger.debug("Watch loop exited");
    }

    /**
     * Schedules a debounced event for a file.
     * If a pending event exists for the file, it is cancelled and replaced.
     *
     * @param file the file path
     * @param action the action to execute after the debounce delay
     */
    private void scheduleEvent(Path file, Runnable action) {
        // Cancel existing pending event
        ScheduledFuture<?> existing = pendingEvents.remove(file);
        if (existing != null) {
            existing.cancel(false);
            logger.trace("Cancelled pending event for: {}", file);
        }

        // Schedule new event
        long debounceMillis = config.getDebounceMillis();
        ScheduledFuture<?> future = debounceScheduler.schedule(() -> {
            pendingEvents.remove(file);
            action.run();
        }, debounceMillis, TimeUnit.MILLISECONDS);

        pendingEvents.put(file, future);
        logger.trace("Scheduled debounced event for: {} (delay: {}ms)", file, debounceMillis);
    }

    /**
     * Scans the watch directory for existing files and notifies listeners.
     *
     * @param watchDir the directory to scan
     */
    private void scanExistingFiles(Path watchDir) {
        logger.debug("Scanning for existing descriptor files");

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(watchDir)) {
            for (Path file : stream) {
                if (Files.isRegularFile(file) && matchesExtension(file.getFileName())) {
                    logger.debug("Found existing descriptor: {}", file);
                    scheduleEvent(file, () -> notifyDescriptorDetected(file));
                }
            }
        } catch (IOException e) {
            logger.error("Failed to scan existing files", e);
        }
    }

    /**
     * Checks if a filename matches the configured file extensions.
     *
     * @param filename the filename to check
     * @return true if the file extension matches, false otherwise
     */
    private boolean matchesExtension(Path filename) {
        String name = filename.toString().toLowerCase();
        Set<String> extensions = config.getFileExtensions();

        for (String ext : extensions) {
            if (name.endsWith("." + ext.toLowerCase())) {
                return true;
            }
        }

        return false;
    }

    /**
     * Notifies all listeners that a descriptor was detected.
     *
     * @param descriptorFile the descriptor file path
     */
    private void notifyDescriptorDetected(Path descriptorFile) {
        logger.debug("Notifying listeners: descriptor detected - {}", descriptorFile);
        for (DeploymentEventListener listener : listeners) {
            try {
                listener.onDescriptorDetected(descriptorFile);
            } catch (Exception e) {
                logger.error("Listener threw exception on descriptor detected", e);
                notifyError(descriptorFile, e, listener);
            }
        }
    }

    /**
     * Notifies all listeners that a descriptor was modified.
     *
     * @param descriptorFile the descriptor file path
     */
    private void notifyDescriptorModified(Path descriptorFile) {
        logger.debug("Notifying listeners: descriptor modified - {}", descriptorFile);
        for (DeploymentEventListener listener : listeners) {
            try {
                listener.onDescriptorModified(descriptorFile);
            } catch (Exception e) {
                logger.error("Listener threw exception on descriptor modified", e);
                notifyError(descriptorFile, e, listener);
            }
        }
    }

    /**
     * Notifies all listeners that a descriptor was removed.
     *
     * @param descriptorFile the descriptor file path
     */
    private void notifyDescriptorRemoved(Path descriptorFile) {
        logger.debug("Notifying listeners: descriptor removed - {}", descriptorFile);
        for (DeploymentEventListener listener : listeners) {
            try {
                listener.onDescriptorRemoved(descriptorFile);
            } catch (Exception e) {
                logger.error("Listener threw exception on descriptor removed", e);
                notifyError(descriptorFile, e, listener);
            }
        }
    }

    /**
     * Notifies a specific listener about an error.
     *
     * @param file the file that caused the error
     * @param error the error that occurred
     * @param listener the listener to notify
     */
    private void notifyError(Path file, Exception error, DeploymentEventListener listener) {
        try {
            listener.onError(file, error);
        } catch (Exception e) {
            logger.error("Listener threw exception in onError handler", e);
        }
    }

    /**
     * Cleans up resources.
     */
    private void cleanup() {
        // Cancel all pending events
        for (ScheduledFuture<?> future : pendingEvents.values()) {
            future.cancel(false);
        }
        pendingEvents.clear();

        // Shutdown thread pools
        if (debounceScheduler != null) {
            debounceScheduler.shutdownNow();
            try {
                debounceScheduler.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        if (watcherExecutor != null) {
            watcherExecutor.shutdownNow();
            try {
                watcherExecutor.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // Close watch service
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException e) {
                logger.error("Error closing watch service", e);
            }
        }
    }
}
