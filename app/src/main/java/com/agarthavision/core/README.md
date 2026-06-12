# Core Package

## Purpose

The `core` package contains shared platform services and utilities used across the AgarthaVision Android app.

This package should not contain feature-specific UI or business logic. It should only contain reusable infrastructure code that other layers can depend on.

## Responsibilities

The `core` package contains:

- CameraX wrappers and frame sampling
- Connectivity monitoring
- Room database setup
- Hilt dependency injection modules
- Location provider wrappers
- Session state management
- Shared utility functions

## Current Subpackages

```text
core/
├── camera/          # CameraManager and FrameSampler
├── connectivity/    # NetworkMonitor
├── database/        # AgarthaDatabase
├── di/              # Hilt modules
├── location/        # FusedLocationProvider
├── session/         # SessionManager and SessionState
└── util/            # DeviceIdProvider, EpgCalculator, image helpers
```
