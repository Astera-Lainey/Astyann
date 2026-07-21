import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, map } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiEnvelope } from '../models/auth.models';
import {
  ApproveDocumentRequest,
  ApproveDocumentResponse,
  ChangeRequestResponseData,
  DocumentListItem,
  DocumentSummary,
  GenerateDocumentsData,
  GenerateDocumentsRequest,
} from '../models/document.models';

@Injectable({ providedIn: 'root' })
export class DocumentService {
  private readonly baseUrl = `${environment.apiBaseUrl}/documents`;

  constructor(private readonly http: HttpClient) {}

  /**
   * GET /api/v1/documents/{projectId}
   * Lists documents for a project. Returns an empty list if none exist yet —
   * never 404s, so this is safe to call unconditionally on tab load.
   */
  list(projectId: string): Observable<DocumentListItem[]> {
    return this.http
      .get<ApiEnvelope<{ documents: DocumentListItem[] }>>(`${this.baseUrl}/${projectId}`)
      .pipe(map((res) => res.data.documents));
  }

  /**
   * POST /api/v1/documents/{projectId}/generate
   * Asynchronous — responds immediately (202) with every requested type
   * saved as a GENERATING placeholder; it does not wait for the AI+merge
   * pipeline to finish. Callers must poll list() until no document is left
   * in GENERATING status. Requires PCSF status APPROVED and every diagram
   * APPROVED (422 / 409 otherwise).
   */
  generate(projectId: string, request: GenerateDocumentsRequest = {}): Observable<GenerateDocumentsData> {
    return this.http
      .post<ApiEnvelope<GenerateDocumentsData>>(`${this.baseUrl}/${projectId}/generate`, request)
      .pipe(map((res) => res.data));
  }

  /**
   * POST /api/v1/documents/{projectId}/{documentId}/regenerate
   * Synchronous and blocking. Returns 200 even when regeneration fails —
   * callers must inspect the returned DocumentSummary.status, not just the
   * HTTP status code. Applies any instructions recorded via a prior
   * submitChangeRequest() call, or performs a plain regeneration if none
   * are stored. Rejects APPROVED documents (submit a change-request first)
   * and documents still GENERATING.
   */
  regenerate(projectId: string, documentId: string): Observable<DocumentSummary> {
    return this.http
      .post<ApiEnvelope<DocumentSummary>>(`${this.baseUrl}/${projectId}/${documentId}/regenerate`, {})
      .pipe(map((res) => res.data));
  }

  /**
   * POST /api/v1/documents/{projectId}/{documentId}/change-request
   * Only records instructions and resets the document to PENDING_APPROVAL —
   * does not regenerate by itself. Always follow with regenerate().
   */
  submitChangeRequest(
    projectId: string,
    documentId: string,
    instructions: string,
  ): Observable<ChangeRequestResponseData> {
    return this.http
      .post<ApiEnvelope<ChangeRequestResponseData>>(
        `${this.baseUrl}/${projectId}/${documentId}/change-request`,
        { instructions },
      )
      .pipe(map((res) => res.data));
  }

  /**
   * POST /api/v1/documents/{projectId}/{documentId}/approve
   * Validates and approves a single document, creating a version snapshot.
   * There is no bulk-approve endpoint — approving several documents means
   * calling this once per documentId.
   */
  approve(
    projectId: string,
    documentId: string,
    request: ApproveDocumentRequest = {},
  ): Observable<ApproveDocumentResponse> {
    return this.http
      .post<ApiEnvelope<ApproveDocumentResponse>>(`${this.baseUrl}/${projectId}/${documentId}/approve`, request)
      .pipe(map((res) => res.data));
  }

  /**
   * GET /api/v1/documents/{projectId}/{documentId}/download
   * Returns the raw .docx bytes directly — this endpoint is NOT wrapped in
   * the ApiResponse envelope. Sits behind the JWT gateway — must go through
   * HttpClient (so the jwt interceptor attaches the bearer token) as a
   * blob. Callers own the resulting Blob's lifecycle: wrap with
   * URL.createObjectURL() and revoke it once the download is triggered.
   */
  download(projectId: string, documentId: string): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/${projectId}/${documentId}/download`, {
      responseType: 'blob',
    });
  }

  /**
   * POST /api/v1/documents/{projectId}/{documentId}/versions/{snapshotId}/activate
   * A real rollback — restores that archived version as the document's current live
   * file, unlike VersionService's own generic activate endpoint which only flips the
   * timeline's active flag. Download and this document's status immediately reflect
   * the restored version.
   */
  activateVersion(projectId: string, documentId: string, snapshotId: string): Observable<DocumentSummary> {
    return this.http
      .post<ApiEnvelope<DocumentSummary>>(
        `${this.baseUrl}/${projectId}/${documentId}/versions/${snapshotId}/activate`,
        {},
      )
      .pipe(map((res) => res.data));
  }

  /**
   * GET /api/v1/documents/{projectId}/{documentId}/versions/{snapshotId}/download
   * Downloads that specific archived version regardless of which one is currently
   * active/live — unlike download(), this is unaffected by activateVersion().
   * Use for a "download vN" action so it always returns vN's actual content.
   */
  downloadVersion(projectId: string, documentId: string, snapshotId: string): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/${projectId}/${documentId}/versions/${snapshotId}/download`, {
      responseType: 'blob',
    });
  }
}
