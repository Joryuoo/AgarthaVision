
```markdown
# Data Package

## Purpose

The `data` package contains the implementation details for local storage, remote API communication, data transfer objects, Room entities, DAOs, and repository implementations.

This layer connects the app to actual data sources such as Room, Retrofit, local files, and the backend API.

## Responsibilities

The `data` package may contain:

- Room entities
- Room DAOs
- Retrofit API interfaces
- Request and response DTOs
- Repository implementations
- Local-to-domain mappers
- Remote-to-domain mappers
- File storage handlers

## Planned Subpackages

```text
data/
├── local/
│   ├── entity/      # Room entities
│   ├── dao/         # Room DAO interfaces
│   └── mapper/      # Entity ↔ Domain mappers
│
├── remote/
│   ├── api/         # Retrofit APIs: InferenceApi, ReportApi, SyncApi
│   ├── dto/         # Request/response DTOs
│   └── mapper/      # DTO ↔ Domain mappers
│
└── repository/      # Repository implementations