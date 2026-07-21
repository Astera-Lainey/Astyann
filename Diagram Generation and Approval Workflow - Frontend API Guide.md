# Diagram Generation & Approval Workflow — Frontend API Guide

This guide walks through the full "UML Diagrams" flow (Sprint 4) as actually implemented, from the frontend's perspective: generating diagrams, reviewing them, approving them, requesting changes, and regenerating with feedback.

## Architecture

```
Frontend
   │  Authorization: Bearer <JWT>
   ▼
API Gateway (:8080)
   │  /api/v1/uml/**  ──────────────►  DiagramGeneratorService (:8084)
   │                                       │
   │                                       ├──► RequirementService (:8083)  — gates on PCSF approval
   │                                       ├──► RAGService (:8090)          — generation context + indexing
   │                                       ├──► AIOrchestrator (:8089)      — PlantUML generation (Ollama)
   │                                       ├──► Kroki (external)            — PlantUML → SVG/PNG
   │                                       └──► VersionService (:8087)      — version snapshots on approve
   │
   │  /api/v1/versions/**  ─────────►  VersionService (:8087)
```

The frontend only ever talks to the **API Gateway**; all downstream calls above are server-to-server. In dev, you can also hit DiagramGeneratorService/VersionService directly on their own ports (no JWT required there — auth is enforced at the gateway).

**Base URL used below**: `http://localhost:8080` (via gateway). Every request needs `Authorization: Bearer <JWT>` in production; omit it if calling the service ports directly in dev.

## Precondition

The project's requirements (PCSF, from RequirementService) must be `APPROVED` before any diagram generation or regeneration will succeed. If not, every generate/regenerate call returns:

```json
{ "status": 422, "message": "Requirements for project <id> are not approved yet." }
```

## Diagram status lifecycle

```
                 generate
                    │
                    ▼
           ┌─────────────────┐
     ┌────►│ PENDING_APPROVAL│◄────────────┐
     │     └────────┬────────┘             │
     │              │ approve              │ change-request
     │              ▼                      │ (stores instructions,
     │        ┌───────────┐                │  resets to PENDING_APPROVAL —
     │        │ APPROVED  │────────────────┘  this is the ONLY way out of APPROVED)
     │        └───────────┘
     │
     │ generation/render failure (after 1 auto self-correction retry)
     │
┌─────────┐
│ FAILED  │──── change-request or regenerate ────► back into the flow above
└─────────┘
```

- `change-request` works on any status, **including `APPROVED`** — it's the only way to move an approved diagram back into an editable state.
- `regenerate` **rejects `APPROVED` diagrams outright** (`409`) — you must submit a `change-request` first (which resets the diagram to `PENDING_APPROVAL`), then `regenerate` to actually apply it. This makes approval a real checkpoint: nothing can silently regenerate an approved diagram without recorded feedback.
- `FAILED` diagrams still have a real `diagramId` and can be retried via `change-request` + `regenerate`, or plain `regenerate`.

---

## 1. Generate diagrams

