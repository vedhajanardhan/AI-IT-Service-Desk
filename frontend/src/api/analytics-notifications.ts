import { apiClient } from './client';
import type { AnalyticsSummary, ApiResponse, AppNotification, DailyCountPoint, PagedResponse } from '@/types';

export const analyticsApi = {
  summary: (from?: string, to?: string) =>
    apiClient
      .get<ApiResponse<AnalyticsSummary>>('/analytics/summary', { params: { from, to } })
      .then((r) => r.data.data),

  incidentsOverTime: (from?: string, to?: string) =>
    apiClient
      .get<ApiResponse<DailyCountPoint[]>>('/analytics/incidents-over-time', { params: { from, to } })
      .then((r) => r.data.data),
};

export const notificationApi = {
  list: (unreadOnly = false, size = 10) =>
    apiClient
      .get<ApiResponse<PagedResponse<AppNotification>>>('/notifications', { params: { unreadOnly, size } })
      .then((r) => r.data.data),

  unreadCount: () =>
    apiClient
      .get<ApiResponse<{ unreadCount: number }>>('/notifications/unread-count')
      .then((r) => r.data.data.unreadCount),

  markRead: (id: string) => apiClient.post<ApiResponse<void>>(`/notifications/${id}/read`).then((r) => r.data.data),
};
