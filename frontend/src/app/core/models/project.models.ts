// ── Enums ─────────────────────────────────────────────────────────────────────
// Mirrors backend ProjectStatus enum exactly: ANALYZING | GENERATING | COMPLETED | FAILED
export type ProjectStatus = 'ANALYZING' | 'GENERATING' | 'COMPLETED' | 'FAILED';

// Mirrors backend PcsfStatus enum exactly: DRAFT | INFERRING | UNDER_REVIEW | VALIDATED | FAILED
export type PcsfStatus = 'DRAFT' | 'INFERRING' | 'UNDER_REVIEW' | 'VALIDATED' | 'FAILED';

// ── Project ───────────────────────────────────────────────────────────────────
// Mirrors backend ProjectDTO exactly (fields: projectId, userId, title, description,
// status, creationDate, updatedDate). No docPath/docUrl — not exposed in ProjectDTO.
export interface ProjectSummary {
  projectId: string;
  userId: string;
  title: string;
  description: string;
  status: ProjectStatus;
  creationDate: string;
  updatedDate: string | null;
}

// getById() returns the same ProjectDTO shape as search results.
export type Project = ProjectSummary;

export interface CreateProjectRequest {
  title: string;
  description: string;
  specificationFile: File;
}

// ── Legacy Guided Questions (Sprint 2 — backend still exposes these) ──────────
// Mirrors backend GuidedQuestionDTO: { gqId, question, answer }
export interface GuidedQuestion {
  gqId: string;
  question: string;
  answer: string | null;
}

export interface GuidedQuestionAnswer {
  gqId: string;
  answer: string;
}

export interface SubmitGuidedQuestionsRequest {
  answers: GuidedQuestionAnswer[];
}

export interface SubmitGuidedQuestionsResponseData {
  projectId: string;
  status: ProjectStatus;
}

// ── PCSF Clarification Questions ──────────────────────────────────────────────
// Mirrors backend ClarificationQuestionDto exactly.
export type ClarificationQuestionType =
  | 'TEXT'
  | 'TEXTAREA'
  | 'YES_NO'
  | 'SINGLE_SELECT'
  | 'MULTI_SELECT';

export interface ClarificationQuestion {
  id: string;
  inventoryRef: string;
  targetPath: string;
  priority: number;
  question: string;
  type: ClarificationQuestionType;
  options: string[] | null;
  placeholder: string | null;
  answered: boolean;
  answer: string | null;
}

// Mirrors backend SubmitAnswersRequest.AnswerSubmission: { questionId, answer }
export interface AnswerEntry {
  questionId: string;
  answer: string;
}

export interface SubmitAnswersRequest {
  answers: AnswerEntry[];
}

// Mirrors backend QAResponse: { pcsfStatus, pendingQuestionsCount, missingItems }
export interface SubmitAnswersResponseData {
  pcsfStatus: string;
  pendingQuestionsCount: number;
  missingItems: string[];
}

// ── PCSF Status / Validate ────────────────────────────────────────────────────
// Mirrors backend PcsfStatusResponse: { pcsfStatus, completenessScore, pendingQuestionsCount }
export interface PcsfStatusResponse {
  pcsfStatus: PcsfStatus;
  completenessScore: number;
  pendingQuestionsCount: number;
}

// Mirrors backend PcsfValidateResponse: { valid, pcsfStatus, errors, warnings }
export interface PcsfValidateResponse {
  valid: boolean;
  pcsfStatus: string;
  errors: string[];
  warnings: string[];
}

// Mirrors backend PatchFieldRequest: { path, value }
export interface PatchFieldRequest {
  path: string;
  value: string;
}
