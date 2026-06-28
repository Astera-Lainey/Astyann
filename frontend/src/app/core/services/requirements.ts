import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, map } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiEnvelope } from '../models/auth.models';
import {
  ApproveRequirementsRequest,
  ApproveRequirementsResponseData,
  RegenerateRequirementsRequest,
  RegenerateRequirementsResponseData,
  RequirementItem,
  Requirements,
  RequirementsChangeRequest,
  RequirementsChangeRequestResponseData,
} from '../models/requirement.models';
import { newIdempotencyKey } from '../utils/idempotency-key';

@Injectable({ providedIn: 'root' })
export class RequirementsService {
  private readonly baseUrl = (projectId: string) =>
    `${environment.apiBaseUrl}/projects/${projectId}/requirements`;

  constructor(private readonly http: HttpClient) {}

  generate(projectId: string): Observable<Requirements> {
    const headers = new HttpHeaders({ 'Idempotency-Key': newIdempotencyKey() });
    return this.http
      .post<ApiEnvelope<Requirements>>(`${this.baseUrl(projectId)}/generate`, {}, { headers })
      .pipe(map((res) => normalizeRequirements(res.data)));
  }

  getCurrent(projectId: string): Observable<Requirements> {
    return this.http
      .get<ApiEnvelope<Requirements>>(this.baseUrl(projectId))
      .pipe(map((res) => normalizeRequirements(res.data)));
  }

  approve(projectId: string, request: ApproveRequirementsRequest = {}): Observable<ApproveRequirementsResponseData> {
    return this.http
      .post<ApiEnvelope<ApproveRequirementsResponseData>>(`${this.baseUrl(projectId)}/approve`, request)
      .pipe(map((res) => res.data));
  }

  submitChangeRequest(projectId: string, request: RequirementsChangeRequest): Observable<RequirementsChangeRequestResponseData> {
    return this.http
      .post<ApiEnvelope<RequirementsChangeRequestResponseData>>(`${this.baseUrl(projectId)}/change-request`, request)
      .pipe(map((res) => res.data));
  }

  regenerate(projectId: string, request: RegenerateRequirementsRequest = {}): Observable<RegenerateRequirementsResponseData> {
    const headers = new HttpHeaders({ 'Idempotency-Key': newIdempotencyKey() });
    return this.http
      .post<ApiEnvelope<RegenerateRequirementsResponseData>>(`${this.baseUrl(projectId)}/regenerate`, request, { headers })
      .pipe(map((res) => res.data));
  }
}

function normalizeRequirements(data: Requirements): Requirements {
  return {
    ...data,
    content: {
      functionalRequirements: (data.content?.functionalRequirements ?? []).map(normalizeItem),
      nonFunctionalRequirements: (data.content?.nonFunctionalRequirements ?? []).map(normalizeItem),
    },
  };
}

function normalizeItem(item: unknown, index: number): RequirementItem {
  if (typeof item === 'string') {
    return { id: `item-${index}`, category: 'General', description: item };
  }
  const c = item as Partial<RequirementItem>;
  return { id: c.id ?? `item-${index}`, category: c.category ?? 'General', description: c.description ?? '' };
}