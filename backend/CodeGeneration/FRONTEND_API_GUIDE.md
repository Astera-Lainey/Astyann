# Code Generation — Frontend API Guide

How to drive the Code Generation service (`:8086`) from the Angular frontend.

- **Base URL:** `${environment.apiBaseUrl}/api/v1/code` → `http://localhost:8080/api/v1/code` in dev
- **Routing:** the gateway forwards `/api/v1/code/**` to `:8086` unchanged (no prefix stripping)
- **Auth:** every endpoint requires a JWT. `jwt.interceptor.ts` attaches it automatically — nothing to do per call.

---

## 1. Response envelope

Every JSON endpoint returns the platform envelope, so services unwrap `data` before returning:

```ts
{ "status": 200, "message": "Validation completed.", "data": { /* payload */ } }
```

```ts
return this.http.post<ApiResponse<ValidationReport>>(url, body)
  .pipe(map(res => res.data));
```

The **only** exception is `GET /{projectId}/download`, which returns raw bytes (`application/octet-stream`).

---

## 2. Core concepts

### Layers

Three independent artifacts, each generated, validated, and approved separately:

| Value | Contents |
|---|---|
| `BACKEND` | Spring Boot 3.3 project (entities, repositories, services, controllers, security, tests) |
| `FRONTEND` | Angular 21 project (models, services, list/form components, shell, CLI scaffold) |
| `INFRASTRUCTURE` | `docker-compose.yml`, `.env.example`, DB schema, Dockerfiles, nginx config |

> Enum values are **case-sensitive**: `BACKEND` works, `backend` returns `400`.

### Status lifecycle

```
GENERATING ──► GENERATED ──► APPROVED
     │              │  ▲
     │              ▼  │
     │        PENDING_APPROVAL  (a change-request was submitted)
     ▼
  FAILED
```

| Status | Meaning for the UI |
|---|---|
| `GENERATING` | Work in flight — show a spinner, keep polling, disable actions |
| `GENERATED` | Ready to download / validate / approve |
| `PENDING_APPROVAL` | Change request recorded; call `regenerate` to apply it |
| `APPROVED` | Locked. `regenerate` is refused until a change-request is submitted |
| `FAILED` | Show `lastError` |

### Generation is asynchronous

`POST /generate` returns **`202`** immediately with every selected layer in `GENERATING`. It does **not**
wait for the work to finish. Poll `GET /{projectId}` until no layer is `GENERATING` to learn the outcome.

---

