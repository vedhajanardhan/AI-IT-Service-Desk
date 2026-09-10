export type Role = 'EMPLOYEE' | 'ENGINEER' | 'ADMIN';

export interface User {
  id: string;
  email: string;
  fullName: string;
  role: Role;
}

export interface AuthResponse {
  userId: string;
  email: string;
  fullName: string;
  role: Role;
  accessToken: string;
  refreshToken: string;
  expiresInMs: number;
}

export type IncidentStatus =
  | 'OPEN'
  | 'TRIAGED'
  | 'AI_ANALYZED'
  | 'ASSIGNED'
  | 'REMEDIATION_PENDING'
  | 'REMEDIATION_RUNNING'
  | 'VALIDATING'
  | 'RESOLVED'
  | 'ESCALATED'
  | 'REOPENED';

export type IncidentCategory =
  | 'APPLICATION_ERROR'
  | 'DATABASE'
  | 'NETWORK'
  | 'AUTHENTICATION'
  | 'PERFORMANCE'
  | 'INFRASTRUCTURE'
  | 'SECURITY'
  | 'OTHER';

export type IncidentSeverity = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';

export type IncidentPriority = 'P4_LOW' | 'P3_MEDIUM' | 'P2_HIGH' | 'P1_URGENT';

export interface Incident {
  id: string;
  title: string;
  description: string;
  category: IncidentCategory;
  severity: IncidentSeverity;
  priority: IncidentPriority | null;
  status: IncidentStatus;
  reporterId: string;
  reporterName: string;
  assignedEngineerId: string | null;
  assignedEngineerName: string | null;
  resolutionDetails: string | null;
  remediationAttempts: number;
  createdAt: string;
  updatedAt: string;
  resolvedAt: string | null;
  escalatedAt: string | null;
  escalationReason: string | null;
}

export interface IncidentSummary {
  id: string;
  title: string;
  category: IncidentCategory;
  severity: IncidentSeverity;
  status: IncidentStatus;
  assignedEngineerName: string | null;
  createdAt: string;
}

export interface IncidentEvent {
  id: string;
  eventType: string;
  description: string;
  actorName: string;
  previousStatus: string | null;
  newStatus: string | null;
  createdAt: string;
}

export interface Comment {
  id: string;
  authorName: string;
  body: string;
  internalOnly: boolean;
  createdAt: string;
}

export interface AiAnalysis {
  id: string;
  incidentId: string;
  classification: string;
  severityRecommendation: string;
  priorityRecommendation: string;
  rootCause: string;
  confidenceScore: number;
  recommendedActionCode: string | null;
  explanation: string;
  relevantKnowledgeTags: string[];
  status: 'SUCCEEDED' | 'FAILED';
  failureReason: string | null;
  createdAt: string;
}

export interface RemediationAction {
  id: string;
  code: string;
  name: string;
  description: string;
  riskLevel: 'LOW' | 'MEDIUM' | 'HIGH';
  requiredRole: Role;
  approvalRequired: boolean;
  timeoutSeconds: number;
  retryLimit: number;
  enabled: boolean;
}

export type RemediationExecutionStatus =
  | 'PENDING_APPROVAL'
  | 'APPROVED'
  | 'REJECTED'
  | 'RUNNING'
  | 'SUCCEEDED'
  | 'FAILED';

export interface RemediationExecution {
  id: string;
  incidentId: string;
  actionCode: string;
  actionName: string;
  status: RemediationExecutionStatus;
  requestedByName: string;
  approvedByName: string | null;
  attemptNumber: number;
  startedAt: string | null;
  completedAt: string | null;
  failureReason: string | null;
  healthCheckPassed: boolean | null;
  createdAt: string;
}

export interface KnowledgeArticleSummary {
  id: string;
  title: string;
  summary: string;
  category: IncidentCategory | null;
  tags: string[];
  status: string;
  updatedAt: string;
}

export interface KnowledgeArticle extends KnowledgeArticleSummary {
  content: string;
  authorName: string;
  createdAt: string;
}

export interface PagedResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
}

export interface AnalyticsSummary {
  from: string;
  to: string;
  totalIncidents: number;
  openIncidents: number;
  resolvedIncidents: number;
  escalatedIncidents: number;
  incidentsBySeverity: Record<string, number>;
  incidentsByCategory: Record<string, number>;
  averageResolutionTimeMinutes: number | null;
  aiAnalysisSuccessRatePercent: number | null;
  remediationSuccessRatePercent: number | null;
  remediationFailureRatePercent: number | null;
  autoRemediationPercent: number | null;
}

export interface DailyCountPoint {
  date: string;
  count: number;
}

export type NotificationType =
  | 'INCIDENT_ASSIGNED'
  | 'AI_ANALYSIS_COMPLETED'
  | 'REMEDIATION_APPROVAL_REQUIRED'
  | 'REMEDIATION_COMPLETED'
  | 'REMEDIATION_FAILED'
  | 'INCIDENT_ESCALATED'
  | 'INCIDENT_RESOLVED';

export interface AppNotification {
  id: string;
  type: NotificationType;
  message: string;
  relatedIncidentId: string | null;
  read: boolean;
  createdAt: string;
}

export interface ApiResponse<T> {
  success: boolean;
  data: T;
  message?: string;
  errorCode?: string;
}
