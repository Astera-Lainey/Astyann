import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, catchError, map, of } from 'rxjs';
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

  /**
   * POST /api/v1/versions/snapshots/{snapshotId}/activate
   * Makes the given snapshot the active one for its artifact (e.g. rollback to an
   * earlier version). Deactivates whichever snapshot was previously active.
   */
  activateSnapshot(snapshotId: string): Observable<Snapshot> {
    return this.http
      .post<ApiEnvelope<Snapshot>>(`${this.baseUrl}/snapshots/${snapshotId}/activate`, {})
      .pipe(map((res) => res.data));
  }

  /**
   * Convenience wrapper around getTimeline() for workflow pages (system design,
   * documentation) that just need "what's the active version of this artifact"
   * rather than the full history. Keyed by artifactId (diagramId/documentId/etc).
   * Resolves to an empty map instead of erroring when the project has no
   * timeline yet (nothing approved so far) — safe to call unconditionally.
   */
  getActiveSnapshotsByArtifact(projectId: string): Observable<Map<string, Snapshot>> {
    return this.getTimeline(projectId).pipe(
      map((timeline) => {
        const result = new Map<string, Snapshot>();
        for (const snap of timeline.snapshots) {
          if (snap.active && snap.artifactId) result.set(snap.artifactId, snap);
        }
        return result;
      }),
      catchError(() => of(new Map<string, Snapshot>())),
    );
  }
}
