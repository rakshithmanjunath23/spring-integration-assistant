import { useState } from 'react';
import type { FileInfo } from '../types';
import { triggerReindex } from '../services/api';

interface SidebarProps {
  files: FileInfo[];
  selectedFiles: string[];
  onToggleFile: (path: string) => void;
  onClose: () => void;
  onReindex: () => void;
}

const FILE_ICONS: Record<string, string> = {
  java: '☕',
  xml: '📄',
  yaml: '⚙️',
  yml: '⚙️',
  properties: '🔧',
  txt: '📝',
  md: '📖',
};

export default function Sidebar({ files, selectedFiles, onToggleFile, onClose, onReindex }: SidebarProps) {
  const [search, setSearch] = useState('');
  const [isReindexing, setIsReindexing] = useState(false);

  const filteredFiles = files.filter(f =>
    f.fileName.toLowerCase().includes(search.toLowerCase()) ||
    f.relativePath?.toLowerCase().includes(search.toLowerCase())
  );

  async function handleReindex() {
    setIsReindexing(true);
    try {
      await triggerReindex();
      setTimeout(() => {
        onReindex();
        setIsReindexing(false);
      }, 2000);
    } catch {
      setIsReindexing(false);
    }
  }

  return (
    <aside className="w-72 border-r border-dark-200 dark:border-dark-700 flex flex-col bg-dark-50 dark:bg-dark-950 shrink-0">
      {/* Header */}
      <div className="p-4 border-b border-dark-200 dark:border-dark-700">
        <div className="flex items-center justify-between mb-3">
          <h2 className="font-semibold text-sm">Project Files</h2>
          <button
            onClick={onClose}
            className="p-1 rounded hover:bg-dark-200 dark:hover:bg-dark-800 transition-colors"
          >
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

        {/* Search */}
        <input
          type="text"
          placeholder="Search files..."
          value={search}
          onChange={e => setSearch(e.target.value)}
          className="w-full px-3 py-2 text-sm rounded-lg border border-dark-200 dark:border-dark-700 
                     bg-white dark:bg-dark-900 focus:outline-none focus:ring-2 focus:ring-primary-500"
        />
      </div>

      {/* File List */}
      <div className="flex-1 overflow-y-auto p-2">
        {filteredFiles.length === 0 ? (
          <p className="text-sm text-dark-400 text-center py-8">
            {files.length === 0 ? 'No files indexed yet' : 'No matching files'}
          </p>
        ) : (
          filteredFiles.map(file => {
            const isSelected = selectedFiles.includes(file.absolutePath);
            return (
              <div
                key={file.id}
                onClick={() => onToggleFile(file.absolutePath)}
                className={`sidebar-item ${isSelected ? 'sidebar-item-selected' : ''}`}
              >
                <span className="text-base shrink-0">
                  {FILE_ICONS[file.fileType] || '📄'}
                </span>
                <div className="min-w-0 flex-1">
                  <p className="text-xs font-medium truncate">{file.fileName}</p>
                  <p className="text-[10px] text-dark-400 dark:text-dark-500 truncate">
                    {file.relativePath}
                  </p>
                </div>
                {isSelected && (
                  <span className="w-2 h-2 rounded-full bg-primary-500 shrink-0" />
                )}
              </div>
            );
          })
        )}
      </div>

      {/* Footer */}
      <div className="p-3 border-t border-dark-200 dark:border-dark-700">
        <button
          onClick={handleReindex}
          disabled={isReindexing}
          className="w-full px-3 py-2 text-xs font-medium rounded-lg bg-primary-600 text-white
                     hover:bg-primary-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
        >
          {isReindexing ? '⏳ Re-indexing...' : '🔄 Re-index Files'}
        </button>
        <p className="text-[10px] text-dark-400 text-center mt-2">
          {files.length} files • {selectedFiles.length} selected
        </p>
      </div>
    </aside>
  );
}
