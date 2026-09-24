# SatQuery AI – Backend

Spring Boot 3 / Java 21 backend for **SatQuery AI**, a natural-language satellite imagery analysis platform.
The backend orchestrates projects, images, analyses, evidence, model registry and PDF reports. **All AI inference is delegated to a separate Python service** over HTTP (WebClient). The backend never fabricates AI results: if the AI service fails, the analysis is stored as `FAILED` and a `502` is returned.

## Architecture

```
 React frontend (Vite, :5173)
          │  REST/JSON
          ▼
 ┌────────────────────────────── Spring Boot backend (:8080) ──────────────────────────────┐
 │ controller ─► service ─► repository ─► MySQL                                            │
 │                 │                                                                        │
 │                 ├─► AnalysisRouter / AnalysisValidator   (AUTO routing, image rules)     │
 │                 ├─► StorageService (local disk, /files/**)                               │
 │                 ├─► ReportGenerator (PDFBox)                                             │
 │                 └─► AIServiceClient ── WebClient ──► Python AI service (:8000)           │
 └──────────────────────────────────────────────────────────────────────────────────────────┘
                                                  /ai/vqa  /ai/caption  /ai/grounding
                                                  /ai/change  /ai/optical-sar  /health
```

Analysis flow: validate request → persist `PENDING` → `PROCESSING` → call AI service (outside any DB transaction) → persist result + evidence → `COMPLETED` (or `FAILED` with `errorMessage`).

Analysis types: `VQA`, `CAPTION`, `GROUNDING`, `CHANGE_DETECTION`, `OPTICAL_SAR`, `AUTO` (resolved from the question and the selected images).
Image rules: change detection needs exactly 2 images; optical+SAR needs exactly 1 optical/multispectral and 1 SAR image; the others need at least 1.

## Requirements

- JDK 21
- Maven 3.9+
- MySQL 8

## Database setup

```sql
CREATE DATABASE satquery CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

The URL includes `createDatabaseIfNotExist=true`, and tables are created/updated automatically (`DDL_AUTO=update`). Use `validate` or migrations in production.

## Environment variables

| Variable | Default | Purpose |
|---|---|---|
| `SERVER_PORT` | `8080` | HTTP port |
| `DB_URL` | `jdbc:mysql://localhost:3306/satquery?...` | JDBC URL |
| `DB_USERNAME` / `DB_PASSWORD` | `root` / `root` | DB credentials |
| `DDL_AUTO` | `update` | Hibernate schema mode |
| `AI_SERVICE_URL` | `http://localhost:8000` | Python AI service base URL |
| `AI_CONNECT_TIMEOUT_MS` / `AI_READ_TIMEOUT_MS` | `5000` / `120000` | AI call timeouts |
| `FRONTEND_URL` | `http://localhost:5173` | Allowed CORS origin (comma separated for several) |
| `UPLOAD_DIR` | `uploads` | Root folder for images (`images/`) and reports (`reports/`) |
| `PUBLIC_BASE_URL` | `http://localhost:8080` | URL used to build file links; **must be reachable by the AI service** |
| `MAX_FILE_SIZE` / `MAX_REQUEST_SIZE` | `200MB` | Multipart limits |

AI endpoint paths are configured under `ai.service.endpoints.*` in `application.properties`; individual models registered via `/api/models` can override the endpoint.

## Running

```bash
# 1. MySQL (example with Docker)
docker run -d --name satquery-mysql -p 3306:3306 -e MYSQL_ROOT_PASSWORD=root -e MYSQL_DATABASE=satquery mysql:8

# 2. Backend
export DB_PASSWORD=root
mvn spring-boot:run

# 3. Python AI service (separate project) must expose, on AI_SERVICE_URL:
#    POST /ai/vqa | /ai/caption | /ai/grounding | /ai/change | /ai/optical-sar   and   GET /health
```

Build and test: `mvn clean verify` (unit tests need no database or AI service).

## API

Swagger UI: <http://localhost:8080/swagger-ui.html> · OpenAPI JSON: `/v3/api-docs`

