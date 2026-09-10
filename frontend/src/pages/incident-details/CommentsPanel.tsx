import { useState } from 'react';
import { MessageSquare, Lock } from 'lucide-react';
import type { Comment } from '@/types';

export default function CommentsPanel({
  comments,
  canPostInternal,
  onAddComment,
}: {
  comments: Comment[];
  canPostInternal: boolean;
  onAddComment: (body: string, internalOnly: boolean) => Promise<void>;
}) {
  const [body, setBody] = useState('');
  const [internalOnly, setInternalOnly] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleSubmit = async () => {
    if (!body.trim()) return;
    setIsSubmitting(true);
    try {
      await onAddComment(body, internalOnly);
      setBody('');
      setInternalOnly(false);
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="card p-5">
      <h2 className="mb-3 flex items-center gap-2 text-sm font-semibold text-slate-900">
        <MessageSquare className="h-4 w-4 text-brand-600" /> Comments
      </h2>

      <div className="mb-4 space-y-3">
        {comments.length === 0 && <p className="text-sm text-slate-400">No comments yet.</p>}
        {comments.map((c) => (
          <div key={c.id} className="rounded-lg border border-slate-100 p-3">
            <div className="mb-1 flex items-center gap-2">
              <span className="text-sm font-medium text-slate-800">{c.authorName}</span>
              {c.internalOnly && (
                <span className="badge bg-slate-100 text-slate-500">
                  <Lock className="mr-1 h-3 w-3" /> Internal
                </span>
              )}
              <span className="text-xs text-slate-400">{new Date(c.createdAt).toLocaleString()}</span>
            </div>
            <p className="text-sm text-slate-600">{c.body}</p>
          </div>
        ))}
      </div>

      <div className="space-y-2">
        <textarea
          className="input"
          rows={3}
          placeholder="Add a comment..."
          value={body}
          onChange={(e) => setBody(e.target.value)}
        />
        <div className="flex items-center justify-between">
          {canPostInternal ? (
            <label className="flex items-center gap-2 text-xs text-slate-500">
              <input type="checkbox" checked={internalOnly} onChange={(e) => setInternalOnly(e.target.checked)} />
              Internal note (engineers/admins only)
            </label>
          ) : (
            <span />
          )}
          <button className="btn-primary text-xs" disabled={!body.trim() || isSubmitting} onClick={handleSubmit}>
            Post comment
          </button>
        </div>
      </div>
    </div>
  );
}
