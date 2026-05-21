import { useEffect } from 'react';
import {
  Sun,
  Moon,
  Menu,
  Loader2,
  CheckCircle2,
  AlertCircle,
  Circle,
} from 'lucide-react';
import { useUIStore } from '../../stores/uiStore';
import { useFileStore } from '../../stores/fileStore';

export default function TopBar() {
  const { theme, sidebarOpen, toggleTheme, toggleSidebar } = useUIStore();
  const { selectedFiles, indexStatus, loadStatus } = useFileStore();

  // Poll indexing status every 2 seconds
  useEffect(() => {
    loadStatus();
    const interval = setInterval(loadStatus, 2000);
    return () => clearInterval(interval);
  }, [loadStatus]);

  function getStatusIcon() {
    switch (indexStatus.status) {
      case 'RUNNING':
        return <Loader2 className="w-3.5 h-3.5 animate-spin text-yellow-500" />;
      case 'COMPLETE':
        return <CheckCircle2 className="w-3.5 h-3.5 text-green-500" />;
      case 'ERROR':
        return <AlertCircle className="w-3.5 h-3.5 text-destructive" />;
      default:
        return <Circle className="w-3.5 h-3.5 text-muted-foreground" />;
    }
  }

  function getStatusLabel() {
    switch (indexStatus.status) {
      case 'RUNNING':
        return 'Indexing...';
      case 'COMPLETE':
        return `${indexStatus.indexedFiles} files indexed`;
      case 'ERROR':
        return indexStatus.errorMessage || 'Index error';
      default:
        return 'Idle';
    }
  }

  function getStatusColor() {
    switch (indexStatus.status) {
      case 'RUNNING':
        return 'bg-yellow-100 dark:bg-yellow-900/30 text-yellow-700 dark:text-yellow-400';
      case 'COMPLETE':
        return 'bg-green-100 dark:bg-green-900/30 text-green-700 dark:text-green-400';
      case 'ERROR':
        return 'bg-red-100 dark:bg-red-900/30 text-red-700 dark:text-red-400';
      default:
        return 'bg-muted text-muted-foreground';
    }
  }

  return (
    <header className="h-14 border-b border-border flex items-center px-4 gap-4 shrink-0 bg-card">
      {/* Sidebar toggle */}
      {!sidebarOpen && (
        <button
          onClick={toggleSidebar}
          className="p-2 rounded-lg hover:bg-muted transition-colors"
          aria-label="Open sidebar"
        >
          <Menu className="w-5 h-5" />
        </button>
      )}

      {/* Model name */}
      <div className="flex items-center gap-2">
        <div className="w-2 h-2 rounded-full bg-green-500 animate-pulse" />
        <span className="text-sm font-medium">Grok-3</span>
      </div>

      <div className="flex-1" />

      {/* Indexing status */}
      <div className={`flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs ${getStatusColor()}`}>
        {getStatusIcon()}
        <span>{getStatusLabel()}</span>
      </div>

      {/* Selected file count */}
      {selectedFiles.length > 0 && (
        <span className="text-xs bg-primary/10 text-primary px-2.5 py-1 rounded-full">
          {selectedFiles.length} file{selectedFiles.length !== 1 ? 's' : ''} selected
        </span>
      )}

      {/* Theme toggle */}
      <button
        onClick={toggleTheme}
        className="p-2 rounded-lg hover:bg-muted transition-colors"
        aria-label={`Switch to ${theme === 'light' ? 'dark' : 'light'} mode`}
      >
        {theme === 'dark' ? <Sun className="w-5 h-5" /> : <Moon className="w-5 h-5" />}
      </button>
    </header>
  );
}
