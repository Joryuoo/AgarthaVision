
```markdown
# Domain Package

## Purpose

The `domain` package contains the core business logic of the AgarthaVision Android app.

This layer should be pure Kotlin as much as possible. It should not depend directly on Android framework classes, Room entities, Retrofit DTOs, or Compose UI.

## Responsibilities

The `domain` package may contain:

- Domain models
- Repository interfaces
- Use cases
- Business rules
- Validation rules
- App-level workflow logic

## Planned Subpackages

```text
domain/
├── model/           # Domain models: Sample, Detection, EPGResult, Report, etc.
├── repository/      # Repository interfaces / contracts
└── usecase/
    ├── capture/     # CaptureSampleUseCase, TransmitPayloadUseCase
    ├── inference/   # FetchInferenceResultUseCase
    ├── validation/  # ApproveSampleUseCase, EditDetectionUseCase, RejectFindingsUseCase
    └── reports/     # GenerateSessionReportUseCase, FetchDetailedRecordsUseCase