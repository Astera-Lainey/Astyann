/**
 * Mirrors backend afb.astyann.versionservice dto/domain exactly.
 * Service: VersionService, base path /api/v1/versions.
 */

export type ArtifactType = 'DOCUMENT' | 'DIAGRAM' | 'CODE' | 'DEPLOYMENT';

/** Mirrors SnapshotDTO. */
export interface Snapshot {
  snapId: string;
  timelineId: string;
  versionName: string | null;
  versionNumber: number | null;
  snapDate: string;
  entrySource: string | null;
  triggerReason: string | null;
  artifactPath: string | null;
  artifactType: ArtifactType | null;
  artifactId: string | null;
  active: boolean;
  diagramId: string | null;
  diagramType: string | null;
  documentId: string | null;
  documentType: string | null;
  codeId: string | null;
  codeLayer: string | null;
  packageId: string | null;
}

/** Mirrors TimelineDTO. */
export interface Timeline {
  timelineId: string;
  projectId: string;
  creationDate: string;
  snapshots: Snapshot[];
}
