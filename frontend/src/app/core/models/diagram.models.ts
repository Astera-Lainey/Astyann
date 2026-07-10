// Mirrors backend afb.astyann.diagramgeneratorservice domain/dto exactly.
// Service: DiagramGeneratorService, base path /api/v1/uml.

export type DiagramType =
  | 'USE_CASE'
  | 'BUSINESS_CLASS'
  | 'DESIGN_CLASS'
  | 'ACTIVITY'
  | 'BUSINESS_SEQUENCE'
  | 'DESIGN_SEQUENCE'
  | 'COMPONENT'
  | 'DEPLOYMENT'
  | 'PACKAGE'
  | 'ENTITY_RELATIONSHIP';

export type DiagramStatus = 'GENERATING' | 'PENDING_APPROVAL' | 'APPROVED' | 'FAILED';

export type RenderFormat = 'SVG' | 'PNG';

export const DIAGRAM_TYPE_LABELS: Record<DiagramType, string> = {
  USE_CASE: 'Use Case',
  BUSINESS_CLASS: 'Business Class',
  DESIGN_CLASS: 'Design Class',
  ACTIVITY: 'Activity',
  BUSINESS_SEQUENCE: 'Business Sequence',
  DESIGN_SEQUENCE: 'Design Sequence',
  COMPONENT: 'Component',
  DEPLOYMENT: 'Deployment',
  PACKAGE: 'Package',
  ENTITY_RELATIONSHIP: 'Entity Relationship',
};

/**
 * Canonical display/request order — also the full set of types to request
 * when generating a project's whole system design. Sent explicitly on
 * /generate rather than relying on the backend's "omit = all" fallback.
 */
export const ALL_DIAGRAM_TYPES: DiagramType[] = Object.keys(DIAGRAM_TYPE_LABELS) as DiagramType[];

/** Mirrors DiagramSummaryDTO — returned by generate/regenerate. */
export interface DiagramSummary {
  diagramId: string;
  type: DiagramType;
  status: DiagramStatus;
  renderUrl: string;
  lastError: string | null;
  previousVersionId?: string | null;
}

/** Mirrors DiagramListItemDTO — returned by the list endpoint. Leaner: no renderUrl. */
export interface DiagramListItem {
  diagramId: string;
  type: DiagramType;
  status: DiagramStatus;
  updatedAt: string;
  lastError: string | null;
}

export interface GenerateDiagramsRequest {
  diagramTypes?: DiagramType[];
  renderFormat?: RenderFormat;
}

/**
 * Mirrors GenerateDiagramsData. The endpoint responds immediately (202) with
 * every requested type as a GENERATING placeholder — it does not wait for
 * generation to finish. Poll DiagramService.list() until none are GENERATING.
 */
export interface GenerateDiagramsData {
  diagrams: DiagramSummary[];
}

export interface RegenerateDiagramRequest {
  renderFormat?: RenderFormat;
}

export interface ApproveDiagramsRequest {
  diagramIds?: string[];
  approvalComment?: string;
}

/** Mirrors ApproveDiagramsResponse. */
export interface ApproveDiagramsResponse {
  snapshotIds: string[];
  updatedCount: number;
  allDiagramsApproved: boolean;
}

/** Mirrors ChangeRequestResponse. */
export interface ChangeRequestResponseData {
  changeRequestId: string;
  status: string;
}