## 3. Endpoints

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/{projectId}/generate` | Start generation (202, async) |
| `GET` | `/{projectId}` | List layers + status (poll this) |
| `GET` | `/{projectId}/download?layer=` | Download a layer's ZIP |
| `POST` | `/{projectId}/validate` | Compile / test / completeness report |
| `POST` | `/{projectId}/approve` | Approve layers, snapshot them |
| `POST` | `/{projectId}/change-request?layer=` | Record feedback |
| `POST` | `/{projectId}/regenerate` | Re-run generation |
| `POST` | `/{projectId}/versions/{snapshotId}/activate?layer=` | Restore an archived version |

### Selecting layers

`generate`, `validate`, `approve`, and `regenerate` all accept a layer filter. **Omit it to act on
every layer.** Three equivalent spellings:

```
?layer=BACKEND                      # single
?layer=BACKEND&layer=FRONTEND       # repeated
?layers=BACKEND,FRONTEND            # comma-separated
```

`generate`, `approve`, and `regenerate` also accept `{ "layers": ["BACKEND"] }` in the body; body and
query params are merged and de-duplicated.

Scoping matters for speed: `?layer=BACKEND` on `/validate` skips `npm install` + `ng build` entirely.

---

### `POST /{projectId}/generate` → `202`

Body optional. Requires the PCSF **and** all documents to be approved (see §5).

```json
{
  "status": 202,
  "message": "Code generation started.",
  "data": {
    "artifacts": [
      { "codeId": "…", "layer": "BACKEND", "status": "GENERATING", "downloadUrl": null,
        "lastError": null, "genDate": "2026-07-30T10:15:03",
        "modulesTotal": null, "modulesPatched": null, "stubMethodsRemaining": null }
    ]
  }
}
```

### `GET /{projectId}` → `200`

Same `artifacts` shape. Once BACKEND finishes, the AI logic-injection counters are populated:

| Field | Meaning |
|---|---|
| `modulesTotal` | Modules the AI was asked to implement |
| `modulesPatched` | Modules it successfully implemented |
| `stubMethodsRemaining` | **Blocking issues**: methods still throwing `UnsupportedOperationException`, plus any file that fails to parse. `0` = verifiably complete |

`stubMethodsRemaining > 0` means the code compiles but will throw at runtime — surface this clearly,
because approval will be refused.

### `GET /{projectId}/download?layer=BACKEND` → `200` (binary)

Returns the ZIP with `Content-Disposition: attachment; filename="backend.zip"`.
Only available while the layer is `GENERATED` or `APPROVED` — otherwise `404`.

### `POST /{projectId}/validate` → `200`

⚠️ **Slow.** Runs Maven compile, an AI fix loop, the generated test suite, then `npm install` +
`ng build`. Expect **minutes**. See the gateway timeout warning in §6.

```json
{
  "projectId": "…",
  "validationStatus": "FAILED",
  "attemptsUsed": 1,
  "layersValidated": ["BACKEND"],
  "checks": [
    { "name": "layer:BACKEND",   "status": "PASSED", "message": "archive present" },
    { "name": "logic:BACKEND",   "status": "FAILED", "message": "2 stub method(s) and 0 unparseable file(s)" },
    { "name": "compile:BACKEND", "status": "PASSED", "message": "compiled successfully on attempt 1" },
    { "name": "test:BACKEND",    "status": "PASSED", "message": "7 test(s) passed" }
  ],
  "remainingIssues": ["BACKEND has 2 un-implemented stub method(s): [XServiceImpl.doThing]"]
}
```

`validationStatus` is `PASSED` only when `remainingIssues` is empty. `layersValidated` echoes the
effective `?layer=` filter — read it if you need to confirm the scope took effect.

**Check names and how to render them:**

| `name` | What failed if `FAILED` |
|---|---|
| `layer:<LAYER>` | Archive missing on disk, or layer is `FAILED`/still generating |
| `logic:BACKEND` | Un-implemented stubs or unparseable files — **not** fixable by retrying compile |
| `compile:BACKEND` | Still won't compile after the AI fix attempts |
| `test:BACKEND` | Generated tests failed (report-only — never auto-"fixed") |
| `compile:FRONTEND` | `npm install` or `ng build` failed |

`status` is one of `PASSED` / `WARNING` / `FAILED`. **`WARNING` never fails the report** — it means a
phase was skipped (disabled by config, or Maven/Node not on PATH). Style it distinctly from `FAILED`.

### `POST /{projectId}/approve` → `200`

Body optional: `{ "layers": ["BACKEND"], "approvalComment": "LGTM" }`

```json
{ "snapshotIds": ["…"], "updatedCount": 1, "allLayersApproved": false }
```

Use `allLayersApproved` to decide whether the project can move to the next pipeline stage.

### `POST /{projectId}/change-request?layer=BACKEND` → `200`

Body **required**: `{ "instructions": "Add pagination to the list endpoint" }` (must be non-blank).
Sets the layer to `PENDING_APPROVAL`. Nothing regenerates until you call `regenerate`.

```json
{ "changeRequestId": "…", "status": "PENDING_APPROVAL" }
```

### `POST /{projectId}/regenerate` → `202`

Same async contract as `generate`. With no filter, regenerates every **non-`APPROVED`** layer.
Explicitly naming an `APPROVED` layer returns `409` — submit a change-request first.

### `POST /{projectId}/versions/{snapshotId}/activate?layer=BACKEND` → `200`

Restores an archived version; returns the layer as `APPROVED`.

---

## 4. Angular integration

### Models — `core/models/code.models.ts`

```ts
export type CodeLayer = 'BACKEND' | 'FRONTEND' | 'INFRASTRUCTURE';
export type CodeStatus = 'GENERATING' | 'GENERATED' | 'PENDING_APPROVAL' | 'APPROVED' | 'FAILED';

