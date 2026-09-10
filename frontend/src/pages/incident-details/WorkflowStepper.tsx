import { CheckCircle2, Circle, Loader2, XCircle } from 'lucide-react';
import type { Incident, RemediationExecution } from '@/types';

type StepState = 'done' | 'active' | 'pending' | 'failed' | 'skipped';

interface Step {
  label: string;
  state: StepState;
  detail?: string;
}

function computeSteps(incident: Incident, latestExecution: RemediationExecution | null): Step[] {
  const s = incident.status;
  const isAfter = (...statuses: string[]) => statuses.includes(s);

  const aiDone = isAfter('AI_ANALYZED', 'ASSIGNED', 'REMEDIATION_PENDING', 'REMEDIATION_RUNNING', 'VALIDATING', 'RESOLVED', 'ESCALATED', 'REOPENED')
    || incident.priority !== null;
  const remediationRequested = latestExecution !== null;
  const approved = latestExecution && ['APPROVED', 'RUNNING', 'SUCCEEDED', 'FAILED'].includes(latestExecution.status);
  const running = s === 'REMEDIATION_RUNNING' || (latestExecution?.status === 'RUNNING');
  const validating = s === 'VALIDATING';
  const resolved = s === 'RESOLVED';
  const escalated = s === 'ESCALATED';

  const steps: Step[] = [
    { label: 'Incident Reported', state: 'done' },
    {
      label: 'AI Diagnosis',
      state: aiDone ? 'done' : s === 'OPEN' || s === 'TRIAGED' ? 'active' : 'pending',
    },
    {
      label: 'Knowledge Recommendations',
      state: aiDone ? 'done' : 'pending',
    },
    {
      label: 'Suggested Remediation',
      state: remediationRequested ? 'done' : aiDone ? 'active' : 'pending',
    },
    {
      label: 'Policy Validation & Approval',
      state: latestExecution?.status === 'REJECTED' ? 'failed' : approved ? 'done'
        : remediationRequested ? 'active' : 'pending',
      detail: latestExecution?.status === 'PENDING_APPROVAL' ? 'Awaiting engineer approval'
        : latestExecution?.status === 'REJECTED' ? 'Rejected' : undefined,
    },
    {
      label: 'Execution',
      state: running ? 'active' : (approved && (validating || resolved || escalated)) ? 'done'
        : latestExecution?.status === 'FAILED' && !validating ? 'failed' : 'pending',
    },
    {
      label: 'Health Check',
      state: validating ? 'active' : resolved ? 'done'
        : latestExecution?.healthCheckPassed === false ? 'failed' : 'pending',
    },
    {
      label: escalated ? 'Escalated' : 'Resolved',
      state: resolved ? 'done' : escalated ? 'failed' : 'pending',
    },
  ];

  return steps;
}

function StepIcon({ state }: { state: StepState }) {
  if (state === 'done') return <CheckCircle2 className="h-5 w-5 text-emerald-500" />;
  if (state === 'active') return <Loader2 className="h-5 w-5 animate-spin text-brand-500" />;
  if (state === 'failed') return <XCircle className="h-5 w-5 text-red-500" />;
  return <Circle className="h-5 w-5 text-slate-300" />;
}

export default function WorkflowStepper({
  incident,
  latestExecution,
}: {
  incident: Incident;
  latestExecution: RemediationExecution | null;
}) {
  const steps = computeSteps(incident, latestExecution);

  return (
    <div className="card p-5">
      <h2 className="mb-4 text-sm font-semibold text-slate-900">Remediation Pipeline</h2>
      <ol className="space-y-0">
        {steps.map((step, idx) => (
          <li key={step.label} className="flex gap-3">
            <div className="flex flex-col items-center">
              <StepIcon state={step.state} />
              {idx < steps.length - 1 && (
                <div className={`my-0.5 h-6 w-px ${step.state === 'done' ? 'bg-emerald-300' : 'bg-slate-200'}`} />
              )}
            </div>
            <div className="pb-5">
              <p
                className={`text-sm font-medium ${
                  step.state === 'pending' ? 'text-slate-400' : step.state === 'failed' ? 'text-red-600' : 'text-slate-800'
                }`}
              >
                {step.label}
              </p>
              {step.detail && <p className="text-xs text-slate-500">{step.detail}</p>}
            </div>
          </li>
        ))}
      </ol>
    </div>
  );
}
