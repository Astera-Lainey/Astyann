import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, map } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiEnvelope } from '../models/auth.models';
import {
  ApproveDiagramsRequest,
  ApproveDiagramsResponse,
  ChangeRequestResponseData,
  DiagramListItem,
  DiagramSummary,
  GenerateDiagramsData,
  GenerateDiagramsRequest,
  RegenerateDiagramRequest,
  RenderFormat,
} from '../models/diagram.models';

@Injectable({ providedIn: 'root' })
export class DiagramService {
  private readonly baseUrl = `${environment.apiBaseUrl}/uml`;

  constructor(private readonly http: HttpClient) {}

  /**
   * GET /api/v1/uml/{projectId}
   * Lists diagrams for a project. Returns an empty list if none exist yet —
   * never 404s, so this is safe to call unconditionally on workspace load.
   */
  list(projectId: string): Observable<DiagramListItem[]> {
    return this.http
      .get<ApiEnvelope<{ diagrams: DiagramListItem[] }>>(`${this.baseUrl}/${projectId}`)
      .pipe(map((res) => res.data.diagrams));
  }

  /**
   * POST /api/v1/uml/{projectId}/generate
   * Asynchronous — responds immediately (202) with every requested type
   * saved as a GENERATING placeholder; it does not wait for the AI+Kroki
   * pipelines to finish. Callers must poll list() until no diagram is left
   * in GENERATING status. Requires PCSF status APPROVED (422 otherwise).
   */
  generate(projectId: string, request: GenerateDiagramsRequest = {}): Observable<GenerateDiagramsData> {
    return this.http
      .post<ApiEnvelope<GenerateDiagramsData>>(`${this.baseUrl}/${projectId}/generate`, request)
      .pipe(map((res) => res.data));
  }

  /**
   * POST /api/v1/uml/{projectId}/{diagramId}/regenerate
   * Synchronous and blocking. Returns 200 even when regeneration fails —
   * callers must inspect the returned DiagramSummary.status, not just the
   * HTTP status code.
   */
  regenerate(
    projectId: string,
    diagramId: string,
    request: RegenerateDiagramRequest = {},
  ): Observable<DiagramSummary> {
    return this.http
      .post<ApiEnvelope<DiagramSummary>>(`${this.baseUrl}/${projectId}/${diagramId}/regenerate`, request)
      .pipe(map((res) => res.data));
  }

  /**
   * POST /api/v1/uml/{projectId}/{diagramId}/change-request
   * Only records instructions and resets the diagram to PENDING_APPROVAL —
   * does not regenerate by itself. Always follow with regenerate().
   */
  submitChangeRequest(
    projectId: string,
    diagramId: string,
    instructions: string,
  ): Observable<ChangeRequestResponseData> {
    return this.http
      .post<ApiEnvelope<ChangeRequestResponseData>>(
        `${this.baseUrl}/${projectId}/${diagramId}/change-request`,
        { instructions },
      )
      .pipe(map((res) => res.data));
  }

  /**
   * POST /api/v1/uml/{projectId}/approve
   * Omit diagramIds to approve every PENDING_APPROVAL diagram in the project.
   */
  approve(projectId: string, request: ApproveDiagramsRequest = {}): Observable<ApproveDiagramsResponse> {
    return this.http
      .post<ApiEnvelope<ApproveDiagramsResponse>>(`${this.baseUrl}/${projectId}/approve`, request)
      .pipe(map((res) => res.data));
  }

  /**
   * POST /api/v1/uml/{projectId}/{diagramId}/approve
   * Approves a single diagram directly via its dedicated endpoint (distinct
   * from the bulk approve() above, which this call does not use).
   */
  approveOne(projectId: string, diagramId: string): Observable<DiagramSummary> {
    return this.http
      .post<ApiEnvelope<DiagramSummary>>(`${this.baseUrl}/${projectId}/${diagramId}/approve`, {})
      .pipe(map((res) => res.data));
  }

  /**
   * GET /api/v1/uml/{projectId}/{diagramId}/render
   * Sits behind the JWT gateway — must go through HttpClient (so the jwt
   * interceptor attaches the bearer token) as a blob, never as a raw <img src>.
   * Callers own the resulting Blob's lifecycle: wrap with URL.createObjectURL()
   * and revoke it when no longer needed.
   */
  renderBlob(projectId: string, diagramId: string, format: RenderFormat = 'SVG'): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/${projectId}/${diagramId}/render`, {
      params: { format },
      responseType: 'blob',
    });
  }
}