| Method | Path | Description |
|---|---|---|
| POST/GET | `/api/projects` | Create / list projects |
| GET/PUT/DELETE | `/api/projects/{id}` | Read / update / delete (cascades) |
| GET | `/api/projects/{id}/images` | Project images |
| GET | `/api/projects/{id}/analysis` | Analysis history |
| POST | `/api/images/upload` | Multipart upload (`file`, `projectId`, `imageType`, optional `satellite`, `acquisitionDate`, `latitude`, `longitude`, `resolution`) |
| POST | `/api/images` | Register externally hosted image metadata |
| GET/DELETE | `/api/images/{id}` | Read / delete (blocked if used by an analysis) |
| POST | `/api/analysis` | Create and run an analysis |
| GET | `/api/analysis/{id}` | Full result |
| GET | `/api/analysis/{id}/evidence` | Evidence / bounding boxes |
| GET | `/api/analysis/{id}/status` | Status only |
| DELETE | `/api/analysis/{id}` | Delete with evidence, result, reports |
| POST | `/api/reports/{analysisId}` | Generate PDF report (COMPLETED analyses only) |
| GET | `/api/reports/{reportId}` | Report metadata and URL |
| GET/POST/PUT | `/api/models`, `/api/models/{id}` | AI model registry |
| GET | `/api/health`, `/api/health/ai` | Backend / AI service health |

Stored files are served from `/files/**`.

## Examples

Create a project:

```bash
curl -X POST localhost:8080/api/projects -H 'Content-Type: application/json' \
  -d '{"name":"Delhi Urban Growth Study","description":"Urban expansion around Delhi NCR"}'
```

Upload an image:

```bash
curl -X POST localhost:8080/api/images/upload \
  -F file=@before.tif -F projectId=1 -F imageType=OPTICAL -F satellite=Sentinel-2 \
  -F acquisitionDate=2023-01-15 -F latitude=28.61 -F longitude=77.23 -F resolution=10
```

Run a change-detection analysis (`AUTO` lets the backend choose):

```bash
curl -X POST localhost:8080/api/analysis -H 'Content-Type: application/json' \
  -d '{"projectId":1,"imageIds":[10,11],"analysisType":"CHANGE_DETECTION","question":"What changed between these two images?"}'
```

Response (`201`):

```json
{
  "id": 5, "projectId": 1, "status": "COMPLETED",
  "analysisType": "CHANGE_DETECTION", "requestedAnalysisType": "CHANGE_DETECTION",
  "question": "What changed between these two images?",
  "answer": "New buildings appeared in the north-east.",
  "confidence": 0.87,
  "model": { "name": "change-model", "version": "v2" },
  "evidence": [ { "type": "CHANGED_REGION", "x": 120, "y": 80, "width": 60, "height": 40, "confidence": 0.9 } ],
  "processingTimeMs": 1234
}
```

Errors share one shape: `{"timestamp","status","error","message","path"}`
(`400` validation, `404` not found, `502` AI service failure, `500` unexpected).

### Python AI service contract

The backend sends `{analysisId, question, imageUrls[], analysisType}` and expects
`{answer, summary?, confidence?, model?, modelVersion?, processingTimeMs?, evidence?[], changeMapUrl?, findings?[]}`.
A response without `answer` is treated as a failure.

## Project structure

```
src/main/java/com/satquery
├── SatQueryApplication.java
├── client/       AIServiceClient (only class that calls the Python service)
├── config/       AiServiceProperties, StorageProperties, WebClient, CORS, static files, OpenAPI
├── controller/   Project, Image, Analysis, Report, Model, Health
├── dto/          request/response records (entities are never exposed)
├── entity/       Project, SatelliteImage, Analysis, AnalysisResult, Evidence, AIModel, Report + enums
├── exception/    domain exceptions + GlobalExceptionHandler
├── mapper/       entity <-> DTO
├── repository/   Spring Data JPA repositories
├── service/      ProjectService, ImageService, AnalysisService, AnalysisRouter, AnalysisValidator,
│                 AIModelService, ReportService, ReportGenerator, StorageService/LocalStorageService
└── util/         PdfReportWriter, TextUtils
src/test/java/com/satquery   service, controller and client tests (JUnit 5, Mockito, MockMvc)
```

## Extending

- Cloud storage: implement `StorageService` (e.g. S3) and replace `LocalStorageService`.
- New analysis type: add enum value, endpoint property, `AIServiceClient` method, routing/validation rules.
