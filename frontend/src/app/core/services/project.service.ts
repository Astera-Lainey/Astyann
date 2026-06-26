import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, map } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiEnvelope } from '../models/auth.models';
import {
  ClarificationQuestion,
  CreateProjectRequest,
  GuidedQuestion,
  PatchFieldRequest,
  PcsfStatusResponse,
  PcsfValidateResponse,
  Project,
  ProjectSummary,
  SubmitAnswersRequest,
  SubmitAnswersResponseData,
  SubmitGuidedQuestionsRequest,
  SubmitGuidedQuestionsResponseData,
} from '../models/project.models';

@Injectable({ providedIn: 'root' })
export class ProjectService {
  private readonly baseUrl = `${environment.apiBaseUrl}/projects`;

  constructor(private readonly http: HttpClient) {}

  /**
   * GET /api/v1/projects/search?query=
   * Returns the authenticated user's projects filtered by title.
   * Omit query to return all. X-User-Id is injected by the API Gateway from the JWT.
   */
  search(query?: string): Observable<ProjectSummary[]> {
    let params = new HttpParams();
    if (query && query.trim()) params = params.set('query', query.trim());
    return this.http
      .get<ApiEnvelope<ProjectSummary[]>>(`${this.baseUrl}/search`, { params })
      .pipe(map((res) => res.data));
  }

  /**
   * POST /api/v1/projects (multipart/form-data)
   * Creates a new project. X-User-Id is injected by the API Gateway from the JWT.
   * Backend expects parts: title, description (optional), document (PDF or DOCX).
   */
  create(request: CreateProjectRequest): Observable<Project> {
    const formData = new FormData();
    formData.append('title', request.title);
    if (request.description) formData.append('description', request.description);
    formData.append('document', request.specificationFile);
    return this.http
      .post<ApiEnvelope<Project>>(this.baseUrl, formData)
      .pipe(map((res) => res.data));
  }

  /**
   * GET /api/v1/projects/{projectId}
   */
  getById(projectId: string): Observable<Project> {
    return this.http
      .get<ApiEnvelope<Project>>(`${this.baseUrl}/${projectId}`)
      .pipe(map((res) => res.data));
  }

  /**
   * PUT /api/v1/projects/{projectId}
   * Updates project title, description, or status.
   */
  update(
    projectId: string,
    dto: { title?: string; description?: string; status?: string },
  ): Observable<Project> {
    return this.http
      .put<ApiEnvelope<Project>>(`${this.baseUrl}/${projectId}`, dto)
      .pipe(map((res) => res.data));
  }

  /**
   * DELETE /api/v1/projects/{projectId}
   * Returns 204 No Content.
   */
  delete(projectId: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${projectId}`).pipe(map(() => undefined));
  }

  /**
   * POST /api/v1/projects/{projectId}/generate
   * Triggers a generation pipeline (REQUIREMENTS | DOCUMENTS | UML | CODE | DEPLOYMENT | FULL).
   */
  triggerGeneration(projectId: string, type: string): Observable<void> {
    return this.http
      .post<ApiEnvelope<void>>(`${this.baseUrl}/${projectId}/generate`, { type })
      .pipe(map(() => undefined));
  }

  // ── Legacy Guided Questions (Sprint 2) ──────────────────────────────────────

  /**
   * GET /api/v1/projects/{projectId}/guided-questions
   * Returns legacy AI-generated guided questions. Fallback when PCSF has no pending questions.
   */
  getGuidedQuestions(projectId: string): Observable<GuidedQuestion[]> {
    return this.http
      .get<ApiEnvelope<GuidedQuestion[]>>(`${this.baseUrl}/${projectId}/guided-questions`)
      .pipe(map((res) => res.data));
  }

  /**
   * POST /api/v1/projects/{projectId}/guided-questions/answers
   * Submits answers to legacy guided questions.
   */
  submitGuidedQuestions(
    projectId: string,
    request: SubmitGuidedQuestionsRequest,
  ): Observable<SubmitGuidedQuestionsResponseData> {
    return this.http
      .post<ApiEnvelope<SubmitGuidedQuestionsResponseData>>(
        `${this.baseUrl}/${projectId}/guided-questions/answers`,
        request,
      )
      .pipe(map((res) => res.data));
  }

  // ── PCSF Clarification Questions ────────────────────────────────────────────

  /**
   * GET /api/v1/projects/{projectId}/questions
   * Returns pending PCSF clarification questions sorted by priority.
   */
  getQuestions(projectId: string): Observable<ClarificationQuestion[]> {
    return this.http
      .get<ApiEnvelope<ClarificationQuestion[]>>(`${this.baseUrl}/${projectId}/questions`)
      .pipe(map((res) => res.data));
  }

  /**
   * POST /api/v1/projects/{projectId}/questions/answers
   * Submits answers to PCSF clarification questions and triggers completeness re-analysis.
   */
  submitAnswers(
    projectId: string,
    request: SubmitAnswersRequest,
  ): Observable<SubmitAnswersResponseData> {
    return this.http
      .post<ApiEnvelope<SubmitAnswersResponseData>>(
        `${this.baseUrl}/${projectId}/questions/answers`,
        request,
      )
      .pipe(map((res) => res.data));
  }

  // ── PCSF Lifecycle ───────────────────────────────────────────────────────────

  /**
   * GET /api/v1/projects/{projectId}/pcsf/status
   * Lightweight polling endpoint returning pcsfStatus, completenessScore, pendingQuestionsCount.
   * Poll this while pcsfStatus is INFERRING.
   */
  getPcsfStatus(projectId: string): Observable<PcsfStatusResponse> {
    return this.http
      .get<ApiEnvelope<PcsfStatusResponse>>(`${this.baseUrl}/${projectId}/pcsf/status`)
      .pipe(map((res) => res.data));
  }

  /**
   * GET /api/v1/projects/{projectId}/pcsf
   * Returns the full PCSF object for the review screen.
   */
  getPcsf(projectId: string): Observable<unknown> {
    return this.http
      .get<ApiEnvelope<unknown>>(`${this.baseUrl}/${projectId}/pcsf`)
      .pipe(map((res) => res.data));
  }

  /**
   * PATCH /api/v1/projects/{projectId}/pcsf/fields
   * Edits a single PCSF field during the review screen.
   */
  patchPcsfField(projectId: string, request: PatchFieldRequest): Observable<void> {
    return this.http
      .patch<ApiEnvelope<void>>(`${this.baseUrl}/${projectId}/pcsf/fields`, request)
      .pipe(map(() => undefined));
  }

  /**
   * POST /api/v1/projects/{projectId}/pcsf/validate
   * Runs all PCSF validation rules and locks the PCSF if they pass.
   */
  validatePcsf(projectId: string): Observable<PcsfValidateResponse> {
    return this.http
      .post<ApiEnvelope<PcsfValidateResponse>>(
        `${this.baseUrl}/${projectId}/pcsf/validate`,
        {},
      )
      .pipe(map((res) => res.data));
  }

  // ── Template Download ────────────────────────────────────────────────────────

  /**
   * GET /api/v1/projects/template
   * Downloads the Astyann project specification template DOCX from the backend.
   */
  downloadTemplate(): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/template`, { responseType: 'blob' });
  }
}
