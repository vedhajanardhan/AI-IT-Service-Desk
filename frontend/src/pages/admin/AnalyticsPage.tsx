import { useEffect, useState } from 'react';
import {
  Bar,
  BarChart,
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { analyticsApi } from '@/api/analytics-notifications';
import { extractErrorMessage } from '@/api/client';
import type { AnalyticsSummary, DailyCountPoint } from '@/types';
import { LoadingState, ErrorState } from '@/components/States';

function StatCard({ label, value, suffix = '' }: { label: string; value: string | number; suffix?: string }) {
  return (
    <div className="card p-5">
      <p className="text-2xl font-semibold text-slate-900">
        {value}
        {suffix}
      </p>
      <p className="mt-1 text-sm text-slate-500">{label}</p>
    </div>
  );
}

export default function AnalyticsPage() {
  const [summary, setSummary] = useState<AnalyticsSummary | null>(null);
  const [dailyCounts, setDailyCounts] = useState<DailyCountPoint[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [rangeDays, setRangeDays] = useState(30);

  useEffect(() => {
    const to = new Date();
    const from = new Date(to.getTime() - rangeDays * 24 * 60 * 60 * 1000);
    setIsLoading(true);
    setError(null);
    Promise.all([
      analyticsApi.summary(from.toISOString(), to.toISOString()),
      analyticsApi.incidentsOverTime(from.toISOString(), to.toISOString()),
    ])
      .then(([s, d]) => {
        setSummary(s);
        setDailyCounts(d);
      })
      .catch((err) => setError(extractErrorMessage(err)))
      .finally(() => setIsLoading(false));
  }, [rangeDays]);

  if (isLoading) return <LoadingState />;
  if (error) return <ErrorState message={error} />;
  if (!summary) return null;

  const severityData = Object.entries(summary.incidentsBySeverity).map(([name, value]) => ({ name, value }));
  const categoryData = Object.entries(summary.incidentsByCategory).map(([name, value]) => ({
    name: name.replace(/_/g, ' '),
    value,
  }));

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-semibold text-slate-900">Analytics</h1>
          <p className="text-sm text-slate-500">Server-computed metrics across the incident lifecycle.</p>
        </div>
        <select className="input w-auto" value={rangeDays} onChange={(e) => setRangeDays(Number(e.target.value))}>
          <option value={7}>Last 7 days</option>
          <option value={30}>Last 30 days</option>
          <option value={90}>Last 90 days</option>
        </select>
      </div>

      <div className="grid grid-cols-2 gap-4 md:grid-cols-4">
        <StatCard label="Total incidents" value={summary.totalIncidents} />
        <StatCard label="Open / in progress" value={summary.openIncidents} />
        <StatCard label="Resolved" value={summary.resolvedIncidents} />
        <StatCard label="Escalated" value={summary.escalatedIncidents} />
        <StatCard
          label="Avg resolution time"
          value={summary.averageResolutionTimeMinutes ? Math.round(summary.averageResolutionTimeMinutes) : '—'}
          suffix={summary.averageResolutionTimeMinutes ? ' min' : ''}
        />
        <StatCard
          label="AI analysis success"
          value={summary.aiAnalysisSuccessRatePercent ?? '—'}
          suffix={summary.aiAnalysisSuccessRatePercent !== null ? '%' : ''}
        />
        <StatCard
          label="Remediation success"
          value={summary.remediationSuccessRatePercent ?? '—'}
          suffix={summary.remediationSuccessRatePercent !== null ? '%' : ''}
        />
        <StatCard
          label="Auto-remediated"
          value={summary.autoRemediationPercent ?? '—'}
          suffix={summary.autoRemediationPercent !== null ? '%' : ''}
        />
      </div>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        <div className="card p-5">
          <h2 className="mb-4 text-sm font-semibold text-slate-900">Incidents over time</h2>
          <ResponsiveContainer width="100%" height={260}>
            <LineChart data={dailyCounts}>
              <CartesianGrid strokeDasharray="3 3" stroke="#f1f5f9" />
              <XAxis dataKey="date" tick={{ fontSize: 12 }} />
              <YAxis allowDecimals={false} tick={{ fontSize: 12 }} />
              <Tooltip />
              <Line type="monotone" dataKey="count" stroke="#2563eb" strokeWidth={2} dot={false} />
            </LineChart>
          </ResponsiveContainer>
        </div>

        <div className="card p-5">
          <h2 className="mb-4 text-sm font-semibold text-slate-900">Incidents by severity</h2>
          <ResponsiveContainer width="100%" height={260}>
            <BarChart data={severityData}>
              <CartesianGrid strokeDasharray="3 3" stroke="#f1f5f9" />
              <XAxis dataKey="name" tick={{ fontSize: 12 }} />
              <YAxis allowDecimals={false} tick={{ fontSize: 12 }} />
              <Tooltip />
              <Bar dataKey="value" fill="#3b82f6" radius={[4, 4, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </div>

        <div className="card p-5 lg:col-span-2">
          <h2 className="mb-4 text-sm font-semibold text-slate-900">Incidents by category</h2>
          <ResponsiveContainer width="100%" height={260}>
            <BarChart data={categoryData} layout="vertical" margin={{ left: 40 }}>
              <CartesianGrid strokeDasharray="3 3" stroke="#f1f5f9" />
              <XAxis type="number" allowDecimals={false} tick={{ fontSize: 12 }} />
              <YAxis type="category" dataKey="name" tick={{ fontSize: 12 }} width={140} />
              <Tooltip />
              <Bar dataKey="value" fill="#6366f1" radius={[0, 4, 4, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </div>
      </div>
    </div>
  );
}
