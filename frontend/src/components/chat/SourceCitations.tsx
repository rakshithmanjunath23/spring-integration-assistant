import { FileText } from 'lucide-react';
import type { SourceCitation } from '../../types';

interface SourceCitationsProps {
  citations: SourceCitation[];
}

export default function SourceCitations({ citations }: SourceCitationsProps) {
  return (
    <div className="mt-3 pt-3 border-t border-border/50">
      <p className="text-[10px] font-medium text-muted-foreground mb-1.5 flex items-center gap-1">
        <FileText className="w-3 h-3" />
        Sources ({citations.length})
      </p>
      <div className="space-y-1">
        {citations.map((citation, i) => (
          <div
            key={i}
            className="text-[10px] text-muted-foreground flex items-center gap-1.5"
          >
            <span className="text-primary shrink-0">•</span>
            <span className="truncate" title={citation.filePath}>
              {citation.filePath.split('/').pop()}
            </span>
            {citation.integrationName && (
              <span className="shrink-0 text-muted-foreground/70">
                [{citation.integrationName}]
              </span>
            )}
            <span className="shrink-0 text-muted-foreground/70">
              ({Math.round(citation.relevanceScore * 100)}%)
            </span>
            {citation.lineRange && (
              <span className="shrink-0 text-muted-foreground/70">
                L{citation.lineRange}
              </span>
            )}
          </div>
        ))}
      </div>
    </div>
  );
}
