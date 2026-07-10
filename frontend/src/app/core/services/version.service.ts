import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, map } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiEnvelope } from '../models/auth.models';
import { Snapshot, Timeline } from '../models/version.models';

@Injectable({ providedIn: 'root' })
export class VersionService {
  private readonly baseUrl = `${environment.apiBaseUrl}/versions`;

  constructor(private readonly http: HttpClient) {}

  /**
   * GET /api/v1/versions/{projectId}
   * Returns the full version timeline (every snapshot) for a project.
   */
  getTimeline(projectId: string): Observable<Timeline> {
    return this.http
      .get<ApiEnvelope<Timeline>>(`${this.baseUrl}/${projectId}`)
      .pipe(map((res) => res.data));
  }

  /**
   * GET /api/v1/versions/snapshots/{snapshotId}
   * Returns a single snapshot's detail, looked up by its own ID (not project-scoped).
   */
  getSnapshot(snapshotId: string): Observable<Snapshot> {
    return this.http
      .get<ApiEnvelope<Snapshot>>(`${this.baseUrl}/snapshots/${snapshotId}`)
      .pipe(map((res) => res.data));
  }
}
