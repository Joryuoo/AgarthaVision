```markdown
# Worker Package

## Purpose

The `worker` package contains background workers for tasks that should run outside the active screen lifecycle.

In AgarthaVision, this mainly supports offline sync, queued uploads, and background report generation.

## Responsibilities

The `worker` package may contain:

- WorkManager workers
- Background upload workers
- Background report generation workers
- Retry-safe sync tasks
- Scheduled background operations

## Planned Files

```text
worker/
├── SyncWorker.kt                 # Upload queued samples to the backend
└── ReportGenerationWorker.kt     # Generate reports in the background