Kicks off PlantUML generation + Kroki rendering for one or more diagram types. **Asynchronous** — returns immediately with every requested type in `GENERATING` status; it does not wait for the AI+Kroki pipelines to finish. (An earlier synchronous version blocked until every type succeeded or failed, which routinely outlived the gateway's 60s response timeout across a full 10-type batch.)

```
POST /api/v1/uml/{projectId}/generate
Content-Type: application/json
```

**Body** (all fields optional):
```json
{
  "diagramTypes": ["USE_CASE", "DESIGN_CLASS", "COMPONENT", "DEPLOYMENT"],
  "renderFormat": "SVG"
}
```
- Omit `diagramTypes` (or send `{}`) to generate all 10 types: `USE_CASE, BUSINESS_CLASS, DESIGN_CLASS, ACTIVITY, BUSINESS_SEQUENCE, DESIGN_SEQUENCE, COMPONENT, DEPLOYMENT, PACKAGE, ENTITY_RELATIONSHIP`.
- `renderFormat`: `"SVG"` (default) or `"PNG"`.

**Response `202`**:
```json
{
  "status": 202,
  "message": "Diagram generation started.",
  "data": {
    "diagrams": [
      {
        "diagramId": "8f14e...",
        "type": "USE_CASE",
        "status": "GENERATING",
        "renderUrl": "/api/v1/uml/<projectId>/8f14e.../render",
        "lastError": null,
        "previousVersionId": null
      }
    ]
  }
}
```
Poll `GET /{projectId}` until nothing is left `GENERATING` to find out how each type turned out — `PENDING_APPROVAL` on success (Kroki rendering includes an automatic self-correction retry internally before giving up), or `FAILED` with `lastError` populated. A `FAILED` type still has a real `diagramId` you can call `change-request`/`regenerate` on directly.

---

## 2. List diagrams

```
GET /api/v1/uml/{projectId}
GET /api/v1/uml/{projectId}?type=DESIGN_CLASS
GET /api/v1/uml/{projectId}?status=FAILED
```

**Response `200`**:
```json
{
  "status": 200,
  "message": "Diagrams retrieved.",
  "data": {
    "diagrams": [
      {
        "diagramId": "8f14e...",
        "type": "USE_CASE",
        "status": "PENDING_APPROVAL",
        "updatedAt": "2026-07-09T10:15:00",
        "lastError": null
      }
    ]
  }
}
```

## 3. Render a diagram (view the image)

```
GET /api/v1/uml/{projectId}/{diagramId}/render?format=SVG
```
Returns the raw image bytes (`Content-Type: image/svg+xml` or `image/png`), not the JSON envelope — bind this directly to an `<img>`/object viewer. If `format` differs from what was cached, it's re-rendered on the fly from the stored PlantUML source (not persisted). `404` if the diagram doesn't exist for that project.

---

## 4. Approve diagrams

### Bulk (all or a selected subset)
```
POST /api/v1/uml/{projectId}/approve
Content-Type: application/json
```
```json
{
  "diagramIds": ["8f14e...", "b02f1..."],
  "approvalComment": "Approved during technical review"
}
```
Omit `diagramIds` (or send `{}`) to approve **all** currently `PENDING_APPROVAL` diagrams for the project.

**Response `200`**:
```json
{
  "status": 200,
  "message": "Diagrams approved.",
  "data": {
    "snapshotIds": ["9c31...", "7ab4..."],
    "updatedCount": 2,
    "allDiagramsApproved": true
  }
}
```
- One version snapshot is created per approved diagram (via VersionService) — `snapshotIds` has one entry per diagram actually approved. If VersionService is unreachable for one diagram, that diagram is still approved and just missing from `snapshotIds` (logged server-side, not surfaced as an error).
- `allDiagramsApproved`: whether *every* diagram in the project is now `APPROVED` (useful to unlock the next workflow stage, e.g. documentation generation).
- `409` if none of the targeted diagrams are currently `PENDING_APPROVAL`. `404` if the project has no diagrams at all.

### Individual
```
POST /api/v1/uml/{projectId}/{diagramId}/approve
Content-Type: application/json
```
```json
{ "approvalComment": "Looks good" }
```
**Response `200`**:
```json
{
  "status": 200,
  "message": "Diagram approved.",
  "data": {
    "diagramId": "8f14e...",
    "type": "USE_CASE",
    "status": "APPROVED",
    "renderUrl": "/api/v1/uml/<projectId>/8f14e.../render",
    "lastError": null,
    "previousVersionId": null
  }
}
```
- `404` if `diagramId` doesn't belong to the project. `409` if it's not currently `PENDING_APPROVAL`.

---

## 5. Request changes on a diagram

Records free-text feedback without regenerating yet — call `regenerate` afterward to actually apply it.

```
POST /api/v1/uml/{projectId}/{diagramId}/change-request
Content-Type: application/json
```
```json
{ "instructions": "Add a Supplier class linked to Product with a one-to-many relationship." }
```

**Response `200`**:
```json
{
  "status": 200,
  "message": "Change request recorded. Call regenerate to apply it.",
  "data": {
    "changeRequestId": "3fa1...",
    "status": "PENDING_APPROVAL"
  }
}
```
- Works on any status, **including `APPROVED`** — it resets the diagram back to `PENDING_APPROVAL` so it's regenerate-able again with this feedback. This is the only way to move an approved diagram back into an editable state.
- `changeRequestId` is a fresh, non-persisted identifier for display purposes only — regenerate doesn't need it back, it just applies whatever instructions are currently stored.

## 6. Regenerate

```
POST /api/v1/uml/{projectId}/{diagramId}/regenerate
Content-Type: application/json
```
```json
{ "renderFormat": "PNG" }
```
Body is optional; omit `renderFormat` to keep the diagram's existing format.

**Asynchronous** — returns immediately with the diagram in `GENERATING` status; it does not wait for the AI+Kroki pipeline to finish (the initial synchronous version routinely outlived the gateway's 60s response timeout, aborting the client connection while generation was still running server-side).

**Response `202`**:
```json
{
  "status": 202,
  "message": "Diagram regeneration started.",
  "data": {
    "diagramId": "8f14e...",
    "type": "DESIGN_CLASS",
    "status": "GENERATING",
    "renderUrl": "/api/v1/uml/<projectId>/8f14e.../render",
    "lastError": null,
    "previousVersionId": "9c31..."
  }
}
```
- If the diagram had pending `change-request` instructions, they're applied (feedback-driven regeneration) and then cleared. Otherwise it's a plain from-scratch regeneration (useful for retrying a `FAILED` diagram with no feedback).
- `previousVersionId`: the diagram's prior *approved* version snapshot (`snapId` from VersionService), or `null` if it was never approved before.
- Poll `GET /{projectId}` until the diagram is no longer `GENERATING` to see the outcome — `PENDING_APPROVAL` on success, or `FAILED` with `lastError` populated on failure.
- `409` if the diagram is currently `APPROVED` — call `change-request` first, which resets it to `PENDING_APPROVAL` and unblocks this call. Also `409` if it's already `GENERATING`.
- `422` if the project's requirements are no longer `APPROVED`.

---

## 7. (Optional) Version history

Useful for a "diagram history" panel. Talks to VersionService directly (also reachable through the gateway at `/api/v1/versions/**`).

```
GET /api/v1/versions/{projectId}
```
```json
{
  "status": 200,
  "message": "Timeline retrieved.",
  "data": {
    "timelineId": "...",
    "projectId": "...",
    "creationDate": "2026-07-09T09:00:00",
    "snapshots": [
      {
        "snapId": "9c31...",
        "versionNumber": 1,
        "snapDate": "2026-07-09T10:20:00",
        "triggerReason": "Diagram approved",
        "artifactType": "DIAGRAM",
        "artifactId": "8f14e...",
        "diagramId": "8f14e...",
        "diagramType": "USE_CASE",
        "active": false
      },
      {
        "snapId": "7ab4...",
        "versionNumber": 2,
        "diagramId": "8f14e...",
        "diagramType": "USE_CASE",
        "active": true
      }
    ]
  }
}
```
Each diagram *type* has its own independent version sequence (`versionNumber` 1, 2, 3...) and its own `active` snapshot — filter client-side by `diagramId` to build a per-diagram history. `GET /api/v1/versions/{projectId}/snapshots` returns the same list flattened (no timeline wrapper); `GET /api/v1/versions/snapshots/{snapId}` fetches one snapshot directly.

### Render a specific version (independent of what's currently active)

```
GET /api/v1/uml/{projectId}/{diagramId}/versions/{snapId}/render?format=SVG
```
Returns the raw image bytes for exactly that snapshot's archived content, same response shape as the regular render endpoint. Unlike `GET /{projectId}/{diagramId}/render`, this is completely unaffected by which snapshot is currently active/live — use it for a "download vN" button in a history panel so it always returns vN's actual content, not whatever the diagram currently shows. `404` if `diagramId` doesn't belong to the project, or if `snapId` has no archived content for that diagram (e.g. it predates this endpoint's rollout).

### Activate a specific version (rollback)

Two different endpoints exist here — use the right one depending on whether you want a real rollback or just a bookkeeping flag flip.

**Real rollback** — restores an archived (previously-approved) version as the diagram's actual current content. After this call, `GET /{projectId}/{diagramId}/render` and the diagram's `status` immediately reflect the restored version — this is what a "Restore this version" button in a history panel should call.

```
POST /api/v1/uml/{projectId}/{diagramId}/versions/{snapId}/activate
```
**Response `200`**:
```json
{
  "status": 200,
  "message": "Diagram version restored.",
  "data": {
    "diagramId": "8f14e...",
    "type": "USE_CASE",
    "status": "APPROVED",
    "renderUrl": "/api/v1/uml/<projectId>/8f14e.../render",
    "lastError": null
  }
}
```
- Restores that snapshot's stored source + rendered image as the diagram's live content and sets its status to `APPROVED` (it was approved once already).
- Also flips the snapshot's `active` flag in VersionService (best-effort — if that call fails, the restore itself still succeeds; the timeline flag may lag until the next successful activate).
- Because only the rendered image is archived per version (not a full render-format-independent history), a restored diagram can be viewed/downloaded/re-approved as-is, but can't be format-converted (SVG↔PNG) or edited via `change-request` until it's regenerated fresh.
- `404` if `diagramId` doesn't belong to the project, or if `snapId` has no archived content for that diagram (e.g. it belongs to a different diagram, or predates this endpoint's rollout).

