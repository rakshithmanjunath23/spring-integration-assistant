import type { IndexStatus } from '../types';

interface TopBarProps {
  indexStatus: IndexStatus | null;
  selectedCount: number;
  sidebarOpen: boolean;
  onToggleSidebar: () => void;
  darkMode: boolean;
  onToggleDarkMode: () => void;
}

export default function TopBar({
  indexStatus,
  selectedCount,
  sidebarOpen,
  onToggleSidebar,
  darkMode,
  onToggleDarkMode,
}: TopBarProps) {
  return (
    <header className="h-14 border-b border-dark-200 dark:border-dark-700 flex items-center px-4 gap-4 shrink-0">
      {!sidebarOpen && (
        <button
          onClick={onToggleSidebar}
          className="p-2 rounded-lg hover:bg-dark-100 dark:hover:bg-dark-800 transition-colors"
          title="Open sidebar"
        >
          <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 6h16M4 12h16M4 18h16" />
          </svg>
        </button>
      )}

      <div className="flex items-center gap-2">
        <div className="w-2 h-2 rounded-full bg-green-500 animate-pulse" />
        <span className="text-sm font-medium">Grok AI</span>
      </div>

      <div className="flex-1" />

      {indexStatus && (
        <div className="flex items-center gap-3 text-xs text-dark-500 dark:text-dark-400">
          <span className={`px-2 py-1 rounded-full ${
            indexStatus.status === 'COMPLETE' ? 'bg-green-100 dark:bg-green-900/30 text-green-700 dark:text-green-400' :
            indexStatus.status === 'RUNNING' ? 'bg-yellow-100 dark:bg-yellow-900/30 text-yellow-700 dark:text-yellow-400' :
            'bg-dark-100 dark:bg-dark-800'
          }`}>
            {indexStatus.status === 'RUNNING' ? '⏳ Indexing...' :
             indexStatus.status === 'COMPLETE' ? `✓ ${indexStatus.indexedFiles} files` :
             indexStatus.status}
          </span>
          <span>{indexStatus.totalChunks} chunks</span>
        </div>
      )}

      {selectedCount > 0 && (
        <span className="text-xs bg-primary-100 dark:bg-primary-900/30 text-primary-700 dark:text-primary-300 px-2 py-1 rounded-full">
          {selectedCount} files selected
        </span>
      )}

      <button
        onClick={onToggleDarkMode}
        className="p-2 rounded-lg hover:bg-dark-100 dark:hover:bg-dark-800 transition-colors"
        title="Toggle dark mode"
      >
        {darkMode ? '☀️' : '🌙'}
      </button>
    </header>
  );
}
