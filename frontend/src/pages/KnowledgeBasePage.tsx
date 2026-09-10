import { useEffect, useState } from 'react';
import { Search, BookOpen } from 'lucide-react';
import { knowledgeApi } from '@/api/ai-remediation-kb';
import type { KnowledgeArticleSummary } from '@/types';
import { LoadingState, EmptyState } from '@/components/States';
import { useAuth } from '@/context/AuthContext';

export default function KnowledgeBasePage() {
  const { user } = useAuth();
  const [articles, setArticles] = useState<KnowledgeArticleSummary[]>([]);
  const [keyword, setKeyword] = useState('');
  const [isLoading, setIsLoading] = useState(true);
  const [expandedId, setExpandedId] = useState<string | null>(null);

  const canSeeAllStatuses = user?.role === 'ENGINEER' || user?.role === 'ADMIN';

  useEffect(() => {
    setIsLoading(true);
    const fetcher = canSeeAllStatuses
      ? knowledgeApi.search({ keyword: keyword || undefined, size: 30 })
      : knowledgeApi.searchPublic({ keyword: keyword || undefined, size: 30 });
    fetcher.then((page) => setArticles(page.content)).finally(() => setIsLoading(false));
  }, [keyword, canSeeAllStatuses]);

  return (
    <div className="space-y-4">
      <div>
        <h1 className="text-xl font-semibold text-slate-900">Knowledge Base</h1>
        <p className="text-sm text-slate-500">Search known issues and their fixes before filing a new incident.</p>
      </div>

      <div className="relative max-w-md">
        <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
        <input
          className="input pl-9"
          placeholder="Search articles..."
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
        />
      </div>

      {isLoading ? (
        <LoadingState />
      ) : articles.length === 0 ? (
        <EmptyState title="No articles found" description="Try a different search term." />
      ) : (
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
          {articles.map((article) => (
            <div key={article.id} className="card p-4">
              <div className="mb-2 flex items-start justify-between gap-2">
                <h3 className="flex items-center gap-2 text-sm font-semibold text-slate-900">
                  <BookOpen className="h-4 w-4 flex-shrink-0 text-brand-600" />
                  {article.title}
                </h3>
                {canSeeAllStatuses && (
                  <span className="badge bg-slate-100 text-slate-600 flex-shrink-0">{article.status}</span>
                )}
              </div>
              <p className="text-sm text-slate-600">{article.summary}</p>
              {article.tags.length > 0 && (
                <div className="mt-3 flex flex-wrap gap-1.5">
                  {article.tags.map((tag) => (
                    <span key={tag} className="badge bg-slate-50 text-slate-500">{tag}</span>
                  ))}
                </div>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
