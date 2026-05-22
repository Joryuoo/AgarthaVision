# AgarthaVision · Cloud Backend Plan

> FastAPI + YOLO inference backend for Phase 1 (cloud architecture).
> This is a separate repository/deployment from the Android app.

---

## 1. Tech Stack

| Layer              | Technology                                    |
|--------------------|-----------------------------------------------|
| Language           | Python 3.11+                                  |
| API Framework      | FastAPI                                       |
| AI / ML            | PyTorch + Ultralytics (YOLOv12 + EfficientNetV2) |
| Database           | PostgreSQL 16                                 |
| ORM                | SQLAlchemy 2.x + Alembic (migrations)         |
| Object Storage     | MinIO (dev) / S3-compatible bucket (prod)     |
| Task Queue         | Celery + Redis                                |
| Auth               | JWT bearer tokens (simple for MVP)            |
| Containerization   | Docker + Docker Compose                       |
| Package Manager    | uv (or pip, but uv is faster)                 |

---

## 2. Project Structure

```
agarthavision-backend/
├── app/
│   ├── main.py                    # FastAPI app factory
│   ├── config.py                  # Pydantic settings (env-based)
│   ├── api/
│   │   ├── v1/
│   │   │   ├── inference.py       # /v1/inference/* routes
│   │   │   ├── results.py         # /v1/results/* routes
│   │   │   ├── reports.py         # /v1/reports/* routes
│   │   │   └── sync.py            # /v1/sync/* routes
│   │   └── deps.py                # Dependency injection (DB session, auth)
│   ├── models/                    # SQLAlchemy ORM models
│   │   ├── sample.py
│   │   ├── inference_request.py
│   │   ├── detection.py
│   │   ├── epg_calculation.py
│   │   ├── validation_record.py
│   │   ├── report.py
│   │   └── sync_queue.py
│   ├── schemas/                   # Pydantic request/response schemas
│   │   ├── inference.py
│   │   ├── detection.py
│   │   ├── epg.py
│   │   └── report.py
│   ├── services/
│   │   ├── inference_service.py   # YOLO model loading + inference
│   │   ├── epg_service.py         # EPG calculation logic
│   │   ├── storage_service.py     # S3/MinIO image storage
│   │   └── report_service.py      # Report generation
│   ├── tasks/
│   │   └── inference_task.py      # Celery async inference task
│   └── db/
│       ├── session.py             # SQLAlchemy session factory
│       └── migrations/            # Alembic migrations
├── ml/
│   ├── weights/                   # Model weight files (.pt)
│   └── config.yaml                # Model config (confidence threshold, NMS, classes)
├── tests/
├── docker-compose.yml
├── Dockerfile
├── pyproject.toml                 # uv/pip dependencies
└── .env.example
```

---

## 3. API Contract

These endpoints are what the Android app calls. Define them clearly so mobile and
backend development can proceed in parallel.

### 3.1 Submit Inference Request

```
POST /v1/inference/submit
Content-Type: multipart/form-data
Authorization: Bearer <token>

Parts:
  - image: (binary JPEG file)
  - metadata: (JSON string)
    {
      "sample_id": "uuid",
      "session_id": "uuid",
      "device_id": "string",
      "timestamp": "2026-05-22T14:22:08+08:00",
      "gps_latitude": 9.6492,
      "gps_longitude": 123.8595,
      "gps_accuracy": 4.0
    }

Response 202 Accepted:
{
  "inference_id": "uuid",
  "status": "queued",
  "estimated_wait_seconds": 15
}
```

### 3.2 Check Inference Status

```
GET /v1/inference/{inference_id}/status
Authorization: Bearer <token>

Response 200:
{
  "inference_id": "uuid",
  "sample_id": "uuid",
  "status": "processing" | "completed" | "failed",
  "progress_percent": 75,
  "error_message": null
}
```

### 3.3 Get Inference Result

```
GET /v1/inference/{inference_id}/result
Authorization: Bearer <token>

Response 200:
{
  "inference_id": "uuid",
  "sample_id": "uuid",
  "model_version": "yolov12-efficientnetv2-v1.0",
  "processing_time_ms": 2340,
  "detections": [
    {
      "detection_id": "uuid",
      "class_label": "trichuris_trichiura",
      "confidence": 0.91,
      "bbox": { "x": 0.45, "y": 0.32, "w": 0.08, "h": 0.06 },
    },
    {
      "detection_id": "uuid",
      "class_label": "ascaris_lumbricoides",
      "confidence": 0.04,
      "bbox": { "x": 0.12, "y": 0.67, "w": 0.05, "h": 0.04 },
    }
  ],
  "epg_calculations": [
    {
      "species": "trichuris_trichiura",
      "raw_egg_count": 12,
      "computed_epg": 1284.0,
      "volumetric_multiplier": 107.0
    }
  ],
  "annotated_image_url": "https://storage.../annotated/uuid.jpg"
}
```

### 3.4 Sync Validation Results (mobile → cloud)

