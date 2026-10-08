# Caliber Core (`caliber-core`)

Backend microservice for **Caliber**, an automated recruiter email intelligence, employment classification, and 1-click recruitment pipeline platform.

---

## Features

- **Gmail Ingestion & Pipeline**:
  - Scheduled background polling (`@Scheduled`) and manual on-demand synchronization.
  - Flexible query filtering for tech and engineering recruiter solicitations.
  - Strips HTML, decodes multi-part MIME messages, and normalizes email metadata.
  - Idempotent deduplication based on unique Gmail message IDs.

- **Hybrid Classification Engine**:
  - **Deterministic Pre-Filter**: Instant regex evaluation for standard contract and employment types (`C2C`, `W2`, `C2H`, `Full-Time`).
  - **Structured LLM Parser**: Multi-tier extraction with **Ollama** as primary and **Google Gemini** as automatic fallback.
  - Structured extraction of job title, client company, pay rate, location type, and skills.
  - Configurable email body truncation limit (`AI_MAX_BODY_CHARS`) to optimize LLM context usage.

- **Thread Integrity & 1-Click Dispatch**:
  - Preserves email thread continuity by linking `threadId`, `In-Reply-To`, `References`, and `Re: Subject`.
  - Supports dynamic MIME multipart generation for attaching PDF resumes.
  - Automatically manages Gmail labels via `users.messages.modify`:
    - Employment categorization: `Jobs/C2C`, `Jobs/C2H`, `Jobs/W2`, `Jobs/Full-Time`, `Jobs/Unspecified`
    - Pipeline action tagging: `Jobs/Inquired`, `Jobs/Applied`, `Jobs/Dismissed`
  - Optional auto-archiving of processed emails from the inbox to maintain a clean workflow.

- **MongoDB Persistence Layer**:
  - Stores extracted job emails, uploaded resume documents, custom templates, and user sync configurations.

- **Stateless Security Integration**:
  - Stateless JWT authentication via HTTP-only cookies and Bearer tokens.
  - Configurable token expiration and CORS controls.

---

## Environment Variables

Configure the following variables in `.env` or your target deployment environment:

| Variable | Description | Default |
| :--- | :--- | :--- |
| `PORT` | Server listening port | `8087` |
| `SPRING_PROFILES_ACTIVE` | Active Spring profile (`dev` or `prod`) | `dev` |
| `MONGODB_URI` | MongoDB connection string | Required |
| `MONGODB_DATABASE` | Target MongoDB database name | `caliber` |
| `JWT_SECRET` | 256-bit secret key for JWT verification | Required |
| `JWT_EXPIRY_MINUTES` | Token expiration time in minutes | `30` |
| `CORS_ALLOWED_ORIGINS` | Allowed CORS origins pattern | `*` |
| `OLLAMA_API_KEY` | Ollama API Key / Bearer token (if hosted/authenticated) | None |
| `OLLAMA_MODEL` | Ollama model identifier | `llama3.2` |
| `OLLAMA_BASE_URL` | Ollama service base endpoint | Required |
| `OLLAMA_TIMEOUT_SECONDS` | Ollama request timeout in seconds | `120` |
| `GEMINI_API_KEY` | Google Gemini API key (fallback extraction) | Optional |
| `GEMINI_MODEL` | Gemini model identifier | `gemini-1.5-flash` |
| `GEMINI_BASE_URL` | Gemini API service endpoint | Optional |
| `GEMINI_TIMEOUT_SECONDS` | Gemini request timeout in seconds | `120` |
| `AI_MAX_BODY_CHARS` | Maximum characters of email body sent to LLM | `10000` |
| `GMAIL_CLIENT_ID` | Google Cloud OAuth 2.0 Client ID | Required |
| `GMAIL_CLIENT_SECRET` | Google Cloud OAuth 2.0 Client Secret | Required |
| `GMAIL_REFRESH_TOKEN` | Google Cloud OAuth 2.0 Refresh Token | Required |
| `GMAIL_REDIRECT_URI` | Registered OAuth callback URI | Required |
| `GMAIL_POLL_INTERVAL_MS`| Scheduled poll heartbeat in milliseconds | `60000` (1 min) |
| `GMAIL_AUTO_ARCHIVE` | Archive original email from inbox upon reply/action | `false` |
| `GMAIL_MAX_RESULTS` | Maximum messages fetched per poll cycle | `50` |

---

## API Endpoints

### Job Openings (`/core/api/v1/jobs`)
- `GET /core/api/v1/jobs?tab=c2c|c2h|w2|full_time|unspecified|history&status=...&search=...` - List and filter openings
- `GET /core/api/v1/jobs/{id}` - Retrieve job details and parsed overview
- `GET /core/api/v1/jobs/{id}/draft?type=INQUIRY|APPLY` - Generate pre-filled reply draft with template
- `POST /core/api/v1/jobs/{id}/reply` - Send in-thread reply, attach resume, and tag label
- `POST /core/api/v1/jobs/{id}/dismiss` - Dismiss job and apply `Jobs/Dismissed` label
- `POST /core/api/v1/jobs/sync` - Trigger immediate on-demand Gmail sync
- `GET /core/api/v1/jobs/stats` - Pipeline metrics summary

### Resume Management (`/core/api/v1/resume`)
- `POST /core/api/v1/resume/upload` - Upload PDF resume
- `GET /core/api/v1/resume/active` - Get active resume metadata
- `GET /core/api/v1/resume` - List uploaded resume versions
- `POST /core/api/v1/resume/{id}/activate` - Set active resume
- `GET /core/api/v1/resume/{id}/download` - Download resume PDF

### Settings & Configuration (`/core/api/v1/settings`)
- `GET /core/api/v1/settings` - Retrieve templates and connection status
- `PUT /core/api/v1/settings` - Update reply templates, search query, and options
- `POST /core/api/v1/settings/test-ai` - Test Ollama & Gemini extraction with sample payload

---

## Build and Run

```bash
# Run unit and integration tests
./gradlew test

# Start the microservice
./gradlew bootRun
```
