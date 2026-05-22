# Core Package

## Purpose

The `core` package contains shared platform services and utilities used across the AgarthaVision Android app.

This package should not contain feature-specific UI or business logic. It should only contain reusable infrastructure code that other layers can depend on.

## Responsibilities

The `core` package may contain:

- Dependency injection modules
- Database setup
- Network setup
- Camera service wrappers
- Sync queue utilities
- Location provider wrappers
- Connectivity monitoring
- DataStore preference setup
- General utility functions

## Planned Subpackages

```text
core/
├── di/              # Hilt modules
├── network/         # Retrofit, OkHttp, API base configuration
├── database/        # Room database class and migrations
├── sync/            # SyncQueueManager and sync helpers
├── location/        # GPS/location provider wrappers
├── connectivity/    # NetworkMonitor
├── datastore/       # DataStore preference wrapper
└── util/            # Formatters, UUID generator, extensions