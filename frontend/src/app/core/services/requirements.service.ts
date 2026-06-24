import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, map } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiEnvelope } from '../models/auth.models';
import {
  ApproveRequirementsResponseData,
  RegenerateRequirementsRequest,
  RegenerateRequirementsResponseData,
  Requirements,
  RequirementsChangeRequest,
  RequirementsChangeRequestResponseData,
} from '../models/requirement.models';

@Injectable({ providedIn: 'root' })
export class RequirementsService {
  private readonly baseUrl = `${environment.apiBaseUrl}/projects`;

  constructor(private readonly http: HttpClient) {}

  getCurrent(projectId: string): Observable<Requirements> {
    return this.http
      .get<ApiEnvelope<Requirements>>(`${this.baseUrl}/${projectId}/requirements`)
      .pipe(map((res) => res.data));
  }

  generate(projectId: string): Observable<Requirements> {
    return this.http
      .post<ApiEnvelope<Requirements>>(`${this.baseUrl}/${projectId}/requirements/generate`, {})
      .pipe(map((res) => res.data));
  }

  approve(projectId: string): Observable<ApproveRequirementsResponseData> {
    return this.http
      .post<ApiEnvelope<ApproveRequirementsResponseData>>(
        `${this.baseUrl}/${projectId}/requirements/approve`,
        {},
      )
      .pipe(map((res) => res.data));
  }

  submitChangeRequest(
    projectId: string,
    request: RequirementsChangeRequest,
  ): Observable<RequirementsChangeRequestResponseData> {
    return this.http
      .post<ApiEnvelope<RequirementsChangeRequestResponseData>>(
        `${this.baseUrl}/${projectId}/requirements/change-requests`,
        request,
      )
      .pipe(map((res) => res.data));
  }

  regenerate(
    projectId: string,
    request: RegenerateRequirementsRequest,
  ): Observable<RegenerateRequirementsResponseData> {
    return this.http
      .post<ApiEnvelope<RegenerateRequirementsResponseData>>(
        `${this.baseUrl}/${projectId}/requirements/regenerate`,
        request,
      )
      .pipe(map((res) => res.data));
  }
}
