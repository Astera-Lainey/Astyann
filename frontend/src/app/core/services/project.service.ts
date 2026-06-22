import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, map } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiEnvelope } from '../models/auth.models';
import { Page, ProjectSummary } from '../models/project.models';

@Injectable({ providedIn: 'root' })
export class ProjectService {
  private readonly baseUrl = `${environment.apiBaseUrl}/projects`;

  constructor(private readonly http: HttpClient) {}

  list(params: {
    size?: number;
    sort?: string;
  }): Observable<Page<ProjectSummary>> {
    return this.http
      .get<ApiEnvelope<Page<ProjectSummary>>>(this.baseUrl, { params })
      .pipe(map((res) => res.data));
  }
}
