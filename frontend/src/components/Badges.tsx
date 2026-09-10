import type { IncidentPriority, IncidentSeverity, IncidentStatus } from '@/types';

const STATUS_STYLES: Record<IncidentStatus, string> = {
  OPEN: 'bg-slate-100 text-slate-700',
  TRIAGED: 'bg-sky-100 text-sky-700',
  AI_ANALYZED: 'bg-indigo-100 text-indigo-700',
  ASSIGNED: 'bg-blue-100 text-blue-700',
  REMEDIATION_PENDING: 'bg-amber-100 text-amber-700',
  REMEDIATION_RUNNING: 'bg-amber-200 text-amber-800',
  VALIDATING: 'bg-purple-100 text-purple-700',
  RESOLVED: 'bg-emerald-100 text-emerald-700',
  ESCALATED: 'bg-red-100 text-red-700',
  REOPENED: 'bg-orange-100 text-orange-700',
};

const SEVERITY_STYLES: Record<IncidentSeverity, string> = {
  LOW: 'bg-slate-100 text-slate-600',
  MEDIUM: 'bg-yellow-100 text-yellow-700',
  HIGH: 'bg-orange-100 text-orange-700',
  CRITICAL: 'bg-red-100 text-red-700',
};

const PRIORITY_LABELS: Record<IncidentPriority, string> = {
  P4_LOW: 'P4 · Low',
  P3_MEDIUM: 'P3 · Medium',
  P2_HIGH: 'P2 · High',
  P1_URGENT: 'P1 · Urgent',
};

export function StatusBadge({ status }: { status: IncidentStatus }) {
  return <span className={`badge ${STATUS_STYLES[status]}`}>{status.replace(/_/g, ' ')}</span>;
}

export function SeverityBadge({ severity }: { severity: IncidentSeverity }) {
  return <span className={`badge ${SEVERITY_STYLES[severity]}`}>{severity}</span>;
}

export function PriorityBadge({ priority }: { priority: IncidentPriority | null }) {
  if (!priority) return <span className="badge bg-slate-100 text-slate-500">Unset</span>;
  return <span className="badge bg-slate-100 text-slate-700">{PRIORITY_LABELS[priority]}</span>;
}
