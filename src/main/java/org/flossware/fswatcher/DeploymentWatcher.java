package org.flossware.fswatcher;

/**
 * Watches a directory for application descriptor files and triggers deployment events.
 * Implementations monitor filesystem changes and notify registered listeners.
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * WatcherConfig config = WatcherConfig.builder()
 *     .watchDirectory(Paths.get("/var/jplatform/apps"))
 *     .autoStart(true)
 *     .autoDeploy(true)
 *     .addFileExtension("yaml")
 *     .build();
 *
 * DeploymentWatcher watcher = new FileSystemDeploymentWatcher(config);
 * watcher.addListener(new AutoDeploymentHandler(applicationManager));
 * watcher.start();
 * }</pre>
 *
 * @see DeploymentEventListener
 * @see WatcherConfig
 */
public interface DeploymentWatcher extends AutoCloseable {

    /**
     * Starts watching the configured directory for changes.
     *
     * @throws Exception if the watcher cannot be started
     */
    void start() throws Exception;

    /**
     * Stops watching the directory.
     *
     * @throws Exception if the watcher cannot be stopped
     */
    void stop() throws Exception;

    /**
     * Adds a listener to be notified of deployment events.
     *
     * @param listener the listener to add
     */
    void addListener(DeploymentEventListener listener);

    /**
     * Removes a previously registered listener.
     *
     * @param listener the listener to remove
     */
    void removeListener(DeploymentEventListener listener);

    /**
     * Checks if the watcher is currently running.
     *
     * @return true if running, false otherwise
     */
    boolean isRunning();
}