**Metadata-only flag flip** — flips which snapshot is flagged `active` in the timeline without touching the diagram's actual live content/status. Mostly useful for VersionService's own bookkeeping (and for artifact types with no rollback endpoint, e.g. CODE/DEPLOYMENT); the diagram workflow above generally wants the real-rollback endpoint instead.

```
POST /api/v1/versions/snapshots/{snapId}/activate
```
**Response `200`**:
```json
{
  "status": 200,
  "message": "Snapshot activated.",
  "data": {
    "snapId": "9c31...",
    "versionNumber": 1,
    "artifactType": "DIAGRAM",
    "artifactId": "8f14e...",
    "diagramId": "8f14e...",
    "diagramType": "USE_CASE",
    "active": true
  }
}
```
- Deactivates whichever snapshot was previously active for the same diagram (same `artifactId`) and activates this one instead.
- Idempotent — activating an already-active snapshot is a no-op success, not an error.
- `404` if `snapId` doesn't exist.

---

## Status code reference

| Code | Meaning here |
|---|---|
| 200 | Success (list/approve/change-request/render) |
| 202 | Diagram generation/regeneration started |
| 404 | Diagram/project/timeline/snapshot not found |
| 409 | Invalid state transition (regenerate on `APPROVED` without a prior change-request; regenerate/approve while still `GENERATING`; approve with nothing `PENDING_APPROVAL`) |
| 422 | Requirements not `APPROVED` (generate/regenerate gate) |
| 503 | A downstream dependency (AI/Kroki/RequirementService) is unreachable |
| 500 | Unexpected server error |

## Typical frontend flow

1. `POST .../generate` (all types) → render the returned `diagrams[]` grid using `renderUrl`; show `failures[]` with a "retry" action per failed type.
2. User reviews, optionally submits `change-request` on specific diagrams → then `regenerate` those.
3. User clicks "Approve all" → `POST .../approve` with `{}` → check `allDiagramsApproved` to unlock the next stage (documentation generation, Sprint 5).
4. Optionally show a version-history timeline per diagram via VersionService.