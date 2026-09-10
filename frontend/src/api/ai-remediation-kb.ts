import { apiClient } from './client';
import type {
  AiAnalysis,
  ApiResponse,
  KnowledgeArticleSummary,
  PagedResponse,
  RemediationAction,
  RemediationExecution,
} from '@/types';

export const aiApi = {
  analyze: (incidentId: string) =>
    apiClient.post<ApiResponse<AiAnalysis>>(`/incidents/${incidentId}/ai-analysis`).then((r) => r.data.data),

  history: (incidentId: string) =>
    apiClient.get<ApiResponse<AiAnalysis[]>>(`/incidents/${incidentId}/ai-analysis`).then((r) => r.data.data),

  latest: (incidentId: string) =>
    apiClient
      .get<ApiResponse<AiAnalysis>>(`/incidents/${incidentId}/ai-analysis/latest`)
      .then((r) => r.data.data)
      .catch(() => null),
};

export const knowledgeApi = {
  recommendationsFor: (incidentId: string) =>
    apiClient
      .get<ApiResponse<KnowledgeArticleSummary[]>>(`/incidents/${incidentId}/knowledge-recommendations`)
      .then((r) => r.data.data)
      .catch(() => [] as KnowledgeArticleSummary[]),

  searchPublic: (params: { category?: string; keyword?: string; page?: number; size?: number }) =>
    apiClient
      .get<ApiResponse<PagedResponse<KnowledgeArticleSummary>>>('/knowledge-base/public', { params })
      .then((r) => r.data.data),

  search: (params: { status?: string; category?: string; keyword?: string; page?: number; size?: number }) =>
    apiClient
      .get<ApiResponse<PagedResponse<KnowledgeArticleSummary>>>('/knowledge-base', { params })
      .then((r) => r.data.data),
};

export const remediationApi = {
  listActions: () =>
    apiClient.get<ApiResponse<RemediationAction[]>>('/remediation-actions').then((r) => r.data.data),

  request: (incidentId: string, actionCode: string) =>
    apiClient
      .post<ApiResponse<RemediationExecution>>(`/incidents/${incidentId}/remediation/request`, { actionCode })
      .then((r) => r.data.data),

  history: (incidentId: string) =>
    apiClient
      .get<ApiResponse<RemediationExecution[]>>(`/incidents/${incidentId}/remediation/history`)
      .then((r) => r.data.data),

  approve: (executionId: string) =>
    apiClient
      .post<ApiResponse<RemediationExecution>>(`/remediation-executions/${executionId}/approve`)
      .then((r) => r.data.data),

  reject: (executionId: string, reason: string) =>
    apiClient
      .post<ApiResponse<RemediationExecution>>(`/remediation-executions/${executionId}/reject`, { reason })
      .then((r) => r.data.data),
};
