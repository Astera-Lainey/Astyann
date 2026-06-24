export type ProjectStatus = 'ANALYZING' | string;

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