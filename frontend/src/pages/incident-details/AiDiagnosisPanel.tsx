import { Brain, RefreshCw } from 'lucide-react';
import type { AiAnalysis } from '@/types';

export default function AiDiagnosisPanel({
  analysis,
  onAnalyze,
  isAnalyzing,
  canAnalyze,
}: {
  analysis: AiAnalysis | null;
  onAnalyze: () => void;
  isAnalyzing: boolean;
  canAnalyze: boolean;
}) {
  return (
    <div className="card p-5">
      <div className="mb-3 flex items-center justify-between">
        <h2 className="flex items-center gap-2 text-sm font-semibold text-slate-900">
          <Brain className="h-4 w-4 text-brand-600" /> AI Diagnosis
        </h2>
        {canAnalyze && (
          <button onClick={onAnalyze} disabled={isAnalyzing} className="btn-secondary text-xs">
            <RefreshCw className={`h-3.5 w-3.5 ${isAnalyzing ? 'animate-spin' : ''}`} />
            {analysis ? 'Re-analyze' : 'Run AI Analysis'}
          </button>
        )}
      </div>

      {!analysis && (
        <p className="text-sm text-slate-400">No AI analysis has been run for this incident yet.</p>
      )}

      {analysis && analysis.status === 'FAILED' && (
        <p className="text-sm text-red-600">AI analysis failed: {analysis.failureReason}</p>
      )}

      {analysis && analysis.status === 'SUCCEEDED' && (
        <div className="space-y-3">
          <div className="flex flex-wrap items-center gap-2 text-xs">
            <span className="badge bg-indigo-100 text-indigo-700">{analysis.classification.replace(/_/g, ' ')}</span>
            <span className="badge bg-slate-100 text-slate-600">
              Confidence: {Math.round(analysis.confidenceScore * 100)}%
            </span>
            {analysis.recommendedActionCode && (
              <span className="badge bg-amber-100 text-amber-700">
                Suggests: {analysis.recommendedActionCode.replace(/_/g, ' ')}
              </span>
            )}
          </div>
          <div>
            <p className="text-xs font-medium uppercase tracking-wide text-slate-400">Probable root cause</p>
            <p className="mt-1 text-sm text-slate-700">{analysis.rootCause}</p>
          </div>
          <div>
            <p className="text-xs font-medium uppercase tracking-wide text-slate-400">Explanation</p>
            <p className="mt-1 text-sm text-slate-600">{analysis.explanation}</p>
          </div>
        </div>
      )}
    </div>
  );
}
