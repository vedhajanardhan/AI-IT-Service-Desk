import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Search } from 'lucide-react';
import { incidentApi } from '@/api/incidents';
import type { IncidentCategory, IncidentSeverity, IncidentStatus, IncidentSummary, PagedResponse } from '@/types';
import { LoadingState, EmptyState, ErrorState } from '@/components/States';
import { SeverityBadge, StatusBadge } from '@/components/Badges';
import { useAuth } from '@/context/AuthContext';
import { extractErrorMessage } from '@/api/client';

const STATUSES: IncidentStatus[] = [
  'OPEN', 'TRIAGED', 'AI_ANALYZED', 'ASSIGNED', 'REMEDIATION_PENDING',
  'REMEDIATION_RUNNING', 'VALIDATING', 'RESOLVED', 'ESCALATED', 'REOPENED',
];
const CATEGORIES: IncidentCategory[] = [
  'APPLICATION_ERROR', 'DATABASE', 'NETWORK', 'AUTHENTICATION', 'PERFORMANCE', 'INFRASTRUCTURE', 'SECURITY', 'OTHER',
];
const SEVERITIES: IncidentSeverity[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];

export default function IncidentListPage() {
  const { user } = useAuth();
  const [page, setPage] = useState<PagedResponse<IncidentSummary> | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [filters, setFilters] = useState<{
    status?: IncidentStatus; category?: IncidentCategory; severity?: IncidentSeverity; keyword?: string;
  }>({});
  const [pageNumber, setPageNumber] = useState(0);

  const load = () => {
    setIsLoading(true);
    setError(null);
    incidentApi
      .search({ ...filters, page: pageNumber, size: 15 })
      .then(setPage)
      .catch((err) => setError(extractErrorMessage(err)))
      .finally(() => setIsLoading(false));
  };

  useEffect(load, [filters, pageNumber]);

  return (
    <div className="space-y-4">
      <div>
        <h1 className="text-xl font-semibold text-slate-900">
          {user?.role === 'EMPLOYEE' ? 'My Incidents' : 'Incident Queue'}
        </h1>
        <p className="text-sm text-slate-500">
          {user?.role === 'EMPLOYEE' ? 'Incidents you have reported.' : 'All incidents across the desk.'}
        </p>
      </div>

      <div className="card flex flex-wrap items-center gap-3 p-4">
        <div className="relative flex-1 min-w-[200px]">
          <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
          <input
            className="input pl-9"
            placeholder="Search title or description..."
            onChange={(e) => setFilters((f) => ({ ...f, keyword: e.target.value || undefined }))}
          />
        </div>
        <select
          className="input w-auto"
          value={filters.status || ''}
          onChange={(e) => setFilters((f) => ({ ...f, status: (e.target.value || undefined) as IncidentStatus }))}
        >
          <option value="">All statuses</option>
          {STATUSES.map((s) => <option key={s} value={s}>{s.replace(/_/g, ' ')}</option>)}
        </select>
        <select
          className="input w-auto"
          value={filters.category || ''}
          onChange={(e) => setFilters((f) => ({ ...f, category: (e.target.value || undefined) as IncidentCategory }))}
        >
          <option value="">All categories</option>
          {CATEGORIES.map((c) => <option key={c} value={c}>{c.replace(/_/g, ' ')}</option>)}
        </select>
        <select
          className="input w-auto"
          value={filters.severity || ''}
          onChange={(e) => setFilters((f) => ({ ...f, severity: (e.target.value || undefined) as IncidentSeverity }))}
        >
          <option value="">All severities</option>
          {SEVERITIES.map((s) => <option key={s} value={s}>{s}</option>)}
        </select>
      </div>

      {error && <ErrorState message={error} />}
      {isLoading ? (
        <LoadingState />
      ) : !page || page.content.length === 0 ? (
        <EmptyState title="No incidents found" description="Try adjusting your filters." />
      ) : (
        <div className="card overflow-hidden">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-slate-100 bg-slate-50 text-left text-xs uppercase tracking-wide text-slate-400">
                <th className="px-5 py-3 font-medium">Title</th>
                <th className="px-5 py-3 font-medium">Category</th>
                <th className="px-5 py-3 font-medium">Severity</th>
                <th className="px-5 py-3 font-medium">Status</th>
                <th className="px-5 py-3 font-medium">Assigned</th>
                <th className="px-5 py-3 font-medium">Created</th>
              </tr>
            </thead>
            <tbody>
              {page.content.map((incident) => (
                <tr key={incident.id} className="border-b border-slate-50 last:border-0 hover:bg-slate-50">
                  <td className="px-5 py-3">
                    <Link to={`/incidents/${incident.id}`} className="font-medium text-slate-800 hover:text-brand-600">
                      {incident.title}
                    </Link>
                  </td>
                  <td className="px-5 py-3 text-slate-500">{incident.category.replace(/_/g, ' ')}</td>
                  <td className="px-5 py-3"><SeverityBadge severity={incident.severity} /></td>
                  <td className="px-5 py-3"><StatusBadge status={incident.status} /></td>
                  <td className="px-5 py-3 text-slate-500">{incident.assignedEngineerName || '—'}</td>
                  <td className="px-5 py-3 text-slate-500">{new Date(incident.createdAt).toLocaleDateString()}</td>
                </tr>
              ))}
            </tbody>
          </table>

          <div className="flex items-center justify-between border-t border-slate-100 px-5 py-3 text-sm text-slate-500">
            <span>
              Page {page.page + 1} of {Math.max(page.totalPages, 1)} · {page.totalElements} total
            </span>
            <div className="flex gap-2">
              <button
                className="btn-secondary"
                disabled={page.page === 0}
                onClick={() => setPageNumber((p) => Math.max(p - 1, 0))}
              >
                Previous
              </button>
              <button className="btn-secondary" disabled={page.last} onClick={() => setPageNumber((p) => p + 1)}>
                Next
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
