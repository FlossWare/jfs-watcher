package org.flossware.fswatcher;

import java.nio.file.Path;

/**
 * Listener for deployment-related filesystem events.
 * Implementations are notified when application descriptor files are detected,
 * modified, or removed from the watched directory.
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * DeploymentEventListener listener = new DeploymentEventListener() {
 *     @Override
 *     public void onDescriptorDetected(Path descriptorFile) {
 *         System.out.println("New app detected: " + descriptorFile);
 *         // Parse and deploy application
 *     }
 *
 *     @Override
 *     public void onDescriptorRemoved(Path descriptorFile) {
 *         System.out.println("App removed: " + descriptorFile);
 *         // Undeploy application
 *     }
 * };
 * }</pre>
 *
 * @see DeploymentWatcher
 */
public interface DeploymentEventListener {

    /**
     * Called when a new descriptor file is detected in the watched directory.
     *
     * @param descriptorFile the path to the descriptor file
     */
    void onDescriptorDetected(Path descriptorFile);

    /**
     * Called when an existing descriptor file is modified.
     *
     * @param descriptorFile the path to the modified descriptor file
     */
    void onDescriptorModified(Path descriptorFile);

    /**
     * Called when a descriptor file is removed from the watched directory.
     *
     * @param descriptorFile the path to the removed descriptor file
     */
    void onDescriptorRemoved(Path descriptorFile);

    /**
     * Called when an error occurs while processing a descriptor file.
     *
     * @param file the file that caused the error
     * @param error the error that occurred
     */
    void onError(Path file, Exception error);
}
