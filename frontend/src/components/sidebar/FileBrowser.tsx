import { useState, useEffect, useMemo } from 'react';
import {
  Search,
  RefreshCw,
  FileCode2,
  FileText,
  FileType,
  Settings,
  X,
  ChevronRight,
  ChevronDown,
} from 'lucide-react';
import { useFileStore } from '../../stores/fileStore';
import type { FileInfo } from '../../types';

const FILE_TYPE_ICONS: Record<string, typeof FileCode2> = {
  java: FileCode2,
  xml: FileText,
  yaml: Settings,
  yml: Settings,
  properties: FileType,
};

function getFileIcon(fileType: string) {
  const Icon = FILE_TYPE_ICONS[fileType] || FileText;
  return Icon;
}

interface FileTreeGroup {
  integrationName: string;
  boundTypes: Record<string, FileInfo[]>;
}

export default function FileBrowser({ onClose }: { onClose?: () => void }) {
  const {
    files,
    selectedFiles,
    searchQuery,
    toggleFileSelection,
    setSearchQuery,
    triggerReindex,
    loadFiles,
    loadStatus,
    filteredFiles,
  } = useFileStore();

  const [isReindexing, setIsReindexing] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [expandedGroups, setExpandedGroups] = useState<Set<string>>(new Set());

  useEffect(() => {
    async function init() {
      try {
        await loadFiles();
        await loadStatus();
        setLoadError(null);
      } catch {
        setLoadError('Failed to load file list. Please try again.');
      }
    }
    init();
  }, [loadFiles, loadStatus]);

  const filtered = filteredFiles();

  // Group files by integration name then bound type
  const groupedFiles = useMemo((): FileTreeGroup[] => {
    const groups: Record<string, Record<string, FileInfo[]>> = {};

    for (const file of filtered) {
      const intName = file.integrationName || 'Unknown';
      if (!groups[intName]) {
        groups[intName] = {};
      }
      const bound = file.boundType || 'common';
      if (!groups[intName][bound]) {
        groups[intName][bound] = [];
      }
      groups[intName][bound].push(file);
    }

    return Object.entries(groups)
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([integrationName, boundTypes]) => ({ integrationName, boundTypes }));
  }, [filtered]);

  function toggleGroup(key: string) {
    setExpandedGroups((prev) => {
      const next = new Set(prev);
      if (next.has(key)) {
        next.delete(key);
      } else {
        next.add(key);
      }
      return next;
    });
  }

  async function handleReindex() {
    setIsReindexing(true);
    try {
      await triggerReindex('full');
      // Poll status until complete
      const poll = setInterval(async () => {
        await loadStatus();
        const status = useFileStore.getState().indexStatus;
        if (status.status !== 'RUNNING') {
          clearInterval(poll);
          setIsReindexing(false);
          await loadFiles();
        }
      }, 2000);
    } catch {
      setIsReindexing(false);
    }
  }

  return (
    <aside className="w-72 border-r border-border flex flex-col bg-card shrink-0 h-full">
      {/* Header */}
      <div className="p-4 border-b border-border">
        <div className="flex items-center justify-between mb-3">
          <h2 className="font-semibold text-sm">Project Files</h2>
          {onClose && (
            <button
              onClick={onClose}
              className="p-1 rounded hover:bg-muted transition-colors"
              aria-label="Close sidebar"
            >
              <X className="w-4 h-4" />
            </button>
          )}
        </div>

        {/* Search input */}
        <div className="relative">
          <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-muted-foreground" />
          <input
            type="text"
            placeholder="Search files..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="w-full pl-8 pr-3 py-2 text-sm rounded-lg border border-input
                       bg-background focus:outline-none focus:ring-2 focus:ring-ring"
            aria-label="Filter files by name, integration, or type"
          />
        </div>
      </div>

      {/* Error state */}
      {loadError && (
        <div className="px-4 py-3 text-xs text-destructive bg-destructive/10 border-b border-border">
          {loadError}
        </div>
      )}

      {/* File tree */}
      <div className="flex-1 overflow-y-auto p-2">
        {filtered.length === 0 ? (
          <p className="text-sm text-muted-foreground text-center py-8">
            {files.length === 0
              ? 'No files indexed yet'
              : searchQuery
                ? 'No matches'
                : 'No files available'}
          </p>
        ) : (
          groupedFiles.map((group) => {
            const groupKey = group.integrationName;
            const isExpanded = expandedGroups.has(groupKey);

            return (
              <div key={groupKey} className="mb-1">
                {/* Integration name header */}
                <button
                  onClick={() => toggleGroup(groupKey)}
                  className="flex items-center gap-1 w-full px-2 py-1.5 text-xs font-medium
                             rounded hover:bg-muted transition-colors text-left"
                >
                  {isExpanded ? (
                    <ChevronDown className="w-3.5 h-3.5 shrink-0" />
                  ) : (
                    <ChevronRight className="w-3.5 h-3.5 shrink-0" />
                  )}
                  <span className="truncate">{group.integrationName}</span>
                </button>

                {isExpanded &&
                  Object.entries(group.boundTypes)
                    .sort(([a], [b]) => a.localeCompare(b))
                    .map(([boundType, boundFiles]) => {
                      const boundKey = `${groupKey}/${boundType}`;
                      const isBoundExpanded = expandedGroups.has(boundKey);

                      return (
                        <div key={boundKey} className="ml-3">
                          <button
                            onClick={() => toggleGroup(boundKey)}
                            className="flex items-center gap-1 w-full px-2 py-1 text-[11px]
                                       text-muted-foreground rounded hover:bg-muted transition-colors text-left"
                          >
                            {isBoundExpanded ? (
                              <ChevronDown className="w-3 h-3 shrink-0" />
                            ) : (
                              <ChevronRight className="w-3 h-3 shrink-0" />
                            )}
                            <span className="capitalize">{boundType}</span>
                            <span className="ml-auto text-[10px]">{boundFiles.length}</span>
                          </button>

                          {isBoundExpanded &&
                            boundFiles.map((file) => {
                              const isSelected = selectedFiles.includes(file.absolutePath);
                              const Icon = getFileIcon(file.fileType);

                              return (
                                <label
                                  key={file.id}
                                  className={`flex items-center gap-2 ml-4 px-2 py-1.5 rounded cursor-pointer
                                    text-xs hover:bg-muted transition-colors ${
                                      isSelected ? 'bg-primary/10' : ''
                                    }`}
                                >
                                  <input
                                    type="checkbox"
                                    checked={isSelected}
                                    onChange={() => toggleFileSelection(file.absolutePath)}
                                    className="w-3.5 h-3.5 rounded border-input accent-primary"
                                    disabled={
                                      !isSelected && selectedFiles.length >= 20
                                    }
                                  />
                                  <Icon className="w-3.5 h-3.5 shrink-0 text-muted-foreground" />
                                  <span className="truncate">{file.fileName}</span>
                                </label>
                              );
                            })}
                        </div>
                      );
                    })}
              </div>
            );
          })
        )}
      </div>

      {/* Footer */}
      <div className="p-3 border-t border-border">
        <button
          onClick={handleReindex}
          disabled={isReindexing}
          className="w-full flex items-center justify-center gap-2 px-3 py-2 text-xs font-medium rounded-lg
                     bg-primary text-primary-foreground hover:bg-primary/90
                     disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
        >
          <RefreshCw className={`w-3.5 h-3.5 ${isReindexing ? 'animate-spin' : ''}`} />
          {isReindexing ? 'Re-indexing...' : 'Re-index Files'}
        </button>
        <p className="text-[10px] text-muted-foreground text-center mt-2">
          {files.length} files • {selectedFiles.length}/20 selected
        </p>
      </div>
    </aside>
  );
}
