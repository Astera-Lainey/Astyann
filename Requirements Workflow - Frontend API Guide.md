# Requirements Workflow — Frontend API Guide

All calls go to `environment.apiBaseUrl` (gateway `:8080`). The JWT interceptor automatically attaches the `Bearer` token. The `X-User-Id` header is injected from the token payload for project-scoped calls.

---

## Phase 1 — Create the Project

**Trigger:** User submits the "New Project" form

```
POST /api/v1/projects
Content-Type: multipart/form-data
X-User-Id: <userId>

title=Loan Management System
description=...
document=<file.docx>
```

**Response `201`:**
```json
{
  "status": 201,
  "data": {
    "projectId": "550e8400-e29b-41d4-a716-446655440000",
    "title": "Loan Management System",
    "status": "ANALYZING"
  }
}
```

**What the user sees:** A loading/progress screen — "Your project is being set up…"

**What happens in the microservices:**
- ProjectService saves the `Project` row and returns 201 immediately
- A background thread sends the document to AIOrchestrator, gets back `extractedContext` + `documentText`, then fires `POST /api/v1/requirements/{projectId}/initialize` to RequirementService
- RequirementService creates the `Requirement` row and starts its own background pipeline

Angular should **save `projectId` to state** and immediately transition to the workspace/polling screen.

---

## Phase 2 — Poll the Pipeline Status

**Trigger:** Immediately after project creation, on a timer (every 3–5 seconds)

```
GET /api/v1/requirements/{projectId}/pcsf/status
```

**Possible responses over time:**

| When | Response body `data` | Meaning |
|------|---------------------|---------|
| First few seconds | `404` | Requirement entity not yet created — keep polling |
| After init | `{ pcsfStatus: "DRAFT", pendingQuestionsCount: 0 }` | PCSF skeleton built |
| After extraction | `{ pcsfStatus: "UNDER_REVIEW", pendingQuestionsCount: 4 }` | Questions ready |
| Gate-1 passed | `{ pcsfStatus: "INFERRING", pendingQuestionsCount: 0 }` | AI inference running |
| Inference done | `{ pcsfStatus: "UNDER_REVIEW", pendingQuestionsCount: 0, completenessScore: 0.91 }` | Ready to review |
| After validate | `{ pcsfStatus: "VALIDATED" }` | Locked for approval |
| After approve | `{ pcsfStatus: "APPROVED" }` | Done |

**Frontend polling logic:**
```typescript
// Stop polling when one of these terminal states is reached
// (or when questions are pending → switch to Q&A flow)
const STOP_POLL_STATES = ['UNDER_REVIEW', 'VALIDATED', 'APPROVED', 'FAILED'];
```

**What the user sees:**
- `DRAFT` / `INFERRING` → animated progress bar, "Analysing your document…"
- `UNDER_REVIEW` with `pendingQuestionsCount > 0` → questions screen appears
- `UNDER_REVIEW` with `pendingQuestionsCount === 0` → PCSF review screen appears
- `FAILED` → error banner with a retry option

---

## Phase 3 — Clarification Questions

**Condition:** Status polling returns `pendingQuestionsCount > 0`

### Step 3a — Fetch questions

```
GET /api/v1/requirements/{projectId}/questions
```

**Response:**
```json
{
  "data": [
    {
      "id": "uuid-1",
      "inventoryRef": "9.6",
      "question": "Should different Afriland branches see ONLY their own data?",
      "type": "YES_NO",
      "options": ["No — all branches share data", "Yes — each branch sees only its own"],
      "placeholder": null,
      "answered": false,
      "priority": 0
    },
    {
      "id": "uuid-2",
      "inventoryRef": "2.1",
      "question": "Who are the different types of users?...",
      "type": "TEXTAREA",
      "options": null,
      "placeholder": "Administrator | Internal | Manages users",
      "answered": false,
      "priority": 2
    }
  ]
}
```

**What the user sees:** A step-by-step questionnaire. Each question type maps to a UI control:

| `type` | UI control |
|--------|-----------|
| `YES_NO` | Radio buttons using `options[]` |
| `TEXT` | Single-line input, `placeholder` shown as hint |
| `TEXTAREA` | Multi-line input with pipe-format instructions |
| `SINGLE_SELECT` | Dropdown from `options[]` |
| `MULTI_SELECT` | Checkbox group from `options[]` |

