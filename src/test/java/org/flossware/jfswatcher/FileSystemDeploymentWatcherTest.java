package org.flossware.jfswatcher;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class FileSystemDeploymentWatcherTest {

    @TempDir
    Path tempDir;

    private WatcherConfig config;
    private FileSystemDeploymentWatcher watcher;

    @BeforeEach
    void setUp() {
        config = WatcherConfig.builder()
                .watchDirectory(tempDir)
                .autoDeploy(true)
                .autoStart(true)
                .addFileExtension("yaml")
                .addFileExtension("json")
                .debounceMillis(100)
                .build();

        watcher = new FileSystemDeploymentWatcher(config);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (watcher != null && watcher.isRunning()) {
            watcher.stop();
        }
    }

    @Test
    void testConstructorNullConfig() {
        assertThrows(NullPointerException.class, () -> {
            new FileSystemDeploymentWatcher(null);
        });
    }

    @Test
    void testConstructor() {
        assertNotNull(watcher);
        assertFalse(watcher.isRunning());
    }

    @Test
    void testStartSuccess() throws Exception {
        watcher.start();
        assertTrue(watcher.isRunning());
    }

    @Test
    void testStartAlreadyRunning() throws Exception {
        watcher.start();
        assertTrue(watcher.isRunning());

        watcher.start();
        assertTrue(watcher.isRunning());
    }

    @Test
    void testStartNullWatchDirectory() {
        WatcherConfig badConfig = WatcherConfig.builder()
                .watchDirectory(null)
                .build();

        FileSystemDeploymentWatcher badWatcher = new FileSystemDeploymentWatcher(badConfig);

        Exception exception = assertThrows(IllegalStateException.class, () -> {
            badWatcher.start();
        });

        assertTrue(exception.getMessage().contains("Watch directory is not configured"));
        assertFalse(badWatcher.isRunning());
    }

    @Test
    void testStartNonExistentDirectory() throws Exception {
        Path nonExistent = tempDir.resolve("does-not-exist");

        WatcherConfig badConfig = WatcherConfig.builder()
                .watchDirectory(nonExistent)
                .build();

        FileSystemDeploymentWatcher badWatcher = new FileSystemDeploymentWatcher(badConfig);

        Exception exception = assertThrows(IllegalStateException.class, () -> {
            badWatcher.start();
        });

        assertTrue(exception.getMessage().contains("does not exist"));
        assertFalse(badWatcher.isRunning());
    }

    @Test
    void testStartFileInsteadOfDirectory() throws Exception {
        Path file = Files.createFile(tempDir.resolve("file.txt"));

        WatcherConfig badConfig = WatcherConfig.builder()
                .watchDirectory(file)
                .build();

        FileSystemDeploymentWatcher badWatcher = new FileSystemDeploymentWatcher(badConfig);

        Exception exception = assertThrows(IllegalStateException.class, () -> {
            badWatcher.start();
        });

        assertTrue(exception.getMessage().contains("not a directory"));
        assertFalse(badWatcher.isRunning());
    }

    @Test
    void testStop() throws Exception {
        watcher.start();
        assertTrue(watcher.isRunning());

        watcher.stop();
        assertFalse(watcher.isRunning());
    }

    @Test
    void testStopWhenNotRunning() throws Exception {
        assertFalse(watcher.isRunning());
        watcher.stop();
        assertFalse(watcher.isRunning());
    }

    @Test
    void testClose() throws Exception {
        watcher.start();
        assertTrue(watcher.isRunning());

        watcher.close();
        assertFalse(watcher.isRunning());
    }

    @Test
    void testAddListener() {
        TestListener listener = new TestListener();
        watcher.addListener(listener);
    }

    @Test
    void testAddNullListener() {
        assertThrows(NullPointerException.class, () -> {
            watcher.addListener(null);
        });
    }

    @Test
    void testRemoveListener() {
        TestListener listener = new TestListener();
        watcher.addListener(listener);
        watcher.removeListener(listener);
    }

    @Test
    void testRemoveNullListener() {
        assertThrows(NullPointerException.class, () -> {
            watcher.removeListener(null);
        });
    }

    @Test
    void testDetectNewFile() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Path> detectedFile = new AtomicReference<>();

        TestListener listener = new TestListener() {
            @Override
            public void onDescriptorDetected(Path descriptorFile) {
                detectedFile.set(descriptorFile);
                latch.countDown();
            }

            @Override
            public void onDescriptorModified(Path descriptorFile) {
                detectedFile.set(descriptorFile);
                latch.countDown();
            }
        };

        watcher.addListener(listener);
        watcher.start();

        Path newFile = tempDir.resolve("app.yaml");
        Files.writeString(newFile, "test: data");

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertNotNull(detectedFile.get());
        assertTrue(detectedFile.get().toString().contains("app.yaml"));
    }

    @Test
    void testDetectModifiedFile() throws Exception {
        Path file = tempDir.resolve("app.yaml");
        Files.writeString(file, "initial");

        CountDownLatch detectLatch = new CountDownLatch(1);
        CountDownLatch modifyLatch = new CountDownLatch(1);

        TestListener listener = new TestListener() {
            @Override
            public void onDescriptorDetected(Path descriptorFile) {
                detectLatch.countDown();
            }

            @Override
            public void onDescriptorModified(Path descriptorFile) {
                modifyLatch.countDown();
            }
        };

        watcher.addListener(listener);
        watcher.start();

        assertTrue(detectLatch.await(5, TimeUnit.SECONDS));

        Files.writeString(file, "modified");

        assertTrue(modifyLatch.await(5, TimeUnit.SECONDS));
    }

    @Test
    void testDetectRemovedFile() throws Exception {
        Path file = tempDir.resolve("app.yaml");
        Files.writeString(file, "test");

        CountDownLatch detectLatch = new CountDownLatch(1);
        CountDownLatch removeLatch = new CountDownLatch(1);

        TestListener listener = new TestListener() {
            @Override
            public void onDescriptorDetected(Path descriptorFile) {
                detectLatch.countDown();
            }

            @Override
            public void onDescriptorRemoved(Path descriptorFile) {
                removeLatch.countDown();
            }
        };

        watcher.addListener(listener);
        watcher.start();

        assertTrue(detectLatch.await(5, TimeUnit.SECONDS));

        Files.delete(file);

        assertTrue(removeLatch.await(5, TimeUnit.SECONDS));
    }

    @Test
    void testFileExtensionFiltering() throws Exception {
        CountDownLatch yamlLatch = new CountDownLatch(1);
        AtomicInteger txtCount = new AtomicInteger(0);

        TestListener listener = new TestListener() {
            private void handleEvent(Path descriptorFile) {
                if (descriptorFile.toString().endsWith(".yaml")) {
                    yamlLatch.countDown();
                } else if (descriptorFile.toString().endsWith(".txt")) {
                    txtCount.incrementAndGet();
                }
            }

            @Override
            public void onDescriptorDetected(Path descriptorFile) {
                handleEvent(descriptorFile);
            }

            @Override
            public void onDescriptorModified(Path descriptorFile) {
                handleEvent(descriptorFile);
            }
        };

        watcher.addListener(listener);
        watcher.start();

        Files.writeString(tempDir.resolve("app.yaml"), "yaml");
        Files.writeString(tempDir.resolve("readme.txt"), "text");

        assertTrue(yamlLatch.await(5, TimeUnit.SECONDS));
        Thread.sleep(500);
        assertEquals(0, txtCount.get());
    }

    @Test
    void testDebouncing() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger modifyCount = new AtomicInteger(0);

        TestListener listener = new TestListener() {
            @Override
            public void onDescriptorModified(Path descriptorFile) {
                modifyCount.incrementAndGet();
                latch.countDown();
            }

            @Override
            public void onDescriptorDetected(Path descriptorFile) {
            }
        };

        Path file = tempDir.resolve("app.yaml");
        Files.writeString(file, "initial");

        watcher.addListener(listener);
        watcher.start();

        Thread.sleep(500);

        for (int i = 0; i < 5; i++) {
            Files.writeString(file, "update-" + i);
            Thread.sleep(20);
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        Thread.sleep(500);

        assertTrue(modifyCount.get() < 5, "Expected debouncing to reduce events, got: " + modifyCount.get());
    }

    @Test
    void testMultipleListeners() throws Exception {
        CountDownLatch latch1 = new CountDownLatch(1);
        CountDownLatch latch2 = new CountDownLatch(1);

        TestListener listener1 = new TestListener() {
            @Override
            public void onDescriptorDetected(Path descriptorFile) {
                latch1.countDown();
            }

            @Override
            public void onDescriptorModified(Path descriptorFile) {
                latch1.countDown();
            }
        };

        TestListener listener2 = new TestListener() {
            @Override
            public void onDescriptorDetected(Path descriptorFile) {
                latch2.countDown();
            }

            @Override
            public void onDescriptorModified(Path descriptorFile) {
                latch2.countDown();
            }
        };

        watcher.addListener(listener1);
        watcher.addListener(listener2);
        watcher.start();

        Files.writeString(tempDir.resolve("app.yaml"), "test");

        assertTrue(latch1.await(5, TimeUnit.SECONDS));
        assertTrue(latch2.await(5, TimeUnit.SECONDS));
    }

    @Test
    void testListenerException() throws Exception {
        CountDownLatch errorLatch = new CountDownLatch(1);
        CountDownLatch goodLatch = new CountDownLatch(1);

        TestListener badListener = new TestListener() {
            private void throwException() {
                throw new RuntimeException("Test exception");
            }

            @Override
            public void onDescriptorDetected(Path descriptorFile) {
                throwException();
            }

            @Override
            public void onDescriptorModified(Path descriptorFile) {
                throwException();
            }

            @Override
            public void onError(Path file, Exception error) {
                errorLatch.countDown();
            }
        };

        TestListener goodListener = new TestListener() {
            @Override
            public void onDescriptorDetected(Path descriptorFile) {
                goodLatch.countDown();
            }

            @Override
            public void onDescriptorModified(Path descriptorFile) {
                goodLatch.countDown();
            }
        };

        watcher.addListener(badListener);
        watcher.addListener(goodListener);
        watcher.start();

        Files.writeString(tempDir.resolve("app.yaml"), "test");

        assertTrue(errorLatch.await(5, TimeUnit.SECONDS));
        assertTrue(goodLatch.await(5, TimeUnit.SECONDS));
    }

    private static class TestListener implements DeploymentEventListener {
        @Override
        public void onDescriptorDetected(Path descriptorFile) {
        }

        @Override
        public void onDescriptorModified(Path descriptorFile) {
        }

        @Override
        public void onDescriptorRemoved(Path descriptorFile) {
        }

        @Override
        public void onError(Path file, Exception error) {
        }
    }
}
