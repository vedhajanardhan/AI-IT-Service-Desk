import { apiClient } from './client';
import type { ApiResponse, AuthResponse, Role } from '@/types';

export interface RegisterPayload {
  email: string;
  password: string;
  fullName: string;
  role: Role;
  department?: string;
}

export interface LoginPayload {
  email: string;
  password: string;
}

export const authApi = {
  register: (payload: RegisterPayload) =>
    apiClient.post<ApiResponse<AuthResponse>>('/auth/register', payload).then((r) => r.data.data),

  login: (payload: LoginPayload) =>
    apiClient.post<ApiResponse<AuthResponse>>('/auth/login', payload).then((r) => r.data.data),
};
