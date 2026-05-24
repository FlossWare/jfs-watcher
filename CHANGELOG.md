# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0] - 2026-05-24

### Added
- Initial release of JFS Watcher
- `WatcherConfig` - Builder-based configuration for filesystem watching
  - Directory path specification
  - Auto-start and auto-deploy flags
  - File extension filtering
  - Debounce delay configuration
  - Default extensions: yaml, json
- `DeploymentWatcher` - Interface for filesystem watching
  - Start/stop lifecycle methods
  - Listener management (add/remove)
  - Running state query
  - AutoCloseable support
- `DeploymentEventListener` - Event handler interface
  - onDescriptorDetected - New file created
  - onDescriptorModified - File modified
  - onDescriptorRemoved - File deleted
  - onError - Error handling
- `FileSystemDeploymentWatcher` - Main implementation
  - Java NIO WatchService integration
  - Thread-safe listener management (CopyOnWriteArrayList)
  - Event debouncing with ScheduledExecutorService
  - File extension filtering
  - Initial directory scanning
  - Graceful shutdown with cleanup
  - Dedicated watch thread
  - Concurrent event processing
- Comprehensive test coverage (21 passing tests)
- Thread-safe concurrent operations

### Features
- Monitor CREATE, MODIFY, DELETE filesystem events
- Configurable debounce delay (default: 500ms)
- Filter by file extensions
- Scan existing files at startup
- Named threads for easy debugging: fs-watcher, fs-watcher-debounce
- Exception isolation (listener errors don't affect other listeners)
- Proper resource cleanup on shutdown
- Overflow event detection

[1.0]: https://github.com/FlossWare/jfs-watcher/releases/tag/v1.0
