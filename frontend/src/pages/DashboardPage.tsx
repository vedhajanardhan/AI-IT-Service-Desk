import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { AlertOctagon, CheckCircle2, Clock, ListChecks } from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import { incidentApi } from '@/api/incidents';
import type { IncidentSummary } from '@/types';
import { LoadingState } from '@/components/States';
import { SeverityBadge, StatusBadge } from '@/components/Badges';

function StatCard({ label, value, icon: Icon, tone }: { label: string; value: number; icon: any; tone: string }) {
  return (
    <div className="card flex items-center gap-4 p-5">
      <div className={`flex h-10 w-10 items-center justify-center rounded-lg ${tone}`}>
        <Icon className="h-5 w-5" />
      </div>
      <div>
        <p className="text-2xl font-semibold text-slate-900">{value}</p>
        <p className="text-sm text-slate-500">{label}</p>
      </div>
    </div>
  );
}

export default function DashboardPage() {
  const { user } = useAuth();
  const [incidents, setIncidents] = useState<IncidentSummary[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    incidentApi
      .search({ size: 8 })
      .then((page) => setIncidents(page.content))
      .finally(() => setIsLoading(false));
  }, []);

  if (isLoading) return <LoadingState />;

  const open = incidents.filter((i) => !['RESOLVED', 'ESCALATED'].includes(i.status)).length;
  const resolved = incidents.filter((i) => i.status === 'RESOLVED').length;
  const escalated = incidents.filter((i) => i.status === 'ESCALATED').length;

  return (
    <div className="space-y-6">
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <StatCard label="Total incidents" value={incidents.length} icon={ListChecks} tone="bg-slate-100 text-slate-600" />
        <StatCard label="Open / in progress" value={open} icon={Clock} tone="bg-blue-100 text-blue-600" />
        <StatCard label="Resolved" value={resolved} icon={CheckCircle2} tone="bg-emerald-100 text-emerald-600" />
        <StatCard label="Escalated" value={escalated} icon={AlertOctagon} tone="bg-red-100 text-red-600" />
      </div>

      <div className="card">
        <div className="flex items-center justify-between border-b border-slate-200 px-5 py-4">
          <h2 className="text-sm font-semibold text-slate-900">
            {user?.role === 'EMPLOYEE' ? 'Your recent incidents' : 'Recent incidents across the desk'}
          </h2>
          <Link to="/incidents" className="text-sm font-medium text-brand-600 hover:text-brand-700">
            View all
          </Link>
        </div>
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-slate-100 text-left text-xs uppercase tracking-wide text-slate-400">
              <th className="px-5 py-3 font-medium">Title</th>
              <th className="px-5 py-3 font-medium">Severity</th>
              <th className="px-5 py-3 font-medium">Status</th>
              <th className="px-5 py-3 font-medium">Assigned</th>
            </tr>
          </thead>
          <tbody>
            {incidents.map((incident) => (
              <tr key={incident.id} className="border-b border-slate-50 last:border-0 hover:bg-slate-50">
                <td className="px-5 py-3">
                  <Link to={`/incidents/${incident.id}`} className="font-medium text-slate-800 hover:text-brand-600">
                    {incident.title}
                  </Link>
                </td>
                <td className="px-5 py-3">
                  <SeverityBadge severity={incident.severity} />
                </td>
                <td className="px-5 py-3">
                  <StatusBadge status={incident.status} />
                </td>
                <td className="px-5 py-3 text-slate-500">{incident.assignedEngineerName || '—'}</td>
              </tr>
            ))}
            {incidents.length === 0 && (
              <tr>
                <td colSpan={4} className="px-5 py-10 text-center text-slate-400">
                  No incidents yet.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