```
POST /v1/sync/validation
Authorization: Bearer <token>
Content-Type: application/json

{
  "sample_id": "uuid",
  "final_status": "validated" | "findings_rejected" | "unusable",
  "validation_records": [
    {
      "validation_id": "uuid",
      "detection_id": "uuid",
      "action_type": "reclassified",
      "previous_value": "ascaris_lumbricoides",
      "new_value": "trichuris_trichiura",
      "timestamp": "2026-05-22T14:30:00+08:00"
    }
  ],
  "final_epg_calculations": [
    {
      "species": "trichuris_trichiura",
      "technician_validated_epg": 1284.0
    }
  ]
}

Response 200:
{
  "sample_id": "uuid",
  "sync_status": "synced",
  "synced_at": "2026-05-22T14:30:05+08:00"
}
```

### 3.5 Export Report Data

```
GET /v1/reports/export?from=2026-05-01&to=2026-05-22&format=csv
Authorization: Bearer <token>

Response 200:
Content-Type: text/csv
(CSV with headers: Sample ID, Detected Species, AI Confidence, AI EPG, Validated EPG, Processing Time)
```

---

## 4. Inference Pipeline

### 4.1 Flow

```
POST /submit → save image to S3 → create InferenceRequest (status=queued)
            → dispatch Celery task

Celery worker:
  1. Download image from S3
  2. Preprocess (resize, normalize)
  3. Run YOLO inference (detect + classify)
  4. Filter detections by confidence threshold (default 0.5)
  5. Count eggs per species
  6. Apply DOH volumetric multiplier → compute EPG
  7. Save Detections + EPGCalculations to DB
  8. Update InferenceRequest status → "completed"
  9. Generate annotated image → save to S3
```

### 4.2 EPG Calculation

```python
def compute_epg(raw_count: int, multiplier: float = 107.0) -> float:
    """
    Standard DOH EPG formula.
    multiplier depends on the preparation method (Kato-Katz standard = ~107).
    """
    return raw_count * multiplier
```

### 4.3 Model Configuration

```yaml
# ml/config.yaml
model_path: "ml/weights/yolov12-efficientnetv2-best.pt"
confidence_threshold: 0.5
nms_iou_threshold: 0.45
classes:
  0: "ascaris_lumbricoides"
  1: "trichuris_trichiura"
  2: "hookworm"
  3: "artifact"
image_size: 640
```

---

## 5. Database Schema (PostgreSQL)

The backend mirrors the SDD's unified ERD. Use Alembic for migrations so the schema
is version-controlled and reviewable in PRs.

Key tables: `samples`, `inference_requests`, `detections`, `epg_calculations`,
`validation_records`, `reports`, `report_samples`, `sync_queue`, `users`, `devices`,
`diagnostic_sessions`.

---

## 6. Development Setup

### 6.1 Docker Compose

```yaml
version: "3.9"
services:
  api:
    build: .
    ports: ["8000:8000"]
    env_file: .env
    depends_on: [db, redis, minio]
    volumes: ["./ml/weights:/app/ml/weights"]

  celery-worker:
    build: .
    command: celery -A app.tasks worker --loglevel=info
    env_file: .env
    depends_on: [db, redis, minio]
    volumes: ["./ml/weights:/app/ml/weights"]
    deploy:
      resources:
        reservations:
          devices:
            - capabilities: [gpu]   # if GPU available

  db:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: agarthavision
      POSTGRES_USER: agartha
      POSTGRES_PASSWORD: dev_password
    ports: ["5432:5432"]
    volumes: ["pgdata:/var/lib/postgresql/data"]

  redis:
    image: redis:7-alpine
    ports: ["6379:6379"]

  minio:
    image: minio/minio
    command: server /data --console-address ":9001"
    ports: ["9000:9000", "9001:9001"]
    environment:
      MINIO_ROOT_USER: minioadmin
      MINIO_ROOT_PASSWORD: minioadmin

volumes:
  pgdata:
```

### 6.2 Local Development

```bash
# Start infrastructure
docker compose up db redis minio -d

# Run API locally (for faster iteration)
uv run uvicorn app.main:app --reload --host 0.0.0.0 --port 8000

# Run Celery worker locally
uv run celery -A app.tasks worker --loglevel=info
```

### 6.3 Bun Scripts for Backend

Add a `package.json` in the backend repo too:

```json
{
  "scripts": {
    "dev": "docker compose up db redis minio -d && uv run uvicorn app.main:app --reload",
    "worker": "uv run celery -A app.tasks worker --loglevel=info",
    "migrate": "uv run alembic upgrade head",
    "migration:new": "uv run alembic revision --autogenerate -m",
    "test": "uv run pytest",
    "lint": "uv run ruff check .",
    "format": "uv run ruff format .",
    "up": "docker compose up -d",
    "down": "docker compose down"
  }
}
```

---

## 7. Parallel Development Strategy

The mobile team and backend team can work in parallel by agreeing on the API contract
(Section 3) upfront. The mobile team mocks the API responses locally while the backend
is being built.

### Mobile-Side Mock

Create a `FakeInferenceApi` implementation in the Android app's `test/` source set:

```kotlin
class FakeInferenceApi : InferenceApi {
    override suspend fun submitPayload(...) =
        Response.success(InferenceSubmitResponse(
            inferenceId = UUID.randomUUID().toString(),
            status = "queued",
            estimatedWaitSeconds = 2,
        ))
    // ... etc
}
```

Inject this via Hilt when `BuildConfig.USE_MOCK_API` is true (set in `build.gradle.kts`
`buildConfigField`). This lets the mobile team build and test all UI flows without waiting
for the backend to be deployed.
