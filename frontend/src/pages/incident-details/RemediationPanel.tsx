import { useState } from 'react';
import { CheckCircle2, ChevronRight, ShieldAlert, Wrench, XCircle } from 'lucide-react';
import type { RemediationAction, RemediationExecution } from '@/types';

const STATUS_STYLES: Record<string, string> = {
  PENDING_APPROVAL: 'bg-amber-100 text-amber-700',
  APPROVED: 'bg-blue-100 text-blue-700',
  REJECTED: 'bg-slate-100 text-slate-500',
  RUNNING: 'bg-amber-200 text-amber-800',
  SUCCEEDED: 'bg-emerald-100 text-emerald-700',
  FAILED: 'bg-red-100 text-red-700',
};

export default function RemediationPanel({
  canManage,
  eligibleForRequest,
  actions,
  executions,
  recommendedActionCode,
  onRequest,
  onApprove,
  onReject,
}: {
  canManage: boolean;
  eligibleForRequest: boolean;
  actions: RemediationAction[];
  executions: RemediationExecution[];
  recommendedActionCode: string | null;
  onRequest: (actionCode: string) => Promise<void>;
  onApprove: (executionId: string) => Promise<void>;
  onReject: (executionId: string, reason: string) => Promise<void>;
}) {
  const [selectedAction, setSelectedAction] = useState(recommendedActionCode || '');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [rejectingId, setRejectingId] = useState<string | null>(null);
  const [rejectReason, setRejectReason] = useState('');

  const enabledActions = actions.filter((a) => a.enabled);

  const handleRequest = async () => {
    if (!selectedAction) return;
    setIsSubmitting(true);
    try {
      await onRequest(selectedAction);
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="card p-5">
      <h2 className="mb-3 flex items-center gap-2 text-sm font-semibold text-slate-900">
        <Wrench className="h-4 w-4 text-brand-600" /> Remediation
      </h2>

      {canManage && eligibleForRequest && (
        <div className="mb-4 flex flex-wrap items-center gap-2 rounded-lg border border-slate-100 bg-slate-50 p-3">
          <select className="input w-auto flex-1" value={selectedAction} onChange={(e) => setSelectedAction(e.target.value)}>
            <option value="">Select a remediation action...</option>
            {enabledActions.map((a) => (
              <option key={a.code} value={a.code}>
                {a.name} {a.approvalRequired ? '(needs approval)' : '(auto-approved)'}
              </option>
            ))}
          </select>
          <button className="btn-primary" disabled={!selectedAction || isSubmitting} onClick={handleRequest}>
            <ChevronRight className="h-4 w-4" /> Request
          </button>
        </div>
      )}

      {executions.length === 0 ? (
        <p className="text-sm text-slate-400">No remediation has been attempted for this incident.</p>
      ) : (
        <ul className="space-y-3">
          {executions.map((exec) => (
            <li key={exec.id} className="rounded-lg border border-slate-100 p-3">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <div>
                  <p className="text-sm font-medium text-slate-800">
                    {exec.actionName} <span className="text-xs text-slate-400">· attempt {exec.attemptNumber}</span>
                  </p>
                  <p className="text-xs text-slate-500">
                    Requested by {exec.requestedByName}
                    {exec.approvedByName ? ` · approved by ${exec.approvedByName}` : ''}
                  </p>
                </div>
                <span className={`badge ${STATUS_STYLES[exec.status] || 'bg-slate-100 text-slate-600'}`}>
                  {exec.status.replace(/_/g, ' ')}
                </span>
              </div>

              {exec.failureReason && (
                <p className="mt-2 flex items-start gap-1.5 text-xs text-red-600">
                  <ShieldAlert className="mt-0.5 h-3.5 w-3.5 flex-shrink-0" /> {exec.failureReason}
                </p>
              )}
              {exec.healthCheckPassed !== null && (
                <p className="mt-2 flex items-center gap-1.5 text-xs text-slate-500">
                  {exec.healthCheckPassed ? (
                    <CheckCircle2 className="h-3.5 w-3.5 text-emerald-500" />
                  ) : (
                    <XCircle className="h-3.5 w-3.5 text-red-500" />
                  )}
                  Health check {exec.healthCheckPassed ? 'passed' : 'failed'}
                </p>
              )}

              {canManage && exec.status === 'PENDING_APPROVAL' && (
                <div className="mt-3 flex flex-wrap items-center gap-2">
                  <button className="btn-primary text-xs" onClick={() => onApprove(exec.id)}>
                    Approve & Execute
                  </button>
                  {rejectingId === exec.id ? (
                    <>
                      <input
                        className="input w-48 text-xs"
                        placeholder="Reason for rejection"
                        value={rejectReason}
                        onChange={(e) => setRejectReason(e.target.value)}
                      />
                      <button
                        className="btn-danger text-xs"
                        disabled={!rejectReason}
                        onClick={() => onReject(exec.id, rejectReason).then(() => setRejectingId(null))}
                      >
                        Confirm reject
                      </button>
                    </>
                  ) : (
                    <button className="btn-secondary text-xs" onClick={() => setRejectingId(exec.id)}>
                      Reject
                    </button>
                  )}
                </div>
              )}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