Questions are sorted by `priority` — the multi-tenancy question (most architecturally impactful) always appears first.

### Step 3b — Submit answers

```
POST /api/v1/requirements/{projectId}/questions/answers
Content-Type: application/json

{
  "answers": [
    { "questionId": "uuid-1", "answer": "No — all branches share data" },
    { "questionId": "uuid-2", "answer": "Administrator | Internal | Manages users\nBranch Manager | Internal | Approves loans" }
  ]
}
```

**Response:**
```json
{
  "data": {
    "pcsfStatus": "INFERRING",
    "pendingQuestionsCount": 0
  }
}
```

**What the user sees:** A "Submitting…" state, then the loading screen reappears — "AI is generating your requirements specification…"

**What happens:** RequirementService applies each answer to the PCSF JSON via `PcsfFieldWriter`, marks questions answered. When `pendingQuestionsCount` hits 0, it automatically triggers `AiInferencePcsfService.runInferenceAsync()` — three sequential LLM calls (INF-1 entities, INF-3 screens, INF-4 API/DB). Resume polling Phase 2.

---

## Phase 4 — PCSF Review

**Condition:** `status === "UNDER_REVIEW"` and `pendingQuestionsCount === 0`

### Step 4a — Load the full PCSF

```
GET /api/v1/requirements/{projectId}/pcsf
```

**Response (abbreviated):**
```json
{
  "data": {
    "project": {
      "name":        { "value": "Loan Management System", "status": "CONFIRMED" },
      "description": { "value": "Tracks loan applications...", "status": "CONFIRMED" },
      "displayName": { "value": "Loan Tracker", "status": "CONFIRMED" }
    },
    "actors": [
      {
        "id": "ACT-01",
        "name": { "value": "Branch Manager" },
        "type": { "value": "INTERNAL" },
        "description": { "value": "Reviews and approves loan applications" }
      }
    ],
    "modules": [
      {
        "id": "MOD-01",
        "name": { "value": "Loan Management" },
        "useCases": [
          {
            "id": "UC-01",
            "name": { "value": "Submit Loan Application" },
            "preconditions": { "value": "User is authenticated" },
            "mainScenario": { "value": ["User opens form", "Fills in details", "Submits"] }
          }
        ]
      }
    ],
    "entities": [{ "name": "LoanApplication", "attributes": [] }],
    "userInterface": { "screens": [], "navigation": [] },
    "apiConfig": { "endpoints": [] }
  }
}
```

**What the user sees:** A structured review screen — tabs or sections for:
- Project Info
- Actors
- Modules & Use-Cases
- Entities & Relationships
- Screens & Navigation
- API Config

Each field shows its `value` and optionally a `status` badge (`CONFIRMED` / `EXTRACTED` / `MISSING`).

### Step 4b — Inline field editing (optional)

```
PATCH /api/v1/requirements/{projectId}/pcsf/fields
Content-Type: application/json

{ "path": "project.displayName.value", "value": "Loan Tracker" }
```

**Response `200`:** `{ "message": "Field updated." }` — Angular re-renders the field in place, no full reload needed.

**Supported paths:**

| Path | Target |
|------|--------|
| `project.name.value` | Project name |
| `project.description.value` | Project description |
| `project.displayName.value` | UI display name |
| `actors` | Full actors list (pipe-delimited text) |
| `modules` | Full modules list (pipe-delimited text) |
| `conditionalFeatures.fileUpload.required.value` | File upload feature flag |
| `conditionalFeatures.dataExport.required.value` | Data export feature flag |
| `conditionalFeatures.searchFilter.required.value` | Search/filter feature flag |
| `conditionalFeatures.multiTenancy.required.value` | Multi-tenancy feature flag |

### Step 4c — Validate and lock

```
POST /api/v1/requirements/{projectId}/pcsf/validate
```

**Success `200`:**
```json
{
  "data": {
    "valid": true,
    "pcsfStatus": "VALIDATED",
    "errors": [],
    "warnings": []
  }
}
```

**Failure `422`:**
```json
{
  "data": {
    "valid": false,
    "pcsfStatus": "UNDER_REVIEW",
    "errors": [
      "VR-07a: Use-case UC-01 missing preconditions.",
      "VR-03: At least one actor is required."
    ]
  }
}
```

**What the user sees:** On `422`, each error highlights the corresponding field/section in red. On `200`, the screen transitions to an "Approve" CTA.

