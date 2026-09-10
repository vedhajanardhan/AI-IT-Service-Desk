import { BookOpen } from 'lucide-react';
import type { KnowledgeArticleSummary } from '@/types';

export default function KnowledgeRecommendationsPanel({ articles }: { articles: KnowledgeArticleSummary[] }) {
  if (articles.length === 0) return null;

  return (
    <div className="card p-5">
      <h2 className="mb-3 flex items-center gap-2 text-sm font-semibold text-slate-900">
        <BookOpen className="h-4 w-4 text-brand-600" /> Related Knowledge Articles
      </h2>
      <ul className="space-y-3">
        {articles.map((article) => (
          <li key={article.id} className="rounded-lg border border-slate-100 p-3">
            <p className="text-sm font-medium text-slate-800">{article.title}</p>
            <p className="mt-1 text-xs text-slate-500">{article.summary}</p>
          </li>
        ))}
      </ul>
    </div>
  );
}