export interface GeneratedCode {
  codeId: string;
  layer: CodeLayer;
  status: CodeStatus;
  downloadUrl: string | null;
  lastError: string | null;
  genDate: string;
  modulesTotal: number | null;
  modulesPatched: number | null;
  stubMethodsRemaining: number | null;
}

export interface ValidationCheck {
  name: string;
  status: 'PASSED' | 'WARNING' | 'FAILED';
  message: string;
}

export interface ValidationReport {
  projectId: string;
  validationStatus: 'PASSED' | 'FAILED';
  attemptsUsed: number;
  layersValidated: string[];
  checks: ValidationCheck[];
  remainingIssues: string[];
}

export interface ApproveResult {
  snapshotIds: string[];
  updatedCount: number;
  allLayersApproved: boolean;
}
```

### Service — `core/services/code.service.ts`

```ts
@Injectable({ providedIn: 'root' })
export class CodeService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/api/v1/code`;

  /** Layer filter as repeated ?layer= params; omit for all layers. */
  private layerParams(layers?: CodeLayer[]): HttpParams {
    let params = new HttpParams();
    for (const layer of layers ?? []) params = params.append('layer', layer);
    return params;
  }

  generate(projectId: string, layers?: CodeLayer[]): Observable<GeneratedCode[]> {
    return this.http
      .post<ApiResponse<{ artifacts: GeneratedCode[] }>>(`${this.base}/${projectId}/generate`, {},
        { params: this.layerParams(layers) })
      .pipe(map(res => res.data.artifacts));
  }

  list(projectId: string): Observable<GeneratedCode[]> {
    return this.http
      .get<ApiResponse<{ artifacts: GeneratedCode[] }>>(`${this.base}/${projectId}`)
      .pipe(map(res => res.data.artifacts));
  }

  validate(projectId: string, layers?: CodeLayer[]): Observable<ValidationReport> {
    return this.http
      .post<ApiResponse<ValidationReport>>(`${this.base}/${projectId}/validate`, {},
        { params: this.layerParams(layers) })
      .pipe(map(res => res.data));
  }

  approve(projectId: string, layers?: CodeLayer[], approvalComment?: string): Observable<ApproveResult> {
    return this.http
      .post<ApiResponse<ApproveResult>>(`${this.base}/${projectId}/approve`,
        { layers: layers ?? null, approvalComment: approvalComment ?? null })
      .pipe(map(res => res.data));
  }

  changeRequest(projectId: string, layer: CodeLayer, instructions: string): Observable<void> {
    return this.http
      .post<ApiResponse<unknown>>(`${this.base}/${projectId}/change-request`, { instructions },
        { params: new HttpParams().set('layer', layer) })
      .pipe(map(() => void 0));
  }

  regenerate(projectId: string, layers?: CodeLayer[]): Observable<GeneratedCode[]> {
    return this.http
      .post<ApiResponse<{ artifacts: GeneratedCode[] }>>(`${this.base}/${projectId}/regenerate`, {},
        { params: this.layerParams(layers) })
      .pipe(map(res => res.data.artifacts));
  }

  /** Binary — must bypass the JSON envelope with responseType: 'blob'. */
  download(projectId: string, layer: CodeLayer): Observable<Blob> {
    return this.http.get(`${this.base}/${projectId}/download`, {
      params: new HttpParams().set('layer', layer),
      responseType: 'blob'
    });
  }
}
```

### Polling until generation settles

