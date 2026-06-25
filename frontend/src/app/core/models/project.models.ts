export type ProjectStatus = 'ANALYZING' | string;

export type PcsfStatus = 'DRAFT' | 'GATE_1_PASSED' | string;

export interface ProjectSummary {
  projectId: string;
  title: string;
  description: string;
  status: ProjectStatus;
  createdAt: string;
  updatedAt: string;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  currentPage: number;
  pageSize: number;
}

export interface Project extends ProjectSummary {
  docPath: string;
  docUrl: string;
}

export interface CreateProjectRequest {
  title: string;
  description: string;
  specificationFile: File;
}

export interface GuidedQuestion {
  gqId: string;
  question: string;
}

export interface CreateProjectResponseData {
  projectId: string;
  title: string;
  status: ProjectStatus;
  createdAt: string;
  guidedQuestions: GuidedQuestion[];
  pcsfStatus?: PcsfStatus;
  pendingQuestionsCount?: number;
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

export type ClarificationQuestionType = 'TEXT' | 'TEXTAREA' | 'YES_NO' | 'SINGLE_SELECT' | 'MULTI_SELECT';

export interface ClarificationQuestion {
  id: string;
  inventoryRef: string;
  question: string;
  type: ClarificationQuestionType;
  options: string[] | null;
  placeholder: string | null;
}

export interface AnswerEntry {
  questionId: string;
  answer: string;
}

export interface SubmitAnswersRequest {
  answers: AnswerEntry[];
}

export interface SubmitAnswersResponseData {
  pcsfStatus: PcsfStatus;
}