export interface ProjectSummary {
  projectId: string;
  title: string;
  description?: string;
  status: string;
  updatedAt: string;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
}
