// Mirrors backend afb.astyann.documentservice domain/dto exactly.
// Service: DocumentService, base path /api/v1/documents.

export type DocumentType =
  | 'SRS'
  | 'FUNCTIONAL_ANALYSIS'
  | 'DESIGN_DOCUMENT'
  | 'DEPLOYMENT_GUIDE'
  | 'ARCHITECTURE_DOCUMENT'
  | 'API_CONTRACT'
  | 'USER_MANUAL'
  | 'DATA_DICTIONARY';

// Mirrors backend DocumentStatus enum exactly: GENERATING | PENDING_APPROVAL | APPROVED | FAILED
// (no separate GENERATED or CHANGE_REQUESTED states — mirrors DiagramStatus one-for-one).
export type DocumentStatus = 'GENERATING' | 'PENDING_APPROVAL' | 'APPROVED' | 'FAILED';

export const DOCUMENT_TYPE_LABELS: Record<DocumentType, string> = {
  SRS: 'Software Requirements Specification',
  FUNCTIONAL_ANALYSIS: 'Analysis Document',
  DESIGN_DOCUMENT: 'Design Document',
  DEPLOYMENT_GUIDE: 'Deployment Guide',
  ARCHITECTURE_DOCUMENT: 'Software Architecture Document',
  API_CONTRACT: 'API Contract',
  USER_MANUAL: 'User Manual',
  DATA_DICTIONARY: 'Data Dictionary',
};

/**
 * Canonical display/request order — also the full set of types to request
 * when generating a project's whole documentation suite.
 */
export const ALL_DOCUMENT_TYPES: DocumentType[] = Object.keys(DOCUMENT_TYPE_LABELS) as DocumentType[];

/** Mirrors DocumentSummaryDTO — returned by generate/regenerate. */
export interface DocumentSummary {
  documentId: string;
  type: DocumentType;
  status: DocumentStatus;
  pageCount: number | null;
  lastError: string | null;
  previousVersionId?: string | null;
}

/** Mirrors DocumentListItemDTO — returned by the list endpoint. */
export interface DocumentListItem {
  documentId: string;
  type: DocumentType;
  status: DocumentStatus;
  version: number;
  generatedAt: string | null;
  lastError: string | null;
}

export interface GenerateDocumentsRequest {
  documentTypes?: DocumentType[];
}

/**
 * Mirrors GenerateDocumentsData. The endpoint responds immediately (202) with
 * every requested type as a GENERATING placeholder — it does not wait for
 * generation to finish. Poll DocumentService.list() until none are GENERATING.
 */
export interface GenerateDocumentsData {
  documents: DocumentSummary[];
}

export interface ApproveDocumentRequest {
  validationNote?: string;
}

/** Mirrors ValidationCheckDTO. */
export interface ValidationCheck {
  name: string;
  passed: boolean;
  detail: string;
}

/** Mirrors ValidationReportDTO. */
export interface ValidationReport {
  checks: ValidationCheck[];
  score: number;
}

/** Mirrors ApproveDocumentResponse. */
export interface ApproveDocumentResponse {
  status: string;
  validationReport: ValidationReport;
  allDocumentsApproved: boolean;
}

/** Mirrors ChangeRequestResponse. */
export interface ChangeRequestResponseData {
  changeRequestId: string;
  status: string;
}
