import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, map } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiEnvelope } from '../models/auth.models';
import {
  ClarificationQuestion,
  CreateProjectRequest,
  CreateProjectResponseData,
  Page,
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

  list(query: { page?: number; size?: number; status?: string; sort?: string } = {}): Observable<Page<ProjectSummary>> {
    let params = new HttpParams();
    if (query.page !== undefined) params = params.set('page', query.page);
    if (query.size !== undefined) params = params.set('size', query.size);
    if (query.status) params = params.set('status', query.status);
    if (query.sort) params = params.set('sort', query.sort);

    return this.http
      .get<ApiEnvelope<Page<ProjectSummary>>>(this.baseUrl, { params })
      .pipe(map((res) => res.data));
  }

  create(request: CreateProjectRequest): Observable<CreateProjectResponseData> {
    const formData = new FormData();
    formData.append('title', request.title);
    formData.append('description', request.description);
    formData.append('specificationFile', request.specificationFile);
    return this.http
      .post<ApiEnvelope<CreateProjectResponseData>>(this.baseUrl, formData)
      .pipe(map((res) => res.data));
  }

  getById(projectId: string): Observable<Project> {
    return this.http
      .get<ApiEnvelope<Project>>(`${this.baseUrl}/${projectId}`)
      .pipe(map((res) => res.data));
  }

  submitGuidedQuestions(
    projectId: string,
    request: SubmitGuidedQuestionsRequest,
  ): Observable<SubmitGuidedQuestionsResponseData> {
    return this.http
      .put<ApiEnvelope<SubmitGuidedQuestionsResponseData>>(
        `${this.baseUrl}/${projectId}/guided-questions`,
        request,
      )
      .pipe(map((res) => res.data));
  }

  downloadTemplate(): Observable<Blob> {
    return this.http.get('/assets/AstyannTemplate.docx', { responseType: 'blob' });
  }

  getQuestions(projectId: string): Observable<ClarificationQuestion[]> {
    return this.http
      .get<ApiEnvelope<ClarificationQuestion[]>>(`${this.baseUrl}/${projectId}/questions`)
      .pipe(map((res) => res.data));
  }

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
}