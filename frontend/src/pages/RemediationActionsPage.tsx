import { useEffect, useState } from 'react';
import { ShieldCheck, ShieldAlert } from 'lucide-react';
import { remediationApi } from '@/api/ai-remediation-kb';
import type { RemediationAction } from '@/types';
import { LoadingState } from '@/components/States';

const RISK_STYLES: Record<string, string> = {
  LOW: 'bg-emerald-100 text-emerald-700',
  MEDIUM: 'bg-amber-100 text-amber-700',
  HIGH: 'bg-red-100 text-red-700',
};

export default function RemediationActionsPage() {
  const [actions, setActions] = useState<RemediationAction[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    remediationApi.listActions().then(setActions).finally(() => setIsLoading(false));
  }, []);

  if (isLoading) return <LoadingState />;

  return (
    <div className="space-y-4">
      <div>
        <h1 className="text-xl font-semibold text-slate-900">Remediation Catalog</h1>
        <p className="text-sm text-slate-500">
          The fixed set of safe actions the platform can execute. This is the only thing an AI recommendation
          or an engineer's approval is ever allowed to trigger - nothing outside this list.
        </p>
      </div>

      <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
        {actions.map((action) => (
          <div key={action.id} className="card p-4">
            <div className="mb-2 flex items-start justify-between gap-2">
              <h3 className="flex items-center gap-2 text-sm font-semibold text-slate-900">
                {action.enabled ? (
                  <ShieldCheck className="h-4 w-4 flex-shrink-0 text-emerald-600" />
                ) : (
                  <ShieldAlert className="h-4 w-4 flex-shrink-0 text-slate-400" />
                )}
                {action.name}
              </h3>
              <span className={`badge flex-shrink-0 ${RISK_STYLES[action.riskLevel]}`}>{action.riskLevel} risk</span>
            </div>
            <p className="text-sm text-slate-600">{action.description}</p>
            <div className="mt-3 flex flex-wrap gap-1.5 text-xs text-slate-500">
              <span className="badge bg-slate-50">Requires {action.requiredRole}</span>
              <span className="badge bg-slate-50">{action.approvalRequired ? 'Needs approval' : 'Auto-approved'}</span>
              <span className="badge bg-slate-50">{action.timeoutSeconds}s timeout</span>
              <span className="badge bg-slate-50">{action.retryLimit} retries</span>
              {!action.enabled && <span className="badge bg-slate-100 text-slate-500">Disabled</span>}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
