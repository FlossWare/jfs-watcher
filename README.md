# JFS Watcher

[![Maven Central](https://img.shields.io/maven-central/v/org.flossware/jfs-watcher.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/org.flossware/jfs-watcher)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

Filesystem watcher for monitoring directory changes with debouncing support.

## Features

- **Directory Monitoring**: Watch directories for file changes using Java NIO WatchService
- **Event Debouncing**: Prevent duplicate processing of rapid file modifications
- **File Extension Filtering**: Monitor only specific file types
- **Thread-Safe**: Concurrent listener management with CopyOnWriteArrayList
- **Lifecycle Management**: Start, stop, and close operations
- **AutoCloseable**: Try-with-resources support
- **Event Types**: CREATE, MODIFY, DELETE detection
- **Scan Existing Files**: Optionally process files present at startup

## Installation

### Maven

```xml
<dependency>
    <groupId>org.flossware</groupId>
    <artifactId>jfs-watcher</artifactId>
    <version>1.0</version>
</dependency>
```

### Gradle

```gradle
implementation 'org.flossware:jfs-watcher:1.0'
```

## Quick Start

### Basic Usage

```java
import org.flossware.jfswatcher.*;
import java.nio.file.*;

// Configure watcher
WatcherConfig config = WatcherConfig.builder()
    .watchDirectory(Paths.get("/var/app/configs"))
    .addFileExtension("yaml")
    .addFileExtension("json")
    .debounceMillis(500)
    .build();

// Create watcher
FileSystemDeploymentWatcher watcher = new FileSystemDeploymentWatcher(config);

// Add listener
watcher.addListener(new DeploymentEventListener() {
    @Override
    public void onDescriptorDetected(Path file) {
        System.out.println("New file: " + file);
    }
    
    @Override
    public void onDescriptorModified(Path file) {
        System.out.println("Modified: " + file);
    }
    
    @Override
    public void onDescriptorRemoved(Path file) {
        System.out.println("Removed: " + file);
    }
    
    @Override
    public void onError(Path file, Exception error) {
        System.err.println("Error with " + file + ": " + error.getMessage());
    }
});

// Start watching
watcher.start();

// ... application runs ...

// Stop when done
watcher.stop();
```

### Try-With-Resources

```java
WatcherConfig config = WatcherConfig.builder()
    .watchDirectory(Paths.get("/var/app"))
    .addFileExtension("txt")
    .build();

try (FileSystemDeploymentWatcher watcher = new FileSystemDeploymentWatcher(config)) {
    watcher.addListener(new MyEventListener());
    watcher.start();
    
    // Keep application running
    Thread.sleep(60000);
    
    // Watcher automatically stops when exiting try block
}
```

## Configuration

### WatcherConfig Builder

```java
WatcherConfig config = WatcherConfig.builder()
    .watchDirectory(Paths.get("/path/to/watch"))  // Required
    .autoStart(true)                               // Default: true
    .autoDeploy(true)                              // Default: true
    .addFileExtension("yaml")                      // Can call multiple times
    .addFileExtension("json")
    .debounceMillis(500)                           // Default: 500ms
    .build();
```

**Configuration Options:**
- `watchDirectory(Path)` - Directory to monitor (required)
- `autoStart(boolean)` - Whether to start automatically (default: true)
- `autoDeploy(boolean)` - Whether to auto-deploy detected files (default: true)
- `addFileExtension(String)` - Add file extension to monitor (default: yaml, json)
- `fileExtensions(Set<String>)` - Replace all file extensions
- `debounceMillis(long)` - Debounce delay in milliseconds (default: 500)

## Event Handling

### DeploymentEventListener Interface

```java
public interface DeploymentEventListener {
    void onDescriptorDetected(Path descriptorFile);
    void onDescriptorModified(Path descriptorFile);
    void onDescriptorRemoved(Path descriptorFile);
    void onError(Path file, Exception error);
}
```

### Lambda Listeners

```java
watcher.addListener(new DeploymentEventListener() {
    @Override
    public void onDescriptorDetected(Path file) {
        // Handle new file
    }
    
    @Override
    public void onDescriptorModified(Path file) {
        // Handle modified file
    }
    
    @Override
    public void onDescriptorRemoved(Path file) {
        // Handle removed file
    }
    
    @Override
    public void onError(Path file, Exception error) {
        // Handle errors
    }
});
```

## Debouncing

File changes are debounced to prevent duplicate processing when files are modified multiple times in quick succession (e.g., during text editor saves).

```java
// Without debouncing: 5 modify events
// With 500ms debouncing: 1 modify event

WatcherConfig config = WatcherConfig.builder()
    .watchDirectory(path)
    .debounceMillis(500)  // Wait 500ms for additional changes
    .build();
```

**How It Works:**
1. File modification detected
2. Timer started for debounce delay
3. Another modification detected → timer reset
4. No modifications for debounce period → event fired
5. Only one event delivered for multiple rapid changes

## Use Cases

1. **Configuration Hot Reload**: Monitor config files and reload on changes
2. **File Processing**: Watch upload directories for new files
3. **Development Tools**: Monitor source files for changes (auto-rebuild)
4. **Deployment**: Watch for deployment descriptor updates
5. **Log Processing**: Monitor log directories for new log files

## Examples

### Configuration Hot Reload

```java
public class ConfigWatcher {
    
    private final FileSystemDeploymentWatcher watcher;
    private volatile Properties config;
    
    public ConfigWatcher(Path configDir) throws Exception {
        WatcherConfig cfg = WatcherConfig.builder()
            .watchDirectory(configDir)
            .addFileExtension("properties")
            .build();
        
        this.watcher = new FileSystemDeploymentWatcher(cfg);
        watcher.addListener(new DeploymentEventListener() {
            @Override
            public void onDescriptorDetected(Path file) {
                reloadConfig(file);
            }
            
            @Override
            public void onDescriptorModified(Path file) {
                reloadConfig(file);
            }
            
            @Override
            public void onDescriptorRemoved(Path file) {
                // Config removed
            }
            
            @Override
            public void onError(Path file, Exception error) {
                System.err.println("Config error: " + error);
            }
        });
        
        watcher.start();
    }
    
    private void reloadConfig(Path file) {
        try {
            Properties props = new Properties();
            props.load(Files.newInputStream(file));
            this.config = props;
            System.out.println("Config reloaded from " + file);
        } catch (Exception e) {
            System.err.println("Failed to reload config: " + e);
        }
    }
}
```

### File Upload Processing

```java
public class UploadProcessor {
    
    public void startWatching(Path uploadDir) throws Exception {
        WatcherConfig config = WatcherConfig.builder()
            .watchDirectory(uploadDir)
            .addFileExtension("csv")
            .addFileExtension("txt")
            .debounceMillis(1000)  // Wait for complete uploads
            .build();
        
        FileSystemDeploymentWatcher watcher = new FileSystemDeploymentWatcher(config);
        
        watcher.addListener(new DeploymentEventListener() {
            @Override
            public void onDescriptorDetected(Path file) {
                processFile(file);
            }
            
            @Override
            public void onDescriptorModified(Path file) {
                // Ignore modifications
            }
            
            @Override
            public void onDescriptorRemoved(Path file) {
                // File removed before processing
            }
            
            @Override
            public void onError(Path file, Exception error) {
                // Log error
            }
        });
        
        watcher.start();
    }
    
    private void processFile(Path file) {
        // Process the uploaded file
        System.out.println("Processing: " + file);
    }
}
```

## Thread Safety

All operations are thread-safe:
- Listener management uses `CopyOnWriteArrayList`
- Event processing uses concurrent data structures
- Multiple threads can add/remove listeners safely

## Requirements

- Java 21 or higher
- SLF4J API (for logging)

## License

MIT License - see [LICENSE](LICENSE) file for details.

## Contributing

Contributions are welcome! Please see [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

## Links

- [GitHub Repository](https://github.com/FlossWare/jfs-watcher)
- [Issue Tracker](https://github.com/FlossWare/jfs-watcher/issues)
- [Javadoc](https://javadoc.io/doc/org.flossware/jfs-watcher)

## Changelog

See [CHANGELOG.md](CHANGELOG.md) for version history.