```ts
readonly artifacts = signal<GeneratedCode[]>([]);

pollUntilSettled(projectId: string): void {
  timer(0, 5000)
    .pipe(
      switchMap(() => this.codeService.list(projectId)),
      tap(artifacts => this.artifacts.set(artifacts)),
      takeWhile(artifacts => artifacts.some(a => a.status === 'GENERATING'), true),
      takeUntilDestroyed(this.destroyRef)
    )
    .subscribe();
}
```

`takeWhile(..., true)` keeps the final emission — the one that actually shows the outcome.

### Triggering a download

```ts
save(projectId: string, layer: CodeLayer): void {
  this.codeService.download(projectId, layer).subscribe(blob => {
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `${layer.toLowerCase()}.zip`;
    a.click();
    URL.revokeObjectURL(url);
  });
}
```

A plain `<a href>` to the download URL will **fail** — the interceptor can't attach the JWT to a
browser navigation. Always fetch as a blob.

---

## 5. Errors

| Status | When | Suggested UI |
|---|---|---|
| `400` | Bad layer value (e.g. `?layer=backend`) or malformed UUID | Developer error — fix the call |
| `404` | No code for the project, or download attempted while `GENERATING`/`FAILED` | "Nothing to download yet" |
| `409` | Documents not approved · layer still `GENERATING` · `regenerate` on `APPROVED` · **stubs remain on approve** | Show `message` verbatim — it is user-facing and explains the next step |
| `422` | PCSF (requirements) not approved | Link back to the requirements step |
| `503` | RequirementService / DocumentService unreachable | "Service unavailable, retry" |

All errors use the same envelope, so `message` is always safe to display:

```json
{ "status": 409, "message": "BACKEND has 2 un-implemented stub method(s); regenerate or fix the logic before approving.", "data": null }
```

### The two gates before generation

`generate` and `regenerate` both verify:

1. The project's **PCSF is `APPROVED`** → else `422`
2. **Every document is `APPROVED`** → else `409`

Check these before enabling the button, so users aren't surprised.

---

## 6. Gotchas

**`/validate` is long-running — don't impose a client timeout.** The gateway no longer applies a
response timeout (the global `response-timeout` was removed from `APIGateway/application.yml`), so the
request stays open until the service answers. Validation runs Maven, the AI fix loop, the generated
test suite and an Angular build, so **several minutes is normal**.

For the UI this means:

- Do not set an HTTP timeout on the `validate()` call, and do not retry on slowness — a retry starts a
  second full build.
- Show indeterminate progress and keep the request in flight; there is no partial/streaming response.
- `connect-timeout` is still 5s, so an unreachable service fails fast rather than hanging.
- If a proxy or browser does abort the connection, the run continues server-side. Poll
  `GET /{projectId}` and call `validate` again once nothing is `GENERATING`.

**A green `compile` does not mean working code.** Stub bodies compile perfectly. Always surface
`logic:BACKEND` / `stubMethodsRemaining` — that is the only signal distinguishing a finished backend
from one whose AI implementation silently failed.

**`WARNING` ≠ failure.** It means a phase was skipped (feature disabled, or Maven/Node missing).
Don't render it as an error.

**`generate` on a `GENERATING` layer returns `409`.** Disable the button while polling.

**`approve` is blocked while stubs remain** (`codegen.approve.require-complete=true`). Offer
"regenerate" as the recovery path, not a retry of approve.

---

## 7. Recommended UI flow

```
1. Requirements + documents approved
        ↓
2. POST /generate  (202)  ──► poll GET /{projectId} every 5s until nothing is GENERATING
        ↓
3. Show per-layer cards: status, modulesPatched/modulesTotal, stubMethodsRemaining
        ↓
4. POST /validate?layer=BACKEND  ──► render checks; block approve if validationStatus = FAILED
        ↓
5a. Happy path:  POST /approve  ──► allLayersApproved → next stage
5b. Needs work:  POST /change-request  ──► POST /regenerate  ──► back to 2
        ↓
6. GET /download?layer=…  (blob)
```
