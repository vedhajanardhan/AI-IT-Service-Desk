import { History } from 'lucide-react';
import type { IncidentEvent } from '@/types';

export default function TimelinePanel({ events }: { events: IncidentEvent[] }) {
  return (
    <div className="card p-5">
      <h2 className="mb-3 flex items-center gap-2 text-sm font-semibold text-slate-900">
        <History className="h-4 w-4 text-brand-600" /> Incident Timeline
      </h2>
      {events.length === 0 ? (
        <p className="text-sm text-slate-400">No events recorded yet.</p>
      ) : (
        <ol className="space-y-4 border-l border-slate-100 pl-4">
          {events.map((event) => (
            <li key={event.id} className="relative">
              <span className="absolute -left-[21px] top-1 h-2 w-2 rounded-full bg-brand-400" />
              <p className="text-sm text-slate-700">{event.description}</p>
              <p className="text-xs text-slate-400">
                {event.actorName} · {new Date(event.createdAt).toLocaleString()}
              </p>
            </li>
          ))}
        </ol>
      )}
    </div>
  );
}
