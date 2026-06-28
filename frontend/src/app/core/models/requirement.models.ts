import { ApiEnvelope } from './auth.models';

export type RequirementsStatus = 'PENDING_APPROVAL' | 'APPROVED' | string;

export interface RequirementItem {
  id: string;
  category: string;
  description: string;
}

export interface RequirementsContent {
  functionalRequirements: RequirementItem[];
  nonFunctionalRequirements: RequirementItem[];
}

export interface Requirements {
  requirementsId: string;
  status: RequirementsStatus;
  content: RequirementsContent;
  updatedAt?: string;
}

export interface ApproveRequirementsRequest {
  approvalComment?: string;
}

export interface ApproveRequirementsResponseData {
  status: RequirementsStatus;
  snapshotId: string;
  snapshotVersion: string;
  nextStep: string;
}

export interface RequirementsChangeRequest {
  instructions: string;
}

export interface RequirementsChangeRequestResponseData {
  status: RequirementsStatus;
  changeRequestId: string;
}

export interface RegenerateRequirementsRequest {
  changeRequestId?: string;
}

export interface RegenerateRequirementsResponseData {
  requirementsId: string;
  status: RequirementsStatus;
  version: number;
  previousVersionId: string;
}