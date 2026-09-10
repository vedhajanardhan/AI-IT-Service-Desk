import { useEffect, useState, useCallback } from 'react';
import { useParams } from 'react-router-dom';
import { incidentApi } from '@/api/incidents';
import { aiApi, knowledgeApi, remediationApi } from '@/api/ai-remediation-kb';
import { extractErrorMessage } from '@/api/client';
import { useAuth } from '@/context/AuthContext';
import type {
  AiAnalysis, Comment, Incident, IncidentEvent, KnowledgeArticleSummary, RemediationAction, RemediationExecution,
} from '@/types';
import { LoadingState, ErrorState } from '@/components/States';
import { PriorityBadge, SeverityBadge, StatusBadge } from '@/components/Badges';
import WorkflowStepper from './incident-details/WorkflowStepper';
import AiDiagnosisPanel from './incident-details/AiDiagnosisPanel';
import KnowledgeRecommendationsPanel from './incident-details/KnowledgeRecommendationsPanel';
import RemediationPanel from './incident-details/RemediationPanel';
import CommentsPanel from './incident-details/CommentsPanel';
import TimelinePanel from './incident-details/TimelinePanel';

const ELIGIBLE_FOR_REMEDIATION = ['AI_ANALYZED', 'ASSIGNED', 'REMEDIATION_PENDING'];

