import { FormEvent, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { incidentApi } from '@/api/incidents';
import { extractErrorMessage } from '@/api/client';
import { ErrorState } from '@/components/States';
import type { IncidentCategory, IncidentSeverity } from '@/types';

const CATEGORIES: IncidentCategory[] = [
  'APPLICATION_ERROR', 'DATABASE', 'NETWORK', 'AUTHENTICATION', 'PERFORMANCE', 'INFRASTRUCTURE', 'SECURITY', 'OTHER',
];
const SEVERITIES: IncidentSeverity[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];

export default function CreateIncidentPage() {
  const navigate = useNavigate();
  const [form, setForm] = useState({
    title: '', description: '', category: 'APPLICATION_ERROR' as IncidentCategory, severity: 'MEDIUM' as IncidentSeverity,
  });
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    setIsSubmitting(true);
    try {
      const incident = await incidentApi.create(form);
      navigate(`/incidents/${incident.id}`);
    } catch (err) {
      setError(extractErrorMessage(err));
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="mx-auto max-w-2xl">
      <h1 className="mb-1 text-xl font-semibold text-slate-900">Report an incident</h1>
      <p className="mb-6 text-sm text-slate-500">
        Describe the problem clearly - AI analysis and knowledge-base matching both work off this text.
      </p>

      <form onSubmit={handleSubmit} className="card space-y-4 p-6">
        {error && <ErrorState message={error} />}

        <div>
          <label className="label">Title</label>
          <input
            required
            className="input"
            placeholder="e.g. Checkout API returning intermittent 503s"
            value={form.title}
            onChange={(e) => setForm((f) => ({ ...f, title: e.target.value }))}
          />
        </div>

        <div>
          <label className="label">Description</label>
          <textarea
            required
            rows={6}
            className="input"
            placeholder="What's happening, when did it start, what have you already tried..."
            value={form.description}
            onChange={(e) => setForm((f) => ({ ...f, description: e.target.value }))}
          />
        </div>

        <div className="grid grid-cols-2 gap-4">
          <div>
            <label className="label">Category</label>
            <select
              className="input"
              value={form.category}
              onChange={(e) => setForm((f) => ({ ...f, category: e.target.value as IncidentCategory }))}
            >
              {CATEGORIES.map((c) => (
                <option key={c} value={c}>{c.replace(/_/g, ' ')}</option>
              ))}
            </select>
          </div>
          <div>
            <label className="label">Severity</label>
            <select
              className="input"
              value={form.severity}
              onChange={(e) => setForm((f) => ({ ...f, severity: e.target.value as IncidentSeverity }))}
            >
              {SEVERITIES.map((s) => (
                <option key={s} value={s}>{s}</option>
              ))}
            </select>
          </div>
        </div>

        <button type="submit" disabled={isSubmitting} className="btn-primary w-full">
          {isSubmitting ? 'Submitting...' : 'Submit incident'}
        </button>
      </form>
    </div>
  );
}
