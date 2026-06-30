import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, map } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiEnvelope } from '../models/auth.models';
import { PcsfData } from '../models/requirement.models';
import { PatchFieldRequest, PcsfValidateResponse } from '../models/project.models';

@Injectable({ providedIn: 'root' })
export class RequirementsService {
  private readonly baseUrl = `${environment.apiBaseUrl}/requirements`;

  constructor(private readonly http: HttpClient) {}

  getPcsf(projectId: string): Observable<PcsfData> {
    return this.http
      .get<ApiEnvelope<PcsfData>>(`${this.baseUrl}/${projectId}/pcsf`)
      .pipe(map((res) => res.data));
  }

  approve(projectId: string): Observable<{ status: string; message: string }> {
    return this.http
      .post<ApiEnvelope<{ status: string; message: string }>>(
        `${this.baseUrl}/${projectId}/approve`,
        {},
      )
      .pipe(map((res) => res.data));
  }

  submitChangeRequest(
    projectId: string,
    instructions: string,
  ): Observable<{ changeRequestId: string; status: string }> {
    return this.http
      .post<ApiEnvelope<{ changeRequestId: string; status: string }>>(
        `${this.baseUrl}/${projectId}/change-request`,
        { instructions },
      )
      .pipe(map((res) => res.data));
  }

  regenerate(projectId: string): Observable<void> {
    return this.http
      .post<ApiEnvelope<void>>(`${this.baseUrl}/${projectId}/regenerate`, {})
      .pipe(map(() => undefined));
  }

  patchField(projectId: string, request: PatchFieldRequest): Observable<void> {
    return this.http
      .patch<ApiEnvelope<void>>(`${this.baseUrl}/${projectId}/pcsf/fields`, request)
      .pipe(map(() => undefined));
  }

  validatePcsf(projectId: string): Observable<PcsfValidateResponse> {
    return this.http
      .post<ApiEnvelope<PcsfValidateResponse>>(
        `${this.baseUrl}/${projectId}/pcsf/validate`,
        {},
      )
      .pipe(map((res) => res.data));
  }
}
