import { apiClient } from './client';
import type {
  ApiResponse,
  Comment,
  Incident,
  IncidentCategory,
  IncidentEvent,
  IncidentSeverity,
  IncidentStatus,
  IncidentSummary,
  PagedResponse,
} from '@/types';

export interface CreateIncidentPayload {
  title: string;
  description: string;
  category: IncidentCategory;
  severity: IncidentSeverity;
}

export interface SearchIncidentsParams {
  status?: IncidentStatus;
  category?: IncidentCategory;
  severity?: IncidentSeverity;
  keyword?: string;
  page?: number;
  size?: number;
}

export const incidentApi = {
  create: (payload: CreateIncidentPayload) =>
    apiClient.post<ApiResponse<Incident>>('/incidents', payload).then((r) => r.data.data),

  get: (id: string) => apiClient.get<ApiResponse<Incident>>(`/incidents/${id}`).then((r) => r.data.data),

  search: (params: SearchIncidentsParams) =>
    apiClient
      .get<ApiResponse<PagedResponse<IncidentSummary>>>('/incidents', { params })
      .then((r) => r.data.data),

  assign: (id: string, engineerId: string) =>
    apiClient.post<ApiResponse<Incident>>(`/incidents/${id}/assign`, { engineerId }).then((r) => r.data.data),

  updateStatus: (id: string, newStatus: IncidentStatus, reason?: string) =>
    apiClient
      .patch<ApiResponse<Incident>>(`/incidents/${id}/status`, { newStatus, reason })
      .then((r) => r.data.data),

  resolve: (id: string, resolutionDetails: string) =>
    apiClient
      .post<ApiResponse<Incident>>(`/incidents/${id}/resolve`, { resolutionDetails })
      .then((r) => r.data.data),

  escalate: (id: string, reason: string) =>
    apiClient.post<ApiResponse<Incident>>(`/incidents/${id}/escalate`, { reason }).then((r) => r.data.data),

  reopen: (id: string) => apiClient.post<ApiResponse<Incident>>(`/incidents/${id}/reopen`).then((r) => r.data.data),

  addComment: (id: string, body: string, internalOnly: boolean) =>
    apiClient
      .post<ApiResponse<Comment>>(`/incidents/${id}/comments`, { body, internalOnly })
      .then((r) => r.data.data),

  getTimeline: (id: string) =>
    apiClient.get<ApiResponse<IncidentEvent[]>>(`/incidents/${id}/timeline`).then((r) => r.data.data),

  getComments: (id: string) =>
    apiClient.get<ApiResponse<Comment[]>>(`/incidents/${id}/comments`).then((r) => r.data.data),
};