---

## Phase 5 — Approve

**Condition:** `status === "VALIDATED"`

```
POST /api/v1/requirements/{projectId}/approve
```

**Response `200`:**
```json
{
  "data": {
    "status": "APPROVED",
    "message": "Requirements approved. RAG indexing will be triggered once the RAG service is available."
  }
}
```

**What the user sees:** A success state — "Requirements approved ✓" — with options to proceed to the next sprint step (UML / Code gen) or submit a change request.

**What happens:** Status is locked to `APPROVED` in RequirementService. The RAG indexing call is stubbed (graceful no-op) — when the RAG service is built it will index the `pcsfJson` content to power AI-assisted generation in later sprints.

---

## Phase 6 — Change Request + Regenerate

**Condition:** User is not satisfied. Can be called from `UNDER_REVIEW` or `VALIDATED`. Blocked once `APPROVED`.

### Step 6a — Submit change instructions

```
POST /api/v1/requirements/{projectId}/change-request
Content-Type: application/json

{
  "instructions": "Add a notification module for SMS alerts when loan status changes. Also add a reporting module for monthly disbursement summaries."
}
```

**Response `200`:**
```json
{
  "data": {
    "changeRequestId": "uuid",
    "status": "CHANGE_REQUESTED",
    "message": "Change request recorded. Call /regenerate to re-run AI inference."
  }
}
```

### Step 6b — Trigger regeneration

```
POST /api/v1/requirements/{projectId}/regenerate
```

**Response `202`:**
```json
{ "message": "Regeneration started. Poll /pcsf/status for updates." }
```

**What the user sees:** A textarea to describe changes, a "Submit & Regenerate" button. After submitting, the loading screen reappears and the user cycles back through Phase 2.

**What happens in the microservices:** `changeInstructions` is appended to the full merged context (original document context + Q&A) before all three inference passes (INF-1/3/4). The LLM adapts the PCSF accordingly. After completion `changeInstructions` is cleared and status reverts to `UNDER_REVIEW`.

---

## Complete State Machine for the Frontend Router

```
/new-project
    ↓ POST /projects → projectId
/workspace/{projectId}/requirements
    ↓ poll GET /pcsf/status every 3–5 s
    │
    ├── DRAFT / INFERRING            → progress screen (keep polling)
    │
    ├── UNDER_REVIEW + questions > 0 → Q&A screen
    │       ↓ GET /questions          → render form
    │       ↓ POST /questions/answers → resume polling
    │
    ├── UNDER_REVIEW + questions = 0 → PCSF review screen
    │       ↓ GET /pcsf               → render tabs
    │       ↓ PATCH /pcsf/fields      → inline edit (optional)
    │       ↓ POST /pcsf/validate     → 200 or 422
    │
    ├── VALIDATED                    → approve screen
    │       ↓ POST /approve          → APPROVED
    │
    ├── APPROVED                     → success + next-steps
    │       ↓ (optional) POST /change-request → CHANGE_REQUESTED
    │
    ├── CHANGE_REQUESTED             → POST /regenerate → INFERRING
    │       ↓ resume polling
    │
    └── FAILED                       → error screen + retry
```

---

## All 10 Endpoints Summary

| # | Method | Path | Phase |
|---|--------|------|-------|
| 1 | `POST` | `/api/v1/projects` | Project creation form submit |
| 2 | `GET` | `/api/v1/requirements/{id}/pcsf/status` | Continuous polling |
| 3 | `GET` | `/api/v1/requirements/{id}/questions` | When `pendingQuestionsCount > 0` |
| 4 | `POST` | `/api/v1/requirements/{id}/questions/answers` | Q&A form submit |
| 5 | `GET` | `/api/v1/requirements/{id}/pcsf` | When status is `UNDER_REVIEW` (0 questions) |
| 6 | `PATCH` | `/api/v1/requirements/{id}/pcsf/fields` | Inline field edit |
| 7 | `POST` | `/api/v1/requirements/{id}/pcsf/validate` | "Validate" button |
| 8 | `POST` | `/api/v1/requirements/{id}/approve` | "Approve" button |
| 9 | `POST` | `/api/v1/requirements/{id}/change-request` | "Request Changes" form |
| 10 | `POST` | `/api/v1/requirements/{id}/regenerate` | After submitting change request |
