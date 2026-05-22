# AgarthaVision Backend

## Overview

The AgarthaVision backend is the cloud API server used by the Android mobile application during the Phase 1 MVP. It receives microscopy image payloads from the mobile app, processes them through the AI inference pipeline, computes Eggs Per Gram (EPG), stores diagnostic records, and provides validated data for reporting.

The backend is separate from the Android app. The Android app communicates with this backend through REST API endpoints using Retrofit and OkHttp.

---

## Purpose

The backend is responsible for:

- Receiving captured microscopy images and metadata from the Android app
- Storing uploaded images in object storage
- Creating and tracking inference requests
- Running AI-assisted STH egg detection
- Returning detection results, bounding boxes, confidence scores, raw egg counts, and computed EPG values
- Receiving Human-in-the-Loop validation results from the mobile dashboard
- Providing report export endpoints for validated records

---

## Technology Stack

| Layer | Technology |
|---|---|
| Language | Python 3.11+ |
| API Framework | FastAPI |
| AI / ML | PyTorch + Ultralytics YOLO |
| Database | PostgreSQL |
| ORM | SQLAlchemy + Alembic |
| Object Storage | MinIO for development / S3-compatible storage for production |
| Task Queue | Celery + Redis |
| Authentication | JWT Bearer Token |
| Containerization | Docker + Docker Compose |

---

## Planned Folder Structure

```text
backend/
├── app/
│   ├── main.py
│   ├── config.py
│   ├── api/
│   │   ├── v1/
│   │   │   ├── inference.py
│   │   │   ├── results.py
│   │   │   ├── reports.py
│   │   │   └── sync.py
│   │   └── deps.py
│   ├── models/
│   ├── schemas/
│   ├── services/
│   ├── tasks/
│   └── db/
├── ml/
│   ├── weights/
│   └── config.yaml
├── tests/
├── docker-compose.yml
├── Dockerfile
├── pyproject.toml
└── .env.example