export default function IncidentDetailsPage() {
  const { id } = useParams<{ id: string }>();
  const { user } = useAuth();

  const [incident, setIncident] = useState<Incident | null>(null);
  const [analysis, setAnalysis] = useState<AiAnalysis | null>(null);
  const [kbArticles, setKbArticles] = useState<KnowledgeArticleSummary[]>([]);
  const [actions, setActions] = useState<RemediationAction[]>([]);
  const [executions, setExecutions] = useState<RemediationExecution[]>([]);
  const [events, setEvents] = useState<IncidentEvent[]>([]);
  const [comments, setComments] = useState<Comment[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [isAnalyzing, setIsAnalyzing] = useState(false);
  const [resolutionDraft, setResolutionDraft] = useState('');
  const [escalationDraft, setEscalationDraft] = useState('');

  const isEngineerOrAdmin = user?.role === 'ENGINEER' || user?.role === 'ADMIN';

  const loadAll = useCallback(async () => {
    if (!id) return;
    setError(null);
    try {
      const [incidentData, latestAnalysis, actionsData, executionsData, eventsData, commentsData] = await Promise.all([
        incidentApi.get(id),
        aiApi.latest(id),
        remediationApi.listActions(),
        remediationApi.history(id),
        incidentApi.getTimeline(id),
        incidentApi.getComments(id),
      ]);
      setIncident(incidentData);
      setAnalysis(latestAnalysis);
      setActions(actionsData);
      setExecutions(executionsData);
      setEvents(eventsData);
      setComments(commentsData);

      if (latestAnalysis?.relevantKnowledgeTags?.length) {
        knowledgeApi.recommendationsFor(id).then(setKbArticles);
      }
    } catch (err) {
      setError(extractErrorMessage(err));
    } finally {
      setIsLoading(false);
    }
  }, [id]);

  useEffect(() => {
    loadAll();
  }, [loadAll]);

  // Comments now come from a real GET /incidents/{id}/comments endpoint
  // (added alongside this page) - refetch after posting rather than only
  // appending locally, so internal-only filtering stays server-authoritative.
  const handleAddComment = async (body: string, internalOnly: boolean) => {
    if (!id) return;
    await incidentApi.addComment(id, body, internalOnly);
    const refreshed = await incidentApi.getComments(id);
    setComments(refreshed);
  };

  if (isLoading) return <LoadingState label="Loading incident..." />;
  if (error) return <ErrorState message={error} />;
  if (!incident) return null;

  const latestExecution = executions[0] || null;

  return (
    <div className="space-y-6">
      <div className="card p-6">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <h1 className="text-xl font-semibold text-slate-900">{incident.title}</h1>
            <p className="mt-1 text-sm text-slate-500">
              Reported by {incident.reporterName} · {new Date(incident.createdAt).toLocaleString()}
            </p>
          </div>
          <div className="flex flex-wrap gap-2">
            <StatusBadge status={incident.status} />
            <SeverityBadge severity={incident.severity} />
            <PriorityBadge priority={incident.priority} />
          </div>
        </div>

        <p className="mt-4 whitespace-pre-wrap text-sm text-slate-700">{incident.description}</p>

        <div className="mt-4 grid grid-cols-2 gap-4 border-t border-slate-100 pt-4 text-sm sm:grid-cols-4">
          <div>
            <p className="text-xs uppercase tracking-wide text-slate-400">Category</p>
            <p className="mt-0.5 text-slate-700">{incident.category.replace(/_/g, ' ')}</p>
          </div>
          <div>
            <p className="text-xs uppercase tracking-wide text-slate-400">Assigned Engineer</p>
            <p className="mt-0.5 text-slate-700">{incident.assignedEngineerName || 'Unassigned'}</p>
          </div>
          <div>
            <p className="text-xs uppercase tracking-wide text-slate-400">Remediation Attempts</p>
            <p className="mt-0.5 text-slate-700">{incident.remediationAttempts}</p>
          </div>
          <div>
            <p className="text-xs uppercase tracking-wide text-slate-400">Last Updated</p>
            <p className="mt-0.5 text-slate-700">{new Date(incident.updatedAt).toLocaleString()}</p>
          </div>
        </div>

        {incident.status === 'ESCALATED' && incident.escalationReason && (
          <div className="mt-4 rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-700">
            <strong>Escalated:</strong> {incident.escalationReason}
          </div>
        )}
        {incident.status === 'RESOLVED' && incident.resolutionDetails && (
          <div className="mt-4 rounded-lg border border-emerald-200 bg-emerald-50 p-3 text-sm text-emerald-700">
            <strong>Resolved:</strong> {incident.resolutionDetails}
          </div>
        )}

        {isEngineerOrAdmin && !['RESOLVED', 'ESCALATED'].includes(incident.status) && (
          <div className="mt-4 flex flex-wrap items-center gap-2 border-t border-slate-100 pt-4">
            <input
              className="input w-64"
              placeholder="Resolution details..."
              value={resolutionDraft}
              onChange={(e) => setResolutionDraft(e.target.value)}
            />
            <button
              className="btn-primary text-xs"
              disabled={!resolutionDraft.trim()}
              onClick={() => incidentApi.resolve(id!, resolutionDraft).then(loadAll)}
            >
              Manually resolve
            </button>
            <input
              className="input w-64"
              placeholder="Escalation reason..."
              value={escalationDraft}
              onChange={(e) => setEscalationDraft(e.target.value)}
            />
            <button
              className="btn-danger text-xs"
              disabled={!escalationDraft.trim()}
              onClick={() => incidentApi.escalate(id!, escalationDraft).then(loadAll)}
            >
              Escalate
            </button>
          </div>
        )}
        {incident.status === 'RESOLVED' && isEngineerOrAdmin && (
          <div className="mt-4 border-t border-slate-100 pt-4">
            <button className="btn-secondary text-xs" onClick={() => incidentApi.reopen(id!).then(loadAll)}>
              Reopen incident
            </button>
          </div>
        )}
      </div>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
        <div className="space-y-6 lg:col-span-2">
          <AiDiagnosisPanel
            analysis={analysis}
            isAnalyzing={isAnalyzing}
            canAnalyze={isEngineerOrAdmin}
            onAnalyze={async () => {
              if (!id) return;
              setIsAnalyzing(true);
              try {
                const result = await aiApi.analyze(id);
                setAnalysis(result);
                await loadAll();
              } finally {
                setIsAnalyzing(false);
              }
            }}
          />

          <KnowledgeRecommendationsPanel articles={kbArticles} />

          <RemediationPanel
            canManage={isEngineerOrAdmin}
            eligibleForRequest={ELIGIBLE_FOR_REMEDIATION.includes(incident.status)}
            actions={actions}
            executions={executions}
            recommendedActionCode={analysis?.status === 'SUCCEEDED' ? analysis.recommendedActionCode : null}
            onRequest={async (actionCode) => {
              await remediationApi.request(incident.id, actionCode);
              await loadAll();
            }}
            onApprove={async (executionId) => {
              await remediationApi.approve(executionId);
              await loadAll();
            }}
            onReject={async (executionId, reason) => {
              await remediationApi.reject(executionId, reason);
              await loadAll();
            }}
          />

          <CommentsPanel comments={comments} canPostInternal={isEngineerOrAdmin} onAddComment={handleAddComment} />
        </div>

        <div className="space-y-6">
          <WorkflowStepper incident={incident} latestExecution={latestExecution} />
          <TimelinePanel events={events} />
        </div>
      </div>
    </div>
  );
}
