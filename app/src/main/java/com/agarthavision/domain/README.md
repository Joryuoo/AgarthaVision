# Domain Package

## Purpose

The `domain` package contains the core business logic of the AgarthaVision Android app.

This layer should be pure Kotlin as much as possible. It should not depend directly on Android framework classes, Room entities, Retrofit DTOs, or Compose UI.

## Responsibilities

The `domain` package contains:

- Domain models and enums
- Repository interfaces
- Use cases
- Business rules
- App-level workflow logic

## Current Subpackages

```text
domain/
├── model/           # Domain models and enums
├── repository/      # Repository interfaces and contracts
└── usecase/
    ├── auth/        # Session restore and sign-in use cases
    ├── capture/     # Inference and flagged-frame capture use cases
    ├── inference/   # Inference error modeling
    ├── records/     # Records, sample detail, and CSV report use cases
    ├── reports/     # Session egg-count use cases
    └── verify/      # Verification and manual-capture submission use cases
```
