# Data Package

## Purpose

The `data` package contains implementation details for local storage, remote inference calls, Supabase sync, file storage, and repository implementations.

This layer connects the app to actual data sources such as Room, Retrofit, local files, and Supabase.

## Responsibilities

The `data` package contains:

- Room entities
- Room DAOs
- Local-to-domain mappers
- Inference API and DTOs
- Supabase remote data sources
- Repository implementations
- Local image/report file storage handlers

## Current Subpackages

```text
data/
├── local/
│   ├── dao/         # Room DAO interfaces
│   ├── entity/      # Room entities
│   └── mapper/      # Entity-to-domain mappers
├── remote/
│   ├── dto/         # Inference request/response DTOs
│   └── InferenceApi.kt
├── repository/      # Repository implementations and local stores
└── supabase/        # Supabase remote data sources and sync use cases
